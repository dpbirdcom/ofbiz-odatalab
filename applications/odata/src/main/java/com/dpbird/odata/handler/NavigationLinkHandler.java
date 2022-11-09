package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.Map;

/**
 * 自定义HandlerNavigation查询参数接口
 * @date 2022/11/8
 */
public interface NavigationLinkHandler {

    /**
     * 自定义Navigation需要实现这个接口来定义参数
     *
     * @param entity 主实体
     * @param edmNavigation EdmNavigationProperty
     * @return 返回自定义的参数
     */
    Map<String, Object> getHandlerParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmNavigationProperty edmNavigation) throws OfbizODataException;
}
