package com.dpbird.test;

import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityPartyHandler implements NavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                                  EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions, List<OdataParts> resourceDataInfos) throws OfbizODataException {
        try {
            Debug.log(">>> resourceDataInfos: " + resourceDataInfos);
            Delegator delegator = (Delegator) odataContext.get("delegator");
            GenericValue party = EntityQuery.use(delegator).from("Party").queryFirst();
            EntityCondition condition = EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, party.getString("partyId"));
            return UtilMisc.toMap("condition", condition);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getInsertParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts) throws OfbizODataException {
        return null;
    }

    @Override
    public Map<String, Object> getUpdateParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts) throws OfbizODataException {
        return null;
    }

    @Override
    public Map<String, Object> getDeleteParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts) throws OfbizODataException {
        return null;
    }

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) {

    }

    @Override
    public void unbindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) throws OfbizODataException {

    }


}
