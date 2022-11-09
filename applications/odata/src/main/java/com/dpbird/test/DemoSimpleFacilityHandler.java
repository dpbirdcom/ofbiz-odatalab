package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.handler.EntityHandler;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/7
 */
public class DemoSimpleFacilityHandler implements EntityHandler {
    @Override
    public Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey) throws OfbizODataException {
        try {
            Delegator delegator = (Delegator) odataContext.get("delegator");
            return EntityQuery.use(delegator).from("Facility").where(primaryKey).queryOne();
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public List<GenericValue> findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        try {
            return EntityQuery.use(delegator).from("Facility").queryList();
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }
}
