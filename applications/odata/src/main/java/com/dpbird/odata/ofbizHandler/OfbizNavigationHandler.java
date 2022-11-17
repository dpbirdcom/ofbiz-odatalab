package com.dpbird.odata.ofbizHandler;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.UriResourceDataInfo;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 如果Navigation没有声明自定义的Handler, 会使用这个缺省的ofbiz查询
 *
 * @date 2022/11/14
 */
public class OfbizNavigationHandler implements NavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                                  EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions, List<UriResourceDataInfo> resourceDataInfos) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        return navigationParam;
    }

    @Override
    public GenericValue createNavigationData(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntityToCreate, Map<String, QueryOption> queryOptions) {
        return null;
    }

    @Override
    public void deleteNavigationData(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity) {

    }

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity) {

    }
}
