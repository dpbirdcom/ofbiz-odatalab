package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityJoinOperator;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * DraftHandler的缺省实现
 *
 * @date 2023/3/2
 */
public class DefaultDraftHandler implements DraftHandler {
    @Override
    public GenericValue finOne(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> keyMap, Map<String, Object> navigationParam) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        try {
            GenericValue genericValue;
            if (UtilValidate.isEmpty(navigationParam)) {
                OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
                keyMap = Util.fieldToProperty(keyMap, csdlEntityType);
                genericValue = EntityQuery.use(delegator).from(csdlEntityType.getDraftEntityName()).where(keyMap).queryFirst();
            } else {
                //多段式查询 根据传递的主对象去做查询
                OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) navigationParam.get("entity");
                EdmEntityType mainEdmEntityType = (EdmEntityType) navigationParam.get("edmEntityType");
                EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
                OfbizCsdlEntityType mainCsdlEntity = (OfbizCsdlEntityType) edmProvider.getEntityType(mainEdmEntityType.getFullQualifiedName());
                OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) mainCsdlEntity.getNavigationProperty(edmNavigationProperty.getName());
                OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
                String parentUuId = (String) ofbizEntity.getPropertyValue("draftUUID");
                List<GenericValue> draftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                        UtilMisc.toMap("parentDraftUUID", parentUuId, "navigationProperty", edmNavigationProperty.getName(), "entityType", navCsdlEntityType.getFullQualifiedNameString()), null, false);
                List<String> draftUuIds = EntityUtil.getFieldListFromEntityList(draftAdminDataList, "draftUUID", true);
                EntityCondition entityCondition = EntityCondition.makeCondition("draftUUID", EntityJoinOperator.IN, draftUuIds);
                EntityCondition relationCondition = Util.getEntityRelationCondition(delegator, ofbizEntity, mainCsdlEntity, csdlNavigationProperty);
                entityCondition = Util.appendCondition(entityCondition, relationCondition);
                if (UtilValidate.isNotEmpty(keyMap)) {
                    EntityCondition primaryCondition = EntityCondition.makeCondition(Util.fieldToProperty(keyMap, navCsdlEntityType));
                    entityCondition = Util.appendCondition(entityCondition, primaryCondition);
                }
                genericValue = EntityQuery.use(delegator).from(navCsdlEntityType.getDraftEntityName()).where(entityCondition).queryFirst();
            }
            if (genericValue == null) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Entity not found: " + edmEntityType.getName());
            }
            return genericValue;
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public List<GenericValue> findList(Map<String, Object> odataContext, Entity entity, EdmEntityType edmEntityType,
                                       EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        //draft模式findList应该只有多段式的 一段查询不会进入到这里
        Delegator delegator = (Delegator) odataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        try {
            //根据主对象的draftUUID 查询子对象
            OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
            Map<String, Object> keyMap = ofbizEntity.getKeyMap();
            List<GenericValue> navDraftAdminList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap("parentDraftUUID", keyMap.get("draftUUID"),
                    "entityType", navCsdlEntityType.getFullQualifiedNameString(), "draftEntityName", navCsdlEntityType.getDraftEntityName(), "navigationProperty", csdlNavigationProperty.getName()), null, false);
            List<String> navDraftIds = EntityUtil.getFieldListFromEntityList(navDraftAdminList, "draftUUID", true);
            List<EntityCondition> entityConditionList = UtilMisc.toList(EntityCondition.makeCondition("isActiveEntity", "Y"),
                    EntityCondition.makeCondition("hasDraftEntity", "Y"));
            EntityCondition queryCondition = EntityCondition.makeCondition(entityConditionList, EntityOperator.OR);
            EntityCondition draftIdCondition = EntityCondition.makeCondition("draftUUID", EntityOperator.IN, navDraftIds);
            queryCondition = Util.appendCondition(queryCondition, draftIdCondition);
            return EntityQuery.use(delegator).from(navCsdlEntityType.getDraftEntityName()).where(queryCondition).queryList();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public GenericValue updateEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> keyMap, Map<String, Object> toUpdateFields) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        try {
            //更新Draft
            GenericValue draftGenericValue = EntityQuery.use(delegator).from(csdlEntityType.getDraftEntityName()).where(keyMap).queryFirst();
            draftGenericValue.putAll(toUpdateFields);
            delegator.store(draftGenericValue);
            return draftGenericValue;
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public GenericValue createEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Entity mainEntity,
                                            Map<String, Object> toCreateFields, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        String draftEntityName;
        if (UtilValidate.isEmpty(mainEntity)) {
            //创建主对象draft
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            draftEntityName = csdlEntityType.getDraftEntityName();
        } else {
            //创建子对象draft
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
            draftEntityName = navCsdlEntityType.getDraftEntityName();
        }
        try {
            return delegator.create(draftEntityName, toCreateFields);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public void deleteEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> primaryKey) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        try {
            Map<String, Object> draftDeleteParam = UtilMisc.toMap("isActiveEntity", "N", "hasDraftEntity", "N");
            EntityCondition delCondition = EntityCondition.makeCondition("draftUUID", primaryKey.get("draftUUID"));
            delegator.storeByCondition(csdlEntityType.getDraftEntityName(), draftDeleteParam, delCondition);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

}
