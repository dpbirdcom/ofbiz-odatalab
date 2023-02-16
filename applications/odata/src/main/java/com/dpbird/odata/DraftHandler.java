package com.dpbird.odata;

import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityJoinOperator;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.LevelsOptionImpl;

import java.util.*;

public class DraftHandler {
    public static final String module = DraftHandler.class.getName();
    protected Delegator delegator;
    protected LocalDispatcher dispatcher;
    protected GenericValue userLogin;
    protected Locale locale;
    protected EntityCondition entityCondition = null;
    protected OfbizAppEdmProvider edmProvider;
    protected OfbizCsdlEntityType csdlEntityType;
    protected EdmEntityType edmEntityType;
    protected String sapContextId;
    public static final int MAX_ROWS = 10000;

    public DraftHandler(Delegator delegator, LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                        OfbizCsdlEntityType csdlEntityType, String sapContextId,
                        GenericValue userLogin, Locale locale, EdmEntityType edmEntityType) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.userLogin = userLogin;
        this.locale = locale;
        this.edmProvider = edmProvider;
        this.csdlEntityType = csdlEntityType;
        this.edmEntityType = edmEntityType;
        this.sapContextId = sapContextId;
    }

    public DraftHandler(Map<String, Object> odataContext, String sapContextId, EdmEntityType edmEntityType) throws OfbizODataException {
        this.delegator = (Delegator) odataContext.get("delegator");
        this.dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        this.userLogin = (GenericValue) odataContext.get("userLogin");
        this.locale = (Locale) odataContext.get("locale");
        this.edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        this.edmEntityType = edmEntityType;
        this.sapContextId = sapContextId;
        this.csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
    }

    public OdataOfbizEntity updateEntityData(Map<String, Object> keyMap, Entity entityToWrite)
            throws OfbizODataException {
        GenericValue draftGenericValue = null;
        Map<String, Object> fieldMap = new HashMap<>(keyMap);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        fieldMap = Util.fieldToProperty(fieldMap, csdlEntityType);
        fieldMap.putAll(Util.entityToMap(entityToWrite));

        if (csdlEntityType.getDraftEntityName() != null) {
            Map<String, Object> serviceParams = UtilMisc.toMap("csdlEntityType", csdlEntityType,
                    "sapContextId", this.sapContextId, "fieldMap", fieldMap, "userLogin", userLogin);
            Map<String, Object> result = null;
            try {
                result = dispatcher.runSync("dpbird.updateEntityToDraft", serviceParams);
            } catch (GenericServiceException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
            draftGenericValue = (GenericValue) result.get("draftGenericValue");
        }
        OdataOfbizEntity updatedEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider,
                csdlEntityType, draftGenericValue, locale);
        return updatedEntity;
    }

    // 主对象的新建都是调用NewAction，而子对象的新建都是调用createRelatedEntity，似乎createEntityData不会进入
    public OdataOfbizEntity createEntityData(Entity entityToWrite)
            throws OfbizODataException {
        String entityName = csdlEntityType.getOfbizEntity();
        String draftEntityName = csdlEntityType.getDraftEntityName();
        Map<String, Object> fieldMap = Util.entityToMap(entityToWrite);
        Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", entityName,
                "fieldMap", fieldMap, "sapContextId", sapContextId,
                "draftEntityName", draftEntityName, "entityType", csdlEntityType.getFullQualifiedNameString(),
                "userLogin", userLogin, "edmProvider", edmProvider, "edmEntityType", edmEntityType);
        Map<String, Object> result;
        try {
            result = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        GenericValue draftGenericValue = (GenericValue) result.get("draftGenericValue");
        OdataOfbizEntity createdEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider,
                csdlEntityType, draftGenericValue, locale);
        return createdEntity;
    }

    public OdataOfbizEntity createRelatedEntityData(Entity mainEntity, Entity entityToWrite, EdmNavigationProperty edmNavigationProperty)
            throws OfbizODataException {
        String entityName = csdlEntityType.getOfbizEntity();
        OdataOfbizEntity mainOfbizEntity = (OdataOfbizEntity) mainEntity;
        Map<String, Object> keyMap = new HashMap<>(mainOfbizEntity.getKeyMap());
        String navigationPropertyName = edmNavigationProperty.getName();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        String nestedEntityName = nestedCsdlEntityType.getOfbizEntity();
        String nestedDraftEntityName = nestedCsdlEntityType.getDraftEntityName();

        Map<String, Object> fieldMap = Util.entityToMap(entityToWrite);
        if (keyMap.size() == 1 && (keyMap.get("id") != null || keyMap.get("draftUUID") != null)) {
            //三段式创建draft数据，要拿第二段的id给第三段当parentUUID
            String draftUUId = keyMap.get("draftUUID") != null ? (String) keyMap.get("draftUUID") : (String) keyMap.get("id");
            try {
                //补全子对象的主键
                GenericValue parentDraftGV = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", draftUUId), false);
                Map<String, Object> relatedFieldMap = Util.getRelatedFieldMap(delegator, entityName, csdlNavigationProperty, parentDraftGV, edmProvider);
                for (String relKey : relatedFieldMap.keySet()) {
                    if (!fieldMap.containsKey(relKey)) {
                        fieldMap.put(relKey, relatedFieldMap.get(relKey));
                    }
                }
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
        } else {
            Map<String, Object> propertyKey = Util.propertyToField(keyMap, csdlEntityType);
            fieldMap.putAll(Util.getRelatedFieldMap(delegator, entityName, csdlNavigationProperty, propertyKey, edmProvider));
        }
        //添加Navigation Condition
        Map<String, Object> relatedConditionMap = Util.getRelatedConditionMap(csdlNavigationProperty);
        if (UtilValidate.isNotEmpty(relatedConditionMap)) {
            fieldMap.putAll(relatedConditionMap);
        }
        //补充seqId
        OfbizCsdlEntityType navOfbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        addDraftNextSeqId(navOfbizCsdlEntityType, fieldMap);
        //添加Entity DefaultProperty
        for (Map.Entry<String, Object> entry : navOfbizCsdlEntityType.getDefaultValueProperties().entrySet()) {
            fieldMap.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", nestedEntityName,
                "fieldMap", fieldMap, "sapContextId", this.sapContextId,
                "draftEntityName", nestedDraftEntityName, "edmProvider", edmProvider,
                "entityType", nestedCsdlEntityType.getFullQualifiedNameString(), "edmEntityType", edmNavigationProperty.getType(),
                "navigationProperty", navigationPropertyName, "userLogin", userLogin);
        Map<String, Object> result = null;
        try {
            result = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        GenericValue nestedGenericValue = (GenericValue) result.get("draftGenericValue");
        return OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, nestedCsdlEntityType, nestedGenericValue, locale);
    }

    public EntityCollection findRelatedEntityCollection(OfbizCsdlEntityType csdlEntityType, Map<String, Object> keyMap,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        String navEntityType = csdlNavigationProperty.getType();
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        String navDraftEntityName = navCsdlEntityType.getDraftEntityName();
        String entityType = csdlEntityType.getFullQualifiedNameString();
        String draftEntityName = csdlEntityType.getDraftEntityName();
        List<GenericValue> navDraftAdminDataList;
        List<GenericValue> mainDraftAdminDataList;
        GenericValue mainDraftAdminData;
        List<GenericValue> navDraftGenericValues = new ArrayList<>();
        List<GenericValue> mainDraftGenericValues;
        GenericValue mainDraftGenericValue;
        try {
            // find main draft entity
            mainDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                    UtilMisc.toMap("entityType", entityType, "draftEntityName", draftEntityName),
                    null, false);
            List<String> mainDraftUUIDs = EntityUtil.getFieldListFromEntityList(mainDraftAdminDataList, "draftUUID", true);
            mainDraftUUIDs = filterDraftUUIDsByMatchingSapContextId(mainDraftUUIDs);
            EntityCondition entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, mainDraftUUIDs);
            EntityCondition keyMapCondition = EntityCondition.makeCondition(keyMap);
            entityCondition = EntityCondition.makeCondition(entityCondition, EntityJoinOperator.AND, keyMapCondition);
            mainDraftGenericValues = delegator.findList(draftEntityName, entityCondition, null, null, null, false);
            mainDraftGenericValue = EntityUtil.getFirst(mainDraftGenericValues);
            if (mainDraftGenericValue == null) {
                return new EntityCollection();
            }
            mainDraftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", mainDraftGenericValue.get("draftUUID")), false);
            String mainDraftParentUUID = mainDraftAdminData.getString("parentDraftUUID");

            boolean isReverse = false;
            navDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                    UtilMisc.toMap("parentDraftUUID", mainDraftAdminData.getString("draftUUID"), "entityType", navEntityType, "draftEntityName", navDraftEntityName, "navigationProperty", csdlNavigationProperty.getName()),
                    null, false);
            if (UtilValidate.isEmpty(navDraftAdminDataList)) { // 说明可能是从子对象反向nav回父对象
                navDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                        UtilMisc.toMap("entityType", navEntityType, "draftEntityName", navDraftEntityName),
                        null, false);
                isReverse = true;
            }
            List<String> draftUUIDs = EntityUtil.getFieldListFromEntityList(navDraftAdminDataList, "draftUUID", true);
            draftUUIDs = filterDraftUUIDsByMatchingSapContextId(draftUUIDs);
            if (isReverse) {
                for (String draftUUID : draftUUIDs) {
                    if (draftUUID.equals(mainDraftParentUUID)) {
                        GenericValue navGenericValue = delegator.findOne(navDraftEntityName, UtilMisc.toMap("draftUUID", draftUUID), false);
                        navDraftGenericValues.add(navGenericValue);
                        break;
                    }
                }
            } else {
                entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, draftUUIDs);
                navDraftGenericValues = delegator.findList(navDraftEntityName, entityCondition, null, null, null, false);
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        //排除在draft中标记为已删除的数据
        EntityCondition isDeleteCondition = EntityCondition.makeCondition(UtilMisc.toList(
                EntityCondition.makeCondition("isActiveEntity", EntityOperator.EQUALS, "N"),
                EntityCondition.makeCondition("hasDraftEntity", EntityOperator.EQUALS, "N")), EntityOperator.AND);
        navDraftGenericValues = EntityUtil.filterOutByCondition(navDraftGenericValues, isDeleteCondition);
        //此刻的数据量 作为count
        int listTotalCount = navDraftGenericValues.size();
        //分页
        if (UtilValidate.isNotEmpty(navDraftGenericValues)) {
            int topValue = getTopOption(queryOptions);
            int skipValue = getSkipOption(queryOptions);
            if ((skipValue + topValue) > navDraftGenericValues.size()) {
                if (navDraftGenericValues.size() <= skipValue) return new EntityCollection();
                navDraftGenericValues = navDraftGenericValues.subList(skipValue, navDraftGenericValues.size());
            } else {
                navDraftGenericValues = navDraftGenericValues.subList(skipValue, skipValue + topValue);
            }
        }
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entityList = entityCollection.getEntities();
        for (GenericValue elementGV : navDraftGenericValues) {
            OdataOfbizEntity rowEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, navCsdlEntityType, elementGV, locale);
            entityList.add(rowEntity);
        }
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            for (Entity entity : entityList) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, navCsdlEntityType, edmNavigationProperty.getType());
            }
        }
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("countOption") != null) {
            CountOption countOption = (CountOption) queryOptions.get("countOption");
            if (countOption.getValue()) {
                entityCollection.setCount(listTotalCount);
            }
        }
        return entityCollection;
    }

    public void deleteEntityData(Map<String, Object> keyMap) throws OfbizODataException {
        Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", csdlEntityType.getOfbizEntity(),
                "draftEntityName", csdlEntityType.getDraftEntityName(), "keyMap", keyMap,
                "sapContextId", sapContextId, "userLogin", userLogin);
        try {
            Map<String, Object> result = dispatcher.runSync("dpbird.deleteEntityFromDraft", serviceParams);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    public Entity getRelatedEntityData(Map<String, Object> keyMap,
                                       EdmNavigationProperty edmNavigationProperty,
                                       Map<String, Object> navKeyMap,
                                       Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        String navEntityType = csdlNavigationProperty.getType();
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        String navDraftEntityName = navCsdlEntityType.getDraftEntityName();
        List<GenericValue> draftAdminDataList;
        List<GenericValue> draftGenericValues;
        GenericValue draftGenericValue;
        try {
            draftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                    UtilMisc.toMap("parentDraftUUID", sapContextId, "navigationProperty", edmNavigationProperty.getName(), "entityType", navEntityType),
                    null, false);
            List<String> draftUUIDs = EntityUtil.getFieldListFromEntityList(draftAdminDataList, "draftUUID", true);
            EntityCondition entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, draftUUIDs);
            //添加主外键条件，如果是多段relations应该不需要添加
            //有时候主外键对应的字段名并不一致(orderId->primaryOrderId),要做转换处理
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            if (relAlias.getRelations().size() == 1) {
                EntityCondition keyMapCondition = EntityCondition.makeCondition(keyMap);
                ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
                ModelRelation relation = modelEntity.getRelation(relAlias.getRelations().get(0));
                if (relation != null) {
                    Map<String, Object> fkMapping = new HashMap<>();
                    for (Map.Entry<String, Object> entry : keyMap.entrySet()) {
                        ModelKeyMap currModelKey = relation.findKeyMap(entry.getKey());
                        if (UtilValidate.isNotEmpty(currModelKey)) {
                            fkMapping.put(currModelKey.getRelFieldName(), entry.getValue());
                        }
                    }
                    keyMapCondition = EntityCondition.makeCondition(fkMapping);
                }
                entityCondition = EntityCondition.makeCondition(entityCondition, EntityJoinOperator.AND, keyMapCondition);
            }
            //query
            draftGenericValues = delegator.findList(navDraftEntityName, entityCondition, null, null, null, false);
            if (UtilValidate.isEmpty(draftGenericValues)) {
                return null;
            }
            if (UtilValidate.isNotEmpty(navKeyMap)) {
                Map<String, Object> propertyKey = Util.fieldToProperty(navKeyMap, navCsdlEntityType);
                draftGenericValues = EntityUtil.filterByAnd(draftGenericValues, propertyKey);
            }
            draftGenericValue = EntityUtil.getFirst(draftGenericValues);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, navCsdlEntityType, draftGenericValue, locale);
        if (queryOptions != null) {
            if (queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, navCsdlEntityType, edmNavigationProperty.getType());
            }
        }
        return entity;
    }


    protected void addExpandOption(ExpandOption expandOption, OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType, EdmEntityType currEdmEntityType)
            throws OfbizODataException {
        if (expandOption == null) {
            return;
        }
        List<ExpandItem> expandItems = expandOption.getExpandItems();
        for (ExpandItem expandItem : expandItems) {
            addExpandItem(entity, expandItem, csdlEntityType, currEdmEntityType);
        } // end for (ExpandItem expandItem : expandItems)
        Debug.logInfo("finished adding all expand items", module);
    }

    private void addExpandItem(OdataOfbizEntity entity, ExpandItem expandItem, OfbizCsdlEntityType csdlEntityType, EdmEntityType currEdmEntityType) throws OfbizODataException {
        EdmNavigationProperty edmNavigationProperty = null;
        LevelsExpandOption levelsExpandOption = expandItem.getLevelsOption();
        int expandLevel = 1;
        if (levelsExpandOption != null) {
            expandLevel = levelsExpandOption.getValue();
        }
        UriResource uriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
        if (uriResource instanceof UriResourceNavigation) {
            edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
        }
        if (edmNavigationProperty == null) {
            return;
        }
        String navPropName = edmNavigationProperty.getName();
        Debug.logInfo("adding expand option with name = " + navPropName, module);
        if (edmNavigationProperty.isCollection()) { // expand的对象是collection
            FilterOption filterOption = expandItem.getFilterOption();
            OrderByOption orderByOption = expandItem.getOrderByOption();
            ExpandOption nestedExpandOption = expandItem.getExpandOption(); // expand nested in expand
            if (nestedExpandOption == null && expandLevel > 1) {
                ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
                LevelsOptionImpl levelsOptionImpl = (LevelsOptionImpl) levelsExpandOption;
                levelsOptionImpl.setValue(expandLevel--);
                expandOptionImpl.addExpandItem(expandItem);
                nestedExpandOption = expandOptionImpl;
            }
            expandCollection(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        } else { // expand对象不是collection
            // 此处改过
            FilterOption filterOption = expandItem.getFilterOption();
            OrderByOption orderByOption = expandItem.getOrderByOption();
            ExpandOption nestedExpandOption = expandItem.getExpandOption(); // expand nested in expand
            expandNonCollection(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        } // end expand对象不是collection
    }

    private void expandCollection(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                  EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                  FilterOption filterOption, OrderByOption orderByOption,
                                  ExpandOption nestedExpandOption) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, csdlEntityType, edmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        String navPropName = edmNavigationProperty.getName();
        Link link = new Link();
        link.setTitle(navPropName);
        link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
        link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
        link.setInlineEntitySet(expandEntityCollection);
        if (entity.getId() != null) { // TODO:要检查一下为什么会有id为null的情况
            String linkHref = entity.getId().toString() + "/" + navPropName;
            link.setHref(linkHref);
        }
        entity.getNavigationLinks().add(link);
    }

    private void expandNonCollection(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                     EdmEntityType currEdmEntityType, EdmNavigationProperty edmNavigationProperty,
                                     FilterOption filterOption, OrderByOption orderByOption,
                                     ExpandOption nestedExpandOption) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        if (null != expandEntityCollection && UtilValidate.isNotEmpty(expandEntityCollection.getEntities())) {
            Entity expandEntity = expandEntityCollection.getEntities().get(0);
            Link link = new Link();
            String navPropName = edmNavigationProperty.getName();
            link.setTitle(navPropName);
            link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
            link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
            link.setInlineEntity(expandEntity);
            if (entity.getId() != null) {
                String linkHref = entity.getId().toString() + "/" + navPropName;
                link.setHref(linkHref);
            }
            entity.getNavigationLinks().add(link);
        }
    }

    private EntityCollection getExpandData(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                           EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                           FilterOption filterOption, OrderByOption orderByOption,
                                           ExpandOption nestedExpandOption) throws OfbizODataException {
        DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, edmEntityType);
        Map<String, QueryOption> queryOptions = UtilMisc.toMap("expandOption", nestedExpandOption);
        Map<String, Object> keyMap = Util.getKeyMapFromEntity(edmProvider, entity);
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
        if (navCsdlEntityType.getDraftEntityName() != null && entity.getKeyMap().containsKey("draftUUID") && !csdlNavigationProperty.isReadOnly()) {
            return draftHandler.findRelatedEntityCollection(csdlEntityType, keyMap, edmNavigationProperty, queryOptions);
        } else {
            try {
                //子对象不使用draft, 或者是saveAction的返回, 读真实数据库
                //pk是id就是第二段Draft数据的expand
                Object pkValue = keyMap.size() == 1 && keyMap.containsKey("id") ? keyMap.get("id") : sapContextId;
                GenericValue mainGenericValue = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", pkValue), false);
                //saveAction之后的expand
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    Map<String, Object> fieldKey = Util.propertyToField(keyMap, csdlEntityType);
                    mainGenericValue = delegator.findOne(csdlEntityType.getOfbizEntity(), fieldKey, false);
                }
                //draft未保存的数据
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    //尝试根据现有主键查询Draft数据 用Draft数据的GenericValue去查询expand
                    Map<String, Object> fieldKey = Util.propertyToField(keyMap, csdlEntityType);
                    List<GenericValue> findByAnd = delegator.findByAnd(csdlEntityType.getDraftEntityName(), fieldKey, null, false);
                    if (UtilValidate.isNotEmpty(findByAnd)) {
                        mainGenericValue = EntityUtil.getFirst(findByAnd);
                    } else {
                        return new EntityCollection();
                    }
                }

                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", null, "locale", locale);
                Map<String, Object> edmParams = UtilMisc.toMap("edmEntityType", edmEntityType);
                OdataReader reader = new OdataReader(odataContext, queryOptions, edmParams);
                OdataOfbizEntity ofbizEntity = reader.makeEntityFromGv(mainGenericValue);
                return reader.findRelatedList(ofbizEntity, edmNavigationProperty, queryOptions, null);
            } catch (GenericEntityException e) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), e.getMessage());
            }
        }
    }

    private List<GenericValue> getGenericValuesFromRelations(GenericValue genericValue, EntityTypeRelAlias relAlias,
                                                             List<String> relations,
                                                             boolean filterByDate) throws GenericEntityException, OfbizODataException {
        String relation = relations.get(0);
        ModelEntity theModelEntity = genericValue.getModelEntity();
        ModelRelation modelRelation = theModelEntity.getRelation(relation);
        if (modelRelation.getType().contains("one")) {
            GenericValue relGenericValue = genericValue.getRelatedOne(relation, true);
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

    private boolean isMatchSapContextId(String draftUUID) throws GenericEntityException {
        if (sapContextId.equals(draftUUID)) {
            return true;
        }
        GenericValue draftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", draftUUID), false);
        String parentDraftUUID = draftAdminData.getString("parentDraftUUID");
        if (parentDraftUUID != null) {
            return isMatchSapContextId(parentDraftUUID);
        } else {
            return false;
        }
    }

    private List<String> filterDraftUUIDsByMatchingSapContextId(List<String> draftUUIDs) throws GenericEntityException {
        List<String> result = new ArrayList<>();
        for (String draftUUID : draftUUIDs) {
            if (isMatchSapContextId(draftUUID)) {
                result.add(draftUUID);
            }
        }
        return result;
    }

    protected int getTopOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("topOption") != null
                && ((TopOption) queryOptions.get("topOption")).getValue() > 0) {
            return ((TopOption) queryOptions.get("topOption")).getValue();
        }
        return MAX_ROWS;
    }

    protected int getSkipOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("skipOption") != null
                && ((SkipOption) queryOptions.get("skipOption")).getValue() > 0) {
            return ((SkipOption) queryOptions.get("skipOption")).getValue();
        }
        return 0;
    }

    private void addDraftNextSeqId(OfbizCsdlEntityType ofbizCsdlEntityType, Map<String, Object> fieldMap) {
        //位数
        final int numericPadding = 5;
        //递增数
        final int incrementBy = 1;
        ModelEntity modelEntity = delegator.getModelEntity(ofbizCsdlEntityType.getOfbizEntity());
        ModelEntity modelDraftEntity = delegator.getModelEntity(ofbizCsdlEntityType.getDraftEntityName());
        Map<String, Object> pkMap = new HashMap<>();
        //获取seqId以外的主键
        for (String pkFieldName : modelEntity.getPkFieldNames()) {
            if (!pkFieldName.endsWith("SeqId")) {
                pkMap.put(pkFieldName, fieldMap.get(pkFieldName));
            }
        }
        //获取下一个seqId 添加到fieldMap
        for (String pkFieldName : modelEntity.getPkFieldNames()) {
            if (pkFieldName.endsWith("SeqId") && modelDraftEntity.isField(pkFieldName) && UtilValidate.isEmpty(fieldMap.get("SeqId"))) {
                GenericValue genericValue = delegator.makeValue(modelDraftEntity.getEntityName(), pkMap);
                delegator.setNextSubSeqId(genericValue, pkFieldName, numericPadding, incrementBy);
                fieldMap.put(pkFieldName, genericValue.get(pkFieldName));
            }
        }
    }

    private List<GenericValue> filterAdminDataListByMatchingSapContextId(List<GenericValue> draftAdminDataList) throws GenericEntityException {
        List<GenericValue> result = new ArrayList<>();
        for (GenericValue draftAdminData : draftAdminDataList) {
            if (isMatchSapContextId(draftAdminData.getString("draftUUID"))) {
                result.add(draftAdminData);
            }
        }
        return result;
    }

    public OdataOfbizEntity readEntityData(EdmEntityType edmEntityType, Map<String, Object> keyMap, Map<String, QueryOption> queryOptions)
            throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        return readEntityData(csdlEntityType, keyMap, queryOptions);
    }

    public OdataOfbizEntity readEntityData(OfbizCsdlEntityType csdlEntityType, Map<String, Object> keyMap, Map<String, QueryOption> queryOptions)
            throws OfbizODataException {
        GenericValue genericValue = null;
        OdataOfbizEntity entity;
        String draftEntityName = csdlEntityType.getDraftEntityName();
        keyMap = Util.fieldToProperty(keyMap, csdlEntityType);
        try {
            List<GenericValue> genericValues = delegator.findByAnd(draftEntityName, keyMap, null, false);
            genericValue = EntityUtil.getFirst(genericValues);
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        if (genericValue == null) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + draftEntityName);
        }
        entity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, genericValue, locale);
        if (queryOptions != null) {
            if (queryOptions.get("expandOption") != null) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, csdlEntityType, edmEntityType);
            }
        }
        if (csdlEntityType.getHandlerClass() != null) {
            OdataProcessorHelper.procEntityListWithHandler(null, delegator, dispatcher,
                    queryOptions, csdlEntityType.getHandlerClass(), UtilMisc.toList(entity), locale, userLogin);
        }
        entity.setKeyMap(keyMap);
        return entity;
    }

}
