package com.dpbird.odata.handler;

import java.util.List;
import java.util.Map;

import com.dpbird.odata.OfbizODataException;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

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
    Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget,
                                Map<String, Object> primaryKey) throws OfbizODataException;

    /**
     * 查询数据列表
     *
     * @param odataContext     odataContext
     * @param edmBindingTarget EdmBindingTarget
     * @return 返回数据列表
     */
    List<? extends Map<String, Object>> findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget)
                                throws OfbizODataException;
}
