package com.dpbird.odata.handler;

import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OdataProcessorHelper;
import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.LocalDispatcher;
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
public class DefaultNavigationHandler implements NavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                                  EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions, List<OdataParts> resourceDataInfos) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getInsertParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                              EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts)
            throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getUpdateParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getDeleteParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, List<OdataParts> odataParts) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) throws OfbizODataException {
        //DefaultHandler只能处理非Collection并且通过外键直接关联的bindLink
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        if(relAlias.getRelations().size() == 1) {
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            ModelRelation modelRelation = modelEntity.getRelation(relAlias.getRelations().get(0));
            Map<String, Object> fkFieldMap = new HashMap<>();
            for (ModelKeyMap relationKeyMap : modelRelation.getKeyMaps()) {
                String fieldName = relationKeyMap.getFieldName();
                String relFieldName = relationKeyMap.getRelFieldName();
                fkFieldMap.put(fieldName, bindPrimaryKey.get(relFieldName));
            }
            OdataProcessorHelper.updateGenericValue(dispatcher, delegator, csdlEntityType.getOfbizEntity(), entity.getKeyMap(), fkFieldMap, userLogin);
        }
    }

}
