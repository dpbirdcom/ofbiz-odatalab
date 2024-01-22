package com.dpbird.odata.services;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.DraftHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.NavigationHandler;
import com.dpbird.odata.handler.annotation.DraftAction;
import com.dpbird.odata.handler.annotation.DraftEventContext;
import com.dpbird.odata.handler.annotation.EdmEntity;
import com.dpbird.odata.handler.annotation.EdmService;
import com.dpbird.odata.processor.DataModifyActions;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericPK;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.datasource.GenericHelperInfo;
import org.apache.ofbiz.entity.jdbc.DatabaseUtil;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.*;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataResponse;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

public class ProcessorServices {


    public final static String module = ProcessorServices.class.getName();
    public final static String resource = "OdataUiLabels";
    //自定义Event注解要扫描的路径
//    public final static String PACKAGE_NAME = "com.dpbird";
    public final static String PACKAGE_NAME = "com.banfftech";

    public static Map<String, Object> createEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Entity entityToWrite = (Entity) context.get("entityToWrite");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "entityToWrite", entityToWrite);
        OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
        Entity createdEntity = writer.createEntityData(entityToWrite);
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("createdEntity", createdEntity);
        return result;
    }

    public static Map<String, Object> createRelatedEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String sapContextId = (String) context.get("sapContextId");
        HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("httpServletRequest");
        Entity entityToWrite = (Entity) context.get("entityToWrite");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        OdataOfbizEntity entity = (OdataOfbizEntity) context.get("entity");
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        Entity createdEntity;
        EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        EdmEntitySet navigationTargetEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
        OfbizCsdlEntitySet navigationCsdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, navigationTargetEntitySet.getName());
        Util.addEntitySetConditionToEntity(delegator, navigationCsdlEntitySet, entityToWrite, userLogin, httpServletRequest);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            DraftReaderAndWriter draftReaderAndWriter = new DraftReaderAndWriter(odataContext, sapContextId, edmBindingTarget.getEntityType());
            createdEntity = draftReaderAndWriter.createEntityData(entity, entityToWrite, edmNavigationProperty);
        } else {
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget,
                    "edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite);
            OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
            createdEntity = writer.createRelatedEntity(entity, entityToWrite);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("createdEntity", createdEntity);
        return result;
    }

    public static Map<String, Object> updateEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String sapContextId = (String) context.get("sapContextId");
        Map<String, Object> primaryKey = UtilGenerics.checkMap(context.get("primaryKey"));
        Entity entityToWrite = (Entity) context.get("entityToWrite");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        Entity updatedEntity;
        if (UtilValidate.isNotEmpty(sapContextId)) {
            DraftReaderAndWriter draftReaderAndWriter = new DraftReaderAndWriter(odataContext, sapContextId, edmBindingTarget.getEntityType());
            updatedEntity = draftReaderAndWriter.updateEntityData(primaryKey, entityToWrite);
        } else {
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "entityToWrite", entityToWrite);
            OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
            updatedEntity = writer.updateEntityData(primaryKey, entityToWrite);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("entity", updatedEntity);
        return result;
    }

    public static Map<String, Object> updateRelatedEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        String sapContextId = (String) context.get("sapContextId");
        Entity entityToWrite = (Entity) context.get("entityToWrite");
        HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("httpServletRequest");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        OdataOfbizEntity entity = (OdataOfbizEntity) context.get("entity");
        Map<String, Object> primaryKey = UtilGenerics.checkMap(context.get("primaryKey"));
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        Entity updatedEntity;
        EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
        EdmEntitySet navigationTargetEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
        OfbizCsdlEntitySet navigationCsdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntitySet(OfbizAppEdmProvider.CONTAINER, navigationTargetEntitySet.getName());
        Util.addEntitySetConditionToEntity(delegator, navigationCsdlEntitySet, entityToWrite, userLogin, httpServletRequest);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            DraftReaderAndWriter draftReaderAndWriter = new DraftReaderAndWriter(odataContext, sapContextId, edmNavigationProperty.getType());
            updatedEntity = draftReaderAndWriter.updateEntityData(primaryKey, entityToWrite);
        } else {
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget,
                    "edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite);
            OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
            updatedEntity = writer.updateRelatedEntity(entity, entityToWrite, primaryKey);
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("entity", updatedEntity);
        return result;
    }

    public static Map<String, Object> deleteEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        EdmEntitySet edmEntitySet = (EdmEntitySet) context.get("edmEntitySet");
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) context.get("entity");
        String sapContextId = (String) context.get("sapContextId");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        if (UtilValidate.isNotEmpty(sapContextId)) {
            DraftReaderAndWriter draftReaderAndWriter = new DraftReaderAndWriter(odataContext, sapContextId, edmEntitySet.getEntityType());
            draftReaderAndWriter.deleteEntityData(ofbizEntity.getKeyMap());
        } else {
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet, "entityToWrite", ofbizEntity);
            OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
            writer.deleteEntity(ofbizEntity);
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> deleteRelatedEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        Entity entityToDelete = (Entity) context.get("entityToDelete");
        OdataOfbizEntity entity = (OdataOfbizEntity) context.get("entity");
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
        Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToDelete);
        OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
        writer.deleteRelatedEntity(entity, entityToDelete);
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> updateEntityToDraft(DispatchContext dctx, Map<String, Object> context)  {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) context.get("csdlEntityType");
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        String draftEntityName = csdlEntityType.getDraftEntityName();
        String sapContextId = (String) context.get("sapContextId");
        GenericValue draftGenericValue;
        Map<String, Object> fieldMap = UtilGenerics.checkMap(context.get("fieldMap")) ;
        for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
            //空字符串转换成null
            if ("".equals(entry.getValue())) {
                entry.setValue(null);
            }
            //如果传递过来的时间格式不对,根据字段类型转换格式
            ModelField field = modelEntity.getField(entry.getKey());
            if (UtilValidate.isNotEmpty(field) && "date".equals(field.getType()) && entry.getValue() instanceof GregorianCalendar) {
                GregorianCalendar calendar = (GregorianCalendar) entry.getValue();
                fieldMap.put(entry.getKey(), new Date(calendar.getTime().getTime()));
            }

        }
        try {
            List<String> pkFieldNames = csdlEntityType.getKeyPropertyNames();
            Map<String, Object> keyMap = new HashMap<>();
            for (String pkFieldName : pkFieldNames) {
                keyMap.put(pkFieldName, fieldMap.get(pkFieldName));
            }
            draftGenericValue = findDraftValue(delegator, (String) fieldMap.get("draftUUID"), keyMap, draftEntityName, sapContextId);
            draftGenericValue.putAll(fieldMap);
            draftGenericValue.put("isActiveEntity", "N");
            draftGenericValue.put("hasDraftEntity", "Y");
            delegator.store(draftGenericValue);

            GenericValue draftAdminData = delegator.findOne("DraftAdministrativeData",
                    UtilMisc.toMap("draftUUID", draftGenericValue.get("draftUUID")), false);
            draftAdminData.put("lastChangedByUser", userLogin.get("userLoginId"));
            draftAdminData.put("lastChangeDateTime", UtilDateTime.nowTimestamp());
            draftAdminData.store();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("draftGenericValue", draftGenericValue);
        return result;
    }

    private static GenericValue findDraftValue(Delegator delegator, String draftUUID, Map<String, Object> keyMap,
                                               String draftEntityName, String sapContextId) throws GenericEntityException {
        if (draftUUID != null) {
            return delegator.findOne(draftEntityName, UtilMisc.toMap("draftUUID", draftUUID), false);
        }
        GenericValue draftGenericValue = null;
        GenericValue mainDraftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", sapContextId), false);
        if (!mainDraftAdminData.getString("draftEntityName").equals(draftEntityName)) {
            List<GenericValue> subDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap("parentDraftUUID", sapContextId, "draftEntityName", draftEntityName), null, false);
            List<String> subDraftUUIDs = EntityUtil.getFieldListFromEntityList(subDraftAdminDataList, "draftUUID", false);
            List<GenericValue> draftGenericValues = delegator.findByAnd(draftEntityName, keyMap, null, false);
            for (GenericValue possibleDraftGenericValue : draftGenericValues) {
                if (subDraftUUIDs.contains(possibleDraftGenericValue.getString("draftUUID"))) {
                    draftGenericValue = possibleDraftGenericValue;
                    break;
                }
            }
            if (draftGenericValue == null) {
                draftGenericValue = EntityQuery.use(delegator).from(draftEntityName).where(keyMap).queryFirst();
            }
        } else {
            draftGenericValue = delegator.findOne(draftEntityName, UtilMisc.toMap("draftUUID", sapContextId), false);
        }
        return draftGenericValue;
    }

    public static Map<String, Object> createEntityToDraft(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String sapContextId = (String) context.get("sapContextId");
        if (UtilValidate.isEmpty(sapContextId)) {
            throw new OfbizODataException("sapContextId is required");
        }
        String entityName = (String) context.get("originEntityName");
        String draftEntityName = (String) context.get("draftEntityName");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
        EdmEntityType edmEntityType = (EdmEntityType) context.get("edmEntityType");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        Map<String, Object> fieldMap = (Map<String, Object>) context.get("fieldMap");
        ModelEntity modelEntity = delegator.getModelEntity(entityName);
        ModelEntity draftModelEntity = delegator.getModelEntity(draftEntityName);
        List<String> pkFieldNames = modelEntity.getPkFieldNames();
        CsdlEntityType csdlEntityType = edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        Map<String, Object> draftFields = new HashMap<>();
        for (CsdlPropertyRef csdlPropertyRef : csdlEntityType.getKey()) {
            Object pkFieldValue = fieldMap.get(csdlPropertyRef.getName());
            if (UtilValidate.isEmpty(pkFieldValue) && pkFieldNames.size() == 1) {
                OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(csdlPropertyRef.getName());
                String ofbizFieldName = csdlProperty.getOfbizFieldName();
                ModelField modelField = modelEntity.getField(ofbizFieldName);
                if ("id".equals(modelField.getType())) {
                    pkFieldValue = "ID" + delegator.getNextSeqId(DataModifyActions.NEXT_ID_KEY);
                }
            }
            draftFields.put(csdlPropertyRef.getName(), pkFieldValue);
        }

        Util.makeupFromDate(draftFields, modelEntity);
        List<String> noPkFieldNames = draftModelEntity.getNoPkFieldNames();
        for (String fieldName : noPkFieldNames) {
            if (fieldMap.get(fieldName) != null) {
                draftFields.put(fieldName, fieldMap.get(fieldName));
            }
        }
        GenericValue draftGenericValue;
        try {
            draftFields.put("draftUUID", sapContextId);
            // 先检查内存数据库是否有此条记录
            List<GenericValue> draftGenericValues = delegator.findByAnd(draftEntityName, UtilMisc.toMap("draftUUID", sapContextId), null, false);
            if (UtilValidate.isNotEmpty(draftGenericValues)) { // 已有数据，不能创建
                return ServiceUtil.returnError("Data already exists, can't create a new one");
            }
            draftFields.put("isActiveEntity", "N");
            draftFields.put("hasActiveEntity", "N");
            draftFields.put("hasDraftEntity", "Y");
            //通过接口实例创建
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "locale", locale);
            DraftHandler draftHandler = HandlerFactory.getDraftHandler(edmEntityType, edmProvider, delegator);
            draftGenericValue = draftHandler.createEntity(odataContext, edmEntityType, null, draftFields, null);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        result.put("draftGenericValue", draftGenericValue);
        return result;
    }

    public static Map<String, Object> deleteEntityFromDraft(DispatchContext dctx, Map<String, Object> context)
            throws ODataException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();

        String draftEntityName = (String) context.get("draftEntityName");
        Map<String, Object> keyMap = (Map<String, Object>) context.get("keyMap");
        String sapContextId = (String) context.get("sapContextId");
        GenericValue draftGenericValue;
        try {
            draftGenericValue = findDraftValue(delegator, (String) keyMap.get("draftUUID"), keyMap, draftEntityName, sapContextId);
            draftGenericValue.put("isActiveEntity", "N");
            draftGenericValue.put("hasDraftEntity", "N");
            draftGenericValue.store();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        result.put("draftGenericValue", draftGenericValue);
        return result;
    }

    // 复制数据到draft table
    public static Map<String, Object> copyGenericValueToDraft(DispatchContext dctx, Map<String, Object> context)
            throws ODataException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String draftEntityName = (String) context.get("draftEntityName");
        String draftUUID = (String) context.get("draftUUID");
        HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("request");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
        Map<String, Object> result = ServiceUtil.returnSuccess();

        List<GenericValue> draftGenericValues = new ArrayList<>();
        result.put("draftGenericValues", draftGenericValues);
        ModelEntity modelEntity = delegator.getModelEntity(draftEntityName);
        if (modelEntity == null) {
            return result;
        }
        GenericValue draftAdminData;
        try {
            // 先检查DraftAdministrativeData是否有相关数据
            draftAdminData = delegator.findOne("DraftAdministrativeData",
                    false, UtilMisc.toMap("draftUUID", draftUUID));
            if (draftAdminData == null) {
                return ServiceUtil.returnError("No DraftAdministrativeData data exists");
            }
            String entityType = draftAdminData.getString("entityType");
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(entityType));
            GenericValue originGenericValue = (GenericValue) context.get("originGenericValue");
            OdataOfbizEntity odataOfbizEntity =
                    OdataProcessorHelper.genericValueToEntity(dispatcher,
                            edmProvider, csdlEntityType, originGenericValue, locale);
            List<Entity> entityList = OdataProcessorHelper.appendSemanticFields(httpServletRequest, delegator, dispatcher,
                    edmProvider, null, UtilMisc.toList(odataOfbizEntity), locale, userLogin);
            odataOfbizEntity = (OdataOfbizEntity) entityList.get(0);
            Map<String, Object> entityMap = Util.entityToMap(odataOfbizEntity);
            GenericPK genericPK = originGenericValue.getPrimaryKey();
            Map<String, Object> fieldMap = new HashMap<>(genericPK);
            fieldMap.put("draftUUID", draftUUID);
            List<GenericValue> oldDraftGenericValues = delegator.findByAnd(draftEntityName, fieldMap, null, false);
            if (UtilValidate.isEmpty(oldDraftGenericValues)) { // 内存数据库没有记录，创建
                fieldMap.put("isActiveEntity", "Y");
                fieldMap.put("hasActiveEntity", "Y");
                fieldMap.put("hasDraftEntity", "N");
                fieldMap.putAll(entityMap);
                GenericValue draftGenericValue = delegator.makeValue(draftEntityName, fieldMap);
                draftGenericValue.create();
                draftGenericValues.add(draftGenericValue);
            } else { // 内存数据库有记录，则更新内存数据库的记录
                // 应该有且只有一条记录
                for (GenericValue draftGenericValue : oldDraftGenericValues) {
                    draftGenericValue.putAll(entityMap);
                    draftGenericValue.store();
                    draftGenericValues.add(draftGenericValue);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return result;
    }

    // 复制数据到draft table
    public static Map<String, Object> copyEntityToDraft(DispatchContext dctx, Map<String, Object> context) {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        String draftUUID = (String) context.get("draftUUID");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        GenericValue draftAdminData;
        try {
            // 先检查DraftAdministrativeData是否有相关数据
            draftAdminData = delegator.findOne("DraftAdministrativeData",
                    false, UtilMisc.toMap("draftUUID", draftUUID));
            if (draftAdminData == null) {
                return ServiceUtil.returnError("No DraftAdministrativeData data exists");
            }
            String draftEntityName = draftAdminData.getString("draftEntityName");
            OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) context.get("entity");
            Map<String, Object> entityMap = Util.entityToMap(odataOfbizEntity);
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.put("draftUUID", draftUUID);
            List<GenericValue> oldDraftGenericValues = delegator.findByAnd(draftEntityName, fieldMap, null, false);
            if (UtilValidate.isEmpty(oldDraftGenericValues)) { // 内存数据库没有记录，创建
                fieldMap.put("isActiveEntity", "Y");
                fieldMap.put("hasActiveEntity", "Y");
                fieldMap.put("hasDraftEntity", "N");
                fieldMap.putAll(entityMap);
                GenericValue draftGenericValue = delegator.makeValidValue(draftEntityName, fieldMap);
                draftGenericValue.create();
                result.put("draftGenericValue", draftGenericValue);
            } else { // 内存数据库有记录，则更新内存数据库的记录
                // 应该有且只有一条记录
                for (GenericValue draftGenericValue : oldDraftGenericValues) {
                    draftGenericValue.putAll(entityMap);
                    draftGenericValue.store();
                    result.put("draftGenericValue", draftGenericValue);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }

        return result;
    }

    public static GenericValue createDraftAdminData(Delegator delegator, String draftUUID, String parentDraftUUID,
                                                    OfbizCsdlEntityType ofbizCsdlEntityType,
                                                    Map<String, Object> keyMap, String navigationProperty,
                                                    GenericValue userLogin)
            throws GenericEntityException {
        String originEntityName = ofbizCsdlEntityType.getOfbizEntity();
        String draftEntityName = ofbizCsdlEntityType.getDraftEntityName();
        String entityTypeFqn = ofbizCsdlEntityType.getFullQualifiedNameString();
        return createDraftAdminData(delegator, draftUUID, parentDraftUUID, originEntityName, draftEntityName,
                entityTypeFqn, keyMap, navigationProperty, userLogin);
    }

    public static GenericValue createDraftAdminData(Delegator delegator, String draftUUID, String parentDraftUUID,
                                                    String originEntityName, String draftEntityName, String entityType,
                                                    Map<String, Object> keyMap, String navigationProperty,
                                                    GenericValue userLogin)
            throws GenericEntityException {
        Timestamp currentTime = UtilDateTime.nowTimestamp();
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put("draftUUID", draftUUID);
        fieldMap.put("parentDraftUUID", parentDraftUUID);
        fieldMap.put("creationDateTime", currentTime);
        fieldMap.put("createdByUser", userLogin.get("userLoginId"));
        fieldMap.put("lastChangeDateTime", currentTime);
        fieldMap.put("lastChangedByUser", userLogin.get("userLoginId"));
        fieldMap.put("inProcessByUser", userLogin.get("userLoginId"));
        fieldMap.put("originEntityName", originEntityName);
        fieldMap.put("draftEntityName", draftEntityName);
        fieldMap.put("entityType", entityType);
        fieldMap.put("navigationProperty", navigationProperty);
        fieldMap.put("entityKeyMap", Util.mapToStr(keyMap));
        GenericValue draftAdminData = delegator.makeValue("DraftAdministrativeData", fieldMap);
        draftAdminData.create();
        return draftAdminData;
    }

    public static Object stickySessionNewAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException, GenericServiceException, ODataException {
        runBefore(oDataContext, actionParameters, edmBindingTarget, DraftAction.NEW_BEFORE);
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        HttpServletRequest httpServletRequest = (HttpServletRequest) oDataContext.get("httpServletRequest");
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntityContainer().getEntitySet(edmBindingTarget.getName());
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        Map<String, Object> entitySetConditionMap = Util.parseConditionMap(csdlEntitySet.getConditionStr(), httpServletRequest);
        Map<String, Object> entityTypeConditionMap = Util.parseConditionMap(csdlEntityType.getEntityConditionStr(), httpServletRequest);
        String entityName = csdlEntityType.getOfbizEntity();
        String draftEntityName = csdlEntityType.getDraftEntityName();
        ModelEntity modelEntity = delegator.getModelEntity(entityName);
        ODataResponse oDataResponse = (ODataResponse) oDataContext.get("oDataResponse");
        List<String> pkFieldNames = modelEntity.getPkFieldNames();
        // 传入的keyMap应该是空
        Map<String, Object> internalKeyMap = new HashMap<>();
        for (CsdlPropertyRef csdlPropertyRef : csdlEntityType.getKey()) {
            Object pkFieldValue = actionParameters.get(csdlPropertyRef.getName());
            if (UtilValidate.isEmpty(pkFieldValue) && pkFieldNames.size() == 1) {
                OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(csdlPropertyRef.getName());
                String ofbizFieldName = csdlProperty.getOfbizFieldName();
                ModelField modelField = modelEntity.getField(ofbizFieldName);
                if (modelField.getType().contains("id")) {
                    pkFieldValue = "ID" + delegator.getNextSeqId(DataModifyActions.NEXT_ID_KEY);
                }
            }
            internalKeyMap.put(csdlPropertyRef.getName(), pkFieldValue);
        }
        String sapContextId = (String) oDataContext.get("sapContextId");
        // 对于有draft table的EntityType，如果直接新建，应该建在内存数据库，并且生成sapContextId返回给客户端
        ProcessorServices.createDraftAdminData(delegator, sapContextId, null, csdlEntityType, internalKeyMap, null, userLogin);
        Map<String, Object> fieldMap = Util.retrieveFieldMap(delegator, actionParameters, csdlEntityType.getDraftEntityName());
        if (UtilValidate.isNotEmpty(entitySetConditionMap)) {
            fieldMap.putAll(entitySetConditionMap);
        }
        if (UtilValidate.isNotEmpty(entityTypeConditionMap)) {
            fieldMap.putAll(entityTypeConditionMap);
        }
        //添加AutoValue
        for (Map.Entry<String, Object> entry : csdlEntityType.getAutoValueProperties().entrySet()) {
            fieldMap.put(entry.getKey(), Util.parseVariable(entry.getValue(), httpServletRequest));
        }
        //添加DefaultValue
        Map<String, Object> defaultValues = csdlEntityType.getDefaultValueProperties();
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            fieldMap.put(entry.getKey(), Util.parseVariable(entry.getValue(), httpServletRequest));
        }
        fieldMap.putAll(internalKeyMap);
        //检查主键约束
        if (checkPrimaryKeyConflict(delegator, csdlEntityType, fieldMap)) {
            throw new OfbizODataException("Duplicate data cannot be entered, which violates the primary key unique constraint.");
        }
        Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", entityName,
                "draftEntityName", draftEntityName, "entityType", csdlEntityType.getName(),
                "fieldMap", fieldMap, "sapContextId", sapContextId, "edmProvider", edmProvider,
                "edmEntityType", edmBindingTarget.getEntityType(), "userLogin", userLogin);
        Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
        GenericValue draftGenericValue = (GenericValue) serviceResult.get("draftGenericValue");
        OdataOfbizEntity ofbizEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmBindingTarget, null,
                draftGenericValue, (Locale) oDataContext.get("locale"));
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                null, UtilMisc.toList(ofbizEntity), (Locale) oDataContext.get("locale"), userLogin);
        //create cascade navigation
        createCascade(oDataContext, ofbizEntity, csdlEntityType, sapContextId, actionParameters);
        //后置处理
        runAfter(oDataContext, actionParameters, ofbizEntity, edmBindingTarget, DraftAction.NEW_AFTER);
        return ofbizEntity;
    }

    public static Object stickySessionEditAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        HttpServletRequest httpServletRequest = (HttpServletRequest) oDataContext.get("httpServletRequest");
        //bound entity
        OdataOfbizEntity ofbizEntity = null;
        for (Map.Entry<String, Object> entry : actionParameters.entrySet()) {
            if (entry.getValue() instanceof Entity) {
                ofbizEntity = (OdataOfbizEntity) entry.getValue();
                break;
            }
        }
        if (ofbizEntity == null) {
            throw new OfbizODataException("The entity to edit was not found.", String.valueOf(HttpStatus.SC_NOT_FOUND));
        }
        Map<String, Object> keyMap = new HashMap<>(ofbizEntity.getKeyMap());
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        String draftEntityName = csdlEntityType.getDraftEntityName();
        EdmEntitySet edmEntitySet = (EdmEntitySet) edmBindingTarget;
        EdmEntityType startEdmEntityType = edmEntitySet.getEntityType();
        OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(startEdmEntityType.getFullQualifiedName());
        oDataContext.put("edmEntitySet", edmEntitySet);

        //获取sapContextId
        String sapContextId = (String) oDataContext.get("sapContextId");
        GenericValue draftAdminData = null;
        OdataOfbizEntity draftEntity;
        GenericValue draftGenericValue;
        try {
            if (sapContextId == null) {
                sapContextId = Util.generateDraftUUID();
            }
            // 检查有没有相同对象，相同用户的draft存在
            List<GenericValue> draftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                    UtilMisc.toMap("originEntityName", entityName, "draftEntityName", draftEntityName, "entityKeyMap", Util.mapToStr(keyMap)),
                    null, false);
            draftAdminData = EntityUtil.getFirst(draftAdminDataList);
            if (UtilValidate.isNotEmpty(draftAdminData)) {
                sapContextId = draftAdminData.getString("draftUUID");
            }

            if (draftAdminData == null) { // 如果在DraftAdministrativeData不存在这个sapContextId，就创建一个
                draftAdminData = ProcessorServices.createDraftAdminData(delegator, sapContextId, null,
                        ofbizCsdlEntityType, keyMap, null, userLogin);
                // 创建draft表的数据，包括子对象数据
                oDataContext.put("sapContextId", sapContextId);
                draftEntity = DataModifyActions.copyEntityToDraft(oDataContext, keyMap);
            } else { // 否则就从draft表里找出这条draft数据返回
                draftGenericValue = delegator.findOne(draftEntityName,
                        UtilMisc.toMap("draftUUID", draftAdminData.get("draftUUID")), false);
                draftEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmEntitySet, null,
                        draftGenericValue, (Locale) oDataContext.get("locale"));
                OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                        null, UtilMisc.toList(ofbizEntity), (Locale) oDataContext.get("locale"), userLogin);
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return draftEntity;
    }

    // saveAction will load data from mem database and store into real database
    public static Object stickySessionSaveAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws ODataException {
        Locale locale = (Locale) oDataContext.get("locale");
        String sapContextId = (String) oDataContext.get("sapContextId");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        //参数校验
        verifyProperty(edmProvider, delegator, sapContextId, 0, locale);
        //执行前置处理
        runBefore(oDataContext, actionParameters, edmBindingTarget, DraftAction.SAVE_BEFORE);
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        if (sapContextId == null) {
            throw new OfbizODataException("We need session contextId while calling saveAction!");
        }
        //save the entity created by the foreign key
        saveNewFkEntity(oDataContext, csdlEntityType, sapContextId);
        // save main entity first
        GenericValue mainGenericValue = DataModifyActions.persistentMainEntity(oDataContext, sapContextId);
        // save NavigationProperty，两层子级
        persistentTwoLevelNavEntity(oDataContext, mainGenericValue, csdlEntityType, sapContextId, 1);
        try {
            DataModifyActions.clearEntityDraft(oDataContext, sapContextId);
            delegator.refresh(mainGenericValue);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        OdataOfbizEntity updatedEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmBindingTarget, null,
                mainGenericValue, (Locale) oDataContext.get("locale"));
        OdataProcessorHelper.appendNonEntityFields(null, delegator, dispatcher, edmProvider,
                null, UtilMisc.toList(updatedEntity), locale, userLogin);
        //执行后置处理
        runAfter(oDataContext, actionParameters, updatedEntity, edmBindingTarget, DraftAction.SAVE_AFTER);
        return updatedEntity;
    }

    public static void persistentTwoLevelNavEntity(Map<String, Object> oDataContext, GenericValue mainGenericValue,
                                                   OfbizCsdlEntityType csdlEntityType, String draftUUID, int navLevel) throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        try {
            List<CsdlNavigationProperty> csdlNavigationProperties = csdlEntityType.getNavigationProperties();
            for (CsdlNavigationProperty csdlNavigationProperty : csdlNavigationProperties) {
                OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
                String navDraftEntityName = navCsdlEntityType.getDraftEntityName();
                List<GenericValue> navDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
                        UtilMisc.toMap("parentDraftUUID", draftUUID, "draftEntityName", navDraftEntityName,
                                "navigationProperty", csdlNavigationProperty.getName()), null, false);
                if (navDraftAdminDataList == null) {
                    continue;
                }
                for (GenericValue navDraftAdminData : navDraftAdminDataList) {
                    GenericValue navGenericValue = DataModifyActions.persistentNavEntity(oDataContext, navDraftAdminData, mainGenericValue, csdlEntityType,
                            (OfbizCsdlNavigationProperty) csdlNavigationProperty);
                    if (navLevel < 2 && navGenericValue != null) {
                        persistentTwoLevelNavEntity(oDataContext, navGenericValue, navCsdlEntityType, navDraftAdminData.getString("draftUUID"), 2);
                    }
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(String.valueOf(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode()), e.getMessage());
        }

    }


    /**
     * 在创建主实体之前 创建在真实数据库不存在的并且是通过外键关联的实体
     */
    private static void saveNewFkEntity(Map<String, Object> oDataContext, OfbizCsdlEntityType csdlEntityType, String sapContextId) throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        try {
            for (CsdlNavigationProperty csdlNavigationProperty : csdlEntityType.getNavigationProperties()) {
                OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlNavigationProperty;
                //需要提前创建的实体
                if (!ofbizCsdlNavigationProperty.preCreate()) {
                    continue;
                }
                List<String> relations = ofbizCsdlNavigationProperty.getRelAlias().getRelations();
                GenericValue draftGenericValue = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", sapContextId), true);
                GenericPK relatedDummyPk = draftGenericValue.getRelatedDummyPK(relations.get(0));
                //外键有值但不存在的数据，去创建
                if (!relatedDummyPk.containsValue(null) && delegator.getFromPrimaryKeyCache(relatedDummyPk) == null) {
                    OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
                    String navDraftEntityName = navCsdlEntityType.getDraftEntityName();
                    GenericValue navDraftAdmin = EntityQuery.use(delegator).from("DraftAdministrativeData").where(UtilMisc.toMap("parentDraftUUID", sapContextId,
                            "draftEntityName", navDraftEntityName, "navigationProperty", csdlNavigationProperty.getName())).select("draftUUID").queryFirst();
                    if (UtilValidate.isNotEmpty(navDraftAdmin)) {
                        Map<String, Object> draftPrimaryKey = UtilMisc.toMap("draftUUID", navDraftAdmin.getString("draftUUID"));
                        GenericValue navDraftGenericValue = delegator.findOne(navDraftEntityName, draftPrimaryKey, true);
                        Map<String, Object> serviceParam = Util.propertyToField(navDraftGenericValue, navCsdlEntityType);
                        serviceParam.put("userLogin", userLogin);
                        String serviceName = Util.getEntityActionService(navCsdlEntityType, navCsdlEntityType.getOfbizEntity(), "create", delegator);
                        OdataProcessorHelper.createGenericValue(dispatcher, serviceName, csdlEntityType.getOfbizEntity(), serviceParam);
                        delegator.removeByAnd(navDraftEntityName, draftPrimaryKey);
                        delegator.removeByAnd("DraftAdministrativeData", draftPrimaryKey);
                    }
                }
            }
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(String.valueOf(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode()), e.getMessage());
        }
    }

    private static void createCascade(Map<String, Object> oDataContext,OdataOfbizEntity ofbizEntity, OfbizCsdlEntityType csdlEntityType,
                                      String sapContextId, Map<String, Object> actionParameters) throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        for (CsdlNavigationProperty navigationProperty : csdlEntityType.getNavigationProperties()) {
            OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty = (OfbizCsdlNavigationProperty) navigationProperty;
            OfbizCsdlEntityType navOfbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navigationProperty.getTypeFQN());
            ModelEntity modelEntity = delegator.getModelEntity(navOfbizCsdlEntityType.getOfbizEntity());
            if (ofbizCsdlNavigationProperty.isCascade()) {
                //关联字段
                Map<String, Object> navProperties = new HashMap<>();
                Map<String, Object> relatedConditionMap = Util.getRelatedConditionMap(ofbizCsdlNavigationProperty);
                if (UtilValidate.isNotEmpty(relatedConditionMap)) {
                    navProperties.putAll(relatedConditionMap);
                }
                Map<String, Object> relatedFieldMap = Util.getRelatedFieldMap(delegator, csdlEntityType.getOfbizEntity(), ofbizCsdlNavigationProperty, Util.entityToMap(ofbizEntity), edmProvider);
                if (UtilValidate.isNotEmpty(relatedFieldMap)) {
                    navProperties.putAll(relatedFieldMap);
                }
                //action参数
                for (Map.Entry<String, Object> entry : actionParameters.entrySet()) {
                    if (UtilValidate.isNotEmpty(navOfbizCsdlEntityType.getProperty(entry.getKey()))) {
                        navProperties.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                }
                //default value
                for (Map.Entry<String, Object> entry : navOfbizCsdlEntityType.getDefaultValueProperties().entrySet()) {
                    navProperties.putIfAbsent(entry.getKey(), entry.getValue());
                }
                //补充seqId
                DraftReaderAndWriter.addDraftNextSeqId(delegator, navOfbizCsdlEntityType, navProperties);
                //补充fromDate
                Util.makeupFromDate(navProperties, modelEntity);
                //如果是单主键并且不存在这个值 获取一个自增主键
                if (modelEntity.getPkFieldNames().size() == 1) {
                    CsdlPropertyRef csdlPropertyRef = navOfbizCsdlEntityType.getKey().get(0);
                    Object primaryKeyValue = navProperties.get(csdlPropertyRef.getName());
                    if (UtilValidate.isEmpty(primaryKeyValue)) {
                        String pkValue = "ID" + delegator.getNextSeqId(DataModifyActions.NEXT_ID_KEY);
                        navProperties.put(csdlPropertyRef.getName(), pkValue);
                    }
                }
                Map<String, Object> primaryKey = new HashMap<>();
                //获取主键
                for (CsdlPropertyRef csdlPropertyRef : navOfbizCsdlEntityType.getKey()) {
                    Object key = navProperties.get(csdlPropertyRef.getName());
                    if (UtilValidate.isNotEmpty(key)) {
                        primaryKey.put(csdlPropertyRef.getName(), key);
                    }
                }
                Util.createNavDraftData(oDataContext, sapContextId,  primaryKey,  navigationProperty.getName(), navProperties);
            }
        }
    }

    public static Map<String, Object> createReference(DispatchContext dctx, Map<String, Object> context) throws OfbizODataException {
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) context.get("entity");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
        Map<String, Object> bindPrimaryKey = UtilGenerics.checkMap(context.get("bindPrimaryKey"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        //通过Handler实例处理Reference
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, dctx.getDelegator());
        navigationHandler.bindNavigationLink(odataContext, ofbizEntity, edmEntityType, edmNavigationProperty, bindPrimaryKey);
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> deleteReference(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) context.get("entity");
        EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
        EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
        Map<String, Object> bindPrimaryKey = UtilGenerics.checkMap(context.get("bindPrimaryKey"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        //通过Handler实例处理Reference
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, dctx.getDelegator());
        navigationHandler.unbindNavigationLink(odataContext, ofbizEntity, edmEntityType, edmNavigationProperty, bindPrimaryKey);
        return ServiceUtil.returnSuccess();
    }

    /**
     * 检查主键是否冲突
     */
    private static boolean checkPrimaryKeyConflict(Delegator delegator, OfbizCsdlEntityType csdlEntityType, Map<String, Object> propertyMap) throws OfbizODataException {
        Map<String, Object> primaryKey = new HashMap<>();
        for (String keyPropertyName : csdlEntityType.getKeyPropertyNames()) {
            Object pkValue = propertyMap.get(keyPropertyName);
            if (UtilValidate.isEmpty(pkValue)) {
                return false;
            }
            OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(keyPropertyName);
            primaryKey.put(csdlProperty.getOfbizFieldName(), pkValue);
        }
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        if (primaryKey.size() != modelEntity.getPkFieldNames().size()) {
            return false;
        }
        try {
            List<GenericValue> findResult = delegator.findByAnd(modelEntity.getEntityName(), primaryKey, null, true);
            return UtilValidate.isNotEmpty(findResult);
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    private static void verifyProperty(OfbizAppEdmProvider edmProvider, Delegator delegator, String sapContextId, int level, Locale locale) throws OfbizODataException {
        try {
            GenericValue draftAdmin = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", sapContextId), false);
            if (UtilValidate.isEmpty(draftAdmin)) {
                String message = UtilProperties.getMessage(resource, "verifyMsg.stickySession.draftMissing", locale);
                throw new OfbizODataException(message);
            }
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(draftAdmin.getString("entityType")));
            GenericValue draftData = delegator.findOne(draftAdmin.getString("draftEntityName"), UtilMisc.toMap("draftUUID", sapContextId), false);
            if (UtilValidate.isEmpty(draftData) || (!draftData.getBoolean("isActiveEntity") && !draftData.getBoolean("hasDraftEntity"))) {
                //不存在或者标记为已删除的draft
                return;
            }
            for (CsdlProperty property : csdlEntityType.getProperties()) {
                OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) property;
                String value = draftData.getString(csdlProperty.getName());
                if (csdlProperty.isRequired() && UtilValidate.isEmpty(value)) {
                    String message = UtilProperties.getMessage(resource, "verifyMsg.property.required",
                            UtilMisc.toMap("property", csdlProperty.getLabel()), locale);
                    throw new OfbizODataException(message);
                }
                if (csdlProperty.isOnly() && UtilValidate.isNotEmpty(value)) {
                    String entityName = draftAdmin.getString("originEntityName");
                    ModelEntity modelEntity = delegator.getModelEntity(entityName);
                    Map<String, Object> draftKey = Util.StringToKeyMap(draftAdmin.getString("entityKeyMap"), ",", true, null, modelEntity, csdlEntityType);
                    List<GenericValue> genericValues = EntityQuery.use(delegator).from(draftAdmin.getString("originEntityName")).where(csdlProperty.getOfbizFieldName(), value).queryList();
                    if (UtilValidate.isEmpty(genericValues)) {
                        continue;
                    }
                    if (UtilValidate.isEmpty(draftKey)) {
                        if (genericValues.size() > 0) {
                            String message = UtilProperties.getMessage(resource, "verifyMsg.property.only",
                                    UtilMisc.toMap("property", csdlProperty.getLabel()), locale);
                            throw new OfbizODataException(message);
                        } else {
                            continue;
                        }
                    } else if (genericValues.size() > 1){
                        String message = UtilProperties.getMessage(resource, "verifyMsg.property.only",
                                UtilMisc.toMap("property", csdlProperty.getLabel()), locale);
                        throw new OfbizODataException(message);
                    }
                    GenericValue firstGV = EntityUtil.getFirst(genericValues);
                    if (UtilValidate.isNotEmpty(firstGV)) {
                        //判断跟当前是不是同一条数据
                        boolean containsAll = draftKey.entrySet().stream()
                                .allMatch(entry -> firstGV.containsKey(entry.getKey()) && firstGV.get(entry.getKey()).equals(entry.getValue()));
                        if (!containsAll) {
                            String message = UtilProperties.getMessage(resource, "verifyMsg.property.only",
                                    UtilMisc.toMap("property", csdlProperty.getLabel()), locale);
                            throw new OfbizODataException(message);
                        }
                    }

                }
            }
            if (level <= 2) {
                //check navigation
                List<GenericValue> navDraftAdminList = EntityQuery.use(delegator).from("DraftAdministrativeData").where("parentDraftUUID", sapContextId).queryList();
                for (GenericValue navDraft : navDraftAdminList) {
                    verifyProperty(edmProvider, delegator, navDraft.getString("draftUUID"), ++ level, locale);
                }
            }
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }

    }

    /**
     * 执行前置处理
     */
    private static void runBefore(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget, DraftAction draftAction) throws OfbizODataException {
        DraftEventContext eventContext = getEventContext(oDataContext, actionParameters, null, edmBindingTarget);
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        String edmEntityTypeName = edmBindingTarget.getEntityType().getName();
        Set<Class<?>> classesWithAnnotation = Util.getClassesWithAnnotation(PACKAGE_NAME, EdmService.class);
        try {
            for (Class<?> clazz : classesWithAnnotation) {
                EdmService annotation = clazz.getAnnotation(EdmService.class);
                String annotationApp = annotation.edmApp();
                if (annotationApp.equals(edmProvider.getWebapp())) {
                    for (Method method : clazz.getMethods()) {
                        EdmEntity edmEntityAnnot = method.getAnnotation(EdmEntity.class);
                        if (UtilValidate.isEmpty(edmEntityAnnot)) {
                            continue;
                        }
                        DraftAction action = edmEntityAnnot.action();
                        List<String> entityTypes = Arrays.asList(edmEntityAnnot.entityTypes());
                        if (entityTypes.contains(edmEntityTypeName) && action.equals(draftAction)) {
                            Object obj = clazz.getDeclaredConstructor().newInstance();
                            method.invoke(obj, eventContext);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e.getCause() instanceof OfbizODataException) {
                throw (OfbizODataException) e.getCause();
            }
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 执行后置处理
     */
    private static void runAfter(Map<String, Object> oDataContext, Map<String, Object> actionParameters, OdataOfbizEntity ofbizEntity, EdmBindingTarget edmBindingTarget,
                                 DraftAction draftAction) throws OfbizODataException {
        DraftEventContext eventContext = getEventContext(oDataContext, actionParameters, ofbizEntity, edmBindingTarget);
        String edmEntityTypeName = edmBindingTarget.getEntityType().getName();
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        Set<Class<?>> classesWithAnnotation = Util.getClassesWithAnnotation(PACKAGE_NAME, EdmService.class);
        try {
            for (Class<?> clazz : classesWithAnnotation) {
                EdmService annotation = clazz.getAnnotation(EdmService.class);
                String annotationApp = annotation.edmApp();
                if (annotationApp.equals(edmProvider.getWebapp())) {
                    for (Method method : clazz.getMethods()) {
                        EdmEntity edmEntityAnnot = method.getAnnotation(EdmEntity.class);
                        if (UtilValidate.isEmpty(edmEntityAnnot)) {
                            continue;
                        }
                        DraftAction action = edmEntityAnnot.action();
                        List<String> entityTypes = Arrays.asList(edmEntityAnnot.entityTypes());
                        if (entityTypes.contains(edmEntityTypeName) && action.equals(draftAction)) {
                            Object obj = clazz.getDeclaredConstructor().newInstance();
                            method.invoke(obj, eventContext);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e.getCause() instanceof OfbizODataException) {
                throw (OfbizODataException) e.getCause();
            }
            throw new OfbizODataException(e.getMessage());
        }
    }

    private static DraftEventContext getEventContext(Map<String, Object> oDataContext, Map<String, Object> actionParameters, OdataOfbizEntity ofbizEntity, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        return new DraftEventContext(delegator, dispatcher, userLogin, oDataContext, actionParameters, edmBindingTarget, ofbizEntity);
    }

    public static Map<String, Object> clearDraft(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        Delegator delegator = dctx.getDelegator();
        List<GenericValue> draftAdminList = EntityQuery.use(delegator).from("DraftAdministrativeData").select("draftUUID", "draftEntityName").queryList();
        for (GenericValue draftAdmin : draftAdminList) {
            ModelEntity modelEntity = delegator.getModelReader().getEntityCache().get(draftAdmin.getString("draftEntityName"));
            if (UtilValidate.isEmpty(modelEntity)) {
                draftAdmin.remove();
                continue;
            }
            delegator.removeByAnd(draftAdmin.getString("draftEntityName"), UtilMisc.toMap("draftUUID", draftAdmin.getString("draftUUID")));
        }
        int count = delegator.removeAll("DraftAdministrativeData");
        Debug.logInfo("Cleaned up: " + count, module);
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> deleteDraftTable(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException {
        Delegator delegator = dctx.getDelegator();
        List<String> entityNames = UtilGenerics.checkList(context.get("entityNames"));
        //只能删除在memory这个组的表
        GenericHelperInfo helperInfo = delegator.getGroupHelperInfo("org.apache.ofbiz.memory");
        DatabaseUtil databaseUtil = new DatabaseUtil(helperInfo);
        for (String entityName : entityNames) {
            ModelEntity modelEntity = delegator.getModelEntity(entityName);
            //删除数据库表
            databaseUtil.deleteTable(modelEntity, null);
            //删除ofbiz实体缓存
            Map<String, ModelEntity> entityCache = delegator.getModelReader().getEntityCache();
            entityCache.remove(modelEntity.getEntityName(), modelEntity);
            //删除ofbiz实体组缓存
            Map<String, String> groupCache = delegator.getModelGroupReader().getGroupCache(delegator.getDelegatorBaseName());
            groupCache.remove(modelEntity.getEntityName());
        }
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> writeOfbizEntity(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GenericServiceException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, ModelEntity> entityCache = delegator.getModelReader().getEntityCache();
        for (ModelEntity modelEntity : entityCache.values()) {
            String entityName = modelEntity.getEntityName();
            createDBEntity(delegator, dispatcher, modelEntity, null, null);
            ModelEntity typeModel = delegator.getModelReader().getModelEntityNoCheck(entityName + "Type");
            if (UtilValidate.isNotEmpty(typeModel)) {
                List<GenericValue> entityTypeList = delegator.findAll(typeModel.getEntityName(), false);
                String typeIdField = typeModel.getFirstPkFieldName();
                for (GenericValue entityType : entityTypeList) {
                    createDBEntity(delegator, dispatcher, modelEntity, typeIdField, entityType.getString(typeIdField));
                }
            }
        }
        return ServiceUtil.returnSuccess();
    }

    private static void createDBEntity(Delegator delegator, LocalDispatcher dispatcher, ModelEntity modelEntity, String typeId, String typeValue) throws GenericEntityException, GenericServiceException {
        String entityName = modelEntity.getEntityName();
        //create Entity
        GenericValue currentType = EntityQuery.use(delegator).from("DBEntity")
                            .where("dbEntityName", entityName, "dbEntityTypeId", typeValue).queryFirst();
        String dbEntityId;
        if (UtilValidate.isEmpty(currentType)) {
            dbEntityId = "DB" + delegator.getNextSeqId("DBEntity");
            dispatcher.runSync("banfftech.createDBEntity", UtilMisc.toMap("dbEntityId", dbEntityId, "dbEntityName", entityName,
                    "dbEntityTypeField", typeId, "dbEntityTypeId", typeValue), 10000, true);
        } else {
            dbEntityId = currentType.getString("dbEntityId");
        }
        //create Field
        Iterator<ModelField> fieldsIterator = modelEntity.getFieldsIterator();
        List<String> pkFieldNames = modelEntity.getPkFieldNames();
        while (fieldsIterator.hasNext()) {
            ModelField field = fieldsIterator.next();
            GenericValue currentField = EntityQuery.use(delegator).from("DBField")
                    .where("dbEntityId", dbEntityId, "dbFieldName", field.getName()).queryFirst();
            if (UtilValidate.isEmpty(currentField)) {
                String dbFieldId = "DB" + delegator.getNextSeqId("DBField");
                dispatcher.runSync("banfftech.createDBField", UtilMisc.toMap("dbFieldId", dbFieldId, "dbEntityId", dbEntityId,
                        "dbFieldName", field.getName(), "isPrimaryKey", pkFieldNames.contains(field.getName()) ? "Y" : "N"), 10000, true);
            }
        }
        //create Relation
        Iterator<ModelRelation> relationsIterator = modelEntity.getRelationsIterator();
        while (relationsIterator.hasNext()) {
            ModelRelation relation = relationsIterator.next();
            String title = relation.getTitle();
            String relEntityName = relation.getRelEntityName();
            String combinedName = relation.getCombinedName();
            String type = relation.getType();
            GenericValue currentRelation = EntityQuery.use(delegator).from("DBRelation")
                    .where("dbEntityId", dbEntityId, "dbRelationName", combinedName).queryFirst();
            if (UtilValidate.isEmpty(currentRelation)) {
                String dbRelationId = "DB" + delegator.getNextSeqId("DBRelation");
                dispatcher.runSync("banfftech.createDBRelation", UtilMisc.toMap("dbRelationId", dbRelationId, "dbEntityId", dbEntityId,
                        "dbRelationTitle", title, "dbRelationEntity", relEntityName, "dbRelationName", combinedName, "dbRelationType", type), 10000, true);
            }
        }
    }
}
