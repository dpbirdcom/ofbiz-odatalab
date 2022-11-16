package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * Navigation需要实现的接口
 */
public interface NavigationHandler {
    /**
     * 读取关联实体的数据
     *
     * @param entity 主实体
     * @param queryOptions  queryOptions
     * @return 关联实体数据
     */
    Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                           EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException;

    /**
     * 创建Navigation的数据
     *
     * @param entity               主实体
     * @param nestedEntityToCreate 要创建的实体
     * @param queryOptions         queryOptions
     * @return 返回创建的数据
     */
    GenericValue createNavigationData(Map<String, Object> odataContext, OdataOfbizEntity entity,
                                      OdataOfbizEntity nestedEntityToCreate, Map<String, QueryOption> queryOptions);

    /**
     * 删除Navigation的数据
     *
     * @param entity       主实体
     * @param nestedEntity 要删除的关联实体
     */
    void deleteNavigationData(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity);

    /**
     * 关联实体数据
     *
     * @param entity       主实体
     * @param nestedEntity 要关联的实体
     */
    void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, OdataOfbizEntity nestedEntity);


}
