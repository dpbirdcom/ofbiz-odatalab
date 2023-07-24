package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.HandlerResults;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
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
    public EntityCollection findApply(EntityCondition rangeCondition, Map<String, QueryOption> queryOptionMap) throws OfbizODataException {
        //从接口实例中读取数据
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        HandlerResults handlerResults = entityHandler.findApply(odataContext, edmEntitySet, queryOptionMap, rangeCondition);
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
        HandlerResults results = entityHandler.findList(odataContext, edmEntitySet, keyMap, null, null);
        if (UtilValidate.isEmpty(results) || UtilValidate.isEmpty(results.getResultData())) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Not found.");
        }
        Map<String, Object> resultMap = results.getResultData().get(0);
        OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(edmEntitySet, edmEntityType, resultMap);
        entity.addOdataParts(new OdataParts(edmEntitySet, edmEntityType, null, entity));
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), UtilMisc.toList(entity), edmEntitySet, this.edmEntityType);
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
            HandlerResults results = entityHandler.findList(odataContext, edmEntitySet, null, queryOptions, null);
            entityCollection.setCount(results.getResultCount());
            for (Map<String, Object> result : results.getResultData()) {
                OdataOfbizEntity resultToEntity = (OdataOfbizEntity) findResultToEntity(edmEntitySet, edmEntityType, result);
                resultToEntity.addOdataParts(new OdataParts(edmEntitySet, edmEntityType, null, resultToEntity));
                entities.add(resultToEntity);
            }
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, entities, locale, userLogin);
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entities, edmEntitySet, this.edmEntityType);
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
                        .where(entityCondition).select(getValidSelect()).orderBy(orderBy).cache(true).cursorScrollInsensitive();
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
     * @param rangeCondition 多段式查询时的范围
     * @return 返回Apply数据组装的Entity
     */
    public HandlerResults ofbizFindApply(EntityCondition rangeCondition) throws OfbizODataException {
        DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
        //print
        Util.printDynamicView(dynamicViewEntity, entityCondition, module);
        if (rangeCondition != null) {
            entityCondition = Util.appendCondition(entityCondition, rangeCondition);
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
            //query
            EntityQuery entityQuery = EntityQuery.use(delegator).where(entityCondition).from(dynamicViewEntity);
            entityQuery = entityQuery.select(getValidSelect()).orderBy(orderBy).maxRows(MAX_ROWS).cursorScrollInsensitive();
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

    /**
     * 获取有效的select
     * 如果请求带有select就使用，如果没有就select所有的字段，但查询数据库时都必须排除掉语义化字段。
     */
    private Set<String> getValidSelect() throws OfbizODataException {
        Set<String> selectSet = new HashSet<>();
        List<OfbizCsdlProperty> selectProperty = new ArrayList<>();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        if (UtilValidate.isNotEmpty(fieldsToSelect)) {
            for (String selectField : fieldsToSelect) {
                OfbizCsdlProperty property = (OfbizCsdlProperty) csdlEntityType.getProperty(selectField);
                selectProperty.add(property);
            }
            //后面要处理expand，添加外键
            selectSet.addAll(Util.getEntityFk(modelEntity));
        } else {
            for (String propertyName : edmEntityType.getPropertyNames()) {
                OfbizCsdlProperty property = (OfbizCsdlProperty) csdlEntityType.getProperty(propertyName);
                if (property == null && csdlEntityType.getBaseType() != null) {
                    property = csdlEntityType.getBaseTypeProperty(propertyName, edmProvider);
                }
                if (property != null) {
                    selectProperty.add(property);
                }
            }
        }
        for (OfbizCsdlProperty csdlProperty : selectProperty) {
            if (csdlProperty != null && csdlProperty.getRelAlias() == null && csdlProperty.getOfbizFieldName() != null) {
                selectSet.add(csdlProperty.getOfbizFieldName());
            }
        }
        return selectSet;
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
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), UtilMisc.toList(entity), edmSingleton, edmEntityType);
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
        HandlerResults handlerList = entityHandler.findList(odataContext, null, null,  queryOptions, navigationParam);
        List<? extends Map<String, Object>> resultData = handlerList.getResultData();
        if (UtilValidate.isEmpty(resultData)) {
            return null;
        }
        EdmBindingTarget navBindingTarget = null;
        if (edmParams.get("edmBindingTarget") != null) {
            EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            navBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
        }
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
        OdataOfbizEntity relEntity = (OdataOfbizEntity) findResultToEntity(navBindingTarget, edmNavigationProperty.getType(), resultData.get(0));
        List<OdataParts> odataPartsList = new ArrayList<>(ofbizEntity.getOdataParts());
        odataPartsList.add(new OdataParts(navBindingTarget, edmNavigationProperty.getType(), null, relEntity));
        relEntity.setOdataParts(odataPartsList);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptionMap.get("selectOption")), UtilMisc.toList(relEntity), locale, userLogin);
        if (UtilValidate.isNotEmpty(queryOptionMap) && queryOptionMap.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptionMap.get("expandOption"), UtilMisc.toList(relEntity), navBindingTarget, edmNavigationProperty.getType());
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
        HandlerResults results = navEntityHandler.findList(odataContext, null, null, queryOptions, navigationParam);
        if (UtilValidate.isEmpty(results) || UtilValidate.isEmpty(results.getResultData())) {
            return entityCollection;
        }
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
        EdmBindingTarget navBindingTarget = null;
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
        if (edmBindingTarget != null) {
            navBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
        }
        for (Map<String, Object> navigationDatum : results.getResultData()) {
            OdataOfbizEntity navigationEntity = (OdataOfbizEntity) findResultToEntity(navBindingTarget, edmNavigationProperty.getType(), navigationDatum);
            List<OdataParts> odataParts = new ArrayList<>(ofbizEntity.getOdataParts());
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
        if (filterOption != null || orderbyOption != null) {
            Util.filterEntityCollection(entityCollection, filterOption, orderbyOption, edmNavigationProperty.getType(),
                    edmProvider, delegator, dispatcher, userLogin, locale, csdlNavigationProperty.isFilterByDate());
        }
        entityCollection.setCount(entityCollection.getEntities().size());
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                UtilMisc.toMap("selectOption", queryOptions.get("selectOption")), entityCollection.getEntities(), locale, userLogin);
        if (Util.isExtraOrderby(orderbyOption, navCsdlEntityType, delegator)) {
            Util.orderbyEntityCollection(entityCollection, orderbyOption, edmNavigationProperty.getType(), edmProvider);
        }
        Util.pageEntityCollection(entityCollection, Util.getSkipOption(queryOptions), Util.getTopOption(queryOptions));
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entityCollection.getEntities(), navBindingTarget, edmNavigationProperty.getType());
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
        EdmBindingTarget navBindingTarget = null;
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
        if (edmBindingTarget != null) {
            navBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
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
            Entity resultToEntity = findResultToEntity(navBindingTarget, edmNavigationPropertyType, genericValue);
            if (navCsdlEntityType.hasStream()) {
                resultToEntity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
            }
            relatedEntityList.add(resultToEntity);
        }
        //获取relation关联字段
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        List<String> relations = relAlias.getRelations();
        List<ModelKeyMap> relKeyMaps = modelEntity.getRelation(relations.get(0)).getKeyMaps();
        List<String> fieldNames = new ArrayList<>();
        List<String> relFieldNames = new ArrayList<>();
        for (ModelKeyMap relKeyMap : relKeyMaps) {
            fieldNames.add(relKeyMap.getFieldName());
            String relFieldName = relKeyMap.getRelFieldName();
            //多段的relations查询时是添加了前缀的,所以当取值的时候也要加前缀
            if (relations.size() > 1) {
                relFieldName = relations.get(0) + Util.firstUpperCase(relFieldName);
            }
            relFieldNames.add(relFieldName);
        }
        //添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, relatedEntityList, locale, userLogin);
        Map<GenericValue, Entity> expandDataMap = new LinkedHashMap<>();
        for (int i = 0; i < relatedEntityList.size(); i++) {
            expandDataMap.put(relatedGenericList.get(i), relatedEntityList.get(i));
        }
        //处理下一层expand
        recursionExpand(entityList, expandDataMap, navBindingTarget, edmNavigationProperty, relAlias, fieldNames, relFieldNames);
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
    private void recursionExpand(Collection<Entity> mainEntityList, Map<GenericValue, Entity> expandEntityMap, EdmBindingTarget navBindingTarget,
                                 EdmNavigationProperty edmNavigationProperty, EntityTypeRelAlias relAlias,
                                 List<String> fieldNames, List<String> relFieldNames) throws OfbizODataException {
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
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), expandEntityMap.values(), navBindingTarget, edmNavigationProperty.getType());
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
            if (navCsdlEntityType.getEntityCondition() != null) {
                if(!navCsdlEntityType.getEntityConditionStr().contains("/")) {
                    //TODO: 暂不持支持expand查询嵌入EntityType的多段式条件
                    Map<String, Object> entityTypeCondition = Util.parseConditionMap(navCsdlEntityType.getEntityConditionStr(), httpServletRequest);
                    condition = Util.appendCondition(condition, EntityCondition.makeCondition(entityTypeCondition));
                } else {
                    Debug.logWarning("The EntityType condition is not supported", module);
                }
            }
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
                Map<String, EntityCondition> relationsCondition = relAlias.getRelationsCondition();
                if (UtilValidate.isNotEmpty(relationsCondition) && UtilValidate.isNotEmpty(relationsCondition.get(relations.get(0)))) {
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
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        List<String> relations = relAlias.getRelations();
        ModelRelation modelRelation = relAlias.getRelationsEntity().get(relations.get(0));
        Map<String, Object> relFieldMap = relAlias.getRelationsFieldMap().get(relations.get(0));
        //所有的查询条件
        EntityCondition entityCondition = Util.appendCondition(condition, getRangeCondition(genericValueList, relAlias));
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        String entityConditionStr = navCsdlEntityType.getEntityConditionStr();
        if (UtilValidate.isNotEmpty(entityConditionStr)) {
            //添加navigation EntityType的Condition
            Map<String, Object> conditionMap = Util.parseConditionMap(navCsdlEntityType.getEntityConditionStr(), httpServletRequest);
            entityCondition = Util.appendCondition(entityCondition, EntityCondition.makeCondition(conditionMap));
        }
        //添加数据的范围条件
        try {
            if (relations.size() > 1) {
                //如果relations是多段的 使用dynamicView做一次查询
                DynamicViewEntity dynamicViewEntity = new DynamicViewEntity();
                Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
                ModelRelation firstModelRelation = relationsEntity.get(relations.get(0));
                Map<String, Object> firstConditionField = relAlias.getRelationsFieldMap().get(relations.get(0));
                dynamicViewEntity.addMemberEntity(relations.get(0), firstModelRelation.getRelEntityName());
                //防止字段名重复 除了最后一段之外的Alias都要添加前缀
                String prefix = relations.get(0);
                dynamicViewEntity.addAliasAll(relations.get(0), prefix, null);
                //第一段的condition
                if (UtilValidate.isNotEmpty(firstConditionField)) {
                    for (Map.Entry<String, Object> entry : firstConditionField.entrySet()) {
                        String condFieldName = prefix + Util.firstUpperCase(entry.getKey());
                        EntityCondition currCondition = EntityCondition.makeCondition(condFieldName, entry.getValue());
                        entityCondition = Util.appendCondition(entityCondition, currCondition);
                    }
                }
                for (int i = 1; i < relations.size(); i++) {
                    String currRel = relations.get(i);
                    String lastRel = relations.get(i - 1);
                    String currPrefix = null;
                    ModelRelation currRelation = relationsEntity.get(currRel);
                    dynamicViewEntity.addMemberEntity(currRel, currRelation.getRelEntityName());
                    if (i < relations.size() - 1) {
                        currPrefix = relations.get(i);
                    }
                    dynamicViewEntity.addAliasAll(currRel, currPrefix, null);
                    //add Link
                    ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
                    ModelViewEntity.ViewEntityCondition viewEntityCondition = null;
                    Map<String, Object> currConditionFields = relAlias.getRelationsFieldMap().get(currRel);
                    if (UtilValidate.isNotEmpty(currConditionFields)) {
                        List<EntityCondition> linkConditions = new ArrayList<>();
                        for (Map.Entry<String, Object> entry : currConditionFields.entrySet()) {
                            String fieldName = entry.getKey();
                            String fieldCondName = entry.getKey();
                            if (currPrefix != null) {
//                                fieldCondName = currPrefix + Util.firstUpperCase(fieldName);
                            }
                            linkConditions.add(EntityCondition.makeCondition(fieldCondName, entry.getValue()));
                        }
                        viewEntityCondition = new ModelViewEntity.ViewEntityCondition(modelViewEntity, null,
                                false, false, null, currRel, null, EntityCondition.makeCondition(linkConditions));
                    }
                    ModelViewEntity.ModelViewLink modelViewLink = new ModelViewEntity.ModelViewLink(lastRel, currRel, false, viewEntityCondition, currRelation.getKeyMaps());
                    dynamicViewEntity.addViewLink(modelViewLink);
                }
                Util.printDynamicView(dynamicViewEntity, null, module);
                EntityQuery entityQuery = EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).orderBy(orderByList);
                if (filterByDate) {
                    entityQuery = entityQuery.filterByDate();
                }
                return entityQuery.queryList();
            } else {
                //添加第一段Relation的条件
                if (UtilValidate.isNotEmpty(relFieldMap)) {
                    entityCondition = Util.appendCondition(entityCondition, EntityCondition.makeCondition(relFieldMap));
                }
                //使用in一次性将所有主实体的所有子对象都查询出来
                EntityQuery entityQuery = EntityQuery.use(delegator).from(modelRelation.getRelEntityName()).orderBy(orderByList).where(entityCondition);
                if (csdlNavigationProperty.isFilterByDate()) {
                    entityQuery = entityQuery.filterByDate();
                }
                return entityQuery.queryList();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    //获取实体集合查询子对象的条件
    private static EntityCondition getRangeCondition(List<GenericValue> genericValues, EntityTypeRelAlias relAlias) {
        List<String> relations = relAlias.getRelations();
        ModelRelation modelRelation = relAlias.getRelationsEntity().get(relations.get(0));
        List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
        String prefix = null;
        if (relations.size() > 1) {
            prefix = relations.get(0);
        }
        //单个key可以直接使用in
        if (keyMaps.size() == 1) {
            String relFieldName = keyMaps.get(0).getRelFieldName();
            if (prefix != null) {
                relFieldName = prefix + Util.firstUpperCase(relFieldName);
            }
            List<Object> fks = EntityUtil.getFieldListFromEntityList(genericValues, keyMaps.get(0).getFieldName(), true);
            return EntityCondition.makeCondition(relFieldName, EntityOperator.IN, fks);
        } else {
            //如果relation是多个字段 要拼范围条件: (id=a AND seqId=01) OR (id=a AND seqId=02) OR ...
            List<EntityCondition> conditionList = new ArrayList<>();
            for (GenericValue genericValue : genericValues) {
                List<EntityCondition> currentConditions = new ArrayList<>();
                for (ModelKeyMap keyMap : keyMaps) {
                    String relFieldName = keyMap.getRelFieldName();
                    if (prefix != null) {
                        relFieldName = prefix + Util.firstUpperCase(relFieldName);
                    }
                    currentConditions.add(EntityCondition.makeCondition(relFieldName, EntityOperator.EQUALS, genericValue.get(keyMap.getFieldName())));
                }
                conditionList.add(EntityCondition.makeCondition(currentConditions, EntityOperator.AND));
            }
            return EntityCondition.makeCondition(conditionList, EntityOperator.OR);
        }
    }

    /**
     * 查询结果转成Entity
     */
    private Entity findResultToEntity(EdmBindingTarget edmBindingTarget, EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        if (resultMap instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmBindingTarget, edmEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return Util.mapToEntity(csdlEntityType, resultMap);
        }
    }

}
