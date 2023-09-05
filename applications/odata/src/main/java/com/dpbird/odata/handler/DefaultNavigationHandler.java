package com.dpbird.odata.handler;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 如果Navigation没有声明自定义的Handler, 会使用这个缺省的Handler
 *
 * @date 2022/11/14
 */
public class DefaultNavigationHandler implements NavigationHandler {

    @Override
    public Map<String, Object> getNavigationParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                                  EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getInsertParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                              EdmNavigationProperty edmNavigationProperty)
            throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getUpdateParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public Map<String, Object> getDeleteParam(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", entity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        return navigationParam;
    }

    @Override
    public void bindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                   EdmNavigationProperty edmNavigationProperty, Map<String, Object> bindPrimaryKey) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");
        Locale locale = (Locale) odataContext.get("locale");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        List<String> relations = relAlias.getRelations();
        if (csdlNavigationProperty.isCollection()) {
            //collection仅支持含有简单中间表的关联 如果中间表主键收集不完整将会报错
            if (relations.size() == 2) {
                ModelRelation relation = modelEntity.getRelation(relations.get(0));
                ModelEntity relModelEntity = delegator.getModelEntity(relation.getRelEntityName());
                Map<String, Object> midFields = new HashMap<>();
                midFields.putAll(bindPrimaryKey);
                midFields.putAll(entity.getKeyMap());
                Map<String, Object> conditionField = relAlias.getRelationsFieldMap().get(relations.get(0));
                if (UtilValidate.isNotEmpty(conditionField)) {
                    midFields.putAll(conditionField);
                }
                if (relModelEntity.getPkFieldNames().contains("fromDate")) {
                    midFields.put("fromDate", UtilDateTime.nowTimestamp());
                }
                midFields.put("userLogin", userLogin);
                String serviceName = Util.getEntityActionService(null, relModelEntity.getEntityName(), "create", delegator);
                try {
                    Map<String, Object> createResult = dispatcher.runSync(serviceName, midFields);
                    if (ServiceUtil.isError(createResult)) {
                        throw new OfbizODataException(ServiceUtil.getErrorMessage(createResult));
                    }
                } catch (GenericServiceException e) {
                    throw new OfbizODataException(e.getMessage());
                }
            }
        } else {
            //非Collection仅支持通过外键直接关联的bindLink
            if (relations.size() == 1) {
                ModelRelation modelRelation = modelEntity.getRelation(relations.get(0));
                Map<String, Object> fkFieldMap = new HashMap<>();
                for (ModelKeyMap relationKeyMap : modelRelation.getKeyMaps()) {
                    String fieldName = relationKeyMap.getFieldName();
                    String relFieldName = relationKeyMap.getRelFieldName();
                    fkFieldMap.put(fieldName, bindPrimaryKey.get(relFieldName));
                }
                OdataProcessorHelper.updateGenericValue(dispatcher, delegator, csdlEntityType.getOfbizEntity(), entity.getKeyMap(),
                        fkFieldMap, csdlEntityType, userLogin, locale);
            }
        }
    }

    @Override
    public void unbindNavigationLink(Map<String, Object> odataContext, OdataOfbizEntity entity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, Map<String, Object> unbindPrimaryKey) throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        Delegator delegator = (Delegator) odataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        Locale locale = (Locale) odataContext.get("locale");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlEntityType navigationEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        try {
            GenericValue genericValue = entity.getGenericValue();
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            ModelRelation modelRelation = modelEntity.getRelation(relAlias.getRelations().get(0));
            if (modelRelation.getType().contains("one")) {
                //将外键置空
                OdataProcessorHelper.removeGenericValueFK(dispatcher, delegator, csdlEntityType.getOfbizEntity(),
                        genericValue.getPrimaryKey(), modelRelation, csdlEntityType, userLogin, locale);
            } else {
                //删除relation实体
                GenericValue navigationGenericValue = delegator.findOne(navigationEntityType.getOfbizEntity(), unbindPrimaryKey, false);
                OdataProcessorHelper.unbindNavigationLink(genericValue, navigationGenericValue, csdlNavigationProperty, dispatcher, userLogin, locale);
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

}
