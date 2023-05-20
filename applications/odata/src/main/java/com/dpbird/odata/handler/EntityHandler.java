package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.uri.queryoption.ApplyOption;
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
     * @return 返回单条数据
     */
    Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey)
            throws OfbizODataException;

    /**
     * 查询数据列表
     *
     * @param odataContext     odataContext
     * @param edmBindingTarget EdmBindingTarget
     * @param queryOptions     queryOptions
     * @param navigationParam  Navigation参数，要根据这个参数做查询
     * @return 返回数据列表
     */
    HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget,
                            Map<String, QueryOption> queryOptions, Map<String, Object> navigationParam) throws OfbizODataException;


    /**
     *
     * @param odataContext odataContext
     * @param edmBindingTarget EdmBindingTarget
     * @param queryOptions queryOptions
     * @param applyCondition 查询范围
     * @return 返回apply数据
     */
    HandlerResults findApply(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget,
                             Map<String, QueryOption> queryOptions, EntityCondition applyCondition) throws OfbizODataException;

    /**
     * 创建实体数据
     *
     * @param entityToWrite 要创建的数据
     * @param odataContext  odataContext
     * @return 返回已创建的数据
     */
    Map<String, Object> create(Entity entityToWrite, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> createParam)
            throws OfbizODataException;

    /**
     * 更新实体数据
     *
     * @param entityToWrite 要更新的数据
     * @param odataContext  odataContext
     * @param updateParam  更新参数
     * @return 返回已更新的数据
     */
    Map<String, Object> update(Map<String, Object> primaryKey, Entity entityToWrite, Map<String, Object> odataContext,
                               EdmBindingTarget edmBindingTarget, Map<String, Object> updateParam)
            throws OfbizODataException;

    /**
     * 删除实体数据
     *
     * @param entityToDelete 要删除的数据
     * @param odataContext   odataContext
     * @param deleteParam   删除参数
     */
    void delete(Entity entityToDelete, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget,
                Map<String, Object> deleteParam) throws OfbizODataException;


}
