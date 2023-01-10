package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.OdataParts;
import com.dpbird.odata.handler.DefaultNavigationHandler;
import com.dpbird.odata.handler.NavigationHandler;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityProductHandler extends DefaultNavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                                  Map<String, QueryOption> queryOptions) throws OfbizODataException {
        try {
            Delegator delegator = (Delegator) odataContext.get("delegator");
            List<GenericValue> genericValueList = EntityQuery.use(delegator).from("Product").maxRows(5).queryList();
            List<String> productIds = EntityUtil.getFieldListFromEntityList(genericValueList, "productId", false);
            EntityCondition condition = EntityCondition.makeCondition("productId", EntityOperator.IN, productIds);
            return UtilMisc.toMap("condition", condition);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

}
