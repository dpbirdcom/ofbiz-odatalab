package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.Map;

/**
 * Navigation需要实现的接口
 */
public interface NavigationHandler {
    /**
     * 读取关联实体的数据
     *
     * @param entity                主实体
     * @param edmEntityType         主实体EdmEntityType
     * @param edmNavigationProperty EdmNavigationProperty
     * @param queryOptions          queryOptions
     * @return 关联实体数据
     */
    Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                           EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException;

    /**
     * 获取创建参数
     *
     * @param entity                主实体
     * @param edmEntityType         主实体EdmEntityType
     * @param edmNavigationProperty EdmNavigationProperty
     * @return 创建参数
     */
    Map<String, Object> getInsertParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                       EdmNavigationProperty edmNavigationProperty) throws OfbizODataException;


    /**
     * 获取更新参数
     *
     * @param entity                主实体
     * @param edmEntityType         主实体EdmEntityType
     * @param edmNavigationProperty EdmNavigationProperty
     * @return 更新参数
     */
    Map<String, Object> getUpdateParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                       EdmNavigationProperty edmNavigationProperty) throws OfbizODataException;

    /**
     * 获取删除参数
     *
     * @param entity                主实体
     * @param edmEntityType         主实体EdmEntityType
     * @param edmNavigationProperty EdmNavigationProperty
     * @return 更新参数
     */
    Map<String, Object> getDeleteParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                       EdmNavigationProperty edmNavigationProperty) throws OfbizODataException;


    /**
     * 关联实体数据
     *
     * @param entity                主实体
     * @param bindPrimaryKey        要关联的实体主键
     * @param edmNavigationProperty edmNavigationProperty
     */
    void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                            EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) throws OfbizODataException;

    /**
     * 解除关联实体数据
     *
     * @param entity                主实体
     * @param bindPrimaryKey        要解除的实体主键
     * @param edmNavigationProperty edmNavigationProperty
     */
    void unbindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                            EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) throws OfbizODataException;

}
