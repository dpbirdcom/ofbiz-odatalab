package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.collections.PagedList;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class OfbizOdataReader extends OfbizOdataProcessor {
    private DynamicViewEntity dynamicViewEntity = null;

    public OfbizOdataReader(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
        retrieveDynamicView();
        if (UtilValidate.isEmpty(this.orderBy) && UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("defaultOrderBy") != null) {
            this.orderBy = (List<String>) queryOptions.get("defaultOrderBy");
        }
    }

    private void retrieveDynamicView() {
        if (this.dynamicViewHolder != null) {
            this.dynamicViewEntity = this.dynamicViewHolder.getDynamicViewEntity();
        }
    }

    public Integer readEntitySetCount(EdmBindingTarget edmBindingTarget, FilterOption filterOption, ApplyOption applyOption)
            throws ODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityNameToFind = OdataProcessorHelper.getEntityNameToFind(csdlEntityType, (String) odataContext.get("sapContextId"), edmProvider);
        return OdataProcessorHelper.readEntitySetCount(odataContext, entityNameToFind,
                filterOption, applyOption, isOdataView, csdlEntityType);
    }

    // 好像进入这个方法的，只能是EntitySet，不可能是Singleton
    public EntityCollection findList() throws ODataException {
        int listTotalCount = 0;
        List<GenericValue> genericValues = null;
        boolean isCount = false;
        if (queryOptions != null && queryOptions.get("countOption") != null) {
            CountOption countOption = (CountOption) queryOptions.get("countOption");
            isCount = countOption.getValue();
        }
        // 先确认是否有EntitySet的handler
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
        if (csdlEntitySet.getHandler() != null) {
            GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
            try {
                genericValues = groovyHelper.findGenericValues(csdlEntitySet.getHandler(), edmProvider, csdlEntitySet, queryOptions, entityCondition);
            } catch (MissingMethodExceptionNoStack e) {
                Debug.logInfo(e.getMessage(), module);
            }
        }

        if (genericValues == null) {
            // OdataView目前支持持简单条件查询，象lambda expression或者navigation的查询
            // 如果这个entityName存在于OdataView中，说明这是数据库定义的view，需要动态生成dynamicView，然后才能查询
            if (isOdataView) {
                OdataView odataView = new OdataView(delegator, this.entityName);
                PagedList<GenericValue> pagedList = odataView.findList(entityCondition,
                        (ApplyOption) queryOptions.get("applyOption"), fieldsToSelect, orderBy, efo, isCount);
                genericValues = pagedList.getData();
                if (isCount) {
                    listTotalCount = pagedList.getSize();
                }
            } else if (dynamicViewEntity == null) {
                String entityToFind = modelEntity.getEntityName();
                try {
                    Boolean useCache = skipValue > 0 || topValue > 0 || entityCondition != null ? false : true;
                    // fieldsToSelect暂时先去掉，因为会影响到expand，如果select的字段不包括expand需要的外键，expand就会出不来数据
                    if (this.filterByDate) {
                        OdataEntityQuery odataEntityQuery = (OdataEntityQuery) OdataEntityQuery.use(delegator).from(entityToFind).filterByDate()
                                .where(entityCondition).orderBy(orderBy).cache(useCache).cursorScrollInsensitive();
                        genericValues = odataEntityQuery.queryList(this.skipValue, this.topValue);
                    } else {
                        OdataEntityQuery odataEntityQuery = (OdataEntityQuery) OdataEntityQuery.use(delegator).from(entityToFind)
                                .where(entityCondition).orderBy(orderBy).cache(useCache).cursorScrollInsensitive();
                        genericValues = odataEntityQuery.queryList(this.skipValue, this.topValue);
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                    throw new ODataException(e.getMessage());
                }
                if (isCount) {
                    try {
                        if (this.filterByDate) {
                            listTotalCount = (int) EntityQuery.use(delegator).from(entityToFind).filterByDate()
                                    .where(entityCondition).cursorScrollInsensitive().queryCount();
                        } else {
                            listTotalCount = (int) EntityQuery.use(delegator).from(entityToFind)
                                    .where(entityCondition).cursorScrollInsensitive().queryCount();
                        }
                    } catch (GenericEntityException e) {
                        e.printStackTrace();
                        throw new ODataException(e.getMessage());
                    }
                }
            } else {
                // 需要用DynamicView去查询
                // 注意，这里的PagedList，只有data和size是维护的，其它字段暂时不可靠
                // 只要使用到了dynamicView，就一定需要对主对象进行groupby，所以isCount参数是true
                PagedList<GenericValue> pagedList = findListWithDynamicView();
                genericValues = pagedList.getData();
                if (isCount) {
                    listTotalCount = pagedList.getSize();
                }
            }
        }
        EntityCollection entityCollection = new EntityCollection();
        // 这个try只是做debug用，将来用重新规划各种exception的处理
        try {
            List<Entity> entityList = entityCollection.getEntities();
            if (genericValues != null) {
                for (GenericValue genericValue : genericValues) {
                    OdataOfbizEntity rowEntity;
                    rowEntity = makeEntityFromGv(genericValue);
                    entityList.add(rowEntity);
                }
            }
            //apply
            if (UtilValidate.isNotEmpty(aggregateSet) || UtilValidate.isNotEmpty(groupBySet)) {
                for (int i = 0; i < entityCollection.getEntities().size(); i++) {
                    OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entityCollection.getEntities().get(i);
                    GenericValue genericValue = odataOfbizEntity.getGenericValue();
                    for (String key : genericValue.keySet()) {
                        if (UtilValidate.isEmpty(odataOfbizEntity.getProperty(key))) {
                            odataOfbizEntity.addProperty(new Property(null, key, ValueType.PRIMITIVE, genericValue.get(key)));
                        }
                    }
                }
                return entityCollection;
            }
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, entityList, locale, userLogin);
            if (isCount) {
                entityCollection.setCount(listTotalCount);
            }
            if (queryOptions != null && queryOptions.get("expandOption") != null) {
                for (Entity entity : entityList) {
                    addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, this.edmEntityType);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return entityCollection;

    }

    private OdataOfbizEntity makeEntityFromGv(GenericValue genericValue) throws OfbizODataException {
        OdataOfbizEntity rowEntity = null;
        if (edmParams.get("edmBindingTarget") != null) {
            rowEntity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmBindingTarget) edmParams.get("edmBindingTarget"),
                    (EdmEntityType) edmParams.get("edmTypeFilter"), genericValue, locale);
        } else {
            rowEntity = OdataProcessorHelper
                    .genericValueToEntity(delegator, this.edmProvider, this.edmEntityType, genericValue, locale);
        }
        return rowEntity;
    }

    public Entity getRelatedEntity(Map<String, Object> keyMap,
                                   EdmNavigationProperty edmNavigationProperty,
                                   Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        GenericValue genericValue;
        try {
            genericValue = delegator.findOne(entityName, keyMap, true);
            if (genericValue == null) {
                throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + entityName);
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return getRelatedEntity(genericValue, edmNavigationProperty, queryOptions);
    }

    private Entity getRelatedEntity(GenericValue genericValue,
                                    EdmNavigationProperty edmNavigationProperty,
                                    Map<String, QueryOption> queryOptions) throws OfbizODataException {

        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        List<GenericValue> genericValues;
        GenericValue relatedGenericValue;
        try {
            if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                String handler = csdlNavigationProperty.getHandler();
                genericValues = groovyHelper.getNavigationData(handler, genericValue, queryOptions, filterByDate, null);
            } else {
                EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
                genericValues = OdataProcessorHelper.getRelatedGenericValues(delegator, genericValue, relAlias, filterByDate);
            }
            relatedGenericValue = EntityUtil.getFirst(genericValues);
            if (relatedGenericValue == null) {
                return null;
            }
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            OdataOfbizEntity entity;
            entity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, relatedGenericValue, locale);
            SelectOption selectOption = null;
            if (UtilValidate.isNotEmpty(queryOptions)) {
                selectOption = (SelectOption) queryOptions.get("selectOption");
            }
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    UtilMisc.toMap("selectOption", selectOption), UtilMisc.toList(entity), locale, userLogin);
            EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
            if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, navEdmEntityType);
            }
            return entity;
        } catch (ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    // expand访问会先进入这里
    public EntityCollection findRelatedEntityCollection(Entity entity,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions,
                                                        boolean isCount) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        Map<String, Object> keyMap = Util.retrieveKeyMapFromEntity(delegator, entity, csdlEntityType);

        //下面需要用keyMap去查询 把EnumType类型的转化成具体的值
        for (Map.Entry<String, Object> entry : keyMap.entrySet()) {
            Property property = entity.getProperty(entry.getKey());
            if (property.getValueType().equals(ValueType.ENUM)) {
                OfbizCsdlEnumType enumTypeOfbiz = (OfbizCsdlEnumType) edmProvider.getEnumType(new FullQualifiedName(property.getType()));
                try {
                    String enumId = Util.getEnumIdFromValue(delegator, enumTypeOfbiz, property.getValue().toString());
                    if (UtilValidate.isNotEmpty(enumId)) {
                        entry.setValue(enumId);
                    }
                } catch (GenericEntityException e) {
                    e.printStackTrace();
                }
            }
        }

        return findRelatedEntityCollection(keyMap, edmNavigationProperty, queryOptions, entity);
    }

    // 两段式访问会直接进到这里
    // expand访问也会进到这里
    public EntityCollection findRelatedEntityCollection(Map<String, Object> keyMap,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions,
                                                        Entity entity) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        GenericValue genericValue = null;
        try {
            Debug.logInfo("keyMap = " + keyMap, module);
            Debug.logInfo("navigationProperty = " + edmNavigationProperty.getName(), module);
            if (entity != null) {
                genericValue = ((OdataOfbizEntity) entity).getGenericValue();
            }
            if (genericValue == null) {
                genericValue = delegator.findOne(entityName, keyMap, false);
            }
            // 有些对象在数据库中是不存在的,比如购物车的促销项
            if (genericValue == null) {
                genericValue = delegator.makeValue(entityName);
                if (entity != null) {
                    for (Property property : entity.getProperties()) {
                        if (genericValue.getModelEntity().getAllFieldNames().contains(property.getName())) {
                            genericValue.set(property.getName(), property.getValue());
                        }
                    }
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return findRelatedEntityCollection(genericValue, edmNavigationProperty, queryOptions);
    }

    public EntityCollection findSingletonRelatedEntityCollection(EdmSingleton edmSingleton,
                                                                 EdmNavigationProperty edmNavigationProperty,
                                                                 Map<String, QueryOption> queryOptions) throws OfbizODataException {
        GenericValue genericValue = (GenericValue) OdataProcessorHelper.readSingletonObject(dispatcher, edmProvider,
                edmSingleton.getName(), httpServletRequest, userLogin);
        return findRelatedEntityCollection(genericValue, edmNavigationProperty, queryOptions);
    }

    public EntityCollection findRelatedEntityCollection(GenericValue genericValue,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions) throws OfbizODataException {
        int listTotalCount = 0;
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        List<GenericValue> genericValues;
        try {
            if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                String handler = csdlNavigationProperty.getHandler();
                try { // 有可能定义了handler，但是没有定义getNavigationData方法
                    genericValues = groovyHelper.getNavigationData(handler, genericValue, queryOptions, filterByDate, null);
                } catch (MissingMethodExceptionNoStack e) {
                    Debug.logInfo(e.getMessage(), module);
                    EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
                    List<String> relations = relAlias.getRelations();
                    genericValues = getGenericValuesFromRelations(genericValue, relAlias, relations, filterByDate);
                }
            } else {
                EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
                List<String> relations = relAlias.getRelations();
                genericValues = getGenericValuesFromRelations(genericValue, relAlias, relations, filterByDate);

            }
            if (UtilValidate.isNotEmpty(genericValues)) {
                int topValue = getTopOption(queryOptions);
                int skipValue = getSkipOption(queryOptions);
                //处理filter
                EntityCondition filterCondition = getFilterOption(queryOptions);
                if (filterCondition != null) {
                    genericValues = EntityUtil.filterByCondition(genericValues, filterCondition);
                }
                //处理search
                if (queryOptions != null && queryOptions.get("searchOption") != null) {
                    genericValues = EntityUtil.filterByCondition(genericValues, this.entitySearchCondition);
                }
                //过滤条件之后为null 直接返回
                if (genericValues == null || genericValues.size() == 0) {
                    return new EntityCollection();
                }
                listTotalCount = genericValues.size();
                if ((skipValue + topValue) > listTotalCount) {
                    if (genericValues.size() <= skipValue) return new EntityCollection();
                    genericValues = genericValues.subList(skipValue, listTotalCount);
                } else {
                    genericValues = genericValues.subList(skipValue, skipValue + topValue);
                }
            }
            if (UtilValidate.isNotEmpty(orderBy) && UtilValidate.isNotEmpty(genericValues)) {
                ModelEntity modelEntity = genericValues.get(0).getModelEntity();
                if (modelEntity.areFields(orderBy)) {
                    genericValues = EntityUtil.orderBy(genericValues, orderBy);
                } else {
                    try {
                        genericValues = EntityUtil.orderBy(genericValues, orderBy);
                    } catch (IllegalArgumentException ignored) {
                        //这里暂时不支持复杂的orderBy
                    }
                }
            }
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            String entitySetName = null;
            List<String> referencedEntitySet = navCsdlEntityType.getReferencedEntitySet();
            if (UtilValidate.isNotEmpty(referencedEntitySet)) {
                entitySetName = referencedEntitySet.get(0);
            }

            EntityCollection entityCollection = new EntityCollection();
            List<Entity> entityList = entityCollection.getEntities();
            if (genericValues != null) {
                for (GenericValue elementGV : genericValues) {
                    OdataOfbizEntity rowEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, elementGV, locale);
                    if (entitySetName != null && rowEntity != null) {
                        URI entityId = Util.createId(entitySetName, edmNavigationProperty.getType(), navCsdlEntityType, elementGV);
                        rowEntity.setId(entityId);
                    }
                    entityList.add(rowEntity);
                }
            }
            SelectOption selectOption = null;
            if (UtilValidate.isNotEmpty(queryOptions)) {
                selectOption = (SelectOption) queryOptions.get("selectOption");
            }
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    UtilMisc.toMap("selectOption", selectOption), entityList, locale, userLogin);
            if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("countOption") != null) {
                CountOption countOption = (CountOption) queryOptions.get("countOption");
                if (countOption.getValue()) {
                    entityCollection.setCount(listTotalCount);
                }
            }
            EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
            if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
                for (Entity entity : entityList) {
                    addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, navEdmEntityType);
                }
            }
            return entityCollection;
        } catch (GenericEntityException | ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }

    }

    private List<GenericValue> getGenericValuesFromRelations(GenericValue genericValue, EntityTypeRelAlias relAlias,
                                                             List<String> relations,
                                                             boolean filterByDate) throws GenericEntityException, OfbizODataException {
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

    private PagedList<GenericValue> findListWithDynamicView() {
        dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
        EntityListIterator entityListIt = null;
        List<GenericValue> resultList = new ArrayList<GenericValue>();
        long listCount = 0L;
        boolean beganTransaction = false;
        try {
            //多段式的apply 添加关联外键的查询条件
            if (UtilValidate.isNotEmpty(odataContext.get("uriResourceParts")) && queryOptions.containsKey("applyOption")) {
                List<UriResource> uriResourceParts = (List<UriResource>) odataContext.get("uriResourceParts");
                EntityCondition applyCondition = procApplyCondition(uriResourceParts);
                //没有数据
                if (applyCondition == null) {
                    return new PagedList<>(0, 20, (int) listCount, 0, 0, resultList);
                }
                entityCondition = Util.appendCondition(entityCondition, applyCondition);
            }
            //print
            printDynamicView();

            // make sure this is in a transaction
            beganTransaction = TransactionUtil.begin();
            EntityQuery entityQuery = EntityQuery.use(delegator)
                    .where(entityCondition)
                    .from(dynamicViewEntity);
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(OfbizMapOdata.NAMESPACE, this.edmEntityType.getName()));

            /* 如果odata请求带有select就使用, 否则select就使用EdmConfig中定义的Property, 但都需要排除语义化字段 */
            List<String> ofbizEntityAllFieldNames = delegator.makeValue(csdlEntityType.getOfbizEntity()).getModelEntity().getAllFieldNames();
            List<String> propertyNames = new ArrayList<>(this.edmEntityType.getPropertyNames());
            propertyNames.removeIf(property -> !ofbizEntityAllFieldNames.contains(property));
            Set<String> selectSet = UtilMisc.toSet(propertyNames);
            if (UtilValidate.isEmpty(fieldsToSelect) && !this.edmEntityType.getName().equals(this.entityName)) {
                selectSet = UtilMisc.toSet(propertyNames);
            } else if (UtilValidate.isNotEmpty(fieldsToSelect)) {
                propertyNames = new ArrayList<>(fieldsToSelect);
                propertyNames.removeIf(property -> !ofbizEntityAllFieldNames.contains(property));
                selectSet = UtilMisc.toSet(propertyNames);
            }
            //如果是aggregate就只查询统计字段
            if (UtilValidate.isNotEmpty(aggregateSet)) {
                selectSet = aggregateSet;
            }
            //如果是groupBy查询 select一定要是groupBy的字段或者使用函数的字段
            if (UtilValidate.isNotEmpty(groupBySet)) {
                selectSet = groupBySet;
                if (UtilValidate.isNotEmpty(aggregateSet)) {
                    selectSet.addAll(aggregateSet);
                }
            }
            entityQuery = entityQuery.select(selectSet);
            entityQuery = entityQuery
                    .orderBy(orderBy)
                    .maxRows(MAX_ROWS)
                    .cursorScrollInsensitive();
            Debug.logInfo("============================= before query " + UtilDateTime.nowTimestamp(), module);
            entityListIt = entityQuery.queryIterator();
            List<GenericValue> dataItems = entityListIt.getPartialList(skipValue + 1, topValue);
            listCount = entityListIt.getResultsSizeAfterPartialList();
            if (UtilValidate.isNotEmpty(aggregateSet) || UtilValidate.isNotEmpty(groupBySet)) {
                resultList = dataItems;
            } else {
                for (GenericValue dataItem : dataItems) {
                    resultList.add(Util.convertToTargetGenericValue(delegator, dataItem, modelEntity));
                }
            }
            Debug.logInfo("============================= after query " + UtilDateTime.nowTimestamp(), module);
        } catch (GenericEntityException | OfbizODataException e) {
            Debug.logError(e, "Error saving product search result info/stats", module);
            try {
                TransactionUtil.rollback(beganTransaction, e.getMessage(), e);
            } catch (GenericTransactionException e1) {
                Debug.logError(e1, module);
            }
        } finally {
            if (entityListIt != null) {
                try {
                    entityListIt.close();
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, "Error closing EntityListIterator when indexing content keywords.", module);
                }
            }

            // commit the transaction
            try {
                TransactionUtil.commit(beganTransaction);
            } catch (GenericTransactionException e) {
                Debug.logError(e, module);
            }
        }
        // 注意，这里的PagedList，只有data和size是维护的，其它字段暂时不可靠
        PagedList<GenericValue> result = new PagedList<GenericValue>(0, 20, (int) listCount, 0, 0, resultList);
        return result;
    }

    public OdataOfbizEntity readEntityData(Map<String, Object> keyMap, Map<String, QueryOption> queryOptions)
            throws OfbizODataException {
        GenericValue genericValue;
        OdataOfbizEntity entity;
        genericValue = OdataProcessorHelper.readEntityData(odataContext,
                (EdmBindingTarget) edmParams.get("edmBindingTarget"),
                keyMap);
        if (genericValue == null) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + entityName);
        }
        entity = makeEntityFromGv(genericValue);
        if (queryOptions != null) {
            if (queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, this.edmEntityType);
            }
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, UtilMisc.toList(entity), locale, userLogin);
        entity.setKeyMap(keyMap);
        return entity;
    }

    public OdataOfbizEntity readSingletonData(boolean withExpand)
            throws OfbizODataException {
        OdataOfbizEntity entity = null;
        EdmSingleton edmSingleton = (EdmSingleton) this.edmParams.get("edmSingleton");
        Object singletonObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        if (singletonObject instanceof GenericValue) {
            entity = makeEntityFromGv((GenericValue) singletonObject);
        } else {
            entity = objectToEntity(edmSingleton.getEntityType().getFullQualifiedName(), singletonObject);
        }
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, UtilMisc.toList(entity), locale, userLogin);

        if (withExpand) {
            if (queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, edmEntityType);
            }
        }
        return entity;
    }

    public void setServiceMetadata(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    public void setOData(OData oData) {
        this.oData = oData;
    }

    public Property readPrimitiveProperty(UriResourcePrimitiveProperty uriResourcePrimitiveProperty, UriResourcePartTyped boundEntity)
            throws OfbizODataException {
        Property result = null;
        EdmProperty edmProperty = uriResourcePrimitiveProperty.getProperty();
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetPrimitiveProperty(edmProperty,
                    (UriResourceEntitySet) boundEntity);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonPrimitiveProperty(edmProperty,
                    (UriResourceSingleton) boundEntity);
        }
        return result;

    }

    private Property singletonPrimitiveProperty(EdmProperty edmProperty, UriResourceSingleton boundEntity)
            throws OfbizODataException {
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Entity entity;
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        if (boundObject instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    edmSingleton.getEntityType(), (GenericValue) boundObject, locale);
        } else {
            entity = objectToEntity(edmSingleton.getEntityType().getFullQualifiedName(), boundObject);
        }
        if (entity != null) {
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(entity), locale, userLogin);
        }
        return entity.getProperty(edmProperty.getName());
    }

    private Property entitySetPrimitiveProperty(EdmProperty edmProperty, UriResourceEntitySet boundEntity)
            throws OfbizODataException {
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        Entity entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                boundEntity.getEntityType(), originGenericValue, locale);
        if (entity != null) {
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(entity), locale, userLogin);
        }
        return entity.getProperty(edmProperty.getName());
    }

    private boolean isMultiApplyQuery() {
        if (UtilValidate.isNotEmpty(odataContext.get("uriResourceParts"))) {
            List<UriResource> uriResourceParts = (List<UriResource>) odataContext.get("uriResourceParts");
            if (uriResourceParts.size() > 1 && (groupBySet != null || aggregateSet != null)) {
                return true;
            }
        }
        return false;
    }

    private EntityCondition procApplyCondition(List<UriResource> uriResourceParts)
            throws OfbizODataException {
        EntityCondition returnCondition = null;
        try {
            UriResourceEntitySet resourceEntitySet = (UriResourceEntitySet) uriResourceParts.get(0);
            Map<String, Object> keyMap = Util.uriParametersToMap(resourceEntitySet.getKeyPredicates(), resourceEntitySet.getEntityType());
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(resourceEntitySet.getEntityType().getFullQualifiedName());
            GenericValue mainGenericValue = delegator.findOne(ofbizCsdlEntityType.getOfbizEntity(), keyMap, true);
            ModelRelation relation = modelEntity.getRelation(uriResourceParts.get(1).getSegmentValue());
            for (ModelKeyMap relKeyMap : relation.getKeyMaps()) {
                Object relValue = mainGenericValue.get(relKeyMap.getFieldName());
                //缺少外键数据
                if (UtilValidate.isEmpty(relValue)) {
                    return null;
                }
                returnCondition = Util.appendCondition(returnCondition, EntityCondition.makeCondition(relKeyMap.getRelFieldName(), relValue));
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        return returnCondition;
    }

    private void printDynamicView() {
        /********************** debug用，输出dynamicViewEntity的xml表达式 ****************************************/
        try {
            String dynamicViewXml = dynamicViewEntity.getViewXml(dynamicViewEntity.getEntityName());
            Debug.logInfo(dynamicViewXml, module);
            if (entityCondition != null) {
                Debug.logInfo(entityCondition.toString(), module);
            }
        } catch (IOException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
        /*******************************************************************************************************/

    }


}
