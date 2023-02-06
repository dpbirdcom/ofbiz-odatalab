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
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");

        //先调通用的方法 创建主实体
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) ProcessorServices.stickySessionNewAction(oDataContext, actionParameters, edmBindingTarget);

        //根据参数创建子对象Draft
        if (UtilValidate.isNotEmpty(actionParameters.get("orderTypeId"))) {
            String orderTypeId = (String) actionParameters.get("orderTypeId");
            EdmNavigationProperty orderTypeEdmNavigation = edmBindingTarget.getEntityType().getNavigationProperty("OrderType");
            OfbizCsdlEntityType csdlNavigationEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(orderTypeEdmNavigation.getType().getFullQualifiedName());
            Map<String, Object> keyMap = UtilMisc.toMap("orderTypeId", orderTypeId);
            //创建DraftAdministrativeData
            String orderTypeDraftId = Util.generateDraftUUID();
            ProcessorServices.createDraftAdminData(delegator, orderTypeDraftId, sapContextId, csdlNavigationEntityType,
                    keyMap, orderTypeEdmNavigation.getName(), userLogin);

            //创建子对象的Draft
            GenericValue realOrderType = delegator.findOne("OrderType", keyMap, false);
            GenericValue draftValue = delegator.makeValue(csdlNavigationEntityType.getDraftEntityName());
            draftValue.put("isActiveEntity", "N");
            draftValue.put("hasActiveEntity", "N");
            draftValue.put("hasDraftEntity", "Y");
            draftValue.put("draftUUID", orderTypeDraftId);
            draftValue.put("orderTypeId", orderTypeId);
            draftValue.put("description", realOrderType.getString("description"));
            draftValue.create();
        }
        return ofbizEntity;
    }

}
