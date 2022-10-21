package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlFunction;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.AliasQueryOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

public class FunctionProcessor extends OfbizOdataReader{
    public FunctionProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    public Property processBoundFunctionPrimitive(UriResourceFunction uriResourceFunction, List<UriParameter> uriParameters,
                                               UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation,
                                                  List<AliasQueryOption> aliases)
            throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);

        Object result = null;
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundFunctionPrimitive(edmFunction, csdlFunction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundFunctionPrimitive(edmFunction, csdlFunction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        //有可能是Collection
        ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, result);
    }

    private Object singletonBoundFunctionPrimitive(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
                                                   Map<String, Object> paramMap, UriResourceSingleton boundEntity,
                                                   UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        Object result;
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, boundObject);
        }
        return result;
    }

    private Object entitySetBoundFunctionPrimitive(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
                                                   Map<String, Object> paramMap, UriResourceEntitySet boundEntity,
                                                   UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        Object result;
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        if (originGenericValue == null) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "","Entity not found:" + boundEntity.getSegmentValue());
        }
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } catch (OfbizODataException e){
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, originGenericValue);
        }
        return result;
    }

    public OdataOfbizEntity processBoundFunctionEntity(UriResourceFunction uriResourceFunction, List<UriParameter> uriParameters,
                                                       UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation, List<AliasQueryOption> aliases)
            throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Object result = null;
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        if (boundEntity instanceof UriResourceEntitySet) {
            // TODO: should be entitySetBoundFunctionEntity
            result = entitySetBoundFunctionPrimitive(edmFunction, csdlFunction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundFunctionPrimitive(edmFunction, csdlFunction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        OdataOfbizEntity entity;
        if (result instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmFunction.getReturnType().getType(), (GenericValue) result, locale);
        } else if (result instanceof Map) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
            entity = (OdataOfbizEntity) Util.mapToEntity(csdlEntityType, (Map<String, Object>) result);
        } else {
            // TODO: 目前硬编码ShoppingCart
            entity = this.objectToEntity(csdlFunction.getReturnType().getTypeFQN(), result);

        }
        if (entity != null) {
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(entity), locale, userLogin);
        }
        return entity;
    }

    public EntityCollection processBoundFunctionEntityCollection(UriResourceFunction uriResourceFunction, List<UriParameter> uriParameters,
                                                       UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation, List<AliasQueryOption> aliases)
            throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);

        List<Object> result = null;
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundFunctionEntityCollection(edmFunction, csdlFunction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundFunctionEntityCollection(edmFunction, csdlFunction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        OdataOfbizEntity entity;
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        if (result == null) {
            //如果允许返回null
            if (edmFunction.getReturnType().isNullable()) {
                return entityCollection;
            } else {
                throw new OfbizODataException(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode() + "", "The return entity collection cannot be null.");
            }
        }
        //对查询结果做分页处理
        for (Object item : result) {
            if (item instanceof GenericValue) {
                entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                        (EdmEntityType) edmFunction.getReturnType().getType(), (GenericValue) item, locale);
            } else if (item instanceof Map) {
                OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
                entity = (OdataOfbizEntity) Util.mapToEntity(csdlEntityType, (Map<String, Object>) item);
            } else {
                entity = this.objectToEntity(csdlFunction.getReturnType().getTypeFQN(), item);
            }
            entities.add(entity);
            if (entity != null) {
                OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                        queryOptions, UtilMisc.toList(entity), locale, userLogin);
            }
        }
        if (queryOptions.get("filterOption") != null) {
            FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
            Util.filterEntityCollection(entityCollection, filterOption, csdlEntityType, edmProvider, delegator, dispatcher, userLogin, locale);
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        return entityCollection;
    }

    private List<Object> singletonBoundFunctionEntityCollection(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
                                                   Map<String, Object> paramMap, UriResourceSingleton boundEntity,
                                                   UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
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
                    csdlFunction.getOfbizMethod(), paramMap, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e){
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, boundObject);
        }
        if (result == null || !(result instanceof List)) {
            return null;
        }
        return (List<Object>) result;
    }

    private List<Object> entitySetBoundFunctionEntityCollection(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
                                                   Map<String, Object> paramMap, UriResourceEntitySet boundEntity,
                                                    UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        Object result = null;
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        Entity entity = null;
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } catch (OfbizODataException e) {
            if (originGenericValue == null) throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + boundEntity.getSegmentValue());
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, originGenericValue);
        }
        if (!(result instanceof List)) {
            return null;
        }
        return (List<Object>) result;
    }

    public Property readFunctionImportPrimitiveCollection(UriResourceFunction uriResourceFunction)
            throws ODataException {
        String functionName = uriResourceFunction.getFunctionImport().getName();
        final List<UriParameter> parameters = uriResourceFunction.getParameters();
        Map<String, Object> outParam = null;
        outParam = runServiceForFunction(functionName, parameters, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            List outList = (List) outParam.get(outParamName);
            return new Property(null, outParamName, ValueType.COLLECTION_PRIMITIVE, outList);
        }
        return null;
    }

    public Property readFunctionImportComplex(UriResourceFunction uriResourceFunction)
            throws ODataException {
        String functionName = uriResourceFunction.getFunctionImport().getName();
        final List<UriParameter> parameters = uriResourceFunction.getParameters();
        Map<String, Object> outParam = null;
        outParam = runServiceForFunction(functionName, parameters, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            Object outValue = outParam.get(outParamName);
            ComplexValue complexValue = objectToComplexValue(outValue);
            return new Property(null, outParamName, ValueType.COMPLEX, complexValue);
        }
        return null;
    }

    public Property readFunctionImportComplexCollection(UriResourceFunction uriResourceFunction)
            throws ODataException {
        String functionName = uriResourceFunction.getFunctionImport().getName(); // functionName就是ofbiz的serviceName
        final List<UriParameter> parameters = uriResourceFunction.getParameters();
        // TODO runServiceForFunction多了个returName，需要想办法实现
        Map<String, Object> outParam = null;
        outParam = runServiceForFunction(functionName, parameters, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            List outList = (List) outParam.get(outParamName); // 返回肯定是个Collection，大多情况会是List，先假设List吧
            List<ComplexValue> complexValues = new ArrayList<ComplexValue>();
            for (Object outValue:outList) {
                ComplexValue complexValue = objectToComplexValue(outValue);
                complexValues.add(complexValue);
            }
            Property listProperty = new Property(null, outParamName, ValueType.COLLECTION_COMPLEX, complexValues);
            return listProperty;
        }
        return null;
    }

    public Property processFunctionComplexMethod(HttpServletRequest httpServletRequest, EdmFunction edmFunction,
                                                 String classMethod, List<UriParameter> params)
            throws ODataException {
        return this.processFunctionPrimitiveMethod(httpServletRequest, edmFunction, classMethod, params);
    }

    public Property processFunctionPrimitiveMethod(HttpServletRequest httpServletRequest, EdmFunction edmFunction,
                                                   String classMethod, List<UriParameter> keyParams)
            throws ODataException {
        String className = classMethod.substring(0, classMethod.lastIndexOf('.'));
        String methodName = classMethod.substring(classMethod.lastIndexOf('.') + 1);
        try {
            Map<String,Object> methodParam = Util.uriParametersToMap(keyParams, edmFunction);
            Class objectClass = Class.forName(className);
            Method method = objectClass.getMethod(methodName, HttpServletRequest.class,HashMap.class);
            Property result = (Property)method.invoke(objectClass, httpServletRequest,methodParam);
            return result;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalAccessException  | IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }catch (InvocationTargetException e){
            Throwable t = e.getTargetException();
            throw new ODataException(t.getMessage());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
    }

    public Property readFunctionImportPrimitive(UriResourceFunction uriResourceFunction)
            throws ODataException {
        String functionName = uriResourceFunction.getFunctionImport().getName();
        final List<UriParameter> parameters = uriResourceFunction.getParameters();
        Map<String, Object> outParam = null;
        outParam = runServiceForFunction(functionName, parameters, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            Object outValue = outParam.get(outParamName);
            if (outValue instanceof Timestamp) {
                Timestamp ts = (Timestamp) outValue;
                return new Property(null, outParamName, ValueType.PRIMITIVE, ts.toLocalDateTime() + "");
            }else{
                return new Property(null, outParamName, ValueType.PRIMITIVE, outValue);
            }
        }
        return null;
    }

    private Map<String, Object> runServiceForFunction(String functionName, List<UriParameter> parameters, String returnName)
            throws ODataException {
        ModelService modelService = null;
        try {
            modelService = dispatcher.getDispatchContext().getModelService(functionName);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
        Map<String, Object> serviceParameters = prepareServiceParameters(modelService, functionName, parameters);
        return runService(modelService, serviceParameters, returnName);
    }

    public Entity readFunctionImportEntity(UriResourceFunction uriResourceFunction)
            throws ODataException {
        String functionName = uriResourceFunction.getFunctionImport().getName(); // functionName就是ofbiz的serviceName
        final List<UriParameter> parameters = uriResourceFunction.getParameters();
        // TODO runServiceForFunction多了个returName，需要想办法实现
        Map<String, Object> outParam = runServiceForFunction(functionName, parameters, null);
        if (UtilValidate.isNotEmpty(outParam)) {
            Iterator<String> keyIt = outParam.keySet().iterator();
            String outParamName = (String) keyIt.next();
            GenericValue outValue = (GenericValue) outParam.get(outParamName);
            if (outValue != null) {
                EdmEntityType edmEntityType = uriResourceFunction.getFunctionImport().getReturnedEntitySet()
                        .getEntityType();
                return OdataProcessorHelper.genericValueToEntity(delegator,
                        this.edmProvider, edmEntityType, outValue, locale);
            }
        }
        return null;
    }

    private Map<String, Object> prepareServiceParameters(ModelService modelService, String functionName,
                                                           List<UriParameter> parameters) {
        Map<String, Object> serviceParameters = new HashMap<String, Object>();
        String regexp = "\'";
        for (UriParameter parameter : parameters) {
            String paramName = parameter.getName();
            ModelParam modelParam = modelService.getParam(paramName);
            String paramType = modelParam.getType();
            String valueString = parameter.getText();
            if (valueString == null || valueString.equals("null")) {
                continue;
            }
            if (paramType.equals("String")) {
                valueString = valueString.replaceAll(regexp, "");
                serviceParameters.put(paramName, valueString);
            } else if (paramType.equals("BigDecimal") || paramType.equals("java.math.BigDecimal")) {
                serviceParameters.put(paramName, new BigDecimal(valueString));
            } else if (paramType.equals("Timestamp") || paramType.equals("java.sql.Timestamp")) {
                valueString = valueString.replaceAll(regexp, "");
                serviceParameters.put(paramName, Timestamp.valueOf(valueString + " 00:00:00")); // 目前只发现odata支持日期，所以要把日期转换成Timestamp的格式
            } else if (paramType.equals("Double")) {
                serviceParameters.put(paramName, new Double(valueString));
            } else if (paramType.equals("Long")) {
                serviceParameters.put(paramName, new Long(valueString));
            } else {
                serviceParameters.put(parameter.getName(), valueString);
            }
        }
        serviceParameters.put("userLogin", userLogin);
        return serviceParameters;
    }

    private Object entitySetBoundFunctionComplexType(EdmFunction edmFunction,
                                                       OfbizCsdlFunction csdlFunction,
                                                       Map<String, Object> parameters,
                                                       UriResourceEntitySet boundEntity,
                                                       UriResourceNavigation uriResourceNavigation) throws OfbizODataException {
        Object result;
        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        GenericValue originGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                boundEntity.getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);

        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        try {
            EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), parameters, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        } catch (OfbizODataException e){
            if (originGenericValue == null) throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "Entity not found: " + boundEntity.getSegmentValue());
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    parameters, originGenericValue);
        }
        return result;
    }

    public Object boundFunctionEntity(EdmFunction edmFunction, UriResourcePartTyped boundEntity,
                                             List<UriParameter> uriParameters, UriResourceNavigation uriResourceNavigation, List<AliasQueryOption> aliases)
            throws OfbizODataException {
        Map<String, Object> parameters = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        FullQualifiedName functionFqn = edmFunction.getFullQualifiedName();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(functionFqn).get(0);
        Object result = null;
        if (boundEntity instanceof UriResourceEntitySet) {
            // TODO: Implements entitySetBoundFunctionComplexType
            List<UriParameter> keyParams = ((UriResourceEntitySet) boundEntity).getKeyPredicates();
            GenericValue boundGenericValue = OdataProcessorHelper.getGenericValue(delegator, edmProvider,
                    ((UriResourceEntitySet) boundEntity).getEntitySet(), keyParams, (String) odataContext.get("sapContextId"));
            // TODO: Implements entitySetBoundFunctionComplexType
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundFunctionEntityType(edmFunction, csdlFunction, parameters,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        return result;
    }

	private Object singletonBoundFunctionComplexType(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
			Map<String, Object> parameters, UriResourceSingleton boundEntity, UriResourceNavigation uriResourceNavigation)
					throws OfbizODataException {
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
            return OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), parameters, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e){
            return OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    parameters, boundObject);
        }
	}

    // 返回的是Entity，或者是EntityCollection
	private Object singletonBoundFunctionEntityType(EdmFunction edmFunction, OfbizCsdlFunction csdlFunction,
                                                       Map<String, Object> parameters, UriResourceSingleton boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Object boundObject = OdataProcessorHelper.readSingletonData(odataContext, edmSingleton);

        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        Object result;
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), parameters, edmSingleton, edmNavigationProperty, null, navKeyMap);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    parameters, boundObject);
        }
        boolean returnNullable = csdlFunction.getReturnType().isNullable();
        if (result == null) {
            if (!returnNullable) {
                throw new OfbizODataException(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode() + "", "The return entity collection cannot be null.");
            } else {
                return new EntityCollection();
            }
        }
        if (result instanceof List) {
            List objectList = (List) result;
            EntityCollection entityCollection = new EntityCollection();
            List<Entity> entityList = entityCollection.getEntities();
            for (Object listItem:objectList) {
                Entity entity;
                if (listItem instanceof GenericValue) {
                    entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                            (EdmEntityType) edmFunction.getReturnType().getType(), (GenericValue) listItem, locale);
                } else if (listItem instanceof Map) {
                    OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
                    entity = Util.mapToEntity(csdlEntityType, (Map<String, Object>) listItem);
                }  else {
                    entity = this.objectToEntity(csdlFunction.getReturnType().getTypeFQN(), listItem);
                }
                if (entity != null) {
                    OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                            queryOptions, UtilMisc.toList(entity), locale, userLogin);
                }
                entityList.add(entity);
            }
            return entityCollection;
        } else {
            Entity entity;
            if (result instanceof GenericValue) {
                entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                        (EdmEntityType) edmFunction.getReturnType().getType(), (GenericValue) result, locale);
            } else {
                entity = this.objectToEntity(csdlFunction.getReturnType().getTypeFQN(), result);
            }
            if (entity != null) {
                OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                        queryOptions, UtilMisc.toList(entity), locale, userLogin);
            }
            return result;
        }
    }

	private Object processFunctionMethod(HttpServletRequest httpServletRequest, String classMethod,
                                         Map<String, Parameter> parameters, Object boundObject) throws ODataException {
        String className = classMethod.substring(0, classMethod.lastIndexOf('.'));
        String methodName = classMethod.substring(classMethod.lastIndexOf('.') + 1);
        try {
            Class objectClass = Class.forName(className);
            if (boundObject == null) {
                Method method = objectClass.getMethod(methodName, HttpServletRequest.class, Map.class);
                return method.invoke(objectClass, httpServletRequest, parameters);
            } else {
                Method method = objectClass.getMethod(methodName, HttpServletRequest.class, Map.class, Object.class);
                return method.invoke(objectClass, httpServletRequest, parameters, boundObject);
            }
        } catch (IllegalAccessException  | IllegalArgumentException
                | NoSuchMethodException | InvocationTargetException
                | ClassNotFoundException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
	}

    public Property processBoundFunctionComplex(UriResourceFunction uriResourceFunction,
                                                List<UriParameter> uriParameters,
                                                UriResourcePartTyped boundEntity, UriResourceNavigation uriResourceNavigation,
                                                List<AliasQueryOption> aliases)
            throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmReturnType edmReturnType = edmFunction.getReturnType();
        EdmComplexType edmComplexType = (EdmComplexType) edmReturnType.getType();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);

        Object result = null;
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        if (boundEntity instanceof UriResourceEntitySet) {
            result = entitySetBoundFunctionComplexType(edmFunction, csdlFunction, paramMap,
                    (UriResourceEntitySet) boundEntity, uriResourceNavigation);
        } else if (boundEntity instanceof UriResourceSingleton) {
            result = singletonBoundFunctionComplexType(edmFunction, csdlFunction, paramMap,
                    (UriResourceSingleton) boundEntity, uriResourceNavigation);
        }
        if (result instanceof Map) {
            ComplexValue complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COMPLEX, complexValue);
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item:(List) result) {
                ComplexValue complexValue = mapToComplexValue((Map<String, Object>) item, edmComplexType);
                complexValues.add(complexValue);
            }
            //这里对返回结果做分页处理 top skip
            List<ComplexValue> complexValuesPage = listToPage(complexValues, topValue, skipValue);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COLLECTION_COMPLEX, complexValuesPage);
        }
        return null;
    }


    public Property processImportFunctionPrimitive(UriResourceFunction uriResourceFunction, List<UriParameter> uriParameters, List<AliasQueryOption> aliases)
            throws ODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        Object result;
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, null, null, null, null);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, null);
        }
        //有可能是Collection
        ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, result);
    }

    public Property processImportFunctionComplex(UriResourceFunction uriResourceFunction, List<UriParameter> uriParameters, List<AliasQueryOption> aliases)
            throws ODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmComplexType edmComplexType = (EdmComplexType) edmFunction.getReturnType().getType();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);

        Object result;
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, null, null, null, null);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, null);
        }
        if (result instanceof Map) {
            ComplexValue complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COMPLEX, complexValue);
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item:(List) result) {
                ComplexValue complexValue = mapToComplexValue((Map<String, Object>) item, edmComplexType);
                complexValues.add(complexValue);
            }
            //这里对返回结果做分页处理 top skip
            List<ComplexValue> complexValuesPage = listToPage(complexValues, topValue, skipValue);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COLLECTION_COMPLEX, complexValuesPage);
        }
        ComplexValue complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
        return new Property(null, null, ValueType.COMPLEX, complexValue);
    }

    public Entity processImportFunctionEntity(UriResourceFunction uriResourceFunction,
                                              List<UriParameter> uriParameters, List<AliasQueryOption> aliases) throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);
        Object result;
        try {
            result = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, null, null, null, null);
        } catch (OfbizODataException e) {
            result = OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, null);
        }
        OdataOfbizEntity entity;
        if (result instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmFunction.getReturnType().getType(), (GenericValue) result, locale);
        } else if (result instanceof Map) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
            entity = (OdataOfbizEntity) Util.mapToEntity(csdlEntityType, (Map<String, Object>) result);
        } else {
            entity = this.objectToEntity(csdlFunction.getReturnType().getTypeFQN(), result);
        }
        //返回值不能为空
        if (entity == null && !edmFunction.getReturnType().isNullable()) {
            throw new OfbizODataException(HttpStatus.SC_NOT_FOUND + "", "The return entity cannot be null.");
        }
        if (entity != null) {
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(entity), locale, userLogin);
        }
        return entity;
    }

    /**
     * 针对ImportFunction返回EntityCollection的处理
     * 将返回结果转换为EntityCollection进行返回
     *
     */
    @SuppressWarnings("unchecked")
    public EntityCollection processImportFunctionEntityCollection(UriResourceFunction uriResourceFunction,
                                              List<UriParameter> uriParameters, List<AliasQueryOption> aliases) throws ODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmFunction function = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Map<String, Object> paramMap = Util.uriParametersToMap(uriParameters, edmFunction, aliases);

        List<Object> result;
        try {
            result = (List<Object>) OdataProcessorHelper.callFunctionActionMethod(odataContext,
                    csdlFunction.getOfbizMethod(), paramMap, null, null, null, null);
        } catch (OfbizODataException e) {
            result = (List<Object>) OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(),
                    paramMap, null);
        }
