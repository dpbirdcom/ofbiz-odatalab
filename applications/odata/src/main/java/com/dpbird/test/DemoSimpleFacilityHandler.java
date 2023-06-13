package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.handler.DefaultEntityHandler;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/7
 */
public class DemoSimpleFacilityHandler extends DefaultEntityHandler {
//    @Override
//    public Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey) throws OfbizODataException {
//        try {
//            Delegator delegator = (Delegator) odataContext.get("delegator");
//            return EntityQuery.use(delegator).from("Facility").where(primaryKey).queryOne();
//        } catch (GenericEntityException e) {
//            throw new OfbizODataException(e.getMessage());
//        }
//    }


    @Override
    public HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey,
                                   Map<String, QueryOption> queryOptions, Map<String, Object> navigationParam) throws OfbizODataException {
        try {
            Delegator delegator = (Delegator) odataContext.get("delegator");
            List<GenericValue> genericValueList = EntityQuery.use(delegator).from("Facility").queryList();
            return new HandlerResults(genericValueList.size(), genericValueList);
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }


}
