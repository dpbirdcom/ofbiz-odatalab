package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import com.dpbird.odata.services.ProcessorServices;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

// EditAction，会把要编辑的主对象copy到内存数据库，其关联对象也copy到内存数据库
// DiscardAction，会把编辑的主对象从内存数据库删除，其关联对象没有管，可能删了，也可能没删，因为有点复杂，暂缓考虑
//
public class DataModifyActions {
    public static final String module = DataModifyActions.class.getName();
    public static final String NEW_ACTION = "NEW_ACTION";
    public static final String EDIT_ACTION = "EDIT_ACTION";
    public static final String SAVE_ACTION = "SAVE_ACTION";
    public static final String UPDATE_ENTITY = "UPDATE_ENTITY";
    public static final String DELETE_ENTITY = "DELETE_ENTITY";
    public static final String CREATE_ENTITY = "CREATE_ENTITY";
    public static final String CREATE_RELATED_ENTITY = "CREATE_RELATED_ENTITY";
    public static final String DISCARD_ACTION = "DISCARD_ACTION";
    public static final String READ_ENTITY = "READ_ENTITY";

    public static String checkSapContextId(Delegator delegator, ODataRequest oDataRequest, OfbizCsdlEntityType csdlEntityType) {
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        if (UtilValidate.isEmpty(sapContextId)) {
            return null;
        }
        GenericValue draftAdminData;
        try {
            draftAdminData = delegator.findOne("DraftAdministrativeData", true,
                    UtilMisc.toMap("draftUUID", sapContextId));
            if (draftAdminData == null) {
                return null;
            }
            if (csdlEntityType != null) { // 更严格的检查，不仅确认DraftAdministrativeData是否有这个draftUUID，并且检查entityName和draftEntityName是否对应上
                String entityName = csdlEntityType.getOfbizEntity();
                String draftEntityName = csdlEntityType.getDraftEntityName();
                if (UtilValidate.isNotEmpty(sapContextId)) { // 如果参数带sapContextId，则要去数据库查一下有没有这个draft
                    if (!draftAdminData.getString("originEntityName").equals(entityName)
                            || !draftAdminData.getString("draftEntityName").equals(draftEntityName)) {
                        return null;
                    }
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return null;
        }
        return sapContextId;
    }

    public static GenericValue persistentMainEntity(Map<String, Object> oDataContext, String draftUUID)
            throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        HttpServletRequest httpServletRequest = (HttpServletRequest) oDataContext.get("httpServletRequest");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        Locale locale = (Locale) oDataContext.get("locale");
        try {
            GenericValue draftAdminData = delegator.findOne("DraftAdministrativeData",
                    UtilMisc.toMap("draftUUID", draftUUID), true);
            String entityName = draftAdminData.getString("originEntityName");
            String draftEntityName = draftAdminData.getString("draftEntityName");
            String entityType = draftAdminData.getString("entityType");
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(entityType));
            String entityTypeHandler = csdlEntityType.getHandlerClass();
            GenericValue draftGenericValue = delegator.findOne(draftEntityName,
                    UtilMisc.toMap("draftUUID", draftUUID), true);
            boolean isDelete = false;
            boolean isActiveEntity = draftGenericValue.getBoolean("isActiveEntity");
            boolean hasActiveEntity = draftGenericValue.getBoolean("hasActiveEntity");
            boolean hasDraftEntity = draftGenericValue.getBoolean("hasDraftEntity");
            if (!isActiveEntity && !hasDraftEntity) {
                isDelete = true;
            }
            String entityKeyMapStr = draftAdminData.getString("entityKeyMap");
            ModelEntity modelEntity = delegator.getModelEntity(entityName);
            Map<String, Object> keyMap = Util.StringToKeyMap(entityKeyMapStr, ",", true, null, modelEntity, csdlEntityType);
            // mainEntity应该没有fromDate
//            keyMap = Util.makeupFromDate(keyMap, modelEntity);
            GenericValue genericValue = null;
            if (UtilValidate.isNotEmpty(keyMap)) {
                genericValue = delegator.findOne(entityName, keyMap, true);
            }
            if (isDelete) { // 好像不太可能在MainEntity发生删除
                if (genericValue == null) {
                    return null;
                } else {
                    deleteEntityWithService(dispatcher, csdlEntityType, entityName, keyMap, userLogin);
                }
            } else {
//                List<String> noPkFieldNames = modelEntity.getNoPkFieldNames();
//                Map<String, Object> noPkFieldMap = new HashMap<>();
//                Map<String, Object> draftFields = draftGenericValue.getAllFields();
//                Set<String> draftFieldNames = draftFields.keySet();
//                for (String noPkFieldName : noPkFieldNames) {
//                    if (draftFieldNames.contains(noPkFieldName)) {
//                        Object noPkFieldValue = draftFields.get(noPkFieldName);
//                        noPkFieldMap.put(noPkFieldName, noPkFieldValue);
//                    } else {
//                        noPkFieldMap.put(noPkFieldName, null);
//                    }
//                }
                Entity draftEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, draftGenericValue, locale);
                if (genericValue == null) {
                    keyMap = createMainEntityFromDraft(dispatcher, delegator, httpServletRequest, csdlEntityType, edmProvider, draftGenericValue, userLogin, locale, draftEntity);
//                    keyMap = createEntityWithService(dispatcher, delegator, entityName, draftGenericValue, userLogin);
                } else if (draftChanged(modelEntity, genericValue, draftGenericValue)) {
                    updateEntityFromDraft(dispatcher, delegator, edmProvider, csdlEntityType, keyMap, draftGenericValue, userLogin, locale);
                }
                // 刷新一下genericValue
                genericValue = delegator.findOne(entityName, keyMap, false);
                // 如果有attribute，也要更新一下
                if (UtilValidate.isNotEmpty(csdlEntityType.getAttrEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrNumericEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrDateEntityName())) {
                    OdataProcessorHelper.updateAttrGenericValue(csdlEntityType, draftGenericValue, userLogin, keyMap, dispatcher, delegator);
                }
            }
            return genericValue;
        } catch (GenericEntityException | GenericServiceException | ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    public static GenericValue persistentNavEntity(Map<String, Object> oDataContext, GenericValue navDraftAdminData,
                                                   GenericValue mainGenericValue,
                                                   OfbizCsdlEntityType mainCsdlEntityType,
                                                   OfbizCsdlNavigationProperty csdlNavigationProperty)
            throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        Locale locale = (Locale) oDataContext.get("locale");
        String draftUUID = navDraftAdminData.getString("draftUUID");
        Map<String, Object> keyMap = new HashMap<>(mainGenericValue.getPrimaryKey());
        try {
            GenericValue draftAdminData = delegator.findOne("DraftAdministrativeData",
                    UtilMisc.toMap("draftUUID", draftUUID), true);
            String entityName = draftAdminData.getString("originEntityName");
            String draftEntityName = draftAdminData.getString("draftEntityName");
            String entityType = draftAdminData.getString("entityType");
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(entityType));
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());

