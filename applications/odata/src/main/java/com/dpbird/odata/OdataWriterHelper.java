package com.dpbird.odata;

import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OdataWriterHelper {
    public static final String module = OdataWriterHelper.class.getName();

    public static OdataOfbizEntity createEntitySetRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                                    HttpServletRequest httpServletRequest,
                                                                    OfbizAppEdmProvider edmProvider,
                                                                    OfbizCsdlEntityType csdlEntityType,
                                                                    Map<String, Object> keyMap, String navigationPropertyName,
                                                                    Entity entityToWrite,
                                                                    Map<String, QueryOption> queryOptions,
                                                                    GenericValue userLogin,
                                                                    Locale locale) throws OfbizODataException {

        GenericValue genericValue;
        String entityName = csdlEntityType.getOfbizEntity();
        try {
            genericValue = delegator.findOne(entityName, keyMap, true);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        if (genericValue == null) {
            throw new OfbizODataException(csdlEntityType.getName() + " with key " + keyMap + " was not found.");
        }
        return createGenericValueRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider, csdlEntityType,
                genericValue, navigationPropertyName, entityToWrite, queryOptions, userLogin, locale);
    }

    public static OdataOfbizEntity createSingletonRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                                    HttpServletRequest httpServletRequest,
                                                                    OfbizAppEdmProvider edmProvider,
                                                                    OfbizCsdlEntityType csdlEntityType,
                                                                    String singletonName, String navigationPropertyName,
                                                                    Entity entityToWrite,
                                                                    Map<String, QueryOption> queryOptions,
                                                                    GenericValue userLogin,
                                                                    Locale locale) throws OfbizODataException {

        GenericValue genericValue;
        genericValue = (GenericValue) OdataProcessorHelper.readSingletonObject(dispatcher, edmProvider,
                singletonName, httpServletRequest, userLogin);
        if (genericValue == null) {
            throw new OfbizODataException(csdlEntityType.getName() + " with singleton " + singletonName + " was not found.");
        }
        return createGenericValueRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
                csdlEntityType, genericValue, navigationPropertyName, entityToWrite, queryOptions, userLogin, locale);
    }

    public static OdataOfbizEntity createGenericValueRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                                       HttpServletRequest httpServletRequest,
                                                                       OfbizAppEdmProvider edmProvider,
                                                                       OfbizCsdlEntityType csdlEntityType,
                                                                       GenericValue genericValue, String navigationPropertyName,
                                                                       Entity entityToWrite,
                                                                       Map<String, QueryOption> queryOptions,
                                                                       GenericValue userLogin,
                                                                       Locale locale) throws OfbizODataException {
        OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, genericValue, locale);
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        try {
            GenericValue nestedGenericValue = null;
            if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
                String handler = csdlNavigationProperty.getHandler();
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                try {
                    nestedGenericValue = groovyHelper.createNestedGenericValue(handler, entityToWrite, entity, dispatcher, userLogin);
                } catch (MissingMethodExceptionNoStack e) {
                    //Groovy 没有定义createNestedData方法
                    Debug.logInfo(e.getMessage(), module);
                }
            }
            if (nestedGenericValue == null) {
                nestedGenericValue = OdataProcessorHelper.createRelatedGenericValue(entityToWrite, entity, relAlias, navCsdlEntityType, edmProvider, dispatcher, delegator, userLogin);
                if (nestedGenericValue == null) {
                    return null;
                }
            }
            OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
            OdataOfbizEntity entityCreated = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, nestedCsdlEntityType, nestedGenericValue, locale);

            // 创建语义话字段对应的数据库表的字段
            OdataProcessorHelper.createSemanticFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    entityToWrite, entityCreated, locale, userLogin);

            // 因为要返回所创建的Entity，所以，某些语义话字段可能是计算字段，也需要返回
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider,
                    queryOptions, Collections.singletonList(entityCreated), locale, userLogin);

            return entityCreated;
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    public static OdataOfbizEntity updateEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                    HttpServletRequest httpServletRequest,
                                                    OfbizAppEdmProvider edmProvider,
                                                    OfbizCsdlEntityType csdlEntityType,
                                                    Map<String, Object> keyMap,
                                                    Entity entityToWrite,
                                                    GenericValue userLogin,
                                                    Locale locale) throws OfbizODataException {
        String entityName = csdlEntityType.getOfbizEntity();
        OdataOfbizEntity updatedEntity;
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "httpServletRequest", httpServletRequest);
        GenericValue genericValue = OdataProcessorHelper.readEntityData(odataContext, csdlEntityType, keyMap);
        Map<String, Object> fieldMapToWrite = Util.entityToMap(entityToWrite);
        Map<String, Object> entityMap = Util.entityToMap(delegator, edmProvider, entityToWrite);
        boolean updateMainEntity = true; // 更新entity，有可能更新entity本身，也有可能只需要更新该entity的navigation的其它entity
        if (UtilValidate.isEmpty(entityMap)) {
            updateMainEntity = false;
        }
        if (updateMainEntity) {
            GenericValue updatedGenericValue = null;
            if (csdlEntityType.getHandlerClass() != null) {
                //自定义Groovy更新
                GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
                try {
                    updatedGenericValue = groovyHelper.updateGenericValue(csdlEntityType.getHandlerClass(), entityToWrite);
                } catch (MissingMethodExceptionNoStack e) {
                    Debug.logInfo(e.getMessage(), module);
                }
            }
            if (updatedGenericValue == null) {
                Map<String, Object> propertyMap = Util.entityToMap(delegator, edmProvider, entityToWrite);
                Map<String, Object> fieldMap = Util.propertyToField(propertyMap, csdlEntityType);
                //如果draft提交保存数据，保留值为null的字段
                if (entityToWrite instanceof OdataOfbizEntity && ((OdataOfbizEntity) entityToWrite).isDraft()) {
                    OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entityToWrite;
                    GenericValue draftGenericValue = odataOfbizEntity.getGenericValue();
                    for (Map.Entry<String, Object> entry : draftGenericValue.getAllFields().entrySet()) {
                        if (entry.getValue() == null && !fieldMap.containsKey(entry.getKey())) {
                            fieldMap.put(entry.getKey(), null);
                        }
                    }
                }
                //更新 lastModifiedDate
                if (delegator.getModelEntity(entityName).isField("lastModifiedDate")) {
                    fieldMap.put("lastModifiedDate", UtilDateTime.nowTimestamp());
                }
                updatedGenericValue = OdataProcessorHelper.updateGenericValue(dispatcher, delegator, csdlEntityType.getOfbizEntity(),
                        keyMap, fieldMap, csdlEntityType, userLogin);
                if (UtilValidate.isNotEmpty(csdlEntityType.getAttrEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrNumericEntityName()) ||
                        UtilValidate.isNotEmpty(csdlEntityType.getAttrDateEntityName())) {
                    //成功主对象更新之后 去处理Attribute
                    OdataProcessorHelper.updateAttrGenericValue(csdlEntityType, fieldMapToWrite, userLogin, keyMap, dispatcher, delegator);
                }
            }
            genericValue = updatedGenericValue;
        }
        updatedEntity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, genericValue, locale);
        List<Entity> updatedEntities = OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher,
                edmProvider, null, UtilMisc.toList(updatedEntity), locale, userLogin);
        updatedEntity = (OdataOfbizEntity) updatedEntities.get(0);

        // update semantic fields
        OdataProcessorHelper.updateSemanticFields(dispatcher, edmProvider, entityToWrite, updatedEntity, locale, userLogin);

        // 补齐entityToWrite的主键
        for (String key : keyMap.keySet()) {
            Property property = new Property();
            property.setName(key);
            property.setValue(ValueType.PRIMITIVE, keyMap.get(key));
            entityToWrite.addProperty(property);
        }
        return updatedEntity;
    }

    public static void deleteSingletonRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                        HttpServletRequest httpServletRequest,
                                                        OfbizAppEdmProvider edmProvider,
                                                        OfbizCsdlEntityType csdlEntityType,
                                                        String navigationPropertyName,
                                                        String singletonName,
                                                        Map<String, Object> navKeyMap,
                                                        GenericValue userLogin,
                                                        Locale locale) throws OfbizODataException {
        GenericValue genericValue = (GenericValue) OdataProcessorHelper.readSingletonObject(dispatcher, edmProvider,
                singletonName, httpServletRequest, userLogin);
        if (genericValue == null) {
            throw new OfbizODataException(csdlEntityType.getName() + " with singleton " + singletonName + " was not found.");
        }
        GenericValue nestedGenericValue = null;
        OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlEntityType.getNavigationProperty(navigationPropertyName).getTypeFQN());
        if (UtilValidate.isNotEmpty(navKeyMap)) { // navigation为非collection的时候，navKeyMap为null
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "httpServletRequest", httpServletRequest);
            nestedGenericValue = OdataProcessorHelper.readEntityData(odataContext, nestedCsdlEntityType, navKeyMap);
        }
        deleteEntitySetRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider, csdlEntityType,
                navigationPropertyName, genericValue, nestedGenericValue, userLogin, locale);
    }

    public static void deleteEntitySetRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                        HttpServletRequest httpServletRequest,
                                                        OfbizAppEdmProvider edmProvider,
                                                        OfbizCsdlEntityType csdlEntityType,
                                                        String navigationPropertyName,
                                                        Map<String, Object> keyMap,
                                                        Map<String, Object> navKeyMap,
                                                        GenericValue userLogin,
                                                        Locale locale) throws OfbizODataException {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "httpServletRequest", httpServletRequest);
        GenericValue genericValue = OdataProcessorHelper.readEntityData(odataContext, csdlEntityType, keyMap);
        GenericValue nestedGenericValue = null;
        OfbizCsdlEntityType nestedCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlEntityType.getNavigationProperty(navigationPropertyName).getTypeFQN());
        if (UtilValidate.isNotEmpty(navKeyMap)) { // navigation为非collection的时候，navKeyMap为null
            nestedGenericValue = OdataProcessorHelper.readEntityData(odataContext, nestedCsdlEntityType, navKeyMap);
        }
        deleteEntitySetRelatedEntityData(delegator, dispatcher, httpServletRequest, csdlEntityType,
                navigationPropertyName, genericValue, nestedGenericValue, userLogin, locale);
    }


    public static void deleteEntitySetRelatedEntityData(Delegator delegator, LocalDispatcher dispatcher,
                                                        HttpServletRequest httpServletRequest,
                                                        OfbizCsdlEntityType csdlEntityType,
                                                        String navigationPropertyName,
                                                        GenericValue genericValue,
                                                        GenericValue nestedGenericValue,
                                                        GenericValue userLogin,
                                                        Locale locale) throws OfbizODataException {
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationPropertyName);
        if (UtilValidate.isNotEmpty(csdlNavigationProperty.getHandler())) {
            GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
            String handler = csdlNavigationProperty.getHandler();
            try {
                groovyHelper.deleteNavigationData(handler, genericValue, nestedGenericValue);
            } catch (MissingMethodExceptionNoStack e) {
                Debug.logInfo(e.getMessage(), module);
                EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
                if (nestedGenericValue == null) { // navigation为非collection时
                    OdataProcessorHelper.clearNavigationLink(genericValue, relAlias, dispatcher, userLogin);
                } else {
                    OdataProcessorHelper.unbindNavigationLink(genericValue, nestedGenericValue, csdlNavigationProperty, dispatcher, userLogin);
                }
            }
        } else {
            EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
            List<String> relations = relAlias.getRelations();
            if (nestedGenericValue == null) { // navigation为非collection时
                OdataProcessorHelper.clearNavigationLink(genericValue, relAlias, dispatcher, userLogin);
            } else {
                OdataProcessorHelper.unbindNavigationLink(genericValue, nestedGenericValue, csdlNavigationProperty, dispatcher, userLogin);
            }
        }
    }

}
