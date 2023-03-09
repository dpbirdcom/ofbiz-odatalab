package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.List;
import java.util.Map;

/**
 * Draft模式需要实现的接口
 *
 * @date 2023/3/2
 */
public interface DraftHandler {
    /**
     * Draft查询单条数据
     *
     * @param keyMap          主键
     * @param edmEntityType   edmEntityType
     * @param navigationParam 多段式参数
     * @return Result entity
     */
    GenericValue finOne(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> keyMap, Map<String, Object> navigationParam) throws OfbizODataException;

    /**
     * Draft查询数据列表 应该只有关联查询会进这里
     *
     * @param entity                mainEntity
     * @param edmNavigationProperty edmNavigationProperty
     * @return Result entities
     */
    List<GenericValue> findList(Map<String, Object> odataContext, Entity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                Map<String, QueryOption> queryOptions) throws OfbizODataException;

    /**
     * 更新draft实体数据
     *
     * @param keyMap         primaryKey
     * @param toUpdateFields 所有要写入的字段
     * @return Updated entity
     */
    GenericValue updateEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> keyMap,
                              Map<String, Object> toUpdateFields) throws OfbizODataException;

    /**
     * 创建一个NavigationDraft
     *
     * @param mainEntity            主实体
     * @param toCreateFields        所有要写入的字段
     * @param edmNavigationProperty edmNavigationProperty
     * @return Created entity
     */
    GenericValue createEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Entity mainEntity, Map<String, Object> toCreateFields,
                              EdmNavigationProperty edmNavigationProperty) throws OfbizODataException;

    /**
     * 将Draft数据标记为已删除
     *
     * @param keyMap primaryKey
     */
    void deleteEntity(Map<String, Object> odataContext, EdmEntityType edmEntityType, Map<String, Object> keyMap) throws OfbizODataException;


}
