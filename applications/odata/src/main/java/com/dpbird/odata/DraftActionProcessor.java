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
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
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
public class DraftActionProcessor extends DraftReaderAndWriter {
    private final Map<String, Object> odataContext;
    private final Map<String, QueryOption> queryOptions;
    private final HttpServletRequest httpServletRequest;
    public DraftActionProcessor(Map<String, QueryOption> queryOptions, Map<String, Object> odataContext, String sapContextId ,EdmEntityType edmEntityType) throws OfbizODataException {
        super(odataContext, sapContextId, edmEntityType);
//        super((Delegator) odataContext.get("delegator"), (LocalDispatcher) odataContext.get("dispatcher"),
//                (OfbizAppEdmProvider) odataContext.get("edmProvider"), csdlEntityType, (String) odataContext.get("sapContextId"),
//                (GenericValue) odataContext.get("userLogin"), (Locale) odataContext.get("locale"), edmEntityType);
        this.httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        this.queryOptions = queryOptions;
        this.odataContext = odataContext;
    }

    public OFbizEntityActionResult processActionEntity(UriResourceAction uriResourceAction, Map<String, Object> parameters,
                                                       EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        EdmAction edmAction = uriResourceAction.getAction();
        EdmEntityType edmReturnType = (EdmEntityType) edmAction.getReturnType().getType();
        OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
        Object actionReturn = OdataProcessorHelper.callFunctionActionMethod(odataContext, csdlAction.getOfbizMethod(), parameters, edmBindingTarget);
        Entity entity = resultToEntity(actionReturn, delegator, edmProvider, edmReturnType, locale);
        OFbizEntityActionResult result = new OFbizEntityActionResult();
        result.setEntity(entity);
        if (result.getEntity() != null) {
            List<CsdlAction> actions = edmProvider.getActions(uriResourceAction.getAction().getFullQualifiedName());
            OfbizCsdlAction ofbizCsdlAction = (OfbizCsdlAction) actions.get(0);
            if (ofbizCsdlAction.isStickySessionEdit()) {
                OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) result.getEntity();
                sapContextId = (String) odataOfbizEntity.getKeyMap().get("draftUUID");
            }
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmReturnType.getFullQualifiedName());
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("expandOption") != null) {
            OdataOfbizEntity resultEntity = (OdataOfbizEntity) result.getEntity();
            this.addExpandOption((ExpandOption) queryOptions.get("expandOption"), resultEntity, csdlEntityType, edmReturnType);
        }
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


    private Entity resultToEntity(Object result, Delegator delegator, OfbizAppEdmProvider edmProvider,
                                  EdmEntityType edmReturnType, Locale locale) throws OfbizODataException {
        Entity entity = null;
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
        }
        if (entity != null && needSemanticFields) {
            appendSemanticFields(edmReturnType.getFullQualifiedName(), UtilMisc.toList(entity));
        }
        return entity;
    }

}
