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
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

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

    public Long readEntitySetCount(EdmBindingTarget edmBindingTarget)
            throws ODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityNameToFind = OdataProcessorHelper.getEntityNameToFind(csdlEntityType, (String) odataContext.get("sapContextId"), edmProvider);
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        if (filterOption != null) {
            Expression filterExpression = filterOption.getExpression();
            OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
            filterExpression.accept(expressionVisitor);
        }
        try {
            Long countLong = 0L;
            if (dynamicViewEntity == null) {
                countLong = EntityQuery.use(delegator).from(entityNameToFind).where(entityCondition).queryCount();
            } else {
                Util.printDynamicView(dynamicViewEntity, entityCondition, module);
                countLong = EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).queryCount();
            }
            return countLong;
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    public Long readRelatedEntityCount(EdmBindingTarget edmBindingTarget, EntityCollection entityCollection)
            throws ODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityNameToFind = OdataProcessorHelper.getEntityNameToFind(csdlEntityType, (String) odataContext.get("sapContextId"), edmProvider);
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        if (filterOption != null) {
            Expression filterExpression = filterOption.getExpression();
            OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
            filterExpression.accept(expressionVisitor);
        }
        try {
            if (entityCollection == null || UtilValidate.isEmpty(entityCollection.getEntities())) {
                return 0L;
            }
            EntityCondition queryCondition = Util.getEntityCollectionQueryCond(entityCollection, dynamicViewEntity != null);
            entityCondition = Util.appendCondition(entityCondition, queryCondition);
            if (dynamicViewEntity == null) {
                return EntityQuery.use(delegator).from(entityNameToFind).where(entityCondition).queryCount();
            } else {
                Util.printDynamicView(dynamicViewEntity, entityCondition, module);
                return EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).queryCount();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
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
                if (modelEntity != null) {
                    genericValues = groovyHelper.findGenericValues(csdlEntitySet.getHandler(), edmProvider, csdlEntitySet, queryOptions, entityCondition);
                } else {
                    return findSemanticList(csdlEntitySet, isCount);
                }
            } catch (MissingMethodExceptionNoStack e) {
                Debug.logInfo(e.getMessage(), module);
            }
        }
        try {
            if (genericValues == null) {
                if (dynamicViewEntity == null) {
                    //查询实体数据
                    boolean useCache = skipValue <= 0 && topValue <= 0 && entityCondition == null;
                    // fieldsToSelect暂时先去掉，因为会影响到expand，如果select的字段不包括expand需要的外键，expand就会出不来数据
                    OdataEntityQuery odataEntityQuery = (OdataEntityQuery) OdataEntityQuery.use(delegator).from(modelEntity.getEntityName())
                            .where(entityCondition).orderBy(orderBy).cache(useCache).cursorScrollInsensitive();
                    if (this.filterByDate) {
                        odataEntityQuery = (OdataEntityQuery) odataEntityQuery.filterByDate();
                    }
                    genericValues = odataEntityQuery.queryList(this.skipValue, this.topValue);
                    if (isCount) {
                        listTotalCount = (int) odataEntityQuery.queryCount();
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
            List<Entity> entityList = entityCollection.getEntities();
            if (genericValues != null) {
                for (GenericValue genericValue : genericValues) {
                    OdataOfbizEntity rowEntity;
                    rowEntity = makeEntityFromGv(genericValue);
                    entityList.add(rowEntity);
                }
            }
            if (isCount) {
                entityCollection.setCount(listTotalCount);
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
            if (queryOptions != null && queryOptions.get("expandOption") != null) {
                for (Entity entity : entityList) {
                    addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity,
                            this.edmEntityType, edmParams);
                }
            }
            return entityCollection;
        } catch (Exception e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 查询语义化实体列表
     */
    public EntityCollection findSemanticList(OfbizCsdlEntitySet csdlEntitySet, boolean isCount) throws ODataException {
        //使用EntitySet的Handler查询数据
        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
        List<Entity> semanticEntities = groovyHelper.findSemanticEntities(edmProvider, csdlEntitySet, queryOptions);
        EntityCollection entityCollection = new EntityCollection();
        entityCollection.getEntities().addAll(semanticEntities);
        //count
        if (isCount) {
            entityCollection.setCount(entityCollection.getEntities().size());
        }
        //分页
        retrieveFindOption();
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        return entityCollection;
    }

    public OdataOfbizEntity makeEntityFromGv(GenericValue genericValue) throws OfbizODataException {
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
        OdataOfbizEntity ofbizEntity;
        try {
            GenericValue genericValue = delegator.findOne(entityName, keyMap, true);
            if (genericValue == null) {
                throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + entityName);
            }
            ofbizEntity = makeEntityFromGv(genericValue);
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    null, UtilMisc.toList(ofbizEntity), locale, userLogin);
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return getRelatedEntity(ofbizEntity, edmNavigationProperty, queryOptions);
    }

    private Entity getRelatedEntity(Entity entity,
                                    EdmNavigationProperty edmNavigationProperty,
                                    Map<String, QueryOption> queryOptions) throws OfbizODataException {
        GenericValue genericValue = ((OdataOfbizEntity) entity).getGenericValue();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        List<GenericValue> genericValues;
        GenericValue relatedGenericValue;
        try {
            //查询Navigation语义化实体数据
            boolean semanticEntity = Util.isSemanticEntity(delegator, edmNavigationProperty.getType(), edmProvider);
            if (semanticEntity) {
                EntityCollection relatedSemanticEntity = findRelatedSemanticEntity(entity, edmNavigationProperty, queryOptions);
                return relatedSemanticEntity.getEntities().get(0);
            }
            if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                String handler = csdlNavigationProperty.getHandler();
                genericValues = groovyHelper.getNavigationData(handler, entity, edmNavigationProperty, queryOptions, filterByDate, null);
            } else {
                EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
                genericValues = OdataProcessorHelper.getRelatedGenericValues(delegator, genericValue, relAlias, filterByDate);
            }
            relatedGenericValue = EntityUtil.getFirst(genericValues);
            if (relatedGenericValue == null) {
                return null;
            }
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            OdataOfbizEntity relEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, relatedGenericValue, locale);
            SelectOption selectOption = null;
            if (UtilValidate.isNotEmpty(queryOptions)) {
                selectOption = (SelectOption) queryOptions.get("selectOption");
            }
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    UtilMisc.toMap("selectOption", selectOption), UtilMisc.toList(relEntity), locale, userLogin);
            EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
            if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) relEntity, navEdmEntityType, edmParams);
            }
            return relEntity;
        } catch (ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    //读取单个关联对象
    public Entity readRelatedEntityOne(Map<String, Object> keyMap,
                                       EdmNavigationProperty edmNavigationProperty, Map<String, Object> navKeyMap) throws OfbizODataException {
        if (navKeyMap == null) {
            //one
            return getRelatedEntity(keyMap, edmNavigationProperty, queryOptions);
        } else {
            //many
            EntityCollection relatedEntityCollection = findRelatedEntityCollectionByCondition(keyMap, edmNavigationProperty, EntityCondition.makeCondition(navKeyMap));
            return relatedEntityCollection.getEntities().stream().findFirst().orElse(null);
        }
    }

    //读取关联对象的单个字段
    public Property readRelatedEntityProperty(Map<String, Object> keyMap,
                                              EdmNavigationProperty edmNavigationProperty, Map<String, Object> navKeyMap,
                                              String propertyName) throws OfbizODataException {
        Entity relatedEntity = readRelatedEntityOne(keyMap, edmNavigationProperty, navKeyMap);
        if (relatedEntity == null) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Associated data not found: " + edmNavigationProperty.getName());
        }
        return relatedEntity.getProperty(propertyName);
    }

    /**
     * 解析一个多段式请求的UriResource
     *
     * @param resourceParts 所有要解析的UriResource，只能是Entity或Navigation
     * @return 返回最终的Entity和Navigation
     */
    public static Map<String, Object> getEntityAndNavigationFromResource(List<UriResource> resourceParts, Map<String, Object> odataContext) throws OfbizODataException {
        //first EntitySet
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        Map<String, Object> keyMap = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntitySet.getEntityType());
        if (resourceParts.size() == 1) {
            return UtilMisc.toMap("edmEntitySet", edmEntitySet, "keyMap", keyMap);
        }
        //first Navigation
        UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
        EdmNavigationProperty edmNavigation = uriResourceNavigation.getProperty();
        EdmEntitySet navigationEntitySet = Util.getNavigationTargetEntitySet(edmEntitySet, edmNavigation);
        List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();
        Map<String, Object> navKeyMap = null;
        if (UtilValidate.isNotEmpty(navKeyPredicates)) {
            navKeyMap = Util.uriParametersToMap(navKeyPredicates, navigationEntitySet.getEntityType());
        }

        //如果还有更多的Navigation 找到最终的进行返回
        for (int i = 2; i < resourceParts.size(); i++) {
            //获取navigation PrimaryKey
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet,
                    "edmNavigationProperty", edmNavigation);
            Map<String, QueryOption> queryParams = UtilMisc.toMap("keyMap", keyMap);
            if (navKeyMap == null) {
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                OdataOfbizEntity relatedEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(keyMap, edmNavigation, null);
                if (UtilValidate.isEmpty(relatedEntity)) {
                    throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Associated data not found: " + edmNavigation.getName());
                }
                navKeyMap = relatedEntity.getKeyMap();
            } else {
                //即使传递了子对象的主键，也要防止是一个错误的主键，要确认存在这个子对象再继续
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                EntityCollection relatedEntityCollection = ofbizOdataReader.findRelatedEntityCollectionByCondition(keyMap, edmNavigation, EntityCondition.makeCondition(navKeyMap));
                if (UtilValidate.isEmpty(relatedEntityCollection.getEntities())) {
                    throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Associated data not found: " + edmNavigation.getName() + navKeyMap);
                }
            }
            if (UtilValidate.isEmpty(navKeyMap)) {
                return null;
            }
            //获取到下一个Navigation，向后推移，把当前的做为最终的Navigation，把上一个Navigation作为EntitySet
            edmEntitySet = navigationEntitySet;
            keyMap = navKeyMap;
            UriResourceNavigation nextUriNavigation = (UriResourceNavigation) resourceParts.get(i);
            edmNavigation = nextUriNavigation.getProperty();
            navigationEntitySet = Util.getNavigationTargetEntitySet(navigationEntitySet, edmNavigation);
            navKeyMap = null;
            if (UtilValidate.isNotEmpty(nextUriNavigation.getKeyPredicates())) {
                navKeyMap = Util.uriParametersToMap(nextUriNavigation.getKeyPredicates(), navigationEntitySet.getEntityType());
            }
        }
        return UtilMisc.toMap("edmEntitySet", edmEntitySet, "keyMap", keyMap, "edmNavigation", edmNavigation, "navKeyMap", navKeyMap);

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

    public EntityCollection findRelatedEntityCollectionByCondition(Map<String, Object> keyMap,
                                                                   EdmNavigationProperty edmNavigationProperty,
                                                                   EntityCondition queryCondition) throws OfbizODataException {
        EntityCollection returnCollection = new EntityCollection();
        //查询子对象数据
        EntityCollection relatedEntityCollection = findRelatedEntityCollection(keyMap, edmNavigationProperty, null, null);
        for (Entity entity : relatedEntityCollection) {
            OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
            GenericValue entityGeneric = ofbizEntity.getGenericValue();
            //只返回符合条件的数据
            List<GenericValue> filterResult = EntityUtil.filterByCondition(UtilMisc.toList(entityGeneric), queryCondition);
            if (UtilValidate.isNotEmpty(filterResult)) {
                returnCollection.getEntities().add(entity);
            }
        }
        return returnCollection;
    }

    // 两段式访问会直接进到这里
    // expand访问也会进到这里
    public EntityCollection findRelatedEntityCollection(Map<String, Object> keyMap,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions,
                                                        Entity entity) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        try {
            Debug.logInfo("keyMap = " + keyMap, module);
            Debug.logInfo("navigationProperty = " + edmNavigationProperty.getName(), module);
            if (entity == null) {
                GenericValue genericValue = delegator.findOne(entityName, keyMap, false);
                //如果是正在编辑的实体并且Navigation不存在Draft 那么肯定没有数据
                if (genericValue == null) {
                    return new EntityCollection();
                }
                entity = makeEntityFromGv(genericValue);
                //这里只有通过语义化字段做Navigation时才需要添加，如果不是就没必要
                if (ofbizCsdlNavigationProperty.getHandler() != null) {
                    OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                            null, UtilMisc.toList(entity), locale, userLogin);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return findRelatedEntityCollection(entity, edmNavigationProperty, queryOptions);
    }

    public EntityCollection findSingletonRelatedEntityCollection(EdmSingleton edmSingleton,
                                                                 EdmNavigationProperty edmNavigationProperty,
                                                                 Map<String, QueryOption> queryOptions) throws OfbizODataException {
        GenericValue genericValue = (GenericValue) OdataProcessorHelper.readSingletonObject(dispatcher, edmProvider,
                edmSingleton.getName(), httpServletRequest, userLogin);
        return findRelatedEntityCollection(makeEntityFromGv(genericValue), edmNavigationProperty, queryOptions);
    }

    public EntityCollection findRelatedEntityCollection(Entity entity,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions) throws OfbizODataException {
        int listTotalCount = 0;
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
        GenericValue genericValue = ofbizEntity.getGenericValue();
        if (genericValue == null) {
            //如果是singleton 可能没有GenericValue
            genericValue = Util.entityToGenericValue(delegator, ofbizEntity, modelEntity.getEntityName());
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        boolean filterByDate = csdlNavigationProperty.isFilterByDate();
        List<GenericValue> genericValues;
        try {
            //查询Navigation语义化实体数据
            boolean semanticEntity = Util.isSemanticEntity(delegator, edmNavigationProperty.getType(), edmProvider);
            if (semanticEntity) {
                return findRelatedSemanticEntity(entity, edmNavigationProperty, queryOptions);
            }
            if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                String handler = csdlNavigationProperty.getHandler();
                try { // 有可能定义了handler，但是没有定义getNavigationData方法
                    genericValues = groovyHelper.getNavigationData(handler, entity, edmNavigationProperty, queryOptions, filterByDate, null);
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
                if (queryOptions != null && queryOptions.get("searchOption") != null && this.entitySearchCondition != null) {
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
                //添加子对象的edmBindingTarget
                Map<String, Object> edmParamOption = new HashMap<>();
                EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
                if (edmBindingTarget != null) {
                    EdmEntitySet navigationTargetEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
                    edmParamOption.put("edmBindingTarget", navigationTargetEntitySet);
                }
                for (Entity en : entityList) {
                    addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) en, navEdmEntityType, edmParamOption);
                }
            }
            return entityCollection;
        } catch (GenericEntityException | ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }

    }

    public EntityCollection findRelatedSemanticEntity(Entity entity,
                                                      EdmNavigationProperty edmNavigationProperty,
                                                      Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
        String handler = csdlNavigationProperty.getHandler();
        List<Entity> semanticNavigationData;
        if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
            semanticNavigationData = groovyHelper.getSemanticNavigationData(handler, entity, edmNavigationProperty, queryOptions, null);
        } else {
            //如果这个语义化Navigation没有Handler 那就从EntitySet的Handler取数据
            EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            EdmEntitySet navigationEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
            if (UtilValidate.isEmpty(navigationEntitySet)) {
                throw new OfbizODataException("Not found EntitySet: " + edmNavigationProperty.getName());
            }
            OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, navigationEntitySet.getName());
            semanticNavigationData = groovyHelper.findSemanticEntities(edmProvider, csdlEntitySet, queryOptions);
        }
        EntityCollection entityCollection = new EntityCollection();
        entityCollection.setCount(semanticNavigationData.size());
        entityCollection.getEntities().addAll(semanticNavigationData);
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        return entityCollection;
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
            Util.printDynamicView(dynamicViewEntity, entityCondition, module);

            // make sure this is in a transaction
            beganTransaction = TransactionUtil.begin();
            EntityQuery entityQuery = EntityQuery.use(delegator)
                    .where(entityCondition)
                    .from(dynamicViewEntity);
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(OfbizMapOdata.NAMESPACE, this.edmEntityType.getName()));

            // select
            ModelEntity ofbizModelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            Set<String> selectSet = new HashSet<>(this.edmEntityType.getPropertyNames());
            if (UtilValidate.isNotEmpty(fieldsToSelect)) {
                selectSet = new HashSet<>(fieldsToSelect);
                //后面要处理expand，添加外键
                selectSet.addAll(Util.getEntityFk(ofbizModelEntity));
            }
            //排除语义化字段
            selectSet.removeIf(property -> !ofbizModelEntity.getAllFieldNames().contains(property));

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
                    resultList.add(Util.convertToTargetGenericValue(delegator, dataItem, this.modelEntity));
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
        OdataOfbizEntity entity;
        if (modelEntity != null) {
            GenericValue genericValue = OdataProcessorHelper.readEntityData(odataContext,
                    (EdmBindingTarget) edmParams.get("edmBindingTarget"),
                    keyMap);
            if (genericValue == null) {
                throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + entityName);
            }
            entity = makeEntityFromGv(genericValue);
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(entity), locale, userLogin);
            if (queryOptions != null) {
                if (queryOptions.get("expandOption") != null) {
                    addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, this.edmEntityType, edmParams);
                }
            }
        } else {
            //语义化实体
            EdmEntitySet edmEntitySet = (EdmEntitySet) edmParams.get("edmBindingTarget");
            OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, edmEntitySet.getName());
            if (csdlEntitySet.getHandler() == null) {
                throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + entityName);
            }
            GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
            entity = (OdataOfbizEntity) groovyHelper.findSemanticEntity(edmProvider, csdlEntitySet, keyMap);
        }
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
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, edmEntityType, edmParams);
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
            Debug.logError(e.getMessage(), module);
        }
        return returnCondition;
    }

}
