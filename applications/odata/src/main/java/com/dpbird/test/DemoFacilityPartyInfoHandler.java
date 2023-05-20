package com.dpbird.test;

import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.DefaultNavigationHandler;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityPartyInfoHandler extends DefaultNavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                                  Map<String, QueryOption> queryOptions) throws OfbizODataException {
        List<String> infoNames = UtilMisc.toList("fromFacility_1","fromFacility_2","fromFacility_3");
        return UtilMisc.toMap("infoNames", infoNames);
    }


}
