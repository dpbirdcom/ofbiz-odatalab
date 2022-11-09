package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.NavigationHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.NavigationLinkHandler;
import org.apache.fop.util.ListUtil;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.collections.PagedList;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityFieldMap;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;

import java.util.*;

/**
 * @author scy
 * @date 2022/10/31
 */
public class OdataReader extends OfbizOdataProcessor {
    public static final String MODULE = OdataReader.class.getName();

    public OdataReader(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    /**
     * 查询EntityCount数据
     *
     * @param edmBindingTarget  要查询的实体
     * @param relatedCollection 多段式查询时子对象的范围
     * @return count
     */
    public Long findCount(EdmBindingTarget edmBindingTarget, EntityCollection relatedCollection)
            throws ODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        String entityNameToFind = OdataProcessorHelper.getEntityNameToFind(csdlEntityType, (String) odataContext.get("sapContextId"), edmProvider);
        if (relatedCollection != null) {
            EntityCondition relatedCondition = Util.getEntityCollectionQueryCond(relatedCollection, dynamicViewHolder != null);
            entityCondition = Util.appendCondition(entityCondition, relatedCondition);
        }
        try {
            EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition);
            if (dynamicViewHolder == null) {
                return entityQuery.from(entityNameToFind).queryCount();
            } else {
                Util.printDynamicView(dynamicViewHolder.getDynamicViewEntity(), entityCondition, MODULE);
                return entityQuery.from(dynamicViewHolder.getDynamicViewEntity()).queryCount();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }


    /**
     * 根据Odata请求查询实体列表
     *
     * @return 实体列表
     */
    public EntityCollection findList() throws OfbizODataException {
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        if (csdlEntitySet.getHandler() != null) {
            //从EntitySetHandler查询数据
            return findListBySetHandler();
        } else if (entityHandler != null) {
            //自定义的实现 从接口实例中获取数据
            return findListByEntityHandler(entityHandler);
        } else {
            //ofbiz查询
            return ofbizFindList();
        }
    }

    /**
     * 使用ofbiz从数据库查询数据
     *
     * @return 实体列表
     */
    public EntityCollection ofbizFindList() throws OfbizODataException {
        int listTotalCount;
        List<GenericValue> genericValues;
        EntityCollection entityCollection = new EntityCollection();
        try {
            if (dynamicViewHolder == null) {
                OdataEntityQuery odataEntityQuery = (OdataEntityQuery) OdataEntityQuery.use(delegator).from(modelEntity.getEntityName())
                        .where(entityCondition).orderBy(orderBy).cache(true).cursorScrollInsensitive();
                if (this.filterByDate) {
                    odataEntityQuery = (OdataEntityQuery) odataEntityQuery.filterByDate();
                }
                listTotalCount = (int) odataEntityQuery.queryCount();
                genericValues = odataEntityQuery.queryList(this.skipValue, this.topValue);
            } else {
                PagedList<GenericValue> pagedList = findListWithDynamicView();
                genericValues = pagedList.getData();
                listTotalCount = pagedList.getSize();
            }
            List<Entity> entities = entityCollection.getEntities();
            for (GenericValue genericValue : genericValues) {
                entities.add(makeEntityFromGv(genericValue));
            }
            entityCollection.setCount(listTotalCount);
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, entities, locale, userLogin);
            if (queryOptions != null && queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entities, this.edmEntityType, edmParams);
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return entityCollection;
    }

    /**
     * 自定义的数据查询 从接口实例中获取数据
     *
     * @return EntityCollection
     */
    public EntityCollection findListByEntityHandler(EntityHandler entityHandler) throws OfbizODataException {
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        List<? extends Map<String, Object>> resultList = entityHandler.findList(odataContext, edmEntitySet);
        for (Map<String, Object> result : resultList) {
            entities.add(findResultToEntity(edmEntityType, result));
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        return entityCollection;
    }


    /**
     * 从EntitySet的handler查询实体集合
     *
     * @return EntityCollection
     */
    public EntityCollection findListBySetHandler() throws OfbizODataException {
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
        List<GenericValue> genericValues = groovyHelper.findGenericValues(csdlEntitySet.getHandler(), edmProvider, csdlEntitySet, queryOptions, entityCondition);
        for (GenericValue genericValue : genericValues) {
            entities.add(makeEntityFromGv(genericValue));
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, entities, locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entities, this.edmEntityType, edmParams);
        }
        return entityCollection;
    }

    /**
     * 查询odata-apply数据
     * 使用dynamicView的自带的function属性实现
     *
     * @return 返回Apply数据组装的Entity
     */
    public EntityCollection findApply(UriInfo uriInfo) throws OfbizODataException {
        List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
        DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        //print
        Util.printDynamicView(dynamicViewEntity, entityCondition, module);
        //多段式的apply 添加关联外键的查询条件
        if (uriResourceParts.size() > 1) {
            EntityCondition applyCondition = Util.procApplyCondition(uriResourceParts, delegator, edmProvider, modelEntity);
            if (applyCondition == null) {
                return new EntityCollection();
            }
            entityCondition = Util.appendCondition(entityCondition, applyCondition);
        }
        //apply select
        Set<String> selectSet = new HashSet<>();
        if (groupBySet != null) {
            selectSet.addAll(groupBySet);
        }
        if (aggregateSet != null) {
            selectSet.addAll(aggregateSet);
        }
        EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition).from(dynamicViewEntity)
                .select(selectSet).maxRows(MAX_ROWS).cursorScrollInsensitive();
        List<GenericValue> partialList;
        try (EntityListIterator iterator = entityQuery.queryIterator()) {
            partialList = iterator.getPartialList(skipValue + 1, topValue);
            entityCollection.setCount(iterator.getResultsSizeAfterPartialList());
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
        for (GenericValue genericValue : partialList) {
            OdataOfbizEntity ofbizEntity = new OdataOfbizEntity();
            genericValue.forEach(ofbizEntity::addProperty);
            entities.add(ofbizEntity);
        }
        return entityCollection;
    }

    /**
     * 使用DynamicView查询数据列表
     *
     * @return 数据结果集_PagedList
     */
    private PagedList<GenericValue> findListWithDynamicView() throws OfbizODataException {
        DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
        Util.printDynamicView(dynamicViewEntity, entityCondition, module);
        List<GenericValue> resultList = new ArrayList<>();
        try {
            // select
            Set<String> selectSet = new HashSet<>(this.edmEntityType.getPropertyNames());
            if (UtilValidate.isNotEmpty(fieldsToSelect)) {
                selectSet = new HashSet<>(fieldsToSelect);
                //后面要处理expand，添加外键
                selectSet.addAll(Util.getEntityFk(modelEntity));
            }
            //select排除语义化字段
            selectSet.removeIf(property -> !modelEntity.getAllFieldNames().contains(property));

            //query
            EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition).from(dynamicViewEntity);
            entityQuery = entityQuery.select(selectSet).orderBy(orderBy).maxRows(MAX_ROWS).cursorScrollInsensitive();
            long listCount;
            List<GenericValue> dataItems;
            try (EntityListIterator iterator = entityQuery.queryIterator()) {
                dataItems = iterator.getPartialList(skipValue + 1, topValue);
                listCount = iterator.getResultsSizeAfterPartialList();
            }
            for (GenericValue dataItem : dataItems) {
                resultList.add(Util.convertToTargetGenericValue(delegator, dataItem, this.modelEntity));
            }
            // 注意，这里的PagedList，只有data和size是维护的，其它字段暂时不可靠
            return new PagedList<>(0, 20, (int) listCount, 0, 0, resultList);
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    public OdataOfbizEntity makeEntityFromGv(GenericValue genericValue) throws OfbizODataException {
        if (edmParams.get("edmBindingTarget") != null) {
            return OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmBindingTarget) edmParams.get("edmBindingTarget"),
                    (EdmEntityType) edmParams.get("edmTypeFilter"), genericValue, locale);
        } else {
            return OdataProcessorHelper
                    .genericValueToEntity(delegator, this.edmProvider, this.edmEntityType, genericValue, locale);
        }
    }

