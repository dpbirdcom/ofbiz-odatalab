package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.server.api.uri.UriResourceAction;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ActionProcessor extends OdataWriter {
    public ActionProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    /**
     * 无返回值的Action
     */
    public void processActionVoid(UriResourceAction uriResourceAction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlAction.getOfbizMethod(), parameters, edmBindingTarget);
    }

    /**
     * Action返回Entity
     */
    public OFbizEntityActionResult processActionEntity(UriResourceAction uriResourceAction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        EdmEntityType edmReturnType = (EdmEntityType) edmAction.getReturnType().getType();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Object actionReturn = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlAction.getOfbizMethod(), parameters, edmBindingTarget);
        Entity entity = resultToEntity(actionReturn, delegator, edmProvider, edmReturnType, locale);
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        result.setEntity(entity);
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            OdataOfbizEntity resultEntity = (OdataOfbizEntity) result.getEntity();
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), resultEntity,
                    (EdmEntityType) edmAction.getReturnType().getType());
        }
        return result;
    }

    /**
     * Action返回EntityCollection
     */
    public EntityCollection processActionEntityCollection(UriResourceAction uriResourceAction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Object invokeResult = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlAction.getOfbizMethod(), parameters, edmBindingTarget);
        EntityCollection entityCollection = new EntityCollection();
        if (invokeResult instanceof List && UtilValidate.isNotEmpty(invokeResult)) {
            EdmEntityType returnType = (EdmEntityType) edmAction.getReturnType().getType();
            for (Object result : UtilGenerics.checkList(invokeResult)) {
                Entity entity = resultToEntity(result, delegator, edmProvider, returnType, locale);
                entityCollection.getEntities().add(entity);
            }
        } else if (invokeResult instanceof EntityCollection) {
            entityCollection = (EntityCollection) invokeResult;
        }
        //TODO:或许需要对结果集进行queryOption的处理.
        return entityCollection;
    }


    /**
     * Action返回Primitive、PrimitiveCollection、Complex、ComplexCollection
     */
    public Property processActionProperty(UriResourceAction uriResourceAction, Map<String, Object> parameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        EdmType type = edmAction.getReturnType().getType();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Object result = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlAction.getOfbizMethod(), parameters, edmBindingTarget);
        EdmComplexType edmComplexType = null;
        if (type instanceof EdmComplexType) {
            edmComplexType = (EdmComplexType) type;
        }
        return resultToProperty(result, edmComplexType);
    }

    private Entity resultToEntity(Object result, Delegator delegator, OfbizAppEdmProvider edmProvider,
                                         EdmEntityType edmReturnType, Locale locale) throws OfbizODataException {
        Entity entity;
        boolean needSemanticFields = true;
        if (result instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmReturnType, (GenericValue) result, locale);
        } else if (result instanceof Entity) {
            entity = (Entity) result;
            needSemanticFields = false;
        } else if (result instanceof Map) {
            OfbizCsdlEntityType returnEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmReturnType.getFullQualifiedName());
            entity = Util.mapToEntity(returnEntityType, (Map<String, Object>) result);
            needSemanticFields = false;
        } else { // TODO: 目前硬编码ShoppingCart
            entity = this.objectToEntity(edmReturnType.getFullQualifiedName(), result);
        }
        if (entity != null && needSemanticFields) {
            appendSemanticFields(edmReturnType.getFullQualifiedName(), UtilMisc.toList(entity));
        }
        return entity;
    }

    private Property resultToProperty(Object result, EdmComplexType edmComplexType) {
        if (edmComplexType == null) {
            ValueType valueType = result instanceof List ? ValueType.COLLECTION_PRIMITIVE : ValueType.PRIMITIVE;
            return new Property(null, null, valueType, result);
        }
        if (result instanceof Map) {
            ComplexValue complexValue = mapToComplexValue(UtilGenerics.checkMap(result), edmComplexType);
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COMPLEX, complexValue);
        } else if (result instanceof List) {
            List<ComplexValue> complexValues = new ArrayList<>();
            for (Object item : UtilGenerics.checkList(result)) {
                ComplexValue complexValue = mapToComplexValue(UtilGenerics.checkMap(item), edmComplexType);
                complexValues.add(complexValue);
            }
            return new Property(edmComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), null, ValueType.COLLECTION_COMPLEX, complexValues);
        }
        return new Property(null, null, ValueType.PRIMITIVE, null);
    }


    private List<Entity> appendSemanticFields(FullQualifiedName fqn, List<Entity> entityList) throws OfbizODataException {
        OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(fqn);
        String handlerClass = ofbizCsdlEntityType.getHandlerClass();
        Debug.logInfo("start handling semantic fields with groovy", module);
        if (handlerClass != null) {
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
}
