package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlComplexType;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ModelService;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;

public class ActionProcessor extends OfbizOdataWriter {
    public ActionProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    public void processActionVoidMethod(HttpServletRequest httpServletRequest, String classMethod, Map<String, Parameter> actionParameters)
            throws ODataException {
        Debug.logInfo("----------------------------------------------------- in storage.processActionComplexMethod", module);
        this.processActionPrimitiveMethod(httpServletRequest, classMethod, actionParameters);
    }

    public Property processActionPrimitiveMethod(HttpServletRequest httpServletRequest, String classMethod,
                                                 Map<String, Parameter> actionParameters)
            throws ODataException {
        Map<String, Object> parametersMap = Util.parametersToMap(actionParameters);
        Object object = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, classMethod, parametersMap, null);

        //让返回的Property适应Primitive和List<Primitive>
        ValueType valueType = object instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, object);
    }

    public OFbizEntityActionResult processBoundActionEntity(UriResourceAction action, Map<String, Parameter> parameters,
                                                            UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        EdmAction edmAction = action.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(parameters);

        OFbizEntityActionResult result = null;
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundActionEntity(edmAction, csdlAction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundActionEntity(edmAction, csdlAction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            OdataOfbizEntity entity = (OdataOfbizEntity) result.getEntity();
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity,
                    (EdmEntityType) edmAction.getReturnType().getType());
        }
        return result;
    }

    private OFbizEntityActionResult entitySetBoundActionEntity(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                                               Map<String, Object> paramMap,
                                                               UriResourceEntitySet boundEntity,
                                                               UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        Entity entity;
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(boundEntity.getEntityType().getFullQualifiedName());
        Object boundObject = csdlEntityType;
        String componentName = Util.getRequestComponentName(httpServletRequest);
        String componentPath = Util.getRequestComponentPath(httpServletRequest, componentName);

        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        if (UtilValidate.isNotEmpty(keyParams)) {
            boundObject = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                    boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        }
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        boolean needSemanticFields = true;
        paramMap.put("sapContextId", odataContext.get("sapContextId")); //TODO: 最好让paramMap保持干净，sapContextId要从另外渠道传入
        Object actionMethodResult;
        if (csdlAction.isEntityAction()) {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } else {
            try {
                EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
                actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                        csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
            } catch (Exception e) {
                if (boundObject == null) {
                    throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + boundEntity.getSegmentValue());
                }
                actionMethodResult = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest,
                        csdlAction.getOfbizMethod(), paramMap, boundObject);
            }
        }
        if (actionMethodResult instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmAction.getReturnType().getType(), (GenericValue) actionMethodResult, locale);
        } else if (actionMethodResult instanceof Entity) {
            entity = (Entity) actionMethodResult;
            needSemanticFields = false;
        } else { // TODO: 目前硬编码ShoppingCart
            entity = this.objectToEntity(csdlAction.getReturnType().getTypeFQN(), actionMethodResult);
        }
        if (entity != null && needSemanticFields) {
            appendSemanticFields(csdlAction.getReturnType().getTypeFQN(), UtilMisc.toList(entity));
        }
        result.setEntity(entity);
        return result;
    }

    private Object entitySetBoundActionPrimitive(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                                 Map<String, Object> paramMap,
                                                 UriResourceEntitySet boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }

        Object callMethodResult;
        try {
            callMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, boundEntity.getEntitySet(), edmNavigationProperty, keyMap, navKeyMap);
        } catch (OfbizODataException e) {
            if (originGenericValue == null)
                throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + boundEntity.getSegmentValue());
            callMethodResult = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                    paramMap, originGenericValue);
        }
        return callMethodResult;
    }

    //boundEntitySet的Action 返回执行结果
    private Object entitySetBoundActionEntityCollection(OfbizCsdlAction csdlAction, Map<String, Object> paramMap,
                                                        UriResourceEntitySet boundEntity,
                                                        UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        GenericValue originGenericValue = null;
        if (UtilValidate.isNotEmpty(keyParams)) {
            originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                    boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        }
        if (csdlAction.isEntityAction()) {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            return OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } else {
            try {
                EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
                return OdataProcessorHelper.callFunctionActionMethod(odataContext,
                        csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
            } catch (Exception e) {
                if (originGenericValue == null)
                    throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + boundEntity.getSegmentValue());
                return OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                        paramMap, originGenericValue);
            }
        }
    }

    //boundSingleton的Action 返回执行结果
    private Object entitySingletonBoundActionEntityCollection(OfbizCsdlAction csdlAction, Map<String, Object> paramMap,
                                                              UriResourceSingleton edmSingleton, UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton.getSingleton());
        Object actionMethodResult = null;
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmSingleton.getSingleton(), edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e) {
            actionMethodResult = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest,
                    csdlAction.getOfbizMethod(), paramMap, boundObject);
        }
        return actionMethodResult;
    }

    private OFbizEntityActionResult singletonBoundActionEntity(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                                               Map<String, Object> paramMap,
                                                               UriResourceSingleton boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        Entity entity = null;
        Object actionMethodResult = null;
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e) {
            actionMethodResult = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest,
                    csdlAction.getOfbizMethod(), paramMap, boundObject);
        }
        String componentName = Util.getRequestComponentName(httpServletRequest);
        String componentPath = Util.getRequestComponentPath(httpServletRequest, componentName);
        if (actionMethodResult instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmAction.getReturnType().getType(), (GenericValue) actionMethodResult, locale);
        } else {
            entity = this.objectToEntity(csdlAction.getReturnType().getTypeFQN(), actionMethodResult);
        }
        if (entity != null) {
            appendSemanticFields(csdlAction.getReturnType().getTypeFQN(), UtilMisc.toList(entity));
        }
        result.setEntity(entity);
        return result;

    }

    private Object singletonBoundActionPrimitive(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                                 Map<String, Object> paramMap,
                                                 UriResourceSingleton boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        Object result = null;
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest,
                    csdlAction.getOfbizMethod(), paramMap, boundObject);
        }
        return result;
    }

    public void processActionVoid(String actionName, Map<String, Parameter> actionParameters)
            throws ODataException {
        runServiceForAction(actionName, actionParameters, null, null, null);
    }

    private Map<String, Object> runServiceForAction(String actionName, Map<String, Parameter> actionParameters,
                                                    String returnName, String boundName, Object boundObj)
            throws ODataException {
        ModelService modelService = null;
        try {
            modelService = dispatcher.getDispatchContext().getModelService(actionName);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
        Map<String, Object> serviceParameters = prepareServiceParameters(modelService, actionName, actionParameters, boundName, boundObj);
        return runService(modelService, serviceParameters, returnName);
    }

    private Map<String, Object> prepareServiceParameters(ModelService modelService,
                                                         String actionName, Map<String, Parameter> actionParameters, String boundName, Object boundObj)
            throws ODataException {
        Set<String> actionParameterNames = actionParameters.keySet();
        Map<String, Object> serviceParameters = new HashMap<String, Object>();
        if (boundName != null && boundObj != null) {
            serviceParameters.put(boundName, boundObj);
        }
        for (String actionParameterName : actionParameterNames) {
            Parameter parameter = actionParameters.get(actionParameterName);
            Object value = parameter.getValue();
            if (parameter.isEntity()) {
                Entity entityParameter = parameter.asEntity();
                String entityType = entityParameter.getType();
                GenericValue genericValue = Util.entityToGenericValue(delegator, entityParameter);
                serviceParameters.put(actionParameterName, genericValue);
                continue;
            }
            if (parameter.isComplex() && UtilValidate.isNotEmpty(parameter.getValue())) {
                ComplexValue entityParameter = parameter.asComplex();
                if (entityParameter != null) {
                    String ofbizTypeFullName = entityParameter.getTypeName();
                    OfbizCsdlComplexType csdlComplexType = (OfbizCsdlComplexType) edmProvider.getComplexType(new FullQualifiedName(ofbizTypeFullName));
                    String ofbizTypeName = entityParameter.getTypeName().substring(
                            entityParameter.getTypeName().lastIndexOf('.') + 1);
                    String innerClassName = csdlComplexType.getOfbizClass();
                    serviceParameters.put(actionParameterName, complexValueToObject(entityParameter, innerClassName));
                } else {
                    List<ComplexValue> entityParameterList = (List<ComplexValue>) parameter.asCollection();
                    List<Object> serviceParamList = new ArrayList<>();
                    for (ComplexValue rowComplex : entityParameterList) {
                        String ofbizTypeFullName = rowComplex.getTypeName();
                        OfbizCsdlComplexType csdlComplexType = (OfbizCsdlComplexType) edmProvider.getComplexType(new FullQualifiedName(ofbizTypeFullName));
                        String ofbizTypeName = rowComplex.getTypeName().substring(
                                rowComplex.getTypeName().lastIndexOf('.') + 1);
                        String innerClassName = csdlComplexType.getOfbizClass();
                        serviceParamList.add(complexValueToObject(rowComplex, innerClassName));
                    }
                    serviceParameters.put(actionParameterName, serviceParamList);
                }
                continue;
            }
            if (parameter.isCollection()) {
                List values = parameter.asCollection();
                serviceParameters.put(actionParameterName, values);
            } else {
                String edmType = parameter.getType();
                if (UtilValidate.isEmpty(value)) {
                    serviceParameters.put(actionParameterName, value);
                } else {
                    if ("Timestamp".equals(edmType) || "java.sql.Timestamp".equals(edmType)
                            || "Edm.DateTimeOffset".equals(edmType)) {
                        serviceParameters.put(actionParameterName, value);
                    } else if ("Edm.Double".equals(edmType)) {
                        serviceParameters.put(actionParameterName, new BigDecimal((Double) value));
                    } else if ("Edm.Int64".equals(edmType)) {
                        serviceParameters.put(actionParameterName, Long.parseLong(value.toString()));
                    } else {
                        serviceParameters.put(actionParameterName, value);
                    }
                }
            }
        }
        serviceParameters.put("userLogin", userLogin);
        if (!validateModelServiceParams(modelService, serviceParameters)) {
            ServiceFixValidation serviceFixValidation = OfbizMapOdata.SERVICE_FIX_VALIDATION.get(actionName);
            if (null != serviceFixValidation) {
                serviceParameters = completionFields(serviceFixValidation.getLeads(), serviceParameters,
                        serviceFixValidation.getNeedEntityParams(), serviceFixValidation.getEntityName());
            }
        }
        return serviceParameters;
    }

    private Map<String, Object> completionFields(Map<String, Object> fieldLeadsMap, Map<String, Object> returnMap,
                                                 Map<String, Object> needEntityParams, String entityName) throws ODataException {
        Map<String, Object> queryMap = new HashMap<>();
        for (String fieldName : queryMap.keySet()) {
            queryMap.put(fieldName, queryMap.get(fieldName));
        }
        queryMap.putAll(fieldLeadsMap);
        for (String fieldName : queryMap.keySet()) {
            String findField = (String) queryMap.get(fieldName);
            queryMap.remove(fieldName);
            queryMap.put(findField, returnMap.get(fieldName));
        }
        GenericValue queryEntity = null;
        try {
            queryEntity = EntityQuery.use(delegator).from(entityName).where(queryMap).queryFirst();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
        if (null == queryEntity) {
            return returnMap;
        }
        for (String fieldName : needEntityParams.keySet()) {
            if (UtilValidate.isEmpty(returnMap.get(fieldName))) {
                returnMap.put(fieldName, queryEntity.get(needEntityParams.get(fieldName)));
            }
        }
        return returnMap;
    }

    private List<Entity> appendSemanticFields(FullQualifiedName fqn, List<Entity> entityList) throws OfbizODataException {
        OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(fqn);
        String handlerClass = ofbizCsdlEntityType.getHandlerClass();
        Debug.logInfo("start handling semantic fields with groovy", module);
        if (handlerClass != null) { // TODO: 今后要用存在数据库中的groovy代码来处理数据
            entityList = procEntityListWithHandler(handlerClass, entityList);
        }
        return entityList;
    }

    private List<Entity> procEntityListWithHandler(String handlerClass, List<Entity> entityList) throws OfbizODataException {
        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
        try {
            entityList = groovyHelper.generateEntityListFields(handlerClass, httpServletRequest, entityList, (SelectOption) queryOptions.get("selectOption"));
        } catch (MissingMethodExceptionNoStack e) {
            Debug.logInfo(e.getMessage(), module);
        }
        return entityList;
    }

    public Property processActionPrimitive(String actionName,
                                           Map<String, Parameter> actionParameters, String returnName)
            throws ODataException {
        Map<String, Object> outParam = runServiceForAction(actionName, actionParameters, returnName, null, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            Object outValue = outParam.get(outParamName);
            return new Property(null, outParamName, ValueType.PRIMITIVE, outValue);
        }
        return null;
    }

    public Property processActionPrimitiveCollection(String actionName,
                                                     Map<String, Parameter> actionParameters, String returnName)
            throws ODataException {
        Map<String, Object> outParam = runServiceForAction(actionName, actionParameters, returnName, null, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            List outList = (List) outParam.get(outParamName);
            return new Property(null, outParamName, ValueType.COLLECTION_PRIMITIVE, outList);
        }
        return null;
    }

    public Property processActionComplex(UriResourceAction uriResourceAction, Map<String, Parameter> actionParameters,
                                         String returnName) throws ODataException {
        Debug.logInfo("----------------------------------------------------- in storage.processActionComplex", module);
        String actionName = "";
        if (null != uriResourceAction.getActionImport()) {
            actionName = uriResourceAction.getActionImport().getName(); // actionName就是ofbiz的serviceName
        } else {
            actionName = uriResourceAction.getAction().getName(); // actionName就是ofbiz的serviceName
        }
        Map<String, Object> outParam = runServiceForAction(actionName, actionParameters, returnName, null, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            Object outValue = outParam.get(outParamName);
            ComplexValue complexValue = objectToComplexValue(outValue);
            return new Property(null, outParamName, ValueType.COMPLEX, complexValue);
        }
        return null;
    }

    public Property processActionComplexMethod(HttpServletRequest httpServletRequest, String classMethod,
                                               Map<String, Parameter> actionParameters)
            throws ODataException {
        Debug.logInfo("----------------------------------------------------- in storage.processActionComplexMethod", module);
        return this.processActionPrimitiveMethod(httpServletRequest, classMethod, actionParameters);
    }

    public Property processBoundActionPrimitive(UriResourceAction action, Map<String, Parameter> parameters,
                                                UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        EdmAction edmAction = action.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(parameters);

        Object result = null;
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundActionPrimitive(edmAction, csdlAction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundActionPrimitive(edmAction, csdlAction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        //区分一下返回的是否是Collection
        ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, result);
    }

    /**
     * 对BoundAction返回EntityCollection的处理
     *
     * @param action
     * @param parameters
     * @param boundEntity
     * @return
     * @throws OfbizODataException
     */
    public Object processBoundActionEntityCollection(UriResourceAction action, Map<String, Parameter> parameters,
                                                     UriResourcePartTyped boundEntity,
                                                     UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        EdmAction edmAction = action.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(parameters);

        //分别处理BoundEntitySet和BoundSingleton
        Object result = null;
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundActionEntityCollection(csdlAction, paramMap, (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = entitySingletonBoundActionEntityCollection(csdlAction, paramMap, (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        return result;
    }

    public Object processImportActionEntityCollection(UriResourceAction uriResourceAction,
                                                      Map<String, Parameter> actionParameters) throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);

        return OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                paramMap, null);
    }

    public void processBoundActionVoid(UriResourceAction action, Map<String, Parameter> parameters,
                                       UriResourcePartTyped boundEntity,
                                       UriResourceNavigation uriResourceNavigation) throws ODataException {
        EdmAction edmAction = action.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(parameters);

        if (boundEntity instanceof UriResourceEntitySet) {
            entitySetBoundActionVoid(edmAction, csdlAction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            singletonBoundActionVoid(edmAction, csdlAction, paramMap,
                    (UriResourceSingleton) boundEntity);
        }
    }

    private void singletonBoundActionVoid(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                          Map<String, Object> paramMap, UriResourceSingleton boundEntity) throws ODataException {
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        OdataProcessorHelper.processFunctionActionMethod(httpServletRequest,
                csdlAction.getOfbizMethod(), paramMap, boundObject);
    }

    private void entitySetBoundActionVoid(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                          Map<String, Object> paramMap, UriResourceEntitySet boundEntity,
                                          UriResourceNavigation uriResourceNavigation) throws ODataException {
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        if (csdlAction.isEntityAction()) {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } else {
            try {
                EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
                OdataProcessorHelper.callFunctionActionMethod(odataContext,
                        csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
            } catch (Exception e) {
                if (originGenericValue == null)
                    throw new ODataApplicationException("Entity not found: " + boundEntity.getSegmentValue(), HttpStatus.SC_NOT_FOUND, this.locale, HttpStatus.SC_NOT_FOUND + "");
                OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                        paramMap, originGenericValue);
            }
        }
    }

    public Property processImportActionPrimitive(UriResourceAction uriResourceAction, Map<String, Parameter> actionParameters)
            throws ODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);
        Object result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                paramMap, null);
        //区分一下返回的是否是Collection
        ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, result);
    }

    public void processImportActionVoid(UriResourceAction uriResourceAction, Map<String, Parameter> actionParameters)
            throws ODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);
        try {
            OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlAction.getOfbizMethod(), paramMap, null, edmNavigationProperty, null, null);
        } catch (Exception e) {
            OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                    paramMap, null);
        }
    }

    public OFbizEntityActionResult processImportActionEntity(UriResourceAction uriResourceAction,
                                                             Map<String, Parameter> actionParameters)
            throws ODataException {
        String componentName = Util.getRequestComponentName(httpServletRequest);
        String componentPath = Util.getRequestComponentPath(httpServletRequest, componentName);
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        Entity entity;
        Object actionMethodResult = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                paramMap, null);
        if (actionMethodResult instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmAction.getReturnType().getType(), (GenericValue) actionMethodResult, locale);
        } else {
            entity = this.objectToEntity(csdlAction.getReturnType().getTypeFQN(), actionMethodResult);
        }
        if (entity != null) {
            appendSemanticFields(csdlAction.getReturnType().getTypeFQN(), UtilMisc.toList(entity));
        }
        result.setEntity(entity);
        return result;
    }

    public Property processBoundActionComplex(UriResourceAction uriResourceAction,
                                              Map<String, Parameter> actionParameters,
                                              UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType().getType();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);

        Object result = null;
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundActionPrimitive(edmAction, csdlAction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundActionPrimitive(edmAction, csdlAction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        if (result instanceof Map) {
            ComplexValue complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COMPLEX, complexValue);
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item : (List) result) {
                ComplexValue complexValue = mapToComplexValue((Map<String, Object>) item, edmComplexType);
                complexValues.add(complexValue);
            }
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COLLECTION_COMPLEX, complexValues);
        }
        return new Property(null, null, ValueType.COMPLEX, null);
    }

    public Property processImportActionComplex(UriResourceAction uriResourceAction,
                                               Map<String, Parameter> actionParameters) throws ODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType().getType();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.parametersToMap(actionParameters);
        Object result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlAction.getOfbizMethod(),
                paramMap, null);
        ComplexValue complexValue;
        if (result instanceof Map) {
            complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
        } else if (result instanceof Property) {
            return (Property) result;
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item : (List) result) {
                ComplexValue complexValue2 = mapToComplexValue((Map<String, Object>) item, edmComplexType);
                complexValues.add(complexValue2);
            }
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COLLECTION_COMPLEX, complexValues);
        } else {
            complexValue = objectToComplexValue(result);
        }
        return new Property(null, null, ValueType.COMPLEX, complexValue);
    }
}
