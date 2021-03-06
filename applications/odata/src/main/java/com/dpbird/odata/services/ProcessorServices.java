package com.dpbird.odata.services;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntitySet;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import com.dpbird.odata.processor.DataModifyActions;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericPK;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.*;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationPropertyBinding;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.UriParameter;

import javax.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;

public class ProcessorServices {

    public final static String module = OdataServices.class.getName();
    
    public static Map<String, Object> createEntityData(DispatchContext dctx, Map<String, Object> context)
			throws OfbizODataException, ODataApplicationException {

        // Service Head
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

		EdmEntitySet edmEntitySet = (EdmEntitySet) context.get("edmEntitySet");
		EdmEntityType edmTypeFilter = (EdmEntityType) context.get("edmTypeFilter");
		EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
		List<UriParameter> keyParams = (List) context.get("keyParams");
		List<UriParameter> navKeyParams = (List) context.get("navKeyParams");
		Map<String, Object> keyMap = null;
		if (UtilValidate.isNotEmpty(keyParams)) {
			keyMap = Util.uriParametersToMap(keyParams, edmEntitySet.getEntityType());
		}
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyParams)) {
			navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
		}
		Entity entityToWrite = (Entity) context.get("entityToWrite");
		Entity createdEntity;
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());
		String sapContextId = (String) context.get("sapContextId");
		if (UtilValidate.isNotEmpty(sapContextId)) {
			DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, edmEntitySet.getEntityType());
			createdEntity = draftHandler.createEntityData(entityToWrite);
		} else {
			String rawServiceUri = (String) context.get("rawServiceUri");
			OData oData = (OData) context.get("oData");
			ServiceMetadata serviceMetadata = (ServiceMetadata) context.get("serviceMetadata");
			Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
					"edmProvider", edmProvider, "oData", oData, "serviceMetadata", serviceMetadata,
					"userLogin", userLogin, "locale", locale);
			Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet, "edmTypeFilter", edmTypeFilter,
					"edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite,
					"rawServiceUri", rawServiceUri);
			OfbizOdataWriter ofbizOdataWriter = new OfbizOdataWriter(odataContext, null, edmParams);
			createdEntity = ofbizOdataWriter.createEntityData(entityToWrite);
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put("createdEntity", createdEntity);
        return result;
    }
    
    public static Map<String, Object> createRelatedEntityData(DispatchContext dctx, Map<String, Object> context)
			throws OfbizODataException {

        // Service Head
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
		HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("httpServletRequest");
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String sapContextId = (String) context.get("sapContextId");
		Entity entityToWrite = (Entity) context.get("entityToWrite");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
		EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
		EdmEntityType edmTypeFilter = (EdmEntityType) context.get("edmTypeFilter");
		EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
		String navigationPropertyName = edmNavigationProperty.getName();
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());

		Entity createdEntity;
		List<UriParameter> keyParams = (List) context.get("keyParams");
		List<UriParameter> navKeyParams = (List) context.get("navKeyParams");
		Map<String, Object> keyMap = null;
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyParams)) {
			navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
		}
		if (UtilValidate.isNotEmpty(keyParams)) {
			keyMap = Util.uriParametersToMap(keyParams, edmBindingTarget.getEntityType());
		}
		// get NavigationBindingTarget
		String navigationBindingTarget = null;
		CsdlEntityContainer csdlEntityContainer = edmProvider.getEntityContainer();
		OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) csdlEntityContainer.getEntitySet(edmBindingTarget.getName());
		List<CsdlNavigationPropertyBinding> csdlNavigationPropertyBindings = csdlEntitySet.getNavigationPropertyBindings();
		for (CsdlNavigationPropertyBinding csdlNavigationPropertyBinding:csdlNavigationPropertyBindings) {
			if (csdlNavigationPropertyBinding.getPath().equals(navigationPropertyName)) {
				navigationBindingTarget = csdlNavigationPropertyBinding.getTarget();
			}
		}
		OfbizCsdlEntitySet navigationBindingEntitySet = (OfbizCsdlEntitySet) csdlEntityContainer.getEntitySet(navigationBindingTarget);
		entityToWrite = Util.addEntitySetConditionToEntity(delegator, navigationBindingEntitySet, entityToWrite, userLogin);
		if (UtilValidate.isNotEmpty(sapContextId)) {
			DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, edmBindingTarget.getEntityType());
			createdEntity = draftHandler.createRelatedEntityData(keyMap, entityToWrite, edmNavigationProperty.getName());
		} else {
			String rawServiceUri = (String) context.get("rawServiceUri");
			OData oData = (OData) context.get("oData");
			ServiceMetadata serviceMetadata = (ServiceMetadata) context.get("serviceMetadata");
			Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
					"edmProvider", edmProvider, "oData", oData, "serviceMetadata", serviceMetadata, "sapContextId", sapContextId,
					"httpServletRequest", httpServletRequest, "userLogin", userLogin, "locale", locale);
			Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "edmTypeFilter", edmTypeFilter,
					"edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite,
					"rawServiceUri", rawServiceUri);
			OfbizOdataWriter ofbizOdataWriter = new OfbizOdataWriter(odataContext, null, edmParams);
			createdEntity = ofbizOdataWriter.createRelatedEntityData(keyMap, entityToWrite);
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put("createdEntity", createdEntity);
        return result;
    }

    public static Map<String, Object> updateEntityData(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {

        // Service Head
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

		EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
		EdmEntityType edmTypeFilter = (EdmEntityType) context.get("edmTypeFilter");
		EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
		List<UriParameter> keyParams = (List) context.get("keyParams");
		List<UriParameter> navKeyParams = (List) context.get("navKeyParams");
		HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("request");
		Map<String, Object> keyMap = null;
		if (UtilValidate.isNotEmpty(keyParams)) {
			keyMap = Util.uriParametersToMap(keyParams, edmBindingTarget.getEntityType());
		}
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyParams)) {
			navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
		}
		OdataOfbizEntity odataOfbizEntity; // this is the updated entity will return
		Entity entityToWrite = (Entity) context.get("entityToWrite");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
		String sapContextId = (String) context.get("sapContextId");
		if (UtilValidate.isNotEmpty(sapContextId)) {
			DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, edmBindingTarget.getEntityType());
			odataOfbizEntity = draftHandler.updateEntityData(keyMap, entityToWrite);
		} else {
			String rawServiceUri = (String) context.get("rawServiceUri");
			OData oData = (OData) context.get("oData");
			ServiceMetadata serviceMetadata = (ServiceMetadata) context.get("serviceMetadata");

			Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
					"edmProvider", edmProvider, "oData", oData, "serviceMetadata", serviceMetadata, "httpServletRequest", httpServletRequest,
					"userLogin", userLogin, "locale", locale);
			Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "edmTypeFilter", edmTypeFilter,
					"edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite,
					"rawServiceUri", rawServiceUri);
			OfbizOdataWriter ofbizOdataWriter = new OfbizOdataWriter(odataContext, null, edmParams);
			odataOfbizEntity = ofbizOdataWriter.updateEntityData(keyMap, entityToWrite);
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put("entity", odataOfbizEntity);
        return result;
    }
    
    public static Map<String, Object> deleteEntityData(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {

        // Service Head
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

		EdmEntitySet edmEntitySet = (EdmEntitySet) context.get("edmEntitySet");
		EdmEntityType edmTypeFilter = (EdmEntityType) context.get("edmTypeFilter");
		EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
		List<UriParameter> keyParams = (List) context.get("keyParams");
		List<UriParameter> navKeyParams = (List) context.get("navKeyParams");
		Map<String, Object> keyMap = null;
		if (UtilValidate.isNotEmpty(keyParams)) {
			keyMap = Util.uriParametersToMap(keyParams, edmEntitySet.getEntityType());
		}
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyParams)) {
			navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
		}
		Entity entityToWrite = (Entity) context.get("entityToWrite");
		String rawServiceUri = (String) context.get("rawServiceUri");
		OData oData = (OData) context.get("oData");
		ServiceMetadata serviceMetadata = (ServiceMetadata) context.get("serviceMetadata");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());
		String sapContextId = (String) context.get("sapContextId");
		if (UtilValidate.isNotEmpty(sapContextId)) {
			DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, edmEntitySet.getEntityType());
			draftHandler.deleteEntityData(keyMap);
		} else {
			Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
					"edmProvider", edmProvider, "oData", oData, "serviceMetadata", serviceMetadata, "sapContextId", sapContextId,
					"userLogin", userLogin, "locale", locale);
			Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet, "edmTypeFilter", edmTypeFilter,
					"edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite,
					"rawServiceUri", rawServiceUri);
			OfbizOdataWriter ofbizOdataWriter = new OfbizOdataWriter(odataContext, null, edmParams);
			ofbizOdataWriter.deleteEntityData(keyMap);
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }

    public static Map<String, Object> deleteRelatedEntityData(DispatchContext dctx, Map<String, Object> context)
            throws ODataException {

        // Service Head
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
		HttpServletRequest httpServletRequest = (HttpServletRequest) context.get("httpServletRequest");

		EdmBindingTarget edmBindingTarget = (EdmBindingTarget) context.get("edmBindingTarget");
		EdmEntityType edmTypeFilter = (EdmEntityType) context.get("edmTypeFilter");
		EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) context.get("edmNavigationProperty");
		List<UriParameter> keyParams = (List) context.get("keyParams");
		List<UriParameter> navKeyParams = (List) context.get("navKeyParams");
		Map<String, Object> keyMap = null;
		if (UtilValidate.isNotEmpty(keyParams)) {
			keyMap = Util.uriParametersToMap(keyParams, edmBindingTarget.getEntityType());
		}
		Map<String, Object> navKeyMap = null;
		if (UtilValidate.isNotEmpty(navKeyParams)) {
			navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
		}
		Entity entityToWrite = (Entity) context.get("entityToWrite");
		String rawServiceUri = (String) context.get("rawServiceUri");
		OData oData = (OData) context.get("oData");
		ServiceMetadata serviceMetadata = (ServiceMetadata) context.get("serviceMetadata");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) context.get("edmProvider");
		
		Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
				"edmProvider", edmProvider, "oData", oData, "serviceMetadata", serviceMetadata,
				"httpServletRequest", httpServletRequest, "userLogin", userLogin, "locale", locale);
		Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "edmTypeFilter", edmTypeFilter,
				"edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityToWrite,
				"rawServiceUri", rawServiceUri);
		OfbizOdataWriter ofbizOdataWriter = new OfbizOdataWriter(odataContext, null, edmParams);
		ofbizOdataWriter.deleteRelatedEntityData(keyMap, navKeyMap, edmNavigationProperty);

		Map<String, Object> result = ServiceUtil.returnSuccess();
        return result;
    }

    public static Map<String, Object> updateEntityToDraft(DispatchContext dctx, Map<String, Object> context)
			throws ODataException {
		LocalDispatcher dispatcher = dctx.getDispatcher();
		Delegator delegator = dispatcher.getDelegator();
		Locale locale = (Locale) context.get("locale");
		GenericValue userLogin = (GenericValue) context.get("userLogin");
		String entityName = (String) context.get("originEntityName");
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		String draftEntityName = (String) context.get("draftEntityName");
		String sapContextId = (String) context.get("sapContextId");
		GenericValue draftGenericValue = null;
		Map<String, Object> fieldMap = (Map<String, Object>) context.get("fieldMap");
		for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
			//?????????????????????null
			if ("".equals(entry.getValue())) {
				entry.setValue(null);
			}
			//???????????????????????????????????????,??????????????????????????????
			ModelField field = modelEntity.getField(entry.getKey());
			if (UtilValidate.isNotEmpty(field) && "date".equals(field.getType()) && entry.getValue() instanceof GregorianCalendar) {
				GregorianCalendar calendar = (GregorianCalendar)entry.getValue();
				fieldMap.put(entry.getKey(), new Date(calendar.getTime().getTime()));
			}

		}
		try {
			List<String> pkFieldNames = modelEntity.getPkFieldNames();
			Map<String, Object> keyMap = new HashMap<>();
			for (String pkFieldName:pkFieldNames) {
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
    	GenericValue mainDraftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", sapContextId), false	);
		if (!mainDraftAdminData.getString("draftEntityName").equals(draftEntityName)) {
			List<GenericValue> subDraftAdminDataList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap("parentDraftUUID", sapContextId, "draftEntityName", draftEntityName), null, false);
			List<String> subDraftUUIDs = new ArrayList<>();
			for (GenericValue subDraftAdminData:subDraftAdminDataList) {
				subDraftUUIDs.add(subDraftAdminData.getString("draftUUID"));
			}
			List<GenericValue> draftGenericValues = delegator.findByAnd(draftEntityName, keyMap, null, false);
			for (GenericValue possibleDraftGenericValue:draftGenericValues) {
				if (subDraftUUIDs.contains(possibleDraftGenericValue.getString("draftUUID"))) {
					draftGenericValue = possibleDraftGenericValue;
					break;
				}
			}
		} else {
			draftGenericValue = delegator.findOne(draftEntityName, UtilMisc.toMap("draftUUID", sapContextId), false);
		}
		return  draftGenericValue;
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
		String entityTypeFqn = (String) context.get("entityType");
		String navigationProperty = (String) context.get("navigationProperty");
		Map<String, Object> result = ServiceUtil.returnSuccess();

		Map<String, Object> fieldMap = (Map<String, Object>) context.get("fieldMap");
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		ModelEntity draftModelEntity = delegator.getModelEntity(draftEntityName);
		List<String> pkFieldNames = modelEntity.getPkFieldNames();
		Map<String, Object> pkFieldMap = new HashMap<>();
		for (String pkFieldName:pkFieldNames) {
			Object pkFieldValue = fieldMap.get(pkFieldName);
			if (pkFieldValue == null && pkFieldNames.size() == 1) { // i.e. productId
				ModelField modelField = modelEntity.getField(pkFieldName);
				if (modelField.getType().equals("id")) {
					String idValue = "ID" + delegator.getNextSeqId(entityName);
					pkFieldValue = idValue;
				}
			}
			pkFieldMap.put(pkFieldName, pkFieldValue);
		}
		pkFieldMap = Util.makeupFromDate(pkFieldMap, modelEntity);
		List<String> noPkFieldNames = draftModelEntity.getNoPkFieldNames();
		Map<String, Object> noPkFieldMap = new HashMap<>();
		for (String fieldName:noPkFieldNames) {
			if (fieldMap.get(fieldName) != null) {
				noPkFieldMap.put(fieldName, fieldMap.get(fieldName));
			}
		}
		GenericValue draftGenericValue;
		boolean isMainEntity = false;
		try {
			GenericValue mainDraftAdminData = delegator.findOne("DraftAdministrativeData", UtilMisc.toMap("draftUUID", sapContextId), false);
			if (UtilValidate.isEmpty(navigationProperty)) {
				isMainEntity = true;
			}
			String draftUUID = sapContextId;
			if (!isMainEntity) {
				draftUUID = Util.generateDraftUUID();
				String parentDraftUUID = sapContextId;
				if (fieldMap.containsKey("parentDraftUUID")) {
					parentDraftUUID = (String) fieldMap.get("parentDraftUUID");
				}
				// ?????????draftAdminData
				GenericValue draftAdminData = createDraftAdminData(delegator, draftUUID, parentDraftUUID, entityName,
						draftEntityName, entityTypeFqn, null, navigationProperty, userLogin);
			}
			pkFieldMap.put("draftUUID", draftUUID);
			// ?????????????????????????????????????????????
			List<GenericValue> draftGenericValues = delegator.findByAnd(draftEntityName, pkFieldMap, null, false);
			if (UtilValidate.isNotEmpty(draftGenericValues)) { // ???????????????????????????
				return ServiceUtil.returnError("Data already exists, can't create a new one");
			}
			noPkFieldMap.put("isActiveEntity", "N");
			noPkFieldMap.put("hasActiveEntity", "N");
			noPkFieldMap.put("hasDraftEntity", "Y");
			draftGenericValue = delegator.makeValue(draftEntityName, pkFieldMap);
			draftGenericValue.putAll(noPkFieldMap);
			draftGenericValue.create();
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
		Locale locale = (Locale) context.get("locale");
		GenericValue userLogin = (GenericValue) context.get("userLogin");
		Map<String, Object> result = ServiceUtil.returnSuccess();

		String entityName = (String) context.get("originEntityName");
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
	// ???????????????draft table
	public static Map<String, Object> copyGenericValueToDraft(DispatchContext dctx, Map<String, Object> context)
			throws ODataException {

		LocalDispatcher dispatcher = dctx.getDispatcher();
		Delegator delegator = dispatcher.getDelegator();
		Locale locale = (Locale) context.get("locale");
		GenericValue userLogin = (GenericValue) context.get("userLogin");
		String entityName = (String) context.get("originEntityName");
		String draftEntityName = (String) context.get("draftEntityName");
		ModelEntity draftModelEntity = delegator.getModelEntity(draftEntityName);
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
			// ?????????DraftAdministrativeData?????????????????????
			draftAdminData = delegator.findOne("DraftAdministrativeData",
					false, UtilMisc.toMap("draftUUID", draftUUID));
			if (draftAdminData == null) {
				return ServiceUtil.returnError("No DraftAdministrativeData data exists");
			}
			String entityType = draftAdminData.getString("entityType");
			OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(entityType));
			GenericValue originGenericValue = (GenericValue) context.get("originGenericValue");
			OdataOfbizEntity odataOfbizEntity =
					OdataProcessorHelper.genericValueToEntity(delegator,
							edmProvider, csdlEntityType, originGenericValue, locale);
			List<Entity> entityList = OdataProcessorHelper.appendSemanticFields(httpServletRequest, delegator, dispatcher,
					edmProvider, null, UtilMisc.toList(odataOfbizEntity), locale, userLogin);
			odataOfbizEntity = (OdataOfbizEntity) entityList.get(0);
			Map<String, Object> entityMap = Util.entityToMap(odataOfbizEntity);
			GenericPK genericPK = originGenericValue.getPrimaryKey();
			Map<String, Object> fieldMap = new HashMap<>();
			fieldMap.putAll(genericPK);
			fieldMap.put("draftUUID", draftUUID);
			List<GenericValue> oldDraftGenericValues = delegator.findByAnd(draftEntityName, fieldMap, null, false);
			if (UtilValidate.isEmpty(oldDraftGenericValues)) { // ????????????????????????????????????
				fieldMap.put("isActiveEntity", "Y");
				fieldMap.put("hasActiveEntity", "Y");
				fieldMap.put("hasDraftEntity", "N");
				fieldMap.putAll(entityMap);
				GenericValue draftGenericValue = delegator.makeValue(draftEntityName, fieldMap);
				draftGenericValue.create();
				draftGenericValues.add(draftGenericValue);
			} else { // ????????????????????????????????????????????????????????????
				// ??????????????????????????????
				for (GenericValue draftGenericValue:oldDraftGenericValues) {
//					draftGenericValue.putAll(originGenericValue);
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

	// ???????????????draft table
	public static Map<String, Object> copyEntityToDraft(DispatchContext dctx, Map<String, Object> context)
			throws ODataException {

		LocalDispatcher dispatcher = dctx.getDispatcher();
		Delegator delegator = dispatcher.getDelegator();
		String draftUUID = (String) context.get("draftUUID");
		Map<String, Object> result = ServiceUtil.returnSuccess();
		GenericValue draftAdminData;
		try {
			// ?????????DraftAdministrativeData?????????????????????
			draftAdminData = delegator.findOne("DraftAdministrativeData",
					false, UtilMisc.toMap("draftUUID", draftUUID));
			if (draftAdminData == null) {
				return ServiceUtil.returnError("No DraftAdministrativeData data exists");
			}
			String draftEntityName = draftAdminData.getString("draftEntityName");
			OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) context.get("entity");
			Map<String, Object> entityMap = Util.entityToMap(odataOfbizEntity);
			// id????????????CsdlEntityType???AutoId????????????????????????
			entityMap.remove("id");
			Map<String, Object> fieldMap = new HashMap<>();
			fieldMap.put("draftUUID", draftUUID);
			List<GenericValue> oldDraftGenericValues = delegator.findByAnd(draftEntityName, fieldMap, null, false);
			if (UtilValidate.isEmpty(oldDraftGenericValues)) { // ????????????????????????????????????
				fieldMap.put("isActiveEntity", "Y");
				fieldMap.put("hasActiveEntity", "Y");
				fieldMap.put("hasDraftEntity", "N");
				fieldMap.putAll(entityMap);
				GenericValue draftGenericValue = delegator.makeValue(draftEntityName, fieldMap);
				draftGenericValue.create();
				result.put("draftGenericValue", draftGenericValue);
			} else { // ????????????????????????????????????????????????????????????
				// ??????????????????????????????
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
	private static String getIdForNewEntity(Delegator delegator, String entityName) {
		return delegator.getNextSeqId(entityName);
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
	private static GenericValue createDraftAdminData(Delegator delegator, String draftUUID, String parentDraftUUID,
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

	public static Object stickySessionNewAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
												EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
												Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException, GenericServiceException, ODataException {
		Delegator delegator = (Delegator) oDataContext.get("delegator");
		LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
		GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
		OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) edmProvider.getEntityContainer().getEntitySet(edmBindingTarget.getName());
		Map<String, Object> entitySetConditionMap = Util.parseConditionMap(csdlEntitySet.getConditionStr(), userLogin);
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
		String entityName = csdlEntityType.getOfbizEntity();
		String draftEntityName = csdlEntityType.getDraftEntityName();
		ModelEntity modelEntity = delegator.getModelEntity(entityName);
		ODataResponse oDataResponse = (ODataResponse) oDataContext.get("oDataResponse");
		List<String> pkFieldNames = modelEntity.getPkFieldNames();
		// ?????????keyMap????????????
		Map<String, Object> internalKeyMap = new HashMap<>();
		for (String pkFieldName:pkFieldNames) {
			Object pkFieldValue = actionParameters.get(pkFieldName);
			if (pkFieldValue == null && pkFieldNames.size() == 1) { // i.e. productId
				ModelField modelField = modelEntity.getField(pkFieldName);
				if (modelField.getType().equals("id")) {
					String idValue = "ID" + delegator.getNextSeqId(entityName);
					pkFieldValue = idValue;
				}
			}
			internalKeyMap.put(pkFieldName, pkFieldValue);
		}
		String sapContextId = (String) oDataContext.get("sapContextId");
		// ?????????draft table???EntityType??????????????????????????????????????????????????????????????????sapContextId??????????????????
		ProcessorServices.createDraftAdminData(delegator, sapContextId, null, csdlEntityType, internalKeyMap, null, userLogin);
		Map<String, Object> fieldMap = Util.retrieveFieldMap(delegator, actionParameters, csdlEntityType.getDraftEntityName());
		if (UtilValidate.isNotEmpty(entitySetConditionMap)) {
			fieldMap.putAll(entitySetConditionMap);
		}
		Map<String, Object> serviceParams = UtilMisc.toMap("originEntityName", entityName,
				"draftEntityName", draftEntityName, "entityType", csdlEntityType.getName(),
				"fieldMap", fieldMap, "sapContextId", sapContextId, "userLogin", userLogin);
		Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createEntityToDraft", serviceParams);
		GenericValue draftGenericValue = (GenericValue) serviceResult.get("draftGenericValue");
		return OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmBindingTarget, null,
				draftGenericValue, (Locale) oDataContext.get("locale"));
	}

	public static Object stickySessionEditAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
												 EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
												 Map<String, Object> keyMap,
												 Map<String, Object> navKeyMap) throws ODataException {
		Delegator delegator = (Delegator) oDataContext.get("delegator");
		LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
		GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
		String entityName = csdlEntityType.getOfbizEntity();
		String draftEntityName = csdlEntityType.getDraftEntityName();

		EdmEntitySet edmEntitySet = (EdmEntitySet) edmBindingTarget;
		EdmEntityType startEdmEntityType = edmEntitySet.getEntityType();
		OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(startEdmEntityType.getFullQualifiedName());
		oDataContext.put("edmEntitySet", edmEntitySet);

		//??????sapContextId
		String sapContextId = (String) oDataContext.get("sapContextId");
		GenericValue draftAdminData = null;
		OdataOfbizEntity draftEntity;
		GenericValue draftGenericValue;
		try {
			if (sapContextId == null) {
				sapContextId = Util.generateDraftUUID();
			}
			// ?????????????????????????????????????????????draft??????
			List<GenericValue> draftAdminDataList = delegator.findByAnd("DraftAdministrativeData",
					UtilMisc.toMap("originEntityName", entityName, "draftEntityName", draftEntityName, "entityKeyMap", Util.mapToStr(keyMap)),
					null, false);
			draftAdminData = EntityUtil.getFirst(draftAdminDataList);
			if (UtilValidate.isNotEmpty(draftAdminData)) {
				sapContextId = draftAdminData.getString("draftUUID");
			}

			if (draftAdminData == null) { // ?????????DraftAdministrativeData???????????????sapContextId??????????????????
				draftAdminData = ProcessorServices.createDraftAdminData(delegator, sapContextId, null,
						ofbizCsdlEntityType, keyMap, null, userLogin);
				// ??????draft????????????????????????????????????
				oDataContext.put("sapContextId", sapContextId);
				draftEntity = DataModifyActions.copyEntityToDraft(oDataContext, keyMap);
			} else { // ????????????draft??????????????????draft????????????
				draftGenericValue = delegator.findOne(draftEntityName,
						UtilMisc.toMap("draftUUID", draftAdminData.get("draftUUID")), false);
				draftEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmEntitySet, null,
						draftGenericValue, (Locale) oDataContext.get("locale"));
			}
		} catch (GenericEntityException e) {
			throw new OfbizODataException(e.getMessage());
		}
		return draftEntity;
	}

	// saveAction will load data from mem database and store into real database
	public static Object stickySessionSaveAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
												 EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
												 Map<String, Object> keyMap,
												 Map<String, Object> navKeyMap) throws ODataException {
		Delegator delegator = (Delegator) oDataContext.get("delegator");
		LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
		GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
		Locale locale = (Locale) oDataContext.get("locale");
		OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
		OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
		String entityName = csdlEntityType.getOfbizEntity();
		String sapContextId = (String) oDataContext.get("sapContextId");
		if (sapContextId == null) {
			throw new OfbizODataException("We need session contextId while calling saveAction!");
		}

		// save main entity first
		GenericValue mainGenericValue = DataModifyActions.persistentMainEntity(oDataContext, sapContextId);
		// save NavigationProperty???????????????
		persistentTwoLevelNavEntity(oDataContext, mainGenericValue, csdlEntityType, sapContextId, 1);
		try {
			DataModifyActions.clearEntityDraft(oDataContext, sapContextId);
		} catch (GenericEntityException e) {
			e.printStackTrace();
			throw new OfbizODataException(e.getMessage());
		}
		OdataOfbizEntity updatedEntity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider, edmBindingTarget, null,
				mainGenericValue, (Locale) oDataContext.get("locale"));
		OdataProcessorHelper.appendNonEntityFields(null, delegator, dispatcher, edmProvider,
				null, UtilMisc.toList(updatedEntity), locale, userLogin);
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

	public static Map<String, Object> saveViewEntityData(DispatchContext dctx, Map<String, Object> context)
			throws OfbizODataException, ODataApplicationException, GenericEntityException, GenericServiceException {
		LocalDispatcher dispatcher = dctx.getDispatcher();
		Delegator delegator = dctx.getDelegator();
		String viewEntityName = (String) context.get("viewEntityName");
		GenericValue userLogin = (GenericValue) context.get("userLogin");
		Map<String, Object> fieldMap = (Map<String, Object>) context.get("fieldMap");
		ModelViewEntity modelViewEntity = (ModelViewEntity) delegator.getModelEntity(viewEntityName);
		String mainEntityAlias = modelViewEntity.getViewLink(0).getEntityAlias();
		ModelEntity mainModelEntity = modelViewEntity.getMemberModelEntity(mainEntityAlias);
		//??????????????????
		Map<String, Object> mainEntityFieldMap = new HashMap<>();
		for (Map.Entry<String, Object> fieldEntry : fieldMap.entrySet()) {
			ModelViewEntity.ModelAlias alias = modelViewEntity.getAlias(fieldEntry.getKey());
			if (alias != null && alias.getEntityAlias().equals(mainEntityAlias)) {
				mainEntityFieldMap.put(alias.getField(), fieldEntry.getValue());
			}
		}
		String mainAction;
		GenericValue mainGenericValue = null;
		if (UtilValidate.isNotEmpty(mainEntityFieldMap)) {
			//mainEntityFieldMap?????????????????????????????????????????????????????????????????????????????????
			if (mainEntityFieldMap.keySet().containsAll(mainModelEntity.getPkFieldNames())) {
				Map<String, Object> mainEntityPk = getEntityPkFromMap(mainModelEntity, mainEntityFieldMap);
				GenericValue mainEntityQueryResult = delegator.findOne(mainModelEntity.getEntityName(), mainEntityPk, false);
				if (UtilValidate.isEmpty(mainEntityQueryResult)) {
					mainAction = "create";
				} else {
					mainAction = "update";
				}
			} else {
				mainAction = "create";
			}
			mainEntityFieldMap.put("userLogin", context.get("userLogin"));
			String entityActionService = Util.getEntityActionService(mainModelEntity.getEntityName(), mainAction, delegator);
			Map<String, Object> mainEntityServiceResult = dispatcher.runSync(entityActionService, mainEntityFieldMap);
			mainEntityServiceResult.putAll(mainEntityFieldMap);
			Map<String, Object> entityPkFromMap = getEntityPkFromMap(mainModelEntity, mainEntityServiceResult);
			mainGenericValue = delegator.findOne(mainModelEntity.getEntityName(), entityPkFromMap, false);
			if (mainGenericValue == null) {
				return null;
			}
		}

		//????????????????????????memberEntity
		Set<String> memberEntityAliasSet = new HashSet<>();
		for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			ModelViewEntity.ModelAlias alias = modelViewEntity.getAlias(entry.getKey());
			if (alias != null && !alias.getEntityAlias().equals(mainEntityAlias)) {
				memberEntityAliasSet.add(alias.getEntityAlias());
			}
		}

		for (String memberEntityAlias : memberEntityAliasSet) {
			GenericValue relGenericValue = findByViewLink(delegator, modelViewEntity, memberEntityAlias, mainGenericValue);
			if (relGenericValue != null) {
				//???????????????????????????
				updateRelatedEntity(modelViewEntity, fieldMap, userLogin, relGenericValue, memberEntityAlias, delegator, dispatcher);
			} else {
				//?????????????????????
				List<String> viewLinkChain = getViewLinkChain(modelViewEntity, memberEntityAlias, UtilMisc.toList(memberEntityAlias));
				Map<String, Object> relationMap = new HashMap<>();
				for (int i = 0; i < viewLinkChain.size() - 1; i++) {
					ModelViewEntity.ModelViewLink viewLink = getViewLinkByEntityAlias(modelViewEntity, viewLinkChain.get(i + 1), viewLinkChain.get(i));
					if (viewLink != null) {
						//?????????????????????memberEntity?????????
						Map<String, Object> createMap = UtilMisc.toMap("userLogin", userLogin);
						for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
							ModelViewEntity.ModelAlias alias = modelViewEntity.getAlias(entry.getKey());
							if (alias != null && alias.getEntityAlias().equals(viewLink.relEntityAlias)) {
								createMap.put(alias.getField(), entry.getValue());
							}
						}
						createMap.putAll(relationMap);
						if (viewLink.getEntityAlias().equals(mainEntityAlias)) {
							//????????????
							for (ModelKeyMap keyMap : viewLink.keyMaps) {
								createMap.put(keyMap.getRelFieldName(), mainGenericValue.get(keyMap.getFieldName()));
							}
						}
						Map<String, Object> relatedEntityFromViewLink = createRelatedEntityFromViewLink(modelViewEntity, viewLink, createMap, delegator, dispatcher);
						relationMap.putAll(relatedEntityFromViewLink);
					}
				}
			}
		}
		Map<String, Object> entityPkFromMap = getEntityPkFromMap(modelViewEntity, fieldMap);
		Map<String, Object> resultMap = ServiceUtil.returnSuccess();
		resultMap.put("pkMap", entityPkFromMap);
		return resultMap;
	}

	/**
	 * ??????viewLink??????????????? ?????????kepMap???????????????
	 */
	private static Map<String, Object> createRelatedEntityFromViewLink(ModelViewEntity modelViewEntity, ModelViewEntity.ModelViewLink viewLink, Map<String, Object> createMap,
																	   Delegator delegator, LocalDispatcher dispatcher) throws GenericEntityException, OfbizODataException, GenericServiceException {

		ModelEntity relModelEntity = modelViewEntity.getMemberModelEntity(viewLink.getRelEntityAlias());
		String relEntityService = Util.getEntityActionService(relModelEntity.getEntityName(), "create", delegator);
		//???condition???????????????entityDateMap
		ModelViewEntity.ViewEntityCondition viewEntityCondition = viewLink.getViewEntityCondition();
		if (viewEntityCondition != null) {
			Map<String, Object> conditionMap = parseViewEntityCondition(viewEntityCondition);
			createMap.putAll(conditionMap);
		}
		//??????service
		Map<String, Object> createRelEntityResult = dispatcher.runSync(relEntityService, createMap);
		Map<String, Object> newRelEntityPk = getEntityPkFromMap(relModelEntity, createRelEntityResult);
		if (!newRelEntityPk.keySet().containsAll(relModelEntity.getPkFieldNames())) {
			//??????service?????????????????????????????????createMap??????
			createRelEntityResult.putAll(createMap);
			newRelEntityPk = getEntityPkFromMap(relModelEntity, createRelEntityResult);
		}
		//????????????
		GenericValue newRelEntity = delegator.findOne(relModelEntity.getEntityName(), newRelEntityPk, false);
		Map<String, Object> resultMap = new HashMap<>();
		List<ModelKeyMap> keyMaps = viewLink.keyMaps;
		for (ModelKeyMap keyMap : keyMaps) {
			resultMap.put(keyMap.getFieldName(), newRelEntity.get(keyMap.getRelFieldName()));
		}
		return resultMap;
	}

	/**
	 * ????????????
	 */
	private static void updateRelatedEntity(ModelViewEntity modelViewEntity, Map<String, Object> fieldMap, GenericValue userLogin, GenericValue relGenericValue, String memberEntityAlias,
											Delegator delegator, LocalDispatcher dispatcher) throws GenericEntityException, OfbizODataException, GenericServiceException {
		Map<String, Object> updateField = UtilMisc.toMap("userLogin", userLogin);
		for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
			ModelViewEntity.ModelAlias alias = modelViewEntity.getAlias(entry.getKey());
			if (alias != null && alias.getEntityAlias().equals(memberEntityAlias)) {
				updateField.put(alias.getField(), entry.getValue());
			}
		}
		String relEntityService = Util.getEntityActionService(relGenericValue.getEntityName(), "update", delegator);
		ModelService modelService = dispatcher.getDispatchContext().getModelService(relEntityService);
		updateField = OdataProcessorHelper.addRequiredParams(modelService, relGenericValue, updateField);
		//??????service
		dispatcher.runSync(relEntityService, updateField);
	}

	private static Map<String, Object> getEntityPkFromMap(ModelEntity modelEntity, Map<String, Object> map) {
		Map<String, Object> resultPKMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if (modelEntity.getPkFieldNames().contains(entry.getKey())) {
				resultPKMap.putIfAbsent(entry.getKey(), entry.getValue());
			}
		}
		return resultPKMap;

	}

	/**
	 * ???????????????????????????
	 */
	private static List<String> getViewLinkChain(ModelViewEntity modelViewEntity, String entityAlias, List<String> viewLinkChain) {
		if (viewLinkChain == null) {
			viewLinkChain = new ArrayList<>();
		}
		for (int i = 0; i < modelViewEntity.getViewLinksSize(); i++) {
			ModelViewEntity.ModelViewLink viewLink = modelViewEntity.getViewLink(i);
			if (viewLink.getRelEntityAlias().equals(entityAlias)) {
				viewLinkChain.add(viewLink.getEntityAlias());
				getViewLinkChain(modelViewEntity, viewLink.getEntityAlias(), viewLinkChain);
			}
		}
		return viewLinkChain;
	}

	/**
	 * ????????????memberEntity?????????ViewLink
	 */
	private static ModelViewEntity.ModelViewLink getViewLinkByEntityAlias(ModelViewEntity modelViewEntity, String entityAlias, String relEntityAlias) {
		for (int i = 0; i < modelViewEntity.getViewLinksSize(); i++) {
			ModelViewEntity.ModelViewLink currViewLink = modelViewEntity.getViewLink(i);
			if (currViewLink.getEntityAlias().equals(entityAlias) && currViewLink.getRelEntityAlias().equals(relEntityAlias)) {
				return currViewLink;
			}
		}
		return null;
	}

	/**
	 * ??????vieLink??????????????????
	 */
	private static GenericValue findByViewLink(Delegator delegator, ModelViewEntity modelViewEntity, String lastEntityAlias, GenericValue mainGenericValue) throws GenericEntityException {
		List<String> viewLinkChain = getViewLinkChain(modelViewEntity, lastEntityAlias, UtilMisc.toList(lastEntityAlias));
		Collections.reverse(viewLinkChain);
		GenericValue findResult = null;
		for (int i = 0; i < viewLinkChain.size() - 1; i++) {
			ModelViewEntity.ModelViewLink viewLink = getViewLinkByEntityAlias(modelViewEntity, viewLinkChain.get(i), viewLinkChain.get(i + 1));
			if (viewLink == null) {
				return null;
			}
			Map<String, Object> queryMap = new HashMap<>();
			//???????????????kepMap???condition??????
			List<ModelKeyMap> keyMaps = viewLink.keyMaps;
			for (ModelKeyMap keyMap : keyMaps) {
				queryMap.put(keyMap.getRelFieldName(), mainGenericValue.get(keyMap.getFieldName()));
			}
			if (viewLink.getViewEntityCondition() != null) {
				Map<String, Object> conditionField = parseViewEntityCondition(viewLink.getViewEntityCondition());
				queryMap.putAll(conditionField);
			}
			ModelEntity relMemberModelEntity = modelViewEntity.getMemberModelEntity(viewLink.getRelEntityAlias());
			List<GenericValue> relGenericValueList = delegator.findByAnd(relMemberModelEntity.getEntityName(), queryMap, null, false);
			findResult = EntityUtil.getFirst(relGenericValueList);
			//??????????????????????????????????????????null
			if (findResult == null) {
				return null;
			} else if (i == 0 && viewLinkChain.size() >= 3) {
				mainGenericValue = findResult;
			}
		}
		return findResult;
	}

	/**
	 * ???ViewEntityCondition?????????map??????????????????eq
	 */
	private static Map<String, Object> parseViewEntityCondition(ModelViewEntity.ViewEntityCondition viewEntityCondition) {
		if (viewEntityCondition.whereCondition instanceof ModelViewEntity.ViewConditionExpr) {
			ModelViewEntity.ViewConditionExpr conditionExpr = (ModelViewEntity.ViewConditionExpr) viewEntityCondition.whereCondition;
			return UtilMisc.toMap(conditionExpr.fieldName, conditionExpr.value);
		} else {
			ModelViewEntity.ViewConditionList conditionList = (ModelViewEntity.ViewConditionList) viewEntityCondition.whereCondition;
			Map<String, Object> fieldKepMap = new HashMap<>();
			for (ModelViewEntity.ViewCondition viewCondition : conditionList.conditionList) {
				ModelViewEntity.ViewConditionExpr conditionExpr = (ModelViewEntity.ViewConditionExpr) viewCondition;
				fieldKepMap.put(conditionExpr.fieldName, conditionExpr.value);
			}
			return fieldKepMap;
		}
	}

}
