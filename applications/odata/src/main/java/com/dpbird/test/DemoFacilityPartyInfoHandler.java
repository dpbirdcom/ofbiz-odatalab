package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.UriResourceDataInfo;
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
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/4
 */
public class DemoFacilityPartyInfoHandler implements NavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                                  Map<String, QueryOption> queryOptions, List<UriResourceDataInfo> resourceDataInfos) throws OfbizODataException {
        Debug.log(">>> resourceDataInfos: " + resourceDataInfos);
        List<String> infoNames = UtilMisc.toList("fromFacility_1","fromFacility_2","fromFacility_3");
        return UtilMisc.toMap("infoNames", infoNames);
    }

    @Override
    public Map<String, Object> getInsertParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<UriResourceDataInfo> uriResourceDataInfos) throws OfbizODataException {
        return null;
    }

    @Override
    public Map<String, Object> getUpdateParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<UriResourceDataInfo> uriResourceDataInfos) throws OfbizODataException {
        return null;
    }

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity) {

    }

    @Override
    public void unbindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity) {

    }
}
