package com.dpbird.test;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.NavigationLinkHandler;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityPartyLink implements NavigationLinkHandler {
    @Override
    public Map<String, Object> getHandlerParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmNavigationProperty edmNavigation) {
        Map<String, Object> param = new HashMap<>();
        Object ownerPartyId = entity.getPropertyValue("ownerPartyId");
        param.put("partyId", ownerPartyId);
        return param;
    }
}
