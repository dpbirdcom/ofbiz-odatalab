package com.dpbird.odata.handler;

import com.dpbird.odata.OdataProcessorHelper;
import com.dpbird.odata.OdataReader;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 如果Entity没有声明自定义的Handler, 会使用这个缺省的ofbiz查询
 *
 * @date 2022/11/14
 */
public class DefaultEntityHandler implements EntityHandler {

    @Override
    public Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey) throws OfbizODataException {
        return OdataProcessorHelper.readEntityData(odataContext, edmBindingTarget, primaryKey);
    }

    @Override
    public HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, QueryOption> queryOptions,
                                   Map<String, Object> navigationParam) throws OfbizODataException {
        //普通的查询
        if (UtilValidate.isEmpty(navigationParam)) {
            OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmBindingTarget", edmBindingTarget));
            return reader.ofbizFindList(null);
        }

        //Navigation查询 根据参数去做查询
        EntityCondition primaryKeyCond = null;
        if (navigationParam.get("primaryKey") != null) {
            Map<String, Object> primaryKey = (Map<String, Object>) navigationParam.get("primaryKey");
            primaryKeyCond = EntityCondition.makeCondition(primaryKey);
        }
        HandlerResults handlerResults = new HandlerResults();
        if (navigationParam.containsKey("condition")) {
            EntityCondition entityCondition = (EntityCondition) navigationParam.get("condition");
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
            entityCondition = Util.appendCondition(entityCondition, primaryKeyCond);
            OdataReader reader = new OdataReader(odataContext, queryOptions, UtilMisc.toMap("edmEntityType", edmNavigationProperty.getType()));
            handlerResults = reader.ofbizFindList(entityCondition);
        } else if (navigationParam.containsKey("entity")) {
            OdataOfbizEntity entity = (OdataOfbizEntity) navigationParam.get("entity");
            EdmEntityType edmEntityType = (EdmEntityType) navigationParam.get("edmEntityType");
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
            Map<String, Object> edmParam = UtilMisc.toMap("edmEntityType", edmEntityType);
            OdataReader reader = new OdataReader(odataContext, new HashMap<>(), edmParam);
            List<GenericValue> relatedList = reader.findRelatedGenericValue(entity, edmNavigationProperty, primaryKeyCond);
            if (UtilValidate.isNotEmpty(relatedList)) {
                handlerResults = new HandlerResults(relatedList.size(), relatedList);
            }
        }
        return handlerResults;
    }

    @Override
    public Map<String, Object> create(Entity entityToWrite, Map<String, Object> odataContext) {
        return null;
    }

    @Override
    public Map<String, Object> update(Entity entityToWrite, Map<String, Object> odataContext) {
        return null;
    }

    @Override
    public void delete(Entity entityToDelete, Map<String, Object> odataContext) {

    }
}
