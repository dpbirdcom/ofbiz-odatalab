package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.HandlerResults;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;

import java.util.*;
import java.util.stream.Collectors;

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
     * @param edmEntityType 要查询的实体
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
     * odata-Apply查询
     */
    public EntityCollection findApply(EntityCondition applyCondition, Map<String, QueryOption> queryOptionMap) throws OfbizODataException {
        //从接口实例中读取数据
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        HandlerResults handlerResults = entityHandler.findApply(odataContext, edmEntitySet, queryOptionMap, applyCondition);
        for (Map<String, Object> genericValue : handlerResults.getResultData()) {
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
        if (UtilValidate.isEmpty(resultMap)) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Not found.");
        }
        OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(edmEntityType, resultMap);
        entity.addOdataParts(new OdataParts(edmEntitySet, edmEntityType, null, entity));
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), UtilMisc.toList(entity), this.edmEntityType);
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
                OdataOfbizEntity resultToEntity = (OdataOfbizEntity) findResultToEntity(edmEntityType, result);
                resultToEntity.addOdataParts(new OdataParts(edmEntitySet, edmEntityType, null, resultToEntity));
                entities.add(resultToEntity);
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
     * 使用dynamicView的自带的function进行apply查询
     *
     * @param applyCondition 多段式查询时的范围
     * @return 返回Apply数据组装的Entity
     */
    public HandlerResults ofbizFindApply(EntityCondition applyCondition) throws OfbizODataException {
        DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
        //print
        Util.printDynamicView(dynamicViewEntity, entityCondition, module);
        if (applyCondition != null) {
            entityCondition = Util.appendCondition(entityCondition, applyCondition);
        }
        EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition).from(dynamicViewEntity)
                .select(applySelect).orderBy(orderBy).maxRows(MAX_ROWS).cursorScrollInsensitive();
        try (EntityListIterator iterator = entityQuery.queryIterator()) {
            List<GenericValue> partialList = iterator.getPartialList(skipValue + 1, topValue);
            int resultSize = iterator.getResultsSizeAfterPartialList();
            return new HandlerResults(resultSize, partialList);
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
            Set<String> selectSet = new HashSet<>();
            if (UtilValidate.isNotEmpty(fieldsToSelect)) {
                selectSet = new HashSet<>(fieldsToSelect);
                //后面要处理expand，添加外键
                selectSet.addAll(Util.getEntityFk(modelEntity));
            } else {
                OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
                for (String propertyName : edmEntityType.getPropertyNames()) {
                    OfbizCsdlProperty property = (OfbizCsdlProperty) csdlEntityType.getProperty(propertyName);
                    if (property == null && csdlEntityType.getBaseType() != null) {
                        property = csdlEntityType.getBaseTypeProperty(propertyName, edmProvider);
                    }
                    if (property != null && property.getRelAlias() == null && property.getOfbizFieldName() != null) {
                        selectSet.add(property.getOfbizFieldName());
                    }
                }
            }

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
            return OdataProcessorHelper.genericValueToEntity(dispatcher, this.edmProvider,
                    (EdmBindingTarget) edmParams.get("edmBindingTarget"),
                    (EdmEntityType) edmParams.get("edmTypeFilter"), genericValue, locale);
        } else {
            return OdataProcessorHelper
                    .genericValueToEntity(dispatcher, this.edmProvider, this.edmEntityType, genericValue, locale);
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
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), UtilMisc.toList(entity), edmEntityType);
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
    public Entity findRelatedOne(Entity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                 Map<String, QueryOption> queryOptionMap) throws OfbizODataException {
        //从Navigation接口实例中获取查询参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> navigationParam = navigationHandler.getNavigationParam(odataContext, (OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, queryOptions);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        //根据调用参数从Handler获取数据
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        HandlerResults handlerList = entityHandler.findList(odataContext, null, queryOptions, navigationParam);
        List<? extends Map<String, Object>> resultData = handlerList.getResultData();
        if (resultData == null) {
            return null;
        }
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
        OdataOfbizEntity relEntity = (OdataOfbizEntity) findResultToEntity(edmNavigationProperty.getType(), resultData.get(0));
        List<OdataParts> odataPartsList = new ArrayList<>(ofbizEntity.getOdataParts());
        EdmBindingTarget navBindingTarget = null;
        if (edmParams.get("edmBindingTarget") != null) {
            EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            navBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
        }
        odataPartsList.add(new OdataParts(navBindingTarget, edmNavigationProperty.getType(), null, relEntity));
        relEntity.setOdataParts(odataPartsList);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptionMap.get("selectOption")), UtilMisc.toList(relEntity), locale, userLogin);
        if (UtilValidate.isNotEmpty(queryOptionMap) && queryOptionMap.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptionMap.get("expandOption"), UtilMisc.toList(relEntity), edmNavigationProperty.getType());
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
                                            Map<String, QueryOption> queryOptions, Map<String, Object> navPrimaryKey) throws OfbizODataException {
        EntityCollection entityCollection = new EntityCollection();
        //从Navigation获取调用参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> navigationParam = navigationHandler.getNavigationParam(odataContext, (OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, queryOptions);
        navigationParam.put("primaryKey", navPrimaryKey);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        //根据调用参数从Handler获取数据
        EntityHandler navEntityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        HandlerResults results = navEntityHandler.findList(odataContext, null, queryOptions, navigationParam);
        if (UtilValidate.isEmpty(results) || UtilValidate.isEmpty(results.getResultData())) {
            return entityCollection;
        }
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
        for (Map<String, Object> navigationDatum : results.getResultData()) {
            OdataOfbizEntity navigationEntity = (OdataOfbizEntity) findResultToEntity(edmNavigationProperty.getType(), navigationDatum);
            List<OdataParts> odataParts = new ArrayList<>(ofbizEntity.getOdataParts());
            EdmBindingTarget navBindingTarget = null;
            if (edmParams.get("edmBindingTarget") != null) {
                EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
                navBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
            }
            odataParts.add(new OdataParts(navBindingTarget, edmNavigationProperty.getType(), null, navigationEntity));
            navigationEntity.setOdataParts(odataParts);
            entityCollection.getEntities().add(navigationEntity);
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        //filter、orderby、page
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        OrderByOption orderbyOption = (OrderByOption) queryOptions.get("orderByOption");
        if (filterOption != null) {
            Util.filterEntityCollection(entityCollection, filterOption, orderbyOption, navCsdlEntityType,
                    edmProvider, delegator, dispatcher, userLogin, locale, csdlNavigationProperty.isFilterByDate());
        }
        entityCollection.setCount(entityCollection.getEntities().size());
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptions.get("selectOption")), entityCollection.getEntities(), locale, userLogin);
        if (Util.isExtraOrderby(orderbyOption, navCsdlEntityType, delegator)) {
            Util.orderbyEntityCollection(entityCollection, orderbyOption, edmNavigationProperty.getType(), edmProvider);
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entityCollection.getEntities(), edmNavigationProperty.getType());
        }
        return entityCollection;
    }

    /**
     * 只有ofbiz缺省的Navigation查询会使用，这里跳过Handler，直接通过ofbiz查询所有关联数据
     */
    public void addDefaultExpandLink(Collection<Entity> entityList, EdmNavigationProperty edmNavigationProperty,
                                                            Map<String, QueryOption> queryOptions) throws OfbizODataException {
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        OrderByOption orderbyOption = (OrderByOption) queryOptions.get("orderByOption");
        EntityCondition condition = null;
        //filter的条件
        if (filterOption != null) {
//            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
            OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
            try {
                condition = (EntityCondition) filterOption.getExpression().accept(expressionVisitor);
            } catch (ExpressionVisitException | ODataApplicationException e) {
                throw new OfbizODataException(e.getMessage());
            }
        }
        EdmEntityType edmNavigationPropertyType = edmNavigationProperty.getType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        List<String> orderbyList = Util.convertOrderbyToField(navCsdlEntityType, orderbyOption);
        List<GenericValue> genericValueList = entityList.stream().map(e -> ((OdataOfbizEntity) e).getGenericValue()).collect(Collectors.toList());
        //find
        List<GenericValue> relatedGenericList = getAllDataFromRelations(genericValueList, csdlNavigationProperty, condition, orderbyList);
        if (UtilValidate.isEmpty(relatedGenericList)) {
            return;
        }
        List<Entity> relatedEntityList = new ArrayList<>();
        for (GenericValue genericValue : relatedGenericList) {
            relatedEntityList.add(findResultToEntity(edmNavigationPropertyType, genericValue));
        }
        //获取relation关联字段
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        List<ModelKeyMap> relKeyMaps = modelEntity.getRelation(csdlNavigationProperty.getRelAlias().getRelations().get(0)).getKeyMaps();
        List<String> fieldNames = new ArrayList<>();
        List<String> relFieldNames = new ArrayList<>();
        for (ModelKeyMap relKeyMap : relKeyMaps) {
            fieldNames.add(relKeyMap.getFieldName());
            relFieldNames.add(relKeyMap.getRelFieldName());
        }
        //添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, relatedEntityList, locale, userLogin);
        Map<GenericValue, Entity> expandDataMap = new LinkedHashMap<>();
        for (int i = 0; i < relatedEntityList.size(); i++) {
            expandDataMap.put(relatedGenericList.get(i), relatedEntityList.get(i));
        }
        //处理下一层expand
        recursionExpand(entityList, expandDataMap, edmNavigationProperty, fieldNames, relFieldNames);
        //将查询出来的数据根据主外键进行匹配
        if (edmNavigationProperty.isCollection()) {
            Map<String, Entity> mainEntityMap = new HashMap<>();
            for (Entity entity : entityList) {
                OdataOfbizEntity mainEntity = (OdataOfbizEntity) entity;
                String fkString = getFieldShortValue(fieldNames, mainEntity.getGenericValue());
                mainEntityMap.put(fkString, entity);
            }
            for (Map.Entry<GenericValue, Entity> entry : expandDataMap.entrySet()) {
                String fkString = getFieldShortValue(relFieldNames, entry.getKey());
                addEntityToLink(mainEntityMap.get(fkString), edmNavigationProperty, entry.getValue());
            }
            //分页InlineEntitySet
            if (queryOptions.get("skipOption") != null || queryOptions.get("topOption") != null) {
                for (Entity entity : entityList) {
                    Link navigationLink = entity.getNavigationLink(edmNavigationProperty.getName());
                    EntityCollection entityCollection = navigationLink.getInlineEntitySet();
                    Util.pageEntityCollection(entityCollection, skipValue, topValue);
                }
            }
        } else {
            Map<String, Entity> subEntityMap = new HashMap<>();
            for (Map.Entry<GenericValue, Entity> entry : expandDataMap.entrySet()) {
                String fkString = getFieldShortValue(relFieldNames, entry.getKey());
                subEntityMap.put(fkString, entry.getValue());
            }
            for (Entity entity : entityList) {
                OdataOfbizEntity mainOfbizEn = (OdataOfbizEntity) entity;
                String fkString = getFieldShortValue(fieldNames, mainOfbizEn.getGenericValue());
                addEntityToLink(entity, edmNavigationProperty, subEntityMap.get(fkString));
            }
        }
    }

    //处理当前所有子实体的expand
    private void recursionExpand(Collection<Entity> mainEntityList, Map<GenericValue, Entity> expandEntityMap,
                                 EdmNavigationProperty edmNavigationProperty, List<String> fieldNames, List<String> relFieldNames) throws OfbizODataException {
        Map<String, Entity> mainEntityMap = new HashMap<>();
        for (Entity entity : mainEntityList) {
            OdataOfbizEntity mainOfbizEn = (OdataOfbizEntity) entity;
            String fkString = getFieldShortValue(fieldNames, mainOfbizEn.getGenericValue());
            mainEntityMap.put(fkString, entity);
        }
        //添加OdataParts
        for (Map.Entry<GenericValue, Entity> entry : expandEntityMap.entrySet()) {
            String fkString = getFieldShortValue(relFieldNames, entry.getKey());
            if (mainEntityMap.containsKey(fkString)) {
                OdataOfbizEntity mainEntity = (OdataOfbizEntity) mainEntityMap.get(fkString);
                OdataOfbizEntity expandEntity = (OdataOfbizEntity) entry.getValue();
                List<OdataParts> odataParts = new ArrayList<>(mainEntity.getOdataParts());
                odataParts.add(new OdataParts(null, edmNavigationProperty.getType(), null, expandEntity));
                expandEntity.setOdataParts(odataParts);
            }
        }
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), expandEntityMap.values(), edmNavigationProperty.getType());
        }
    }

    public List<GenericValue> findRelatedGenericValue(Entity entity, EdmNavigationProperty edmNavigationProperty, EntityCondition condition) throws
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
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            //添加Navigation对应EntityType的Condition
            condition = Util.appendCondition(condition, navCsdlEntityType.getEntityCondition());
            List<GenericValue> relatedList = getGenericValuesFromRelations(genericValue, relAlias, relAlias.getRelations(), csdlNavigationProperty.isFilterByDate());
            if (condition != null) {
                relatedList = EntityUtil.filterByCondition(relatedList, condition);
            }
            return relatedList;
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
     * 获取实体列表的所有关联数据
     */
    private List<GenericValue> getAllDataFromRelations(List<GenericValue> genericValueList, OfbizCsdlNavigationProperty csdlNavigationProperty,
                                                       EntityCondition condition, List<String> orderByList) throws OfbizODataException {
        if (UtilValidate.isEmpty(genericValueList)) {
            return null;
        }
        //获取第一段Relation
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        List<String> relations = relAlias.getRelations();
        ModelRelation modelRelation = relAlias.getRelationsEntity().get(relations.get(0));
        Map<String, Object> relFieldMap = relAlias.getRelationsFieldMap().get(relations.get(0));
        //所有的查询条件
        List<EntityCondition> conditionList = new ArrayList<>();
        //第一段Relation的条件
        List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
        if (UtilValidate.isNotEmpty(relFieldMap)) {
            conditionList.add(EntityCondition.makeCondition(relFieldMap));
        }
        //数据的范围条件，单个字段直接使用in，多主键使用and+or
        if (keyMaps.size() == 1) {
            List<Object> fks = EntityUtil.getFieldListFromEntityList(genericValueList, keyMaps.get(0).getFieldName(), true);
            conditionList.add(EntityCondition.makeCondition(keyMaps.get(0).getRelFieldName(), EntityOperator.IN, fks));
        } else {
            //如果relation是多个字段 要拼范围条件: (id=a AND seqId=01) OR (id=a AND seqId=02) OR ...
            List<EntityCondition> rangeCondition = new ArrayList<>();
            for (GenericValue genericValue : genericValueList) {
                List<EntityCondition> currentConditions = new ArrayList<>();
                for (ModelKeyMap keyMap : keyMaps) {
                    currentConditions.add(EntityCondition.makeCondition(keyMap.getRelFieldName(), EntityOperator.EQUALS, genericValue.get(keyMap.getFieldName())));
                }
                rangeCondition.add(EntityCondition.makeCondition(currentConditions, EntityOperator.OR));
            }
            conditionList.add(EntityCondition.makeCondition(rangeCondition, EntityOperator.AND));
        }
        if (condition != null) {
            conditionList.add(condition);
        }
        EntityCondition entityCondition = EntityCondition.makeCondition(conditionList);
        try {
            if (relations.size() > 1) {
                //如果relations是多段的 使用dynamicView做一次查询
                DynamicViewEntity dynamicViewEntity = new DynamicViewEntity();
                Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
                Map<String, EntityCondition> relationsCondition = relAlias.getRelationsCondition();
                ModelRelation firstModelRelation = relationsEntity.get(relations.get(0));
                dynamicViewEntity.addMemberEntity(relations.get(0), firstModelRelation.getRelEntityName());
                dynamicViewEntity.addAliasAll(relations.get(0), null, null);
                for (int i = 1; i < relations.size(); i++) {
                    String currRel = relations.get(i);
                    String lastRel = relations.get(i - 1);
                    ModelRelation currRelation = relationsEntity.get(currRel);
                    dynamicViewEntity.addMemberEntity(currRel, currRelation.getRelEntityName());
                    dynamicViewEntity.addAliasAll(currRel, null, null);
                    //add Link
                    ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
                    ModelViewEntity.ViewEntityCondition viewEntityCondition = null;
                    EntityCondition relCondition = relationsCondition.get(currRel);
                    if (relCondition != null) {
                        viewEntityCondition = new ModelViewEntity.ViewEntityCondition(modelViewEntity, null, false, false, null, currRel, null, relCondition);
                    }
                    ModelViewEntity.ModelViewLink modelViewLink = new ModelViewEntity.ModelViewLink(lastRel, currRel, false, viewEntityCondition, currRelation.getKeyMaps());
                    dynamicViewEntity.addViewLink(modelViewLink);
                }
                Util.printDynamicView(dynamicViewEntity, null, module);
                return EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).orderBy(orderByList).queryList();
            } else {
                //使用in一次性将所有主实体的所有子对象都查询出来
                EntityQuery entityQuery = EntityQuery.use(delegator).from(modelRelation.getRelEntityName()).orderBy(orderByList).where(entityCondition);
                if (filterByDate) {
                    entityQuery = entityQuery.filterByDate();
                }
                return entityQuery.queryList();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 查询结果转成Entity
     */
    private Entity findResultToEntity(EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        if (resultMap instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return Util.mapToEntity(csdlEntityType, resultMap);
        }
    }

}
