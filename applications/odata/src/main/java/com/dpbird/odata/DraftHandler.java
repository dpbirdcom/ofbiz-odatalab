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
import org.apache.ofbiz.entity.condition.EntityFieldMap;
import org.apache.ofbiz.entity.condition.EntityJoinOperator;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.LevelsOptionImpl;

import java.net.URI;
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

    public OdataOfbizEntity updateEntityData(Map<String, Object> keyMap, Entity entityToWrite)
            throws OfbizODataException {
        GenericValue draftGenericValue = null;
        Map<String, Object> fieldMap;
        if (csdlEntityType.isAutoId()) {
            fieldMap = UtilMisc.toMap("draftUUID", keyMap.get("id"));
        } else {
            fieldMap = new HashMap<>();
            fieldMap.putAll(keyMap);
        }
        fieldMap.putAll(Util.entityToMap(entityToWrite));

        String entityName = csdlEntityType.getOfbizEntity();
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
        OdataOfbizEntity updatedEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider,
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
                "userLogin", userLogin);
        Map<String, Object> result;
        try {
            result = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        GenericValue draftGenericValue = (GenericValue) result.get("draftGenericValue");
        OdataOfbizEntity createdEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider,
                csdlEntityType, draftGenericValue, locale);
        return createdEntity;
    }

    public OdataOfbizEntity createRelatedEntityData(Map<String, Object> keyMap, Entity entityToWrite, String navigationPropertyName)
            throws OfbizODataException {
        String entityName = csdlEntityType.getOfbizEntity();
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        String nestedEntityName = nestedCsdlEntityType.getOfbizEntity();
        String nestedDraftEntityName = nestedCsdlEntityType.getDraftEntityName();

        Map<String, Object> fieldMap = Util.entityToMap(entityToWrite);
        if (keyMap.size() == 1 && keyMap.get("id") != null){
            //三段式创建draft数据，要拿第二段的id给第三段当parentUUID
            fieldMap.put("parentDraftUUID", keyMap.get("id"));
            try {
                //补全子对象的主键
                GenericValue parentDraftGV = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", keyMap.get("id")), false);
                Map<String, Object> relatedFieldMap = Util.getRelatedFieldMap(delegator, entityName, csdlNavigationProperty, parentDraftGV);
                for (String relKey : relatedFieldMap.keySet()) {
                    if (!fieldMap.containsKey(relKey)) {
                        fieldMap.put(relKey, relatedFieldMap.get(relKey));
                    }
                }
            } catch (GenericEntityException e) {
                e.printStackTrace();
            }
        } else {
            fieldMap.putAll(Util.getRelatedFieldMap(delegator, entityName, csdlNavigationProperty, keyMap));
        }
        Map<String, Object> relatedConditionMap = Util.getRelatedConditionMap(csdlNavigationProperty);
        if (UtilValidate.isNotEmpty(relatedConditionMap)) {
            fieldMap.putAll(relatedConditionMap);
        }
        //补充seqId
        OfbizCsdlEntityType navOfbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        addDraftNextSeqId(navOfbizCsdlEntityType, fieldMap);

        Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", nestedEntityName,
                "fieldMap", fieldMap, "sapContextId", this.sapContextId,
                "draftEntityName", nestedDraftEntityName,
                "entityType", nestedCsdlEntityType.getFullQualifiedNameString(),
                "navigationProperty", navigationPropertyName, "userLogin", userLogin);
        Map<String, Object> result = null;
        try {
            result = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        GenericValue nestedGenericValue = (GenericValue) result.get("draftGenericValue");
        OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, nestedCsdlEntityType, nestedGenericValue, locale);
        if (nestedCsdlEntityType.isAutoId()) {
            try {
                entity = (OdataOfbizEntity) OdataProcessorHelper.procEntityWithAutoId(true, delegator, nestedCsdlEntityType, entity);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
        }
        return entity;
    }

    public EntityCollection findRelatedEntityCollection(OfbizCsdlEntityType csdlEntityType, Map<String, Object> keyMap,
                                                        EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, QueryOption> queryOptions) throws OfbizODataException{
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
            EntityCondition entityCondition;
            if (csdlEntityType.isAutoId() && keyMap.keySet().contains("id")) {
                mainDraftGenericValue = delegator.findOne(draftEntityName, UtilMisc.toMap("draftUUID", keyMap.get("id")), false);
            } else {
                mainDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                        UtilMisc.toMap("entityType", entityType, "draftEntityName", draftEntityName),
                        null, false);
                List<String> mainDraftUUIDs = EntityUtil.getFieldListFromEntityList(mainDraftAdminDataList, "draftUUID", true);
                mainDraftUUIDs = filterDraftUUIDsByMatchingSapContextId(mainDraftUUIDs);
                entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, mainDraftUUIDs);
                EntityCondition keyMapCondition = EntityCondition.makeCondition(keyMap);
                entityCondition = EntityCondition.makeCondition(entityCondition, EntityJoinOperator.AND, keyMapCondition);
                mainDraftGenericValues = delegator.findList(draftEntityName, entityCondition, null, null, null, false);
                mainDraftGenericValue = EntityUtil.getFirst(mainDraftGenericValues);
            }
            if (mainDraftGenericValue == null) return new EntityCollection();
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
                for (String draftUUID:draftUUIDs) {
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
            OdataOfbizEntity rowEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, elementGV, locale);
            if (UtilValidate.isEmpty(rowEntity)) {
                continue;
            }
            if (navCsdlEntityType.isAutoId()) {
                rowEntity = procEntityWithAutoId(elementGV, rowEntity);
            }
