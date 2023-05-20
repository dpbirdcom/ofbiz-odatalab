package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.DefaultEntityHandler;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/7
 */
public class DemoTelecomNumberHandler extends DefaultEntityHandler {
    @Override
    public Map<String, Object> create(Entity entityToWrite, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> createParam) throws OfbizODataException {
        try {
            LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
            Delegator delegator = (Delegator) odataContext.get("delegator");
            GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
            OdataOfbizEntity entity = (OdataOfbizEntity) createParam.get("entity");
            String contactMechId = (String) entity.getPropertyValue("contactMechId");
            Map<String, Object> serviceMap = Util.entityToMap(entityToWrite);
            serviceMap.put("userLogin", userLogin);
            serviceMap.put("contactMechId", contactMechId);
            GenericValue tel = delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), true);
            Map<String, Object> result;
            if (UtilValidate.isEmpty(tel)) {
                result = dispatcher.runSync("dpbird.createDerivedTelecomNumber", serviceMap);
            } else {
                result = dispatcher.runSync("dpbird.updateDerivedTelecomNumber", serviceMap);
            }
            if (ServiceUtil.isSuccess(result)) {
                return delegator.findOne("TelecomNumber", UtilMisc.toMap("contactMechId", contactMechId), true);
            }
        } catch (GenericServiceException | GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return null;
    }
}