//        List<GenericValue> invokeResult = (List<GenericValue>)OdataProcessorHelper.processFunctionActionMethod(httpServletRequest, csdlFunction.getOfbizMethod(), paramMap, null);
        if (result == null){
            //如果允许为null
            if (edmFunction.getReturnType().isNullable()) {
                return new EntityCollection();
            } else {
                throw new OfbizODataException(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode() + "", "The return entity collection cannot be null.");
            }
        }
        EntityCollection responseEntityCollection = new EntityCollection();
        List<Entity> entities = responseEntityCollection.getEntities();
        for (Object item : result) {
            if (item instanceof GenericValue) {
                OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                        (EdmEntityType) function.getReturnType().getType(), (GenericValue) item, locale);
                entities.add(entity);
                if (entity != null) {
                    OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                            queryOptions, UtilMisc.toList(entity), locale, userLogin);
                }
            } else if (item instanceof Map) {
                OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
                entities.add(Util.mapToEntity(csdlEntityType, (Map<String, Object>) item));
            }
        }
        if (queryOptions.get("filterOption") != null) {
            FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmFunction.getReturnType().getType().getFullQualifiedName());
            Util.filterEntityCollection(responseEntityCollection, filterOption, csdlEntityType, edmProvider, delegator, dispatcher, userLogin, locale);
        }
        Util.pageEntityCollection(responseEntityCollection, skipValue, topValue);
        return responseEntityCollection;
    }

    /**
     * 对List进行分页处理
     *
     * @param list list
     * @param topOption top
     * @param skipOption skip
     * @return list
     */
    public static <T>List<T> listToPage(List<T> list, int topOption, int skipOption) {
        //skip比数据长度大 说明全部跳过了
        if (UtilValidate.isNotEmpty(skipOption) && list.size() <= skipOption) {
            return new ArrayList<>();
        }
        //只存在top
        if (UtilValidate.isNotEmpty(topOption) && UtilValidate.isEmpty(skipOption)) {
            //数据量没top大 ? 全部返回 : 截取 0-top
            return list.size() <= topOption ? list : new ArrayList<>(list.subList(0, topOption));
        }

        //只存在skip
        if (UtilValidate.isNotEmpty(skipOption) && UtilValidate.isEmpty(topOption)) {
            return new ArrayList<>(list.subList(skipOption, list.size()));
        }

        //top和skip同时存在
        if (UtilValidate.isNotEmpty(skipOption) && UtilValidate.isNotEmpty(topOption)) {
            //skip之后的数据没top大,返回skip之后所有的数据
            if (list.size() - skipOption <= topOption) {
                return new ArrayList<>(list.subList(skipOption, list.size()));
            }
            //正常截取
            return new ArrayList<>(list.subList(skipOption, skipOption + topOption));
        }

        //top和skip都没有值时返回原数据
        return list;
    }
}
