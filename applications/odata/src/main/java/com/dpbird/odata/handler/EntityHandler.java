package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.Map;

/**
 * 自定义查询数据需要实现的接口
 */
public interface EntityHandler {

    /**
     * 查询单条数据
     *
     * @param odataContext     odataContext
     * @param edmBindingTarget EdmBindingTarget
     * @param primaryKey       主键
     * @return 返回单挑数据
     */
    Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey)
            throws OfbizODataException;

    /**
     * 查询数据列表
     *
     * @param odataContext     odataContext
     * @param edmBindingTarget EdmBindingTarget
     * @param queryOptions queryOptions
     * @param navigationParam Navigation参数，要根据这个参数做查询
     * @return 返回数据列表
     */
    HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget,
                            Map<String, QueryOption> queryOptions, Map<String, Object> navigationParam) throws OfbizODataException;
}
