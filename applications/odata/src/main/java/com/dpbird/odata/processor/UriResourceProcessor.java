package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.AliasQueryOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有的UriResource处理
 *
 * @date 2022/11/18
 */
public class UriResourceProcessor {
    private final Map<String, Object> odataContext;
    private final Map<String, QueryOption> queryOptions;

    public UriResourceProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions) {
        this.odataContext = odataContext;
        this.queryOptions = queryOptions;
    }

    /**
     * 遍历查询所有的UriResource的数据
     *
     * @return 返回每一段的结果
     */
    public List<UriResourceDataInfo> readUriResource(List<UriResource> uriResourceParts, List<AliasQueryOption> aliases) throws OfbizODataException {
        List<UriResourceDataInfo> resourceDataInfoList = new ArrayList<>();
        Map<String, QueryOption> queryOptions = new HashMap<>();
        for (int i = 0; i < uriResourceParts.size(); i++) {
            //只有最后一段需要使用queryOption
            if (i == uriResourceParts.size() - 1) {
                queryOptions = this.queryOptions;
            }
            UriResource resourcePart = uriResourceParts.get(i);
            if (resourcePart instanceof UriResourceEntitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePart;
                //如果不含主键 并且下一段是Function时是空数据
                if (UtilValidate.isEmpty(uriResourceEntitySet.getKeyPredicates()) &&
                        uriResourceParts.size() > i + 1 && uriResourceParts.get(i + 1) instanceof UriResourceFunction) {
                    resourceDataInfoList.add(new UriResourceDataInfo(uriResourceEntitySet.getEntitySet(), uriResourceEntitySet.getEntityType(), resourcePart, null));
                } else {
                    UriResourceDataInfo uriResourceDataInfo = readUriResourceEntitySet(resourcePart, queryOptions);
                    resourceDataInfoList.add(uriResourceDataInfo);
                }
            }
            if (resourcePart instanceof UriResourceSingleton) {
                UriResourceDataInfo uriResourceDataInfo = readUriResourceSingleton(resourcePart, queryOptions);
                resourceDataInfoList.add(uriResourceDataInfo);
            }
            if (resourcePart instanceof UriResourceNavigation) {
                UriResourceDataInfo uriResourceDataInfo = readUriResourceNavigation(resourcePart, queryOptions, resourceDataInfoList);
                resourceDataInfoList.add(uriResourceDataInfo);
            }
            if (resourcePart instanceof UriResourceFunction) {
                UriResourceDataInfo uriResourceDataInfo = readUriResourceFunction(resourcePart, resourceDataInfoList, aliases, queryOptions);
                resourceDataInfoList.add(uriResourceDataInfo);
            }
        }
        return resourceDataInfoList;
    }

    private UriResourceDataInfo readUriResourceEntitySet(UriResource uriResource, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        Map<String, Object> primaryKey = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntitySet.getEntityType());
        OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmBindingTarget", edmEntitySet));
        Object entityData = UtilValidate.isEmpty(primaryKey) ? reader.findList() : reader.findOne(primaryKey, queryOptions);
        return new UriResourceDataInfo(uriResourceEntitySet.getEntitySet(), uriResourceEntitySet.getEntityType(), uriResource, entityData);
    }

    private UriResourceDataInfo readUriResourceSingleton(UriResource uriResource, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        UriResourceSingleton uriResourceEntitySet = (UriResourceSingleton) uriResource;
        EdmBindingTarget edmBindingTarget = uriResourceEntitySet.getSingleton();
        OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmSingleton", edmBindingTarget));
        return new UriResourceDataInfo(edmBindingTarget, edmBindingTarget.getEntityType(), uriResource, reader.findSingleton(true));
    }

    private UriResourceDataInfo readUriResourceNavigation(UriResource uriResource, Map<String, QueryOption> queryOptions,
                                                          List<UriResourceDataInfo> resourceDataInfos) throws OfbizODataException {
        //navigation
        UriResourceNavigation resourceNavigation = (UriResourceNavigation) uriResource;
        EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
        EdmEntityType navigationEntityType = edmNavigationProperty.getType();
        Map<String, Object> navigationPrimaryKey = Util.uriParametersToMap(resourceNavigation.getKeyPredicates(), navigationEntityType);
        //last uriResource
        UriResourceDataInfo uriResourceDataInfo = ListUtil.getLast(resourceDataInfos);
        Entity entity = (Entity) uriResourceDataInfo.getEntityData();
        EdmEntityType edmEntityType = uriResourceDataInfo.getEdmEntityType();
        EdmEntitySet navigationTargetEntitySet = null;
        if (uriResourceDataInfo.getEdmBindingTarget() != null) {
            navigationTargetEntitySet = Util.getNavigationTargetEntitySet(uriResourceDataInfo.getEdmBindingTarget(), edmNavigationProperty);
        }
        UriResourceDataInfo currentUriResourceData = new UriResourceDataInfo(navigationTargetEntitySet, navigationEntityType, uriResource, null);
        //query
        OdataReader reader = new OdataReader(odataContext, new HashMap<>(), UtilMisc.toMap("edmEntityType", edmEntityType));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        boolean isCollection = resourceIsCollection(uriResourceDataInfo.getUriResource(), uriResource, edmProvider);
        if (isCollection) {
            EntityCollection relatedEntityCollection = reader.findRelatedList(entity, edmNavigationProperty, queryOptions, navigationPrimaryKey, resourceDataInfos);
            Object entityData = UtilValidate.isEmpty(navigationPrimaryKey) ?
                    relatedEntityCollection : relatedEntityCollection.getEntities().get(0);
            currentUriResourceData.setEntityData(entityData);
        } else {
            Entity entityData = reader.findRelatedOne(entity, edmEntityType, edmNavigationProperty, resourceDataInfos);
            currentUriResourceData.setEntityData(entityData);
        }
        return currentUriResourceData;
    }

    private UriResourceDataInfo readUriResourceFunction(UriResource uriResource, List<UriResourceDataInfo> resourceDataInfoList,
                                                        List<AliasQueryOption> aliasParam, Map<String, QueryOption> queryOption) throws OfbizODataException {
        UriResourceFunction uriResourceFunction = (UriResourceFunction) uriResource;
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmEntityType returnEdmEntityType = (EdmEntityType) edmFunction.getReturnType().getType();
        Map<String, Object> parameters = Util.uriParametersToMap(uriResourceFunction.getParameters(), edmFunction, aliasParam);
        EdmBindingTarget edmBindingTarget = null;
        if (edmFunction.isBound()) {
            //添加bound参数
            UriResourceDataInfo uriResourceDataInfo = ListUtil.getLast(resourceDataInfoList);
            edmBindingTarget = uriResourceDataInfo.getEdmBindingTarget();
            Object entityData = uriResourceDataInfo.getEntityData();
            String boundParamName = edmFunction.getParameterNames().get(0);
            Object boundParam = null;
            if (entityData != null) {
                boolean boundCollection = edmFunction.getParameter(boundParamName).isCollection();
                boundParam = boundCollection ? ((EntityCollection) entityData).getEntities() : entityData;
            }
            parameters.put(boundParamName, boundParam);
        }
        UriResourceDataInfo currentUriResourceData = new UriResourceDataInfo(edmBindingTarget, returnEdmEntityType, uriResource, null);
        FunctionProcessor functionProcessor = new FunctionProcessor(odataContext, queryOption, null);
        if (edmFunction.getReturnType().isCollection()) {
            EntityCollection entityCollection = functionProcessor.processFunctionEntityCollection(uriResourceFunction, parameters, edmBindingTarget);
            currentUriResourceData.setEntityData(entityCollection);
        } else {
            Entity entity = functionProcessor.processFunctionEntity(uriResourceFunction, parameters, edmBindingTarget);
            currentUriResourceData.setEntityData(entity);
        }
        return currentUriResourceData;
    }

    private static boolean resourceIsCollection(UriResource uriResource, UriResource nextUriResource, OfbizAppEdmProvider edmProvider) throws OfbizODataException {
        EdmEntityType edmEntityType = null;
        if (uriResource instanceof UriResourceEntitySet) {
            UriResourceEntitySet resourceEntitySet = (UriResourceEntitySet) uriResource;
            edmEntityType = resourceEntitySet.getEntityType();
        }
        if (uriResource instanceof UriResourceSingleton) {
            UriResourceSingleton resourceSingleton = (UriResourceSingleton) uriResource;
            edmEntityType = resourceSingleton.getEntityType();
        }
        if (uriResource instanceof UriResourceFunction) {
            UriResourceFunction resourceFunction = (UriResourceFunction) uriResource;
            edmEntityType = (EdmEntityType) resourceFunction.getFunction().getReturnType().getType();
        }
        if (uriResource instanceof UriResourceNavigation) {
            UriResourceNavigation resourceNavigation = (UriResourceNavigation) uriResource;
            edmEntityType = resourceNavigation.getProperty().getType();
        }
        if (edmEntityType != null) {
            OfbizCsdlEntityType entityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            CsdlNavigationProperty navigationProperty = entityType.getNavigationProperty(nextUriResource.getSegmentValue());
            return navigationProperty.isCollection();
        } else {
            throw new OfbizODataException("Unknown type :" + uriResource.getSegmentValue());
        }
    }


}
