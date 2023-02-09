package com.dpbird.test.stickyDemo;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.services.ProcessorServices;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.ex.ODataException;

import java.util.HashMap;
import java.util.Map;

/**
 * @date 2023/2/6
 */
public class OrderStickyDemo {

    /**
     * 实现自定义创建Draft数据
     */
    public static Object orderDemoStickyNewAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException, GenericServiceException, ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String sapContextId = (String) oDataContext.get("sapContextId");
        //先调通用的方法 创建主实体
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) ProcessorServices.stickySessionNewAction(oDataContext, actionParameters, edmBindingTarget);
        //根据参数创建子对象Draft
        if (UtilValidate.isNotEmpty(actionParameters.get("orderTypeId"))) {
            Map<String, Object> keyMap = UtilMisc.toMap("orderTypeId", actionParameters.get("orderTypeId"));
            GenericValue orderType = delegator.findOne("OrderType", keyMap, false);
            Map<String, Object> draftFields = new HashMap<>(keyMap);
            draftFields.put("orderTypeId", orderType.getString("orderTypeId"));
            draftFields.put("description", orderType.getString("description"));
            //create
            GenericValue orderTypeDraft = Util.createNavDraftData(oDataContext, sapContextId, keyMap, "OrderType", draftFields);
        }
        return ofbizEntity;
    }

}
