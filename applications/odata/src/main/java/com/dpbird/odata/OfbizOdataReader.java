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
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
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
                printDynamicView();
                countLong = EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).queryCount();
            }
            return countLong;
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
//        return OdataProcessorHelper.readEntitySetCount(odataContext, entityNameToFind, filterOption, csdlEntityType);
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
            EntityCondition queryCondition = Util.getGenericValuesQueryCond(entityCollection, dynamicViewEntity != null);
            entityCondition = Util.appendCondition(entityCondition, queryCondition);
            if (dynamicViewEntity == null) {
                return EntityQuery.use(delegator).from(entityNameToFind).where(entityCondition).queryCount();
            } else {
                printDynamicView();
                return EntityQuery.use(delegator).from(dynamicViewEntity).where(entityCondition).queryCount();
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    // ???????????????????????????????????????EntitySet???????????????Singleton
    public EntityCollection findList() throws ODataException {
        int listTotalCount = 0;
        List<GenericValue> genericValues = null;
        boolean isCount = false;
        if (queryOptions != null && queryOptions.get("countOption") != null) {
            CountOption countOption = (CountOption) queryOptions.get("countOption");
            isCount = countOption.getValue();
        }
        // ??????????????????EntitySet???handler
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
            // OdataView???????????????????????????????????????lambda expression??????navigation?????????
            // ????????????entityName?????????OdataView????????????????????????????????????view?????????????????????dynamicView?????????????????????
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
                    // fieldsToSelect????????????????????????????????????expand?????????select??????????????????expand??????????????????expand?????????????????????
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
                // ?????????DynamicView?????????
                // ??????????????????PagedList?????????data???size??????????????????????????????????????????
                // ??????????????????dynamicView????????????????????????????????????groupby?????????isCount?????????true
                PagedList<GenericValue> pagedList = findListWithDynamicView();
                genericValues = pagedList.getData();
                if (isCount) {
                    listTotalCount = pagedList.getSize();
                }
            }
        }
        EntityCollection entityCollection = new EntityCollection();
        // ??????try?????????debug?????????????????????????????????exception?????????
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


    public Property readRelatedEntityProperty(Map<String, Object> keyMap,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> navKeyMap,
                                   String property) throws OfbizODataException {
        Property resultProperty = null;
        try {
            if (navKeyMap == null) {
                //one
                Entity relatedEntity = getRelatedEntity(keyMap, edmNavigationProperty, queryOptions);
                if (relatedEntity != null) {
                    resultProperty = relatedEntity.getProperty(property);
                }
            } else {
                //many
                GenericValue genericValue = delegator.findOne(modelEntity.getEntityName(), keyMap, true);
                EntityCollection relatedEntityCollection = findRelatedEntityCollection(genericValue, edmNavigationProperty, null);
                for (Entity entity : relatedEntityCollection.getEntities()) {
                    OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
                    GenericValue entityGv = ofbizEntity.getGenericValue();
                    List<GenericValue> genericValueList = EntityUtil.filterByAnd(UtilMisc.toList(entityGv), navKeyMap);
                    if (UtilValidate.isNotEmpty(genericValueList)) {
                        resultProperty = entity.getProperty(property);
                        break;
                    }
                }
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return resultProperty;
    }

    // expand????????????????????????
    public EntityCollection findRelatedEntityCollection(Entity entity,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions,
                                                        boolean isCount) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        Map<String, Object> keyMap = Util.retrieveKeyMapFromEntity(delegator, entity, csdlEntityType);

        //???????????????keyMap????????? ???EnumType??????????????????????????????
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

    // ????????????????????????????????????
    // expand????????????????????????
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
            // ??????????????????????????????????????????,???????????????????????????
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
                try { // ??????????????????handler?????????????????????getNavigationData??????
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
                //??????filter
                EntityCondition filterCondition = getFilterOption(queryOptions);
                if (filterCondition != null) {
                    genericValues = EntityUtil.filterByCondition(genericValues, filterCondition);
                }
                //??????search
                if (queryOptions != null && queryOptions.get("searchOption") != null) {
                    genericValues = EntityUtil.filterByCondition(genericValues, this.entitySearchCondition);
                }
                //?????????????????????null ????????????
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
                        //??????????????????????????????orderBy
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
            //????????????apply ?????????????????????????????????
            if (UtilValidate.isNotEmpty(odataContext.get("uriResourceParts")) && queryOptions.containsKey("applyOption")) {
                List<UriResource> uriResourceParts = (List<UriResource>) odataContext.get("uriResourceParts");
                EntityCondition applyCondition = procApplyCondition(uriResourceParts);
                //????????????
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

            /* ??????odata????????????select?????????, ??????select?????????EdmConfig????????????Property, ????????????????????????????????? */
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
            //?????????aggregate????????????????????????
            if (UtilValidate.isNotEmpty(aggregateSet)) {
                selectSet = aggregateSet;
            }
            //?????????groupBy?????? select????????????groupBy????????????????????????????????????
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
        // ??????????????????PagedList?????????data???size??????????????????????????????????????????
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
                //??????????????????
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
        /********************** debug????????????dynamicViewEntity???xml????????? ****************************************/
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
