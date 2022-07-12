package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlAction;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 只有StickySession模式下的Action会使用
 */
public class DraftActionProcessor extends DraftHandler {
    private final Map<String, Object> odataContext;
    private final Map<String, QueryOption> queryOptions;
    private final HttpServletRequest httpServletRequest;
    public DraftActionProcessor(Map<String, QueryOption> queryOptions, Map<String, Object> odataContext, OfbizCsdlEntityType csdlEntityType,EdmEntityType edmEntityType) {
        super((Delegator) odataContext.get("delegator"), (LocalDispatcher) odataContext.get("dispatcher"),
                (OfbizAppEdmProvider) odataContext.get("edmProvider"), csdlEntityType, (String) odataContext.get("sapContextId"),
                (GenericValue) odataContext.get("userLogin"), (Locale) odataContext.get("locale"), edmEntityType);
        this.httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        this.queryOptions = queryOptions;
        this.odataContext = odataContext;
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
        if (result != null) {
            List<CsdlAction> actions = edmProvider.getActions(action.getAction().getFullQualifiedName());
            OfbizCsdlAction ofbizCsdlAction = (OfbizCsdlAction) actions.get(0);
            if (ofbizCsdlAction.isStickySessionEdit()) {
                OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) result.getEntity();
                sapContextId = (String) odataOfbizEntity.getKeyMap().get("draftUUID");
            }
        }
        if (UtilValidate.isNotEmpty(queryOptions) && UtilValidate.isNotEmpty(queryOptions.get("expandOption")) && result != null) {
            OdataOfbizEntity entity = (OdataOfbizEntity) result.getEntity();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(boundEntity.getType().getFullQualifiedName());
            this.addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, ofbizCsdlEntityType, edmEntityType);
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

        List<UriParameter> keyParams = boundEntity.getKeyPredicates();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyParams, edmEntityType);
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
        EdmBindingTarget edmBindingTarget = boundEntity.getEntitySet();
        actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                csdlAction.getOfbizMethod(), paramMap, edmBindingTarget, edmNavigationProperty, keyMap, navKeyMap);
        if (actionMethodResult instanceof GenericValue) {
            entity = OdataProcessorHelper.genericValueToEntity(delegator, this.edmProvider,
                    (EdmEntityType) edmAction.getReturnType().getType(), (GenericValue) actionMethodResult, locale);
        } else if (actionMethodResult instanceof Entity) {
            entity = (Entity) actionMethodResult;
            needSemanticFields = false;
        } else {
            entity = this.objectToEntity(csdlAction.getReturnType().getTypeFQN(), actionMethodResult);
        }
        if (entity != null && needSemanticFields) {
            appendSemanticFields(csdlAction.getReturnType().getTypeFQN(), UtilMisc.toList(entity));
        }
        result.setEntity(entity);
        return result;
    }

    private OFbizEntityActionResult singletonBoundActionEntity(EdmAction edmAction, OfbizCsdlAction csdlAction,
                                                               Map<String, Object> paramMap,
                                                               UriResourceSingleton boundEntity, UriResourceNavigation uriResourceNavigation)
            throws OfbizODataException {
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        EdmSingleton edmSingleton = boundEntity.getSingleton();
        Entity entity;
        Object actionMethodResult;
        Map<String, Object> navKeyMap = null;
        EdmNavigationProperty edmNavigationProperty = null;
        if (uriResourceNavigation != null) {
            edmNavigationProperty = uriResourceNavigation.getProperty();
            List<UriParameter> navKeyParams = uriResourceNavigation.getKeyPredicates();
            navKeyMap = Util.uriParametersToMap(navKeyParams, edmNavigationProperty.getType());
        }
        actionMethodResult = OdataProcessorHelper.callFunctionActionMethod(odataContext,
                csdlAction.getOfbizMethod(), paramMap, edmSingleton, edmNavigationProperty, null, navKeyMap);
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


    private List<Entity> appendSemanticFields(FullQualifiedName fqn, List<Entity> entityList) throws OfbizODataException {
        Entity entity = entityList.get(0);
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
            entityList = groovyHelper.generateEntityListFields(handlerClass, httpServletRequest, entityList, (SelectOption)queryOptions.get("selectOption"));
        } catch (MissingMethodExceptionNoStack e) {
            Debug.logInfo(e.getMessage(), module);
        }
        return entityList;
    }

    protected OdataOfbizEntity objectToEntity(FullQualifiedName fqn, Object object) throws OfbizODataException {
        if (object == null) return null;
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(fqn);
        OdataOfbizEntity e1 = new OdataOfbizEntity();
        e1.setType(fqn.getFullQualifiedNameAsString());
        List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
        Map<String, Object> cartFieldAndValueMap = Util.objectFieldValueMap(object, true);
        for (CsdlProperty csdlProperty : csdlProperties) {
            String propertyName = csdlProperty.getName();
            Object value = cartFieldAndValueMap.get(propertyName);
            if (UtilValidate.isNotEmpty(value)) {
                //如果是数组复杂对象，会有问题
                if (!(value instanceof Collection)) {
                    Property property = new Property(csdlProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(), propertyName,
                            ValueType.PRIMITIVE, value);
                    e1.addProperty(property);
                }
            }
        }
        return e1;
    }
}
