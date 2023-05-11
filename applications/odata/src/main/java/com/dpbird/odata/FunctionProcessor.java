package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlFunction;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.server.api.uri.UriResourceFunction;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FunctionProcessor extends OdataReader {
    public FunctionProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    /**
     * return Entity
     */
    public Entity processFunctionEntity(UriResourceFunction uriResourceFunction, Map<String, Object> paramMap,
                                        EdmBindingTarget edmBindingTarget, List<OdataParts> resourceDataInfoList) throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmEntityType returnEntityType = (EdmEntityType) edmFunction.getReturnType().getType();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        //Invoke method.
        odataContext.put("uriResources", resourceDataInfoList);
        Object result = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlFunction.getOfbizMethod(), paramMap, edmBindingTarget);
        Entity responseEntity = resultToEntity(returnEntityType, edmProvider, result);
        if (responseEntity != null) {
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, UtilMisc.toList(responseEntity), locale, userLogin);
        }
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) responseEntity, returnEntityType);
        }
        return responseEntity;
    }

    /**
     * return EntityCollection
     */
    public EntityCollection processFunctionEntityCollection(UriResourceFunction uriResourceFunction, Map<String, Object> paramMap,
                                                            EdmBindingTarget edmBindingTarget, List<OdataParts> resourceDataInfoList) throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        //Invoke method.
        EntityCollection entityCollection = new EntityCollection();
        EdmEntityType returnEdmEntityType = (EdmEntityType) edmFunction.getReturnType().getType();
        odataContext.put("uriResources", resourceDataInfoList);
        List<Object> resultList = (List) OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlFunction.getOfbizMethod(), paramMap, edmBindingTarget);
        List<Entity> entities = entityCollection.getEntities();
        for (Object result : resultList) {
            entities.add(resultToEntity(returnEdmEntityType, edmProvider, result));
        }
        //filter,orderBy,page
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        OrderByOption orderByOption = (OrderByOption) queryOptions.get("orderByOption");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(returnEdmEntityType.getFullQualifiedName());
        if (filterOption != null) {
            Util.filterEntityCollection(entityCollection, filterOption, orderByOption, edmEntityType, edmProvider, delegator, dispatcher, userLogin, locale, false);
        }
        if (Util.isExtraOrderby(orderByOption, csdlEntityType, delegator)) {
            Util.orderbyEntityCollection(entityCollection, orderByOption, edmEntityType, edmProvider);
        }
        Util.pageEntityCollection(entityCollection, skipValue, topValue);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                queryOptions, entities, locale, userLogin);
        //expand
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            for (Entity entity : entities) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, returnEdmEntityType);
            }
        }
        return entityCollection;
    }

    /**
     * return Primitive、PrimitiveCollection
     */
    public Property processFunctionPrimitive(UriResourceFunction uriResourceFunction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        //Invoke method.
        Object result = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlFunction.getOfbizMethod(), parameters, edmBindingTarget);
        ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
        return new Property(null, null, valueType, result);
    }

    /**
     * return Complex、ComplexCollection
     */
    public Property processFunctionComplex(UriResourceFunction uriResourceFunction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        EdmReturnType edmReturnType = edmFunction.getReturnType();
        EdmComplexType edmComplexType = (EdmComplexType) edmReturnType.getType();
        String fullQualifiedName = edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString();
        OfbizCsdlFunction csdlFunction = (OfbizCsdlFunction) this.edmProvider.getFunctions(edmFunction.getFullQualifiedName()).get(0);
        Object result = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlFunction.getOfbizMethod(), parameters, edmBindingTarget);
        Property property = null;
        if (result instanceof Map) {
            ComplexValue complexValue = mapToComplexValue((Map<String, Object>) result, edmComplexType);
            property = new Property(fullQualifiedName, null, ValueType.COMPLEX, complexValue);
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item : (List<?>) result) {
                ComplexValue complexValue = mapToComplexValue((Map<String, Object>) item, edmComplexType);
                complexValues.add(complexValue);
            }
            property = new Property(fullQualifiedName, null, ValueType.COLLECTION_COMPLEX, complexValues);
        }
        return property;
    }

    /**
     * 转换Function的返回结果
     */
    private Entity resultToEntity(EdmEntityType edmEntityType, OfbizAppEdmProvider edmProvider, Object result) throws OfbizODataException {
        if (result instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(dispatcher, this.edmProvider, edmEntityType, (GenericValue) result, locale);
        } else if (result instanceof Map) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return Util.mapToEntity(csdlEntityType, (Map<String, Object>) result);
        } else {
            // TODO: 目前硬编码ShoppingCart
            return this.objectToEntity(edmEntityType.getFullQualifiedName(), result);
        }
    }

}
