package com.dpbird.app;

import com.dpbird.odata.services.OdataServices;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AppServices {
    public final static String module = OdataServices.class.getName();
    public final static String DEFAULT_VALUE = "_NA_";

    public static Map<String, Object> createFacility(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        String facilityId = (String) context.get("facilityId");
        if (UtilValidate.isEmpty(facilityId)) {
            facilityId = delegator.getNextSeqId("Facility");
        }
        GenericValue facility = delegator.makeValue("Facility", UtilMisc.toMap("facilityId", facilityId));
        facility.setAllFields(context, false, null, false);
        facility.create();
        result.put("facilityId", facility.get("facilityId"));

        return result;
    }

    public static Map<String, Object> createPostalAddress(DispatchContext dctx, Map<String, Object> context)
            throws GenericEntityException, GenericServiceException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");

        if (context.get("city") == null) {
            context.put("city", DEFAULT_VALUE);
        }
        if (context.get("postalCode") == null) {
            context.put("postalCode", DEFAULT_VALUE);
        }
        Map<String, Object> result = dispatcher.runSync("createPostalAddress", context);
        return result;
    }

    public static Map<String, Object> createUserPreference(DispatchContext dctx, Map<String, Object> context)
            throws GenericEntityException, GenericServiceException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dispatcher.getDelegator();
        Locale locale = (Locale) context.get("locale");

        String userPrefTypeId = (String) context.get("userPrefTypeId");
        Object userPrefValue = context.get("userPrefValue");
        String userPrefGroupTypeId = (String) context.get("userPrefGroupTypeId");
        String userPrefDataType = (String) context.get("userPrefDataType");
        String userPrefLoginId = (String) context.get("userLoginId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        dispatcher.runSync("setUserPreference", UtilMisc.toMap("userPrefTypeId", userPrefTypeId,
                "userPrefValue", userPrefValue, "userPrefGroupTypeId", userPrefGroupTypeId,
                "userPrefDataType", userPrefDataType, "userPrefLoginId", userPrefLoginId, "userLogin", userLogin));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        String userLoginId = userPrefLoginId;
        if (userLoginId == null) {
            userLoginId = userLogin.getString("userLoginId");
        }
        // 作为创建对象的service，按照惯例返回主键
        result.put("userLoginId", userLoginId);
        result.put("userPrefTypeId", userPrefTypeId);
        return result;
    }

    public static Map<String, Object> createProductStoreFacility(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        String serviceName = "createProductStoreFacility";
        Map<String, Object> serviceParams = new HashMap<>(context);
        Timestamp fromDate = (Timestamp) context.get("fromDate");
        if (fromDate == null) {
            fromDate = UtilDateTime.nowTimestamp();
        }
        serviceParams.put("fromDate", fromDate);
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> serviceResult = dispatcher.runSync(serviceName, serviceParams);
        result.put("productStoreId", context.get("productStoreId"));
        result.put("facilityId", context.get("facilityId"));
        result.put("fromDate", fromDate);
        return result;
    }

    public static Map<String, Object> createWorkEffort(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        String serviceName = "createWorkEffort";
        Map<String, Object> serviceParams = new HashMap<>(context);
        String currentStatusId = (String) context.get("currentStatusId");
        if (currentStatusId == null) {
            currentStatusId = "PTS_CREATED";
        }
        serviceParams.put("currentStatusId", currentStatusId);
        String workEffortName = (String) context.get("workEffortName");
        if (workEffortName == null) {
            workEffortName = "_NA_";
        }
        serviceParams.put("workEffortName", workEffortName);
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> serviceResult = dispatcher.runSync(serviceName, serviceParams);
        result.put("workEffortId", context.get("workEffortId"));
        return result;
    }

    public static Map<String, Object> createProductFeatureAndAppl(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        String serviceName = "applyFeatureToProduct";
        Map<String, Object> serviceParams = new HashMap<>(context);
        if( UtilValidate.isEmpty(serviceParams.get("productFeatureApplTypeId")) ){
            serviceParams.put("productFeatureApplTypeId","STANDARD_FEATURE");
        }
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> serviceResult = dispatcher.runSync(serviceName, serviceParams);
        return serviceResult;
    }

    public static Map<String, Object> updateProductFeatureAndAppl(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        String serviceName = "updateFeatureToProductApplication";
        Map<String, Object> serviceParams = new HashMap<>(context);

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> serviceResult = dispatcher.runSync(serviceName, serviceParams);
        return serviceResult;
    }

    public static Map<String, Object> deleteProductFeatureAndAppl(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericEntityException, GenericServiceException {
        String serviceName = "removeFeatureFromProduct";
        Map<String, Object> serviceParams = new HashMap<>(context);
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> serviceResult = dispatcher.runSync(serviceName, serviceParams);
        return serviceResult;
    }



}
