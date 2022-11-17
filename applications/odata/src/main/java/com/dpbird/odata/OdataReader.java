package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.HandlerResults;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
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
     * @param edmEntityType     要查询的实体
     * @return count
     */
    public Long findCount(EdmEntityType edmEntityType) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityNameToFind = OdataProcessorHelper.getEntityNameToFind(csdlEntityType, (String) odataContext.get("sapContextId"), edmProvider);
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
     * odata_apply数据查询
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
     * 查询单个实体数据
     *
     * @param keyMap       实体主键
     * @param queryOptions queryOptions
     * @return Entity
     */
    public Entity findOne(Map<String, Object> keyMap, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        //从接口实例中读取数据
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        Map<String, Object> resultMap = entityHandler.findOne(odataContext, edmEntitySet, keyMap);
        OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(edmEntityType, resultMap);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, this.edmEntityType);
        }
        entity.setKeyMap(keyMap);
        return entity;
    }

    /**
     * 查询实体数据列表
     *
     * @return 实体列表
     */
    public EntityCollection findList() throws OfbizODataException {
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
        if (csdlEntitySet.getHandler() != null) {
            //从EntitySetHandler查询数据
            GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
            List<GenericValue> genericValues = groovyHelper.findGenericValues(csdlEntitySet.getHandler(), edmProvider, csdlEntitySet, queryOptions, entityCondition);
            for (GenericValue genericValue : genericValues) {
                entities.add(makeEntityFromGv(genericValue));
            }
        } else {
            //从接口实例获取数据
            EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
            HandlerResults results = entityHandler.findList(odataContext, edmEntitySet, queryOptions, null);
            entityCollection.setCount(results.getResultCount());
            for (Map<String, Object> result : results.getResultData()) {
                entities.add(findResultToEntity(edmEntityType, result));
            }
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, entities, locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entities, this.edmEntityType);
        }
        return entityCollection;
    }

    /**
     * 使用ofbiz从数据库查询数据
     *
     * @return 实体列表
     */
    public HandlerResults ofbizFindList(EntityCondition otherCondition) throws OfbizODataException {
        try {
            entityCondition = Util.appendCondition(entityCondition, otherCondition);
            if (dynamicViewHolder == null) {
                OdataEntityQuery odataEntityQuery = (OdataEntityQuery) OdataEntityQuery.use(delegator).from(modelEntity.getEntityName())
                        .where(entityCondition).orderBy(orderBy).cache(true).cursorScrollInsensitive();
                if (this.filterByDate) {
                    odataEntityQuery = (OdataEntityQuery) odataEntityQuery.filterByDate();
                }
                int listTotalCount = (int) odataEntityQuery.queryCount();
                List<GenericValue> genericValues = odataEntityQuery.queryList(this.skipValue, this.topValue);
                return new HandlerResults(listTotalCount, genericValues);
            } else {
                return findListWithDynamicView();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 使用DynamicView查询数据列表
     *
     * @return 数据结果集_PagedList
     */
    private HandlerResults findListWithDynamicView() throws OfbizODataException {
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
            int listCount;
            List<GenericValue> dataItems;
            try (EntityListIterator iterator = entityQuery.queryIterator()) {
                dataItems = iterator.getPartialList(skipValue + 1, topValue);
                listCount = iterator.getResultsSizeAfterPartialList();
            }
            for (GenericValue dataItem : dataItems) {
                resultList.add(Util.convertToTargetGenericValue(delegator, dataItem, this.modelEntity));
            }
            return new HandlerResults(listCount, resultList);
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
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, edmEntityType);
        }
        return entity;
    }


    /**
     * 通过实体查询单个关联数据
     *
     * @param entity                实体
     * @param edmNavigationProperty 要查询的NavigationProperty
     * @return 子对象数据集
     */
    public Entity findRelatedOne(Entity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<UriResourceDataInfo> uriResourceDataInfos) throws OfbizODataException {
        //从Navigation接口实例中获取查询参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> navigationParam = navigationHandler.getNavigationParam(odataContext, (OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, queryOptions, uriResourceDataInfos);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        //根据调用参数从Handler获取数据
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        HandlerResults handlerList = entityHandler.findList(odataContext, null, queryOptions, navigationParam);
        List<? extends Map<String, Object>> resultData = handlerList.getResultData();
        Entity relEntity = findResultToEntity(edmNavigationProperty.getType(), resultData.get(0));
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptions.get("selectOption")), UtilMisc.toList(relEntity), locale, userLogin);
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) relEntity, edmNavigationProperty.getType());
        }
        return relEntity;
    }

    /**
     * 通过实体查询关联数据集合
     *
     * @param entity                实体
     * @param edmNavigationProperty 要查询的NavigationProperty
     * @return 子对象数据集
     */
    public EntityCollection findRelatedList(Entity entity, EdmNavigationProperty edmNavigationProperty,
                                            Map<String, QueryOption> queryOptions, Map<String, Object> navPrimaryKey,
                                            List<UriResourceDataInfo> resourceDataInfos) throws OfbizODataException {
        EntityCollection entityCollection = new EntityCollection();
        //从Navigation获取调用参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> navigationParam = navigationHandler.getNavigationParam(odataContext, (OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, queryOptions, resourceDataInfos);
        navigationParam.put("primaryKey", navPrimaryKey);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        //根据调用参数从Handler获取数据
        EntityHandler navEntityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        HandlerResults results = navEntityHandler.findList(odataContext, null, queryOptions, navigationParam);
        if (UtilValidate.isEmpty(results) || UtilValidate.isEmpty(results.getResultData())) {
            return entityCollection;
        }
        for (Map<String, Object> navigationDatum : results.getResultData()) {
            Entity navigationEntity = findResultToEntity(edmNavigationProperty.getType(), navigationDatum);
            entityCollection.getEntities().add(navigationEntity);
        }
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
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entityCollection.getEntities(), edmNavigationProperty.getType());
        }
        return entityCollection;
    }

    public List<GenericValue> findRelatedGenericValue(Entity entity, EdmNavigationProperty edmNavigationProperty) throws
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
            return getGenericValuesFromRelations(genericValue, relAlias, relAlias.getRelations(), csdlNavigationProperty.isFilterByDate());
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
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
     * 查询结果转成Entity
     */
    private Entity findResultToEntity(EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        if (resultMap instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return Util.mapToEntity(csdlEntityType, resultMap);
        }
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