            GenericValue draftGenericValue = delegator.findOne(draftEntityName,
                    UtilMisc.toMap("draftUUID", draftUUID), false);
            boolean isDelete = false;
            boolean isActiveEntity = draftGenericValue.getBoolean("isActiveEntity");
            boolean hasActiveEntity = draftGenericValue.getBoolean("hasActiveEntity");
            boolean hasDraftEntity = draftGenericValue.getBoolean("hasDraftEntity");
            if (!isActiveEntity && !hasDraftEntity) {
                isDelete = true;
            }
            String entityKeyMapStr = draftAdminData.getString("entityKeyMap");
            ModelEntity modelEntity = delegator.getModelEntity(entityName);
            // 对于新建的DraftEntity，navKeyMap是null
            Map<String, Object> navKeyMap = Util.StringToKeyMap(entityKeyMapStr, ",", true, null, modelEntity, navCsdlEntityType);
            navKeyMap = Util.makeupFromDate(navKeyMap, modelEntity);
            GenericValue genericValue = null;
            if (UtilValidate.isNotEmpty(navKeyMap)) {
                genericValue = delegator.findOne(entityName, navKeyMap, true);
            }
            if (isDelete) {
                if (genericValue == null) {
                    return null;
                } else {
//                    deleteEntityWithService(dispatcher, entityName, navKeyMap, userLogin);
                    deleteRelatedEntity(dispatcher, delegator, edmProvider, keyMap, mainCsdlEntityType,
                            csdlNavigationProperty.getName(), navKeyMap, userLogin, locale);
                }
            } else {
                List<String> noPkFieldNames = modelEntity.getNoPkFieldNames();
                Map<String, Object> noPkFieldMap = new HashMap<>();
                Map<String, Object> draftFields = draftGenericValue.getAllFields();
                Set<String> draftFieldNames = draftFields.keySet();
                for (String noPkFieldName : noPkFieldNames) {
                    if (draftFieldNames.contains(noPkFieldName)) {
                        Object noPkFieldValue = draftFields.get(noPkFieldName);
                        noPkFieldMap.put(noPkFieldName, noPkFieldValue);
                    } else {
                        noPkFieldMap.put(noPkFieldName, null);
                    }
                }
                Entity draftEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, draftGenericValue, locale);
                OdataOfbizEntity entityCreated = null;
                if (genericValue == null) {
                    entityCreated = createRelatedEntityFromDraft(dispatcher, delegator, edmProvider, keyMap, mainCsdlEntityType,
                            csdlNavigationProperty.getName(), draftEntity, userLogin, locale);
                } else if (!draftHasSamePk(draftGenericValue, navKeyMap, navCsdlEntityType)) {
                    deleteRelatedEntity(dispatcher, delegator, edmProvider, keyMap, mainCsdlEntityType,
                            csdlNavigationProperty.getName(), navKeyMap, userLogin, locale);
                    entityCreated = createRelatedEntityFromDraft(dispatcher, delegator, edmProvider, keyMap, mainCsdlEntityType,
                            csdlNavigationProperty.getName(), draftEntity, userLogin, locale);
                } else if (draftChanged(modelEntity, genericValue, draftGenericValue)) {
                    updateEntityFromDraft(dispatcher, delegator, edmProvider, csdlEntityType, navKeyMap, draftGenericValue, userLogin, locale);
                }
                // 刷新一下genericValue
                if (entityCreated != null) {
                    navKeyMap = entityCreated.getKeyMap();
                }
                genericValue = delegator.findOne(entityName, navKeyMap, true);
                // 如果有attribute，也要更新一下
                if (UtilValidate.isNotEmpty(csdlEntityType.getAttrEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrNumericEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrDateEntityName())) {
                    OdataProcessorHelper.updateAttrGenericValue(csdlEntityType, draftGenericValue, userLogin, navKeyMap, dispatcher, delegator);
                }
            }
            return genericValue;
        } catch (GenericEntityException | GenericServiceException | ODataException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    private static void deleteRelatedEntity(LocalDispatcher dispatcher, Delegator delegator,
                                            OfbizAppEdmProvider edmProvider, Map<String, Object> keyMap,
                                            OfbizCsdlEntityType mainCsdlEntityType,
                                            String navigationPropertyName,
                                            Map<String, Object> navKeyMap,
                                            GenericValue userLogin, Locale locale) throws OfbizODataException {
        OdataWriterHelper.deleteEntitySetRelatedEntityData(delegator, dispatcher, null, edmProvider,
                mainCsdlEntityType, navigationPropertyName, keyMap, navKeyMap, userLogin, locale);
    }

    private static boolean draftChanged(ModelEntity modelEntity, GenericValue genericValue, GenericValue draftGenericValue) {
        List<String> fieldNames = modelEntity.getAllFieldNames();
        List<String> deftFieldNames = draftGenericValue.getModelEntity().getNoPkFieldNames();
        List<String> autoFieldNames = modelEntity.getAutomaticFieldNames();
        autoFieldNames.addAll(Arrays.asList("isActiveEntity", "hasActiveEntity", "hasDraftEntity", "draftUUID", "id"));
        for (String fieldName : deftFieldNames) {
            if (autoFieldNames.contains(fieldName)) {
                continue;
            }
            //如果内存数据库只修改了语义化字段，真实数据库是不知道的
            //现在如果真实数据库中并没有这个字段就认为改了
            if (!fieldNames.contains(fieldName)) {
                return true;
            }
            Object value = genericValue.get(fieldName);
            Object draftValue = draftGenericValue.get(fieldName);
            if (value == null) {
                if (draftValue != null) {
                    return true;
                }
            } else if (!value.equals(draftValue)) {
                return true;
            }
        }
        return false;
    }

    private static void updateEntityFromDraft(LocalDispatcher dispatcher, Delegator delegator,
                                              OfbizAppEdmProvider edmProvider,
                                              OfbizCsdlEntityType csdlEntityType,
                                              Map<String, Object> keyMap,
                                              GenericValue draftGenericValue,
                                              GenericValue userLogin, Locale locale) throws ODataException {
        OdataOfbizEntity entityToWrite = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, draftGenericValue, locale);
        OdataWriterHelper.updateEntityData(delegator, dispatcher, null, edmProvider,
                csdlEntityType, keyMap, entityToWrite, userLogin, locale);

    }

    private static boolean draftHasSamePk(GenericValue draftGenericValue, Map<String, Object> keyMap, OfbizCsdlEntityType csdlEntityType) throws OfbizODataException {
        Map<String, Object> primaryKey = Util.fieldToProperty(keyMap, csdlEntityType);
        for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
            if (!entry.getValue().equals(draftGenericValue.get(entry.getKey()))) {
                return false;
            }
        }