    /**
     * 查询单个实体数据
     *
     * @param keyMap       实体主键
     * @param queryOptions queryOptions
     * @return Entity
     */
    public Entity findOne(Map<String, Object> keyMap, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OdataOfbizEntity entity;
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        if (entityHandler != null) {
            //自定义的实现 从接口实例中取数据
            Map<String, Object> resultMap = entityHandler.findOne(odataContext, edmEntitySet, keyMap);
            entity = (OdataOfbizEntity) findResultToEntity(edmEntityType, resultMap);
        } else {
            //ofbiz查询
            GenericValue genericValue = OdataProcessorHelper.readEntityData(odataContext, edmEntitySet, keyMap);
            if (genericValue == null) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Entity not found: " + entityName);
            }
            entity = makeEntityFromGv(genericValue);
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
            if (queryOptions != null && queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, this.edmEntityType, edmParams);
            }
        }
        entity.setKeyMap(keyMap);
        return entity;
    }

    /**
     * 查询单个实体数据 (Singleton)
     *
     * @param withExpand expand
     * @return Entity
     */
    public OdataOfbizEntity findSingleton(boolean withExpand)
            throws OfbizODataException {
        OdataOfbizEntity entity;
        EdmSingleton edmSingleton = (EdmSingleton) this.edmParams.get("edmSingleton");
        Object singletonObject = OdataProcessorHelper.readSingletonObject(dispatcher, edmProvider, edmSingleton.getName(), httpServletRequest, userLogin);
        if (singletonObject instanceof GenericValue) {
            entity = makeEntityFromGv((GenericValue) singletonObject);
        } else {
            entity = objectToEntity(edmSingleton.getEntityType().getFullQualifiedName(), singletonObject);
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, UtilMisc.toList(entity), locale, userLogin);
        if (withExpand && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, edmEntityType, edmParams);
        }
        return entity;
    }

    /**
     * 遍历查询所有的UriResource的数据
     *
     * @param uriInfo Request uriInfo
     * @return 返回每一段的结果
     */
    public List<UriResourceDataInfo> readUriResource(UriInfo uriInfo) throws OfbizODataException {
        List<UriResourceDataInfo> resourceDataInfoList = new ArrayList<>();
        List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
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
                UriResourceDataInfo uriResourceDataInfo = readUriResourceFunction(resourcePart, resourceDataInfoList, uriInfo.getAliases());
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
            EntityCollection relatedEntityCollection = reader.findRelatedList(entity, edmNavigationProperty, queryOptions, navigationPrimaryKey);
            Object entityData = UtilValidate.isEmpty(navigationPrimaryKey) ?
                    relatedEntityCollection : relatedEntityCollection.getEntities().get(0);
            currentUriResourceData.setEntityData(entityData);
        } else {
            Entity entityData = reader.findRelatedOne(entity, edmNavigationProperty);
            currentUriResourceData.setEntityData(entityData);
        }
        return currentUriResourceData;
    }

    private UriResourceDataInfo readUriResourceFunction(UriResource uriResource, List<UriResourceDataInfo> resourceDataInfoList,
                                                        List<AliasQueryOption> aliasParam) throws OfbizODataException {
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
        FunctionProcessor functionProcessor = new FunctionProcessor(odataContext, new HashMap<>(), null);
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


    private List<GenericValue> getGenericValuesFromRelations(GenericValue genericValue, EntityTypeRelAlias relAlias,
                                                             List<String> relations,
                                                             boolean filterByDate) throws GenericEntityException {
        String relation = relations.get(0);
        ModelEntity theModelEntity = genericValue.getModelEntity();
        ModelRelation modelRelation = theModelEntity.getRelation(relation);
        if (modelRelation.getType().contains("one")) {
            GenericValue relGenericValue;
            if (delegator.getModelEntity(modelRelation.getRelEntityName()) instanceof ModelViewEntity) {
                relGenericValue = EntityUtil.getFirst(genericValue.getRelated(relation, null, null, true));
            } else {
                relGenericValue = genericValue.getRelatedOne(relation, true);
            }
            if (relGenericValue == null) {
                return null;
            }
            if (relations.size() == 1) {
                return UtilMisc.toList(relGenericValue);
            } else {
                return getGenericValuesFromRelations(relGenericValue, relAlias, relations.subList(1, relations.size()), filterByDate);
            }
        } else {
            Map<String, Object> relFieldMap = relAlias.getRelationsFieldMap().get(relation);
            List<GenericValue> relGenericValues = genericValue.getRelated(relation, relFieldMap, null, true);
            if (filterByDate) {
                relGenericValues = EntityUtil.filterByDate(relGenericValues);
            }
            if (UtilValidate.isEmpty(relGenericValues)) {
                return null;
            }
            if (relations.size() == 1) {
                if (UtilValidate.isNotEmpty(relAlias.getRelationsCondition())) {
                    EntityCondition entityCondition = relAlias.getRelationsCondition().get(relations.get(0));
                    return EntityUtil.filterByCondition(relGenericValues, entityCondition);
                }
                return relGenericValues;
            } else {
                List<GenericValue> result = new ArrayList<>();
                for (GenericValue relGenericValue : relGenericValues) {
                    List<GenericValue> genericValuesFromRelations = getGenericValuesFromRelations(relGenericValue, relAlias, relations.subList(1, relations.size()), filterByDate);
                    if (UtilValidate.isNotEmpty(genericValuesFromRelations)) {
                        result.addAll(genericValuesFromRelations);
                    }
                }
                return result;
            }
        }
    }

    /**
     * 通过实体查询单个关联数据
     *
     * @param entity                实体
     * @param edmNavigationProperty 要查询的NavigationProperty
     * @return 子对象数据集
     */
    public Entity findRelatedOne(Entity entity, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        String resource = edmProvider.getComponentName() + "Edm.properties";
        String handlerPath = edmProvider.getWebapp() + "." + edmEntityType.getName() + "." + edmNavigationProperty.getName();
        String handlerImpl = EntityUtilProperties.getPropertyValue(resource, handlerPath, delegator);
        Entity relEntity;
        if (UtilValidate.isNotEmpty(handlerImpl)) {
            //自定义查询 从接口实例中获取数据
            relEntity = findRelatedOneByHandler(entity, edmNavigationProperty);
        } else {
            //ofbiz查询
            relEntity = findRelatedOneByRelation(entity, edmNavigationProperty);
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptions.get("selectOption")), UtilMisc.toList(relEntity), locale, userLogin);
        EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) relEntity, navEdmEntityType, edmParams);
        }
        return relEntity;
    }

    public Entity findRelatedOneByHandler(Entity entity, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        //从LinkHandler获取参数
        NavigationLinkHandler navigationLinkHandler = HandlerFactory.getNavigationLinkHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> handlerParam = navigationLinkHandler.getHandlerParam(odataContext, (OdataOfbizEntity) entity, edmNavigationProperty);
        //从NavigationHandler获取数据
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        List<? extends Map<String, Object>> navigationData = navigationHandler.getNavigationData(odataContext, handlerParam, queryOptions);
        Entity resultEntity = null;
        //转换成Entity返回
        if (UtilValidate.isNotEmpty(navigationData)) {
            resultEntity = findResultToEntity(edmNavigationProperty.getType(), navigationData.get(0));
        }
        return resultEntity;
    }

    public Entity findRelatedOneByRelation(Entity entity, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        GenericValue genericValue = ((OdataOfbizEntity) entity).getGenericValue();
        List<GenericValue> relatedGenericValues = OdataProcessorHelper.getRelatedGenericValues(delegator, genericValue, csdlNavigationProperty.getRelAlias(), filterByDate);
        GenericValue relatedGenericValue = EntityUtil.getFirst(relatedGenericValues);
        if (relatedGenericValue == null) {
            return null;
        }
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        return OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, relatedGenericValue, locale);
    }


    /**
     * 通过实体查询关联数据集合
     *
     * @param entity                实体
     * @param edmNavigationProperty 要查询的NavigationProperty
     * @return 子对象数据集
     */
    public EntityCollection findRelatedList(Entity entity, EdmNavigationProperty edmNavigationProperty,
                                            Map<String, QueryOption> queryOptions, Map<String, Object> navPrimaryKey) throws OfbizODataException {
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entityList;
        String resource = edmProvider.getComponentName() + "Edm.properties";
        String handlerPath = edmProvider.getWebapp() + "." + edmEntityType.getName() + "." + edmNavigationProperty.getName();
        String handlerImpl = EntityUtilProperties.getPropertyValue(resource, handlerPath, delegator);
        if (UtilValidate.isNotEmpty(handlerImpl)) {
            //自定义查询 从接口实例中获取数据
            entityList = findRelatedListByHandler(entity, edmNavigationProperty);
        } else {
            //ofbiz查询
            entityList = findRelatedListByRelation(entity, edmNavigationProperty);
        }
        if (UtilValidate.isEmpty(entityList)) {
            return entityCollection;
        }
        if (UtilValidate.isNotEmpty(navPrimaryKey)) {
            Entity relationEntity = Util.getEntityCollectionOne(entityList, navPrimaryKey);
            if (relationEntity == null) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Entity not found:" + edmNavigationProperty.getName());
            }
            entityCollection.getEntities().add(relationEntity);
            return entityCollection;
        }
        entityCollection.getEntities().addAll(entityList);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        //filter、orderby、page
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        OrderByOption orderbyOption = (OrderByOption) queryOptions.get("orderByOption");
        if (filterOption != null || orderbyOption != null) {
            Util.filterEntityCollection(entityCollection, filterOption, orderbyOption, navCsdlEntityType,
                    edmProvider, delegator, dispatcher, userLogin, locale);
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptions.get("selectOption")), entityCollection.getEntities(), locale, userLogin);
        //expand
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            //添加子对象的edmBindingTarget
            Map<String, Object> edmParamOption = new HashMap<>();
            EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            if (edmBindingTarget != null) {
                EdmEntitySet navigationTargetEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
                edmParamOption.put("edmBindingTarget", navigationTargetEntitySet);
            }
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entityCollection.getEntities(), edmNavigationProperty.getType(), edmParamOption);
        }
        return entityCollection;
    }

    public List<Entity> findRelatedListByHandler(Entity entity, EdmNavigationProperty edmNavigationProperty) throws
            OfbizODataException {
        //从LinkHandler获取参数
        List<Entity> resultEntities = new ArrayList<>();
        NavigationLinkHandler navigationLinkHandler = HandlerFactory.getNavigationLinkHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> linkParameter = navigationLinkHandler.getHandlerParam(odataContext, (OdataOfbizEntity) entity, edmNavigationProperty);
        //从NavigationHandler获取数据
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        List<? extends Map<String, Object>> navigationData = navigationHandler.getNavigationData(odataContext, linkParameter, queryOptions);
        //转换成Entity返回
        for (Map<String, Object> navigationDatum : navigationData) {
            Entity navigationEntity = findResultToEntity(edmNavigationProperty.getType(), navigationDatum);
            resultEntities.add(navigationEntity);
        }
        return resultEntities;
    }


    public List<Entity> findRelatedListByRelation(Entity entity, EdmNavigationProperty edmNavigationProperty) throws
            OfbizODataException {
        try {
            OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
            GenericValue genericValue = ofbizEntity.getGenericValue();
            if (genericValue == null) {
                genericValue = Util.entityToGenericValue(delegator, ofbizEntity, modelEntity.getEntityName());
            }
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            List<GenericValue> relatedList = getGenericValuesFromRelations(genericValue, relAlias, relAlias.getRelations(), csdlNavigationProperty.isFilterByDate());
            if (UtilValidate.isEmpty(relatedList)) {
                return null;
            }
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            List<Entity> entities = new ArrayList<>();
            for (GenericValue related : relatedList) {
                OdataOfbizEntity rowEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, related, locale);
                entities.add(rowEntity);
            }
            return entities;
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }

    }

    /**
     * 自定义查询结果转成Entity
     */
    private Entity findResultToEntity(EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        if (resultMap instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return Util.mapToEntity(csdlEntityType, resultMap);
        }
    }

}
