package com.dpbird.odata.events;

import com.banfftech.common.util.CommonUtils;
import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.*;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelKeyMap;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.GeneralServiceException;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlParameter;
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
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) oDataContext.get("action");
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
                OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty) mainCsdlEntityType.getNavigationProperty(navEntityParts.getUriResource().getSegmentValue());
                EntityTypeRelAlias relAlias = navigationProperty.getRelAlias();
                if (UtilValidate.isNotEmpty(relAlias) && relAlias.getRelations().size() == 1) {
                    ModelRelation modelRelation = relAlias.getRelationsEntity().get(relAlias.getRelations().get(0));
                    for (ModelKeyMap keyMap : modelRelation.getKeyMaps()) {
                        actionParameters.put(keyMap.getFieldName(), mainGenericValue.get(keyMap.getRelFieldName()));
                    }
                }
            }
            for (Map.Entry<String, Object> entry : actionParameters.entrySet()) {
                String key = entry.getKey();
                if (UtilValidate.isEmpty(entry.getValue())) {
                    continue;
                }
//                CsdlProperty property = csdlEntityType.getProperty(key);
//                if (UtilValidate.isNotEmpty(property) && "com.dpbird.Date".equals(property.getType()) && entry.getValue() instanceof GregorianCalendar) {
                if (entry.getValue() instanceof GregorianCalendar) {
                    GregorianCalendar calendar = (GregorianCalendar) entry.getValue();
                    actionParameters.put(entry.getKey(), new Date(calendar.getTime().getTime()));
                }
            }
            actionParameters.put("userLogin", userLogin);
            for (Map.Entry<String, Object> entry : csdlEntityType.getDefaultValueProperties().entrySet()) {
                actionParameters.putIfAbsent(entry.getKey(), Util.parseVariable(entry.getValue(), request));
            }
            //创建Path参数
            Map<String, Object> createdKeys = createRelationOnePath(oDataContext, actionParameters, csdlEntityType);
            actionParameters.putAll(createdKeys);
            //创建主对象
            Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, createService, actionParameters, userLogin, null, null);
            Map<String, Object> result = dispatcher.runSync(createService, validFieldsForService);
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            List<String> primaryKeys = modelEntity.getPkFieldNames();
            if (result.keySet().containsAll(primaryKeys)) {
                Map<String, Object> pkMap = new HashMap<>();
                for (String primaryKey : primaryKeys) {
                    pkMap.put(primaryKey, result.get(primaryKey));
                }
                //创建Path参数
                actionParameters.putAll(result);
                createRelationManyPath(oDataContext, actionParameters, csdlEntityType,  pkMap);

                //Return Entity
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
            for (Map.Entry<String, Object> entry : actionParameters.entrySet()) {
                if (UtilValidate.isEmpty(entry.getValue())) {
                    actionParameters.put(entry.getKey(), null);
                    continue;
                }
//                CsdlProperty property = csdlEntityType.getProperty(entry.getKey());
//                if (UtilValidate.isNotEmpty(property) && "com.dpbird.Date".equals(property.getType()) && entry.getValue() instanceof GregorianCalendar) {
                if (entry.getValue() instanceof GregorianCalendar) {
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

    /**
     * 获取参数中子对象的参数
     */
    private static Map<String, Map<String, Object>> getNavigationParam(Map<String, Object> oDataContext, Map<String, Object> actionParameters) {
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) oDataContext.get("action");
        Map<String, Map<String, Object>> navigationMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : actionParameters.entrySet()) {
            OfbizCsdlParameter csdlActionParameter = (OfbizCsdlParameter) csdlAction.getParameter(entry.getKey());
            if (UtilValidate.isNotEmpty(csdlActionParameter) && UtilValidate.isNotEmpty(csdlActionParameter.getPath())) {
                String path = csdlActionParameter.getPath();
                String[] split = path.split("/");
                String navName = split[0];
                String navParam = split[1];
                Map<String, Object> navMap = navigationMap.get(navName);
                if (UtilValidate.isEmpty(navMap)) {
                    navigationMap.put(navName, UtilMisc.toMap(navParam, entry.getValue()));
                } else {
                    navMap.put(navParam, entry.getValue());
                }
            }
        }
        return navigationMap;
    }


    /**
     * 需要提前创建的对象
     */
    private static Map<String, Object> createRelationOnePath(Map<String, Object> oDataContext, Map<String, Object> actionParameters, OfbizCsdlEntityType csdlEntityType) throws OfbizODataException, GeneralServiceException, GenericServiceException {
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        Map<String, Object> createdKeys = new HashMap<>();
        //获取关联对字段
        Map<String, Map<String, Object>> navigationMap = getNavigationParam(oDataContext, actionParameters);
        //创建对一的Navigation
        for (Map.Entry<String, Map<String, Object>> navMapEntry : navigationMap.entrySet()) {
            String navigationName = navMapEntry.getKey();
            Map<String, Object> navigationParam = navMapEntry.getValue();
            OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationName);
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            String firstRelation = relAlias.getRelations().get(0);
            Map<String, Object> conditionMap = relAlias.getRelationsFieldMap().get(firstRelation);
            if (UtilValidate.isNotEmpty(conditionMap)) {
                navigationParam.putAll(conditionMap);
            }
            ModelRelation modelRelation = relAlias.getRelationsEntity().get(firstRelation);
            if (modelRelation.getType().startsWith("one")) {
                //先创建Navigation 将主键传递给主对象
                String navCreateService = Util.getEntityActionService(null, modelRelation.getRelEntityName(), "create", delegator);
                Map<String, Object> serviceFields = ServiceUtil.setServiceFields(dispatcher, navCreateService, navigationParam, userLogin, null, null);
                Map<String, Object> navResult = dispatcher.runSync(navCreateService, serviceFields);
                createdKeys.putAll(navResult);
            }
        }
        return createdKeys;
    }

    /**
     * 创建对多的关联对象
     */
    private static void createRelationManyPath(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                               OfbizCsdlEntityType csdlEntityType, Map<String, Object> primaryKey) throws OfbizODataException, GeneralServiceException, GenericServiceException {
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        //获取关联对字段
        Map<String, Map<String, Object>> navigationMap = getNavigationParam(oDataContext, actionParameters);
        //创建对多的Navigation
        for (Map.Entry<String, Map<String, Object>> navMapEntry : navigationMap.entrySet()) {
            String navigationName = navMapEntry.getKey();
            Map<String, Object> navigationParam = navMapEntry.getValue();
            navigationParam.putAll(primaryKey);
            OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationName);
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            String firstRelation = relAlias.getRelations().get(0);
            Map<String, Object> conditionMap = relAlias.getRelationsFieldMap().get(firstRelation);
            if (UtilValidate.isNotEmpty(conditionMap)) {
                navigationParam.putAll(conditionMap);
            }
            ModelRelation modelRelation = relAlias.getRelationsEntity().get(firstRelation);
            if (modelRelation.getType().startsWith("many")) {
                String navCreateService = Util.getEntityActionService(null, modelRelation.getRelEntityName(), "create", delegator);
                Map<String, Object> serviceFields = ServiceUtil.setServiceFields(dispatcher, navCreateService, navigationParam, userLogin, null, null);
                dispatcher.runSync(navCreateService, serviceFields);
            }
        }
    }

}
