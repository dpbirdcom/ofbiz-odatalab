package com.dpbird.odata.events;

import com.banfftech.common.util.CommonUtils;
import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;

import javax.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author scy
 * @date 2023/10/10
 */
public class ActionEvents {
    /**
     * Create entity
     */
    public static Object createEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        List<OdataParts> odataParts = UtilGenerics.checkList(oDataContext.get("odataParts"));
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        HttpServletRequest request = (HttpServletRequest) oDataContext.get("httpServletRequest");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        //get service
        String createService = Util.getEntityActionService(csdlEntityType, csdlEntityType.getOfbizEntity(), "create", delegator);
        try {
            if (UtilValidate.isNotEmpty(odataParts) && odataParts.size() > 1) {
                //多段式创建 向前一段获取relation key
                OdataParts mainEntityParts = odataParts.get(odataParts.size() - 2);
                OdataParts navEntityParts = odataParts.get(odataParts.size() - 1);
                OdataOfbizEntity mainEntity = (OdataOfbizEntity) mainEntityParts.getEntityData();
                GenericValue mainGenericValue = mainEntity.getGenericValue();
                OfbizCsdlEntityType mainCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(mainEntityParts.getEdmEntityType().getFullQualifiedName());
                OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty)  mainCsdlEntityType.getNavigationProperty(navEntityParts.getUriResource().getSegmentValue());
                EntityTypeRelAlias relAlias = navigationProperty.getRelAlias();
                if (UtilValidate.isNotEmpty(relAlias) && relAlias.getRelations().size() == 1) {
                    ModelRelation modelRelation = relAlias.getRelationsEntity().get(relAlias.getRelations().get(0));
                    for (ModelKeyMap keyMap : modelRelation.getKeyMaps()) {
                        actionParameters.put(keyMap.getFieldName(), mainGenericValue.get(keyMap.getRelFieldName()));
                    }
                }
            }
            for (Map.Entry<String, Object> entry : actionParameters.entrySet()){
                String key = entry.getKey();
                if (UtilValidate.isEmpty(entry.getValue())){
                    continue;
                }
                CsdlProperty property = csdlEntityType.getProperty(key);
                if (UtilValidate.isNotEmpty(property) && "com.dpbird.Date".equals(property.getType()) && entry.getValue() instanceof GregorianCalendar) {
                    GregorianCalendar calendar = (GregorianCalendar) entry.getValue();
                    actionParameters.put(entry.getKey(), new Date(calendar.getTime().getTime()));
                }
            }
            actionParameters.put("userLogin", userLogin);
            for (Map.Entry<String, Object> entry : csdlEntityType.getDefaultValueProperties().entrySet()) {
                actionParameters.putIfAbsent(entry.getKey(), Util.parseVariable(entry.getValue(), request));
            }
            Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, createService, actionParameters, userLogin, null, null);
            Map<String, Object> result = dispatcher.runSync(createService, validFieldsForService);
            //Return Entity
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            List<String> primaryKeys = modelEntity.getPkFieldNames();
            if (result.keySet().containsAll(primaryKeys)) {
                Map<String, Object> pkMap = new HashMap<>();
                for (String primaryKey : primaryKeys) {
                    pkMap.put(primaryKey, result.get(primaryKey));
                }
                return delegator.findOne(csdlEntityType.getOfbizEntity(), pkMap, false);
            }
        } catch (GeneralException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return null;
    }

    /**
     * Update entity
     */
    public static Object updateEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        OdataOfbizEntity boundEntity = Util.getBoundEntity(actionParameters);
        if (UtilValidate.isEmpty(boundEntity) || UtilValidate.isEmpty(boundEntity.getGenericValue())) {
            throw new OfbizODataException("The bound data is empty");
        }
        GenericValue genericValue = boundEntity.getGenericValue();
        actionParameters.putAll(genericValue.getPrimaryKey());
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        //get service
        String createService = Util.getEntityActionService(csdlEntityType, csdlEntityType.getOfbizEntity(), "update", delegator);
        try {
            for (Map.Entry<String, Object> entry : actionParameters.entrySet()){
                if (UtilValidate.isEmpty(entry.getValue())) {
                    actionParameters.put(entry.getKey(), null);
                    continue;
                }
                CsdlProperty property = csdlEntityType.getProperty(entry.getKey());
                if (UtilValidate.isNotEmpty(property) && "com.dpbird.Date".equals(property.getType()) && entry.getValue() instanceof GregorianCalendar) {
                    GregorianCalendar calendar = (GregorianCalendar) entry.getValue();
                    actionParameters.put(entry.getKey(), new Date(calendar.getTime().getTime()));
                }
            }
            actionParameters.put("userLogin", userLogin);

            Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, createService, actionParameters, userLogin, null, null);
            Map<String, Object> result = dispatcher.runSync(createService, validFieldsForService);
            //Return Entity
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            List<String> primaryKeys = modelEntity.getPkFieldNames();
            if (result.keySet().containsAll(primaryKeys)) {
                Map<String, Object> pkMap = new HashMap<>();
                for (String primaryKey : primaryKeys) {
                    pkMap.put(primaryKey, result.get(primaryKey));
                }
                return delegator.findOne(csdlEntityType.getOfbizEntity(), pkMap, false);
            }
        } catch (GeneralException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return null;
    }



    /**
     * Create navigation entity
     */
    public static Object createNavigation(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        List<OdataParts> odataPartsList = UtilGenerics.toList(oDataContext.get("odataParts"));
        if (UtilValidate.isNotEmpty(odataPartsList) || odataPartsList.size() >= 2) {
            OdataParts mainOdataParts = odataPartsList.get(odataPartsList.size() - 2);
            OdataParts lastOdataParts = odataPartsList.get(odataPartsList.size() - 1);
            EdmEntityType edmEntityType = mainOdataParts.getEdmEntityType();
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(lastOdataParts.getUriResource().getSegmentValue());
            //添加对象之间的外键字段 和Navigation中定义的条件
            Map<String, Object> entityFields = Util.entityToMap((Entity) mainOdataParts.getEntityData());
            Map<String, Object> relatedFieldMap = Util.getRelatedFieldMap(delegator, csdlEntityType.getOfbizEntity(), csdlNavigationProperty, entityFields, edmProvider);
            Map<String, Object> relatedConditionMap = Util.getRelatedConditionMap(csdlNavigationProperty);
            if (UtilValidate.isNotEmpty(relatedFieldMap)) {
                actionParameters.putAll(relatedFieldMap);
            }
            if (UtilValidate.isNotEmpty(relatedConditionMap)) {
                actionParameters.putAll(relatedConditionMap);
            }
        }
        return createEntity(oDataContext, actionParameters, edmBindingTarget);
    }

}