//        Set<Map.Entry<String, Object>> entrySet = keyMap.entrySet();
//        Iterator it = entrySet.iterator();
//        while (it.hasNext()) {
//            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) it.next();
//            if (!entry.getValue().equals(draftGenericValue.get(entry.getKey()))) {
//                return false;
//            }
//        }
        return true;
    }

    private static void deleteEntityWithService(LocalDispatcher dispatcher, OfbizCsdlEntityType csdlEntityType, String entityName,
                                                Map<String, Object> keyMap, GenericValue userLogin)
            throws GenericServiceException, OfbizODataException {
        String serviceName = Util.getEntityActionService(csdlEntityType, entityName, "delete", dispatcher.getDelegator());
        ModelService modelService = dispatcher.getDispatchContext().getModelService(serviceName);
        Map<String, Object> serviceParams = Util.prepareServiceParameters(modelService, keyMap);
        serviceParams.put("userLogin", userLogin);
        dispatcher.runSync(serviceName, serviceParams);
    }

    private static Map<String, Object> createMainEntityFromDraft(LocalDispatcher dispatcher, Delegator delegator, HttpServletRequest httpServletRequest,
                                                                 OfbizCsdlEntityType csdlEntityType, OfbizAppEdmProvider edmProvider,
                                                                 GenericValue draftGenericValue, GenericValue userLogin, Locale locale, Entity entity)
            throws GenericServiceException, ODataException, GenericEntityException {
        Map<String, Object> pkMap = null;
        if (csdlEntityType.getHandlerClass() != null) {
            try {
                pkMap = createEntityWithHandler(dispatcher, delegator, csdlEntityType, draftGenericValue, userLogin, locale, entity);
            } catch (Exception e) {
                Debug.logInfo(e.getMessage(), module);
            }
        }
        if (UtilValidate.isEmpty(pkMap)) {
            pkMap = createEntityWithService(dispatcher, delegator, csdlEntityType, draftGenericValue, userLogin, httpServletRequest);
            //创建DerivedEntity
            if (csdlEntityType.isHasDerivedEntity()) {
                OfbizCsdlEntityType derivedType = OdataProcessorHelper.getDerivedType(edmProvider, delegator, draftGenericValue, csdlEntityType);
                if (UtilValidate.isNotEmpty(derivedType)) {
                    createEntityWithService(dispatcher, delegator, derivedType, draftGenericValue, userLogin, httpServletRequest);
                }
            }
            try {
                //创建relAlias字段
                GenericValue createdGenericValue = delegator.findOne(csdlEntityType.getOfbizEntity(), pkMap, false);
                OdataOfbizEntity entityCreated = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, createdGenericValue, locale);
                if (entityCreated != null) {
                    OdataProcessorHelper.createSemanticFields(httpServletRequest, delegator, dispatcher, edmProvider,
                            entity, entityCreated, locale, userLogin);
                }
            } catch (GenericEntityException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
        }
        return pkMap;
    }

    private static OdataOfbizEntity createRelatedEntityFromDraft(LocalDispatcher dispatcher, Delegator delegator,
                                                                 OfbizAppEdmProvider edmProvider,
                                                                 Map<String, Object> keyMap,
                                                                 OfbizCsdlEntityType csdlEntityType,
                                                                 String navigationPropertyName,
                                                                 Entity entityToWrite, GenericValue userLogin, Locale locale)
            throws GenericServiceException, ODataException {
        return OdataWriterHelper.createEntitySetRelatedEntityData(delegator, dispatcher, null, edmProvider,
                csdlEntityType, keyMap, navigationPropertyName, entityToWrite, null, userLogin, locale);
    }

    private static Map<String, Object> createEntityWithHandler(LocalDispatcher dispatcher, Delegator delegator,
                                                               OfbizCsdlEntityType csdlEntityType,
                                                               GenericValue draftGenericValue,
                                                               GenericValue userLogin, Locale locale, Entity entity) throws ODataException, GenericServiceException {

        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, null);
        GenericValue genericValue = groovyHelper.createGenericValue(csdlEntityType.getHandlerClass(), entity);
        return genericValue.getPrimaryKey();
    }

    private static Map<String, Object> createEntityWithService(LocalDispatcher dispatcher, Delegator delegator, OfbizCsdlEntityType csdlEntityType,
                                                               GenericValue genericValue, GenericValue userLogin, HttpServletRequest request)
            throws GenericServiceException, OfbizODataException {
        String entityName = csdlEntityType.getOfbizEntity();
        String serviceName;
        Map<String, Object> serviceParams;
        try {
            Map<String, Object> allFields = new HashMap<>(Util.propertyToField(genericValue.getAllFields(), csdlEntityType));
            //添加EntityType condition字段
            Map<String, Object> entityTypeConditionMap = Util.parseConditionMap(csdlEntityType.getEntityConditionStr(), request);
            if (UtilValidate.isNotEmpty(entityTypeConditionMap)) {
                allFields.putAll(entityTypeConditionMap);
            }
            //添加缺省字段
            Map<String, Object> defaultFields = Util.propertyToField(csdlEntityType.getDefaultValueProperties(), csdlEntityType);
            for (Map.Entry<String, Object> entry : defaultFields.entrySet()) {
                allFields.putIfAbsent(entry.getKey(), entry.getValue());
            }
            serviceName = Util.getEntityActionService(csdlEntityType, entityName, "create", delegator);
            ModelService modelService = dispatcher.getDispatchContext().getModelService(serviceName);
            serviceParams = Util.prepareServiceParameters(modelService, allFields);
        } catch (OfbizODataException e) {
            if (!(delegator.getModelEntity(entityName) instanceof ModelViewEntity)) {
                throw e;
            } else {
                serviceName = "dpbird.saveViewEntityData";
                serviceParams = UtilMisc.toMap("viewEntityName", entityName, "fieldMap", genericValue.getAllFields(), "userLogin", userLogin);
            }
        }

        if (serviceParams.containsKey("fromDate")) {
            serviceParams.put("fromDate", UtilDateTime.nowTimestamp());
        }
        serviceParams.put("userLogin", userLogin);
        Map<String, Object> result = dispatcher.runSync(serviceName, serviceParams);
        if (result.containsKey("pkMap")) {
            return (Map<String, Object>) result.get("pkMap");
        }
        // 光运行了创建entity的service，我们都还不知道是哪个具体的数据被创建了，所以需要获取新创建的entity的pk，然后从数据库获取这个新创建的GenericValue
        return Util.retrievePkMap(delegator, serviceParams, result, entityName);
    }

    public static void discardAction(Map<String, Object> editContext) throws OfbizODataException {
        ODataRequest oDataRequest = (ODataRequest) editContext.get("oDataRequest");
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        if (UtilValidate.isEmpty(sapContextId)) {
            throw new OfbizODataException("We need session contextId while calling discardAction!");
        }
        try {
            clearEntityDraft(editContext, sapContextId);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    public static String generateSessionContext(ODataResponse oDataResponse) {
        String newContextId = Util.generateDraftUUID();
        setResponseSessionContext(oDataResponse, newContextId);
        return newContextId;
    }

    public static void setResponseSessionContext(ODataResponse oDataResponse, String sapContextId) {
        oDataResponse.setHeader("SAP-ContextId", sapContextId);
    }

    public static OdataOfbizEntity copyEntityToDraft(Map<String, Object> odataContext,
                                                     Map<String, Object> keyMap)
            throws OfbizODataException {
        EdmEntitySet edmEntitySet = (EdmEntitySet) odataContext.get("edmEntitySet");
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityName = ofbizCsdlEntityType.getOfbizEntity();
        String draftEntityName = ofbizCsdlEntityType.getDraftEntityName();
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        Delegator delegator = (Delegator) odataContext.get("delegator");
        Locale locale = (Locale) odataContext.get("locale");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        String sapContextId = (String) odataContext.get("sapContextId");
        List<GenericValue> draftGenericValues = null;
        GenericValue draftGenericValue = null;
        // TODO: 所有copy到draft的操作，数据源应该是从OfbizOdataReader读出来的Entity，而不是ofbiz的genericValue
        try {
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet);
            Map<String, Object> readerContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "locale", locale, "httpServletRequest", httpServletRequest);
            OdataReader reader = new OdataReader(readerContext, null, edmParams);
            OdataOfbizEntity entity = (OdataOfbizEntity) reader.findOne(keyMap, null);
            Map<String, Object> svcResult = dispatcher.runSync("dpbird.copyEntityToDraft",
                    UtilMisc.toMap("draftUUID", sapContextId, "entity", entity, "userLogin", userLogin));
            draftGenericValue = (GenericValue) svcResult.get("draftGenericValue");
            copyNavigationToDraft(readerContext, edmEntitySet, keyMap, sapContextId, draftEntityName, 1);

        } catch (GenericServiceException | GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), e.getMessage());
        }
        if (UtilValidate.isEmpty(draftGenericValue)) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found");
        }
        return OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmEntitySet, null,
                draftGenericValue, locale);
    }

    private static void copyNavigationToDraft(Map<String, Object> readerContext, EdmBindingTarget edmBindingTarget, Map<String, Object> keyMap,
                                              String parentUUID, String mainEntityDraftName, int navLevel) throws OfbizODataException, GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) readerContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) readerContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) readerContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) readerContext.get("edmProvider");
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        List<CsdlNavigationProperty> csdlNavigationProperties = csdlEntityType.getNavigationProperties();
        for (CsdlNavigationProperty csdlNavigationProperty : csdlNavigationProperties) {
            OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlNavigationProperty;
            OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            String originNavEntityName = navCsdlEntityType.getOfbizEntity();
            String draftNavEntityName = navCsdlEntityType.getDraftEntityName();
            //navigation没有draft或者navigation是主对象，不创建
            if (draftNavEntityName == null || draftNavEntityName.equals(mainEntityDraftName) || ofbizCsdlNavigationProperty.isReadOnly()) {
                continue;
            }
            EdmNavigationProperty edmNavigationProperty = edmEntityType.getNavigationProperty(csdlNavigationProperty.getName());
            EdmEntitySet navigationTargetEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget);
            OdataReader reader = new OdataReader(readerContext, new HashMap<>(), edmParams);
            Entity mainEntity = reader.findOne(keyMap, null);
            EntityCollection entities = reader.findRelatedList(mainEntity, edmNavigationProperty, new HashMap<>(), null);
            for (Entity entity : entities.getEntities()) {
                String navDraftUUID = Util.generateDraftUUID();
                OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
                GenericValue navGenericValue = odataOfbizEntity.getGenericValue();
                ProcessorServices.createDraftAdminData(delegator, navDraftUUID, parentUUID, navCsdlEntityType,
                        navGenericValue.getPrimaryKey(), edmNavigationProperty.getName(), userLogin);
                dispatcher.runSync("dpbird.copyEntityToDraft",
                        UtilMisc.toMap("draftUUID", navDraftUUID, "entity", entity, "userLogin", userLogin));
                if (navLevel < 2) {
                    //暂时只支持两级的Draft Navigation
                    copyNavigationToDraft(readerContext, navigationTargetEntitySet, new HashMap<>(odataOfbizEntity.getKeyMap()),
                            navDraftUUID, mainEntityDraftName, 2);
                }
            }
        }
    }

    // 将主对象及其自对象从内存数据库中删除
    public static void clearEntityDraft(Map<String, Object> odataContext, String sapContextId)
            throws OfbizODataException, GenericEntityException {
//        String sapContextId = (String) odataContext.get("sapContextId");
        // 找到主对象
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue mainDraftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", sapContextId), false);
        if (mainDraftAdminData == null) {
            return;
        }
        String mainDraftEntityName = mainDraftAdminData.getString("draftEntityName");
        List<GenericValue> subDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap("parentDraftUUID", sapContextId), null, false);
        for (GenericValue subDraftAdminData : subDraftAdminDataList) {
            delegator.removeByAnd(subDraftAdminData.getString("draftEntityName"),
                    UtilMisc.toMap("draftUUID", subDraftAdminData.getString("draftUUID")));
            clearEntityDraft(odataContext, subDraftAdminData.getString("draftUUID"));
            subDraftAdminData.remove();
        }
        delegator.removeByAnd(mainDraftEntityName,
                UtilMisc.toMap("draftUUID", sapContextId));
        mainDraftAdminData.remove();
    }
}