//            URI entityId = Util.createId(edmNavigationProperty.getName(), edmNavigationProperty.getType(), navCsdlEntityType, elementGV);
//            rowEntity.setId(entityId);
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

    private static OdataOfbizEntity procEntityWithAutoId (GenericValue draftGenericValue,
                                                OdataOfbizEntity entity) {
        String id = draftGenericValue.getString("draftUUID");
        entity.addProperty(new Property(null, "id", ValueType.PRIMITIVE, id));
        return entity;
    }

    public void deleteEntityData(Map<String, Object> keyMap) throws OfbizODataException {
        if (csdlEntityType.isAutoId()) {
            keyMap = UtilMisc.toMap("draftUUID", keyMap.get("id"));
        }
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
            if (navCsdlEntityType.isAutoId()) { // 主键就是id，并且值就是draftUUID
                String id = (String) navKeyMap.get("id");
                if (draftUUIDs.contains(id)) {
                    draftGenericValue = delegator.findOne(navDraftEntityName, UtilMisc.toMap("draftUUID", id), false);
                } else {
                    return null;
                }
            } else {
                EntityCondition entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, draftUUIDs);
                EntityCondition keyMapCondition = EntityCondition.makeCondition(keyMap);
                entityCondition = EntityCondition.makeCondition(entityCondition, EntityJoinOperator.AND, keyMapCondition);
                draftGenericValues = delegator.findList(navDraftEntityName, entityCondition, null, null, null, false);
                if (UtilValidate.isNotEmpty(navKeyMap)) {
                    draftGenericValues = EntityUtil.filterByAnd(draftGenericValues, navKeyMap);
                }
                draftGenericValue = EntityUtil.getFirst(draftGenericValues);

            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, navCsdlEntityType, draftGenericValue, locale);
        if (navCsdlEntityType.isAutoId()) {
            entity = procEntityWithAutoId(draftGenericValue, entity);
        }
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
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
        if (navCsdlEntityType.getDraftEntityName() != null && entity.getKeyMap().containsKey("draftUUID")) {
            return draftHandler.findRelatedEntityCollection(csdlEntityType, keyMap, edmNavigationProperty, queryOptions);
        } else {
            try {
                //子对象不使用draft, 或者是saveAction的返回, 读真实数据库
                //pk是id就是第二段Draft数据的expand
                Object pkValue = keyMap.size() == 1 && keyMap.containsKey("id") ? keyMap.get("id") : sapContextId;
                GenericValue mainGenericValue = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", pkValue), false);
                //saveAction之后的expand
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    mainGenericValue = delegator.findOne(csdlEntityType.getOfbizEntity(), keyMap, false);
                }
                //draft未保存的数据
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    //尝试根据现有主键查询Draft数据 用Draft数据的GenericValue去查询expand
                    List<GenericValue> findByAnd = delegator.findByAnd(csdlEntityType.getDraftEntityName(), keyMap, null, false);
                    if (UtilValidate.isNotEmpty(findByAnd)) {
                        mainGenericValue = EntityUtil.getFirst(findByAnd);
                    } else {
                        return new EntityCollection();
                    }
                }

                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", null, "locale", locale);
                Map<String, Object> edmParams = UtilMisc.toMap("edmEntityType", edmEntityType);
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryOptions, edmParams);
                OdataOfbizEntity ofbizEntity = ofbizOdataReader.makeEntityFromGv(mainGenericValue);
                return ofbizOdataReader.findRelatedEntityCollection(ofbizEntity, edmNavigationProperty, queryOptions);
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
        for (String draftUUID:draftUUIDs) {
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
        for (GenericValue draftAdminData:draftAdminDataList) {
            if (isMatchSapContextId(draftAdminData.getString("draftUUID"))) {
                result.add(draftAdminData);
            }
        }
        return result;
    }

    public OdataOfbizEntity readEntityData(OfbizCsdlEntityType csdlEntityType, Map<String, Object> keyMap, Map<String, QueryOption> queryOptions)
            throws OfbizODataException {
        GenericValue genericValue = null;
        OdataOfbizEntity entity;
        String draftEntityName = csdlEntityType.getDraftEntityName();
        try {
            Map<String, Object> conditionMap = keyMap;
            if (csdlEntityType.isAutoId() && keyMap.containsKey("id") && keyMap.size() == 1) {
                conditionMap = UtilMisc.toMap("draftUUID", keyMap.get("id"));
            }
            List<GenericValue> genericValues = delegator.findByAnd(draftEntityName, conditionMap, null, false);
            genericValue = EntityUtil.getFirst(genericValues);
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        if (genericValue == null) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + draftEntityName);
        }
        entity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, csdlEntityType, genericValue, locale);
        if (csdlEntityType.isAutoId()) {
            entity = procEntityWithAutoId(genericValue, entity);
        }
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
