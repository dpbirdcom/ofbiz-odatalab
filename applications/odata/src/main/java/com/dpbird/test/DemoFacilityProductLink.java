package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.NavigationLinkHandler;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;

import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityProductLink implements NavigationLinkHandler {

    @Override
    public Map<String, Object> getHandlerParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmNavigationProperty edmNavigation) throws OfbizODataException {
        try {
            Delegator delegator = (Delegator) odataContext.get("delegator");
            List<GenericValue> products = EntityQuery.use(delegator).from("Product").queryPagedList(0, 5).getData();
            List<Object> productIds = EntityUtil.getFieldListFromEntityList(products, "productId", false);
            return UtilMisc.toMap("productIds", productIds);
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }
}
