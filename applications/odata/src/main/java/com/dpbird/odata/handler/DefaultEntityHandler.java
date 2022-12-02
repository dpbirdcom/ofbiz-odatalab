package com.dpbird.odata.handler;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import groovy.lang.Delegate;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.tomcat.jni.Local;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 如果Entity没有声明自定义的Handler, 会使用这个缺省的ofbiz查询
 *
 * @date 2022/11/14
 */
public class DefaultEntityHandler implements EntityHandler {

    @Override
    public Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey) throws OfbizODataException {
        return OdataProcessorHelper.readEntityData(odataContext, edmBindingTarget, primaryKey);
    }

    @Override
    public HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, QueryOption> queryOptions,
                                   Map<String, Object> navigationParam) throws OfbizODataException {
        //普通的查询
        if (UtilValidate.isEmpty(navigationParam)) {
            OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmBindingTarget", edmBindingTarget));
            return reader.ofbizFindList(null);
        }

        //Navigation查询 根据参数去做查询
        EntityCondition primaryKeyCond = null;
        if (navigationParam.get("primaryKey") != null) {
            Map<String, Object> primaryKey = (Map<String, Object>) navigationParam.get("primaryKey");
            primaryKeyCond = EntityCondition.makeCondition(primaryKey);
        }
        HandlerResults handlerResults = new HandlerResults();
        if (navigationParam.containsKey("condition")) {
            EntityCondition entityCondition = (EntityCondition) navigationParam.get("condition");
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
            entityCondition = Util.appendCondition(entityCondition, primaryKeyCond);
            OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmEntityType", edmNavigationProperty.getType()));
            handlerResults = reader.ofbizFindList(entityCondition);
        } else if (navigationParam.containsKey("entity")) {
            OdataOfbizEntity entity = (OdataOfbizEntity) navigationParam.get("entity");
            EdmEntityType edmEntityType = (EdmEntityType) navigationParam.get("edmEntityType");
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
            Map<String, Object> edmParam = UtilMisc.toMap("edmEntityType", edmEntityType);
            OdataReader reader = new OdataReader(odataContext, new HashMap<>(), edmParam);
            List<GenericValue> relatedList = reader.findRelatedGenericValue(entity, edmNavigationProperty, primaryKeyCond);
            if (UtilValidate.isNotEmpty(relatedList)) {
                handlerResults = new HandlerResults(relatedList.size(), relatedList);
            }
        }
        return handlerResults;
    }

    @Override
    public Map<String, Object> create(Entity entityToWrite, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> createParam) throws OfbizODataException {
        Locale locale = (Locale) odataContext.get("locale");
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        GenericValue genericValue;
        if (UtilValidate.isEmpty(createParam)) {
            //创建
            genericValue = OdataProcessorHelper.createGenericValue(dispatcher, delegator, edmBindingTarget.getEntityType(),
                    entityToWrite, edmProvider, userLogin);
            OdataOfbizEntity entityCreated = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmBindingTarget, edmBindingTarget.getEntityType(), genericValue, locale);
            //创建Attribute
            if (csdlEntityType.getAttrEntityName() != null || csdlEntityType.getAttrNumericEntityName() != null || csdlEntityType.getAttrDateEntityName() != null) {
                OdataProcessorHelper.createAttrGenericValue(csdlEntityType, entityToWrite, userLogin, genericValue.getPrimaryKey(), dispatcher);
            }
            //创建RelAlias
            OdataProcessorHelper.createSemanticFields(httpServletRequest, delegator, dispatcher, edmProvider, entityToWrite, entityCreated, locale, userLogin);
        } else {
            //多段式创建
            OdataOfbizEntity entity = (OdataOfbizEntity) createParam.get("entity");
            EdmEntityType edmEntityType = (EdmEntityType) createParam.get("edmEntityType");
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) createParam.get("edmNavigationProperty");
            OfbizCsdlEntityType entityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty) entityType.getNavigationProperty(edmNavigationProperty.getName());
            genericValue = OdataProcessorHelper.createRelatedGenericValue(entityToWrite, entity, navigationProperty.getRelAlias(), dispatcher, delegator, userLogin);
        }
        return genericValue;
    }

    @Override
    public Map<String, Object> update(Map<String, Object> primaryKey, Entity entityToWrite, Map<String, Object> odataContext,
                                      EdmBindingTarget edmBindingTarget, Map<String, Object> updateParam) throws OfbizODataException {
        Locale locale = (Locale) odataContext.get("locale");
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        Map<String, Object> fieldMapToWrite = Util.entityToMap(entityToWrite);
        OfbizCsdlEntityType csdlEntityType;
        if (UtilValidate.isNotEmpty(updateParam)) {
            //多段式更新
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) updateParam.get("edmNavigationProperty");
            csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
        } else {
            csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        }
        if (delegator.getModelEntity(csdlEntityType.getOfbizEntity()).isField("lastModifiedDate")) {
            fieldMapToWrite.put("lastModifiedDate", UtilDateTime.nowTimestamp());
        }
        //更新实体
        GenericValue genericValue = OdataProcessorHelper.updateGenericValue(dispatcher, delegator, csdlEntityType.getOfbizEntity(), primaryKey, fieldMapToWrite, userLogin);
        OdataOfbizEntity updatedEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, csdlEntityType, genericValue, locale);
        //更新Attribute
        if (UtilValidate.isNotEmpty(csdlEntityType.getAttrEntityName()) ||
                UtilValidate.isNotEmpty(csdlEntityType.getAttrNumericEntityName()) ||
                UtilValidate.isNotEmpty(csdlEntityType.getAttrDateEntityName())) {
            OdataProcessorHelper.updateAttrGenericValue(csdlEntityType, fieldMapToWrite, userLogin, primaryKey, dispatcher, delegator);
        }
        List<Entity> updatedEntities = OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher,
                edmProvider, null, UtilMisc.toList(updatedEntity), locale, userLogin);
        updatedEntity = (OdataOfbizEntity) updatedEntities.get(0);
        //更新RelAlias
        OdataProcessorHelper.updateSemanticFields(dispatcher, edmProvider, entityToWrite, updatedEntity, locale, userLogin);
        return genericValue;
    }

    @Override
    public void delete(Entity entityToDelete, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget) {

    }
}
