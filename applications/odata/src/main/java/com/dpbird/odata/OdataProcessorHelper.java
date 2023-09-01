package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.processor.DataModifyActions;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlEnumMember;
import org.apache.olingo.commons.api.edm.provider.CsdlEnumType;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.uri.UriHelper;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.UriHelperImpl;
import org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.util.*;


public class OdataProcessorHelper {

    public static final String module = OdataProcessorHelper.class.getName();

    public static String getDraftEntity(OfbizAppEdmProvider edmProvider, EdmEntityType edmEntityType)
            throws OfbizODataException {
        FullQualifiedName fullQualifiedName = edmEntityType.getFullQualifiedName();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(fullQualifiedName);
        return csdlEntityType.getDraftEntityName();
    }

    public static String getEntityNameToFind(OfbizCsdlEntityType csdlEntityType,
                                             String sapContextId, OfbizAppEdmProvider edmProvider) {
        if (UtilValidate.isNotEmpty(sapContextId) && csdlEntityType.getDraftEntityName() != null) {
            return csdlEntityType.getDraftEntityName();
        } else {
            return csdlEntityType.getOfbizEntity();
        }
    }


    public static GenericValue readEntityData(Map<String, Object> odataContext,
                                              EdmEntityType edmEntityType, Map<String, Object> keyMap)
            throws OfbizODataException {
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        return readEntityData(odataContext, csdlEntityType, keyMap);
    }

    public static GenericValue readEntityData(Map<String, Object> odataContext,
                                              OfbizCsdlEntityType csdlEntityType, Map<String, Object> keyMap)
            throws OfbizODataException {
        // String entityName = edmEntityType.getName();
        Delegator delegator = (Delegator) odataContext.get("delegator");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        String sapContextId = (String) odataContext.get("sapContextId");
        GenericValue genericValue = null;
        try {
            String entityNameToFind = getEntityNameToFind(csdlEntityType, sapContextId, edmProvider);
            Map<String, Object> conditionMap = keyMap;
            if (keyMap.size() == 1 && keyMap.get("draftUUID") != null) {
                conditionMap = UtilMisc.toMap("draftUUID", keyMap.get("id"));
            }
            EntityCondition queryCondition = EntityCondition.makeCondition(conditionMap);
            if (UtilValidate.isNotEmpty(csdlEntityType.getEntityConditionStr()) && !csdlEntityType.getEntityConditionStr().contains("/")) {
                Debug.logWarning("Multi-segment conditions are not currently supported", module);
                Map<String, Object> entityTypeCondition = Util.parseConditionMap(csdlEntityType.getEntityConditionStr(), httpServletRequest);
                queryCondition = Util.appendCondition(queryCondition, EntityCondition.makeCondition(entityTypeCondition));
            }
            genericValue = EntityQuery.use(delegator).from(entityNameToFind).where(queryCondition).queryFirst();
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        return genericValue;
    }

    @Deprecated
    public static Object readSingletonData(Map<String, Object> odataContext, EdmSingleton edmSingleton)
            throws OfbizODataException {
        LocalDispatcher dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        Delegator delegator = dispatcher.getDelegator();
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        GenericValue userLogin = (GenericValue) odataContext.get("userLogin");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        return readSingletonObject(dispatcher, edmProvider, edmSingleton.getName(), httpServletRequest, userLogin);
    }

    // 通常来说，返回的是个GenericValue
    public static Object readSingletonObject(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                             String singletonName, HttpServletRequest httpServletRequest,
                                             GenericValue userLogin) throws OfbizODataException {
        OfbizCsdlSingleton csdlSingleton = (OfbizCsdlSingleton) edmProvider
                .getSingleton(OfbizAppEdmProvider.CONTAINER, singletonName);
        String handlerMethod = csdlSingleton.getHandlerMethod();
        String className = handlerMethod.substring(0, handlerMethod.lastIndexOf('.'));
        String methodName = handlerMethod.substring(handlerMethod.lastIndexOf('.') + 1);
        Object singletonObject;
        try {
            Map<String, Object> singletonContext = UtilMisc.toMap("delegator", dispatcher.getDelegator(),
                    "dispatcher", dispatcher, "edmProvider", edmProvider,
                    "userLogin", userLogin);
            Class<?> objectClass = Class.forName(className);
            Method method = objectClass.getMethod(methodName, HttpSession.class, Map.class);
            singletonObject = method.invoke(objectClass,
                    httpServletRequest.getSession(false),
                    singletonContext);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new OfbizODataException(OfbizMapOdata.ERROR_CODE_ONE, e.getMessage());
        }
        if (singletonObject == null) {
            throw new OfbizODataException(HttpStatusCode.NOT_FOUND.getStatusCode() + "", "The value of Singleton is null");
        }
        return singletonObject;
    }

    public static Object callFunctionActionMethod(Map<String, Object> oDataContext, String classMethod,
                                                  Map<String, Object> paramMap, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        String className = classMethod.substring(0, classMethod.lastIndexOf('.'));
        String methodName = classMethod.substring(classMethod.lastIndexOf('.') + 1);
        try {
            Class<?> objectClass = Class.forName(className);
            Method method = objectClass.getMethod(methodName, Map.class, Map.class, EdmBindingTarget.class);
            return method.invoke(objectClass, oDataContext, paramMap, edmBindingTarget);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            if (e instanceof InvocationTargetException) {
                InvocationTargetException invocationTargetException = (InvocationTargetException) e;
                throw (OfbizODataException) invocationTargetException.getTargetException();
            } else {
                throw new OfbizODataException(OfbizMapOdata.ERROR_CODE_TWO, e.getMessage());
            }
        }
    }


    public static OdataOfbizEntity genericValueToEntity(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                                        EdmEntityType edmEntityType, GenericValue genericValue,
                                                        Locale locale) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        return genericValueToEntity(dispatcher, edmProvider, csdlEntityType, genericValue, locale);
    }

    public static OdataOfbizEntity genericValueToEntity(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                                        OfbizCsdlEntityType csdlEntityType, GenericValue genericValue,
                                                        Locale locale) {
        Delegator delegator = dispatcher.getDelegator();
        String entityName = csdlEntityType.getOfbizEntity();
        boolean needI18n = entityName.endsWith("Type") || entityName.endsWith("Purpose") || "StatusItem".equals(entityName) || "Enumeration".equals(entityName)
                || "Geo".equals(entityName) || "Uom".equals(entityName) || "CustRequestResolution".equals(entityName);
        try {
            OdataOfbizEntity e1 = new OdataOfbizEntity(csdlEntityType, genericValue);
            e1.setType(csdlEntityType.getFullQualifiedNameString());
            e1.setRequestEntityTypeFQN(csdlEntityType.getFullQualifiedNameString());
            String pkFieldName = null;
            Object pkFieldValue = null;
            ModelEntity modelEntity = genericValue.getModelEntity();
            if ("DynamicViewEntity".equals(genericValue.getEntityName())) {
                //expand的结果或许是View 不能使用ViewModelEntity
                modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            }
            if (needI18n) {
                pkFieldName = modelEntity.getPkFieldNames().get(0);
            }
            Iterator<ModelField> fieldIterator = modelEntity.getFieldsIterator();
            // 获取所有的外键字段，以及Enumeration字段
//            Set<String> fkFieldNames = Util.getEntityFk(modelEntity);
            List<String> automaticFieldNames = modelEntity.getAutomaticFieldNames(); // lastUpdatedStamp, lastUpdatedTxStamp, createdStamp, createdTxStamp
            while (fieldIterator.hasNext()) {
                ModelField field = fieldIterator.next();
                String fieldName = field.getName();
                //edmConfig未定义、stamp公共字段、空值，跳过
                if ((csdlEntityType.getProperty(fieldName) == null && csdlEntityType.getPropertyFromField(fieldName) == null) ||
                        automaticFieldNames.contains(fieldName) ||
                        genericValue.get(fieldName) == null) {
                    continue;
                }
                Object fieldValue = genericValue.get(fieldName);
                if (needI18n) {
                    if (fieldName.equals(pkFieldName)) {
                        pkFieldValue = fieldValue;
                    }
                }
                Property theProperty = null;
                // 对于dynamicview中，如果字段是计算项，会自动生成scale为9的数字，需要限制
                if ("fixed-point".equals(field.getType())) {
                    BigDecimal bigDecimalValue = (BigDecimal) fieldValue;
                    fieldValue = bigDecimalValue.setScale(6, BigDecimal.ROUND_UNNECESSARY);
                } else if ("currency-amount".equals(field.getType())) {
                    BigDecimal bigDecimalValue = (BigDecimal) fieldValue;
                    fieldValue = bigDecimalValue.setScale(2, BigDecimal.ROUND_UNNECESSARY);
                } else if ("currency-precise".equals(field.getType())) {
                    BigDecimal bigDecimalValue = (BigDecimal) fieldValue;
                    fieldValue = bigDecimalValue.setScale(3, BigDecimal.ROUND_UNNECESSARY);
                }
                if (needI18n) {
                    if (fieldName.equals(pkFieldName)) {
                        pkFieldValue = fieldValue;
                    }
                    if ("description".equals(fieldName)) {
                        // Debug.logInfo("looking for i18n " + entityName + ".description" + "." + pkFieldValue, module);
                        Object uiLabelValue = Util.getUiLabelMap(locale).get(entityName + ".description" + "." + pkFieldValue);
                        if (!uiLabelValue.equals(entityName + ".description." + pkFieldValue)) {
                            fieldValue = uiLabelValue;
                        }
                    } else if ("geoName".equals(fieldName)) { // Geo对象没有description字段，是geoName
                        // Debug.logInfo("looking for i18n " + entityName + ".geoName" + "." + pkFieldValue, module);
                        Object uiLabelValue = Util.getUiLabelMap(locale).get(entityName + ".geoName" + "." + pkFieldValue);
                        if (!uiLabelValue.equals(entityName + ".geoName." + pkFieldValue)) {
                            fieldValue = uiLabelValue;
                        }
                    }
                }
                CsdlProperty csdlProperty = csdlEntityType.getProperty(fieldName);
                if (UtilValidate.isEmpty(csdlProperty)) {
                    csdlProperty = csdlEntityType.getPropertyFromField(fieldName);
                    fieldName = csdlProperty.getName();
                }
                FullQualifiedName propertyFqn = csdlProperty.getTypeAsFQNObject();
                if (csdlEntityType.isAutoEnum()) {
                    theProperty = getPropertyFromIndicator(delegator, field, fieldValue);
                }
                CsdlEnumType csdlEnumType = edmProvider.getEnumType(propertyFqn);
                if (theProperty == null) {
                    if (csdlEnumType != null
                            && fieldValue != null) {
                        theProperty = getPropertyFromEnumField(delegator, edmProvider, propertyFqn, fieldName, (String) fieldValue);
                    } else if ("Edm.Boolean".equals(csdlProperty.getType())
                            && fieldValue != null) {
                        if ("Y".equals(fieldValue)) {
                            theProperty = new Property("Edm.Boolean", fieldName, ValueType.PRIMITIVE, true);
                        } else if ("N".equals(fieldValue)) {
                            theProperty = new Property("Edm.Boolean", fieldName, ValueType.PRIMITIVE, false);
                        }
                    }
                }
                if (theProperty == null) {
                    theProperty = new Property(propertyFqn.getFullQualifiedNameAsString(), fieldName, ValueType.PRIMITIVE, fieldValue);
                }
                e1.addProperty(theProperty);
            }
            //处理BaseType
            if (UtilValidate.isNotEmpty(csdlEntityType.getBaseType()) || csdlEntityType.isHasDerivedEntity()) {
                mergeEntity(dispatcher, edmProvider, e1, csdlEntityType, locale);
            }
            //处理Decimal字段的精度，根据Edm中Property的Scale
            for (Property property : e1.getProperties()) {
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(property.getName());
                if (ofbizCsdlProperty != null && ofbizCsdlProperty.getScale() != null && ofbizCsdlProperty.getType().contains("Decimal")) {
                    BigDecimal newPropertyValue = new BigDecimal(property.getValue().toString()).setScale(ofbizCsdlProperty.getScale(), BigDecimal.ROUND_DOWN);
                    property.setValue(ValueType.PRIMITIVE, newPropertyValue);
                }
            }
            //添加ETag
            e1.setETag(Util.getGenericValueETag(genericValue));
            return e1;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static OdataOfbizEntity genericValueToEntity(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                                        EdmBindingTarget edmBindingTarget, EdmEntityType edmTypeFilter,
                                                        GenericValue genericValue, Locale locale) throws OfbizODataException {
        EdmEntityType edmEntityType;
        if (edmTypeFilter != null) {
            edmEntityType = edmTypeFilter;
        } else {
            edmEntityType = edmBindingTarget.getEntityType();
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OdataOfbizEntity odataOfbizEntity =
                genericValueToEntity(dispatcher, edmProvider, csdlEntityType, genericValue, locale);
        try {
            if (odataOfbizEntity.getId() == null) {
                final UriHelper uriHelper = new UriHelperImpl();
                String idName = edmBindingTarget == null ? edmEntityType.getName() : edmBindingTarget.getName();
                odataOfbizEntity.setId(URI.create(idName + '(' + uriHelper.buildKeyPredicate(edmEntityType, odataOfbizEntity) + ')'));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        /****** set edit link ********************/
        Link link = new Link();
        if (odataOfbizEntity.getId() != null) { // TODO:要检查一下为什么会有id为null的情况
            String linkHref = odataOfbizEntity.getId().toString();
            link.setHref(linkHref);
        }
        link.setTitle(odataOfbizEntity.getTitle());
        link.setType(odataOfbizEntity.getType());
        odataOfbizEntity.setEditLink(link);
        /*****************************************/
        return odataOfbizEntity;
    }

    private static Property getPropertyFromIndicator(Delegator delegator, ModelField field, Object fieldValue) {
        String fieldType = field.getType();
        String fieldName = field.getName();

        if ("indicator".equals(fieldType)) {
            if ("gender".equals(fieldName)) {
                if ("M".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 1);
                } else if ("F".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 2);
                }
            } else if ("maritalStatus".equals(fieldName)) {
                if ("S".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 1);
                } else if ("M".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 2);
                } else if ("P".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 3);
                } else if ("D".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 4);
                } else if ("W".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 5);
                }
            } else if ("priority".equals(fieldName)) {
                if ("1".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 1);
                } else if ("2".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 2);
                } else if ("3".equals(fieldValue)) {
                    return new Property(null, fieldName, ValueType.PRIMITIVE, 3);
                }
            } else {
                if ("Y".equals(fieldValue)) {
                    return new Property("Edm.Boolean", fieldName, ValueType.PRIMITIVE, true);
                } else if ("N".equals(fieldValue)) {
                    return new Property("Edm.Boolean", fieldName, ValueType.PRIMITIVE, false);
                }
            }
        }
        return null;
    }

    private static Property getPropertyFromEnumField(Delegator delegator, OfbizAppEdmProvider edmProvider, FullQualifiedName fieldFqn, String fieldName, String enumId)
            throws GenericEntityException, OfbizODataException {
        OfbizCsdlEnumType ofbizCsdlEnumType = (OfbizCsdlEnumType) edmProvider.getEnumType(fieldFqn);
        String enumOfbizEntity = ofbizCsdlEnumType.getOfbizEntity();
        if (enumOfbizEntity != null) {
            ModelEntity enumOfbizModelEntity = delegator.getModelEntity(enumOfbizEntity);
            String pkField = enumOfbizModelEntity.getFirstPkFieldName();
            if (enumOfbizModelEntity.getAllFieldNames().contains("sequenceId")) {
                GenericValue enumeration = delegator.findOne(enumOfbizEntity, UtilMisc.toMap(pkField, enumId), true);
                String sequenceId = enumeration.getString("sequenceId");
                // 主要将01、02等转换成1、2
                return new Property(fieldFqn.getFullQualifiedNameAsString(), fieldName, ValueType.ENUM, Integer.parseInt(sequenceId));
            } else {
                List<GenericValue> enumerations = delegator.findList(enumOfbizEntity, ofbizCsdlEnumType.getEntityCondition(), null, UtilMisc.toList(pkField), null, false);
                int index = 0;
                for (GenericValue enumeration : enumerations) {
                    index++;
                    String pkValue = enumeration.getString(pkField);
                    if (pkValue.equals(enumId)) {
                        return new Property(fieldFqn.getFullQualifiedNameAsString(), fieldName, ValueType.ENUM, index);
                    }
                }
            }
        } else {
            List<CsdlEnumMember> csdlEnumMembers = ofbizCsdlEnumType.getMembers();
            for (CsdlEnumMember csdlEnumMember : csdlEnumMembers) {
                if (csdlEnumMember.getName().equals(enumId)) {
                    return new Property(fieldFqn.getFullQualifiedNameAsString(), fieldName, ValueType.ENUM, Long.valueOf(csdlEnumMember.getValue()));
                }
            }
        }
        return null;
    }

    /**
     * 处理BaseType，合并实体字段
     */
    private static void mergeEntity(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider, OdataOfbizEntity ofbizEntity,
                                    OfbizCsdlEntityType csdlEntityType, Locale locale) throws OfbizODataException, GenericEntityException {
        Delegator delegator = dispatcher.getDelegator();
        OfbizCsdlEntityType mergeCsdlEntityType = null;
        if (csdlEntityType.isHasDerivedEntity()) {
            //获取DerivedEntityType，并转换类型
            mergeCsdlEntityType = getDerivedType(edmProvider, delegator, ofbizEntity, csdlEntityType);
            if (mergeCsdlEntityType != null) {
                ofbizEntity.setType(mergeCsdlEntityType.getFullQualifiedNameString());
            }
        } else if (UtilValidate.isNotEmpty(csdlEntityType.getBaseType())) {
            //获取BaseEntityType
            mergeCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(csdlEntityType.getBaseType()));
        }
        if (mergeCsdlEntityType == null) {
            return;
        }
        //合并所有的字段
        GenericValue genericValue = ofbizEntity.getGenericValue();
        GenericValue mergeGenericValue = genericValue.getRelatedOne(mergeCsdlEntityType.getOfbizEntity(), true);
        if (UtilValidate.isNotEmpty(mergeGenericValue)) {
            OdataOfbizEntity mergeEntity = (OdataOfbizEntity) Util.mapToEntity(mergeCsdlEntityType, new HashMap<>(mergeGenericValue));
            appendNonEntityFields(null, delegator, dispatcher, edmProvider,
                    null, UtilMisc.toList(mergeEntity), locale, Util.getSystemUser(delegator));
            for (Property property : mergeEntity.getProperties()) {
                CsdlProperty csdlProperty = mergeCsdlEntityType.getProperty(property.getName());
                FullQualifiedName propertyFqn = csdlProperty.getTypeAsFQNObject();
                CsdlEnumType csdlEnumType = edmProvider.getEnumType(propertyFqn);
                if (csdlEnumType != null) {
                    property = getPropertyFromEnumField(delegator, edmProvider, propertyFqn, property.getName(), (String) property.getValue());
                }
                ofbizEntity.addProperty(property);
            }
        }
    }

    public static OfbizCsdlEntityType getDerivedType(OfbizAppEdmProvider edmProvider, Delegator delegator,
                                                      GenericValue genericValue, OfbizCsdlEntityType csdlEntityType) throws OfbizODataException {
        if ("DynamicViewEntity".equals(genericValue.getEntityName())) {
            return null;
        }
        ModelEntity typeModelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity() + "Type");
        String typeIdName = typeModelEntity.getOnlyPk().getName();
        String typeIdValue = genericValue.getString(typeIdName);
        Map<String, Object> typePrimaryKey = UtilMisc.toMap(typeModelEntity.getOnlyPk().getName(), typeIdValue);
        try {
            GenericValue typeGenericValue = delegator.findOne(typeModelEntity.getEntityName(), typePrimaryKey, true);
            if (UtilValidate.isEmpty(typeGenericValue)) {
                //没有衍生类型 保持原始类型
                return null;
            }
            String derivedEntityName = null;
            if (typeGenericValue.getBoolean("hasTable")) {
                derivedEntityName = Util.underlineToUpperHump(typeIdValue);
            }
            for (CsdlEntityType csdlEntity : edmProvider.cachedSchema.getEntityTypes()) {
                OfbizCsdlEntityType currET = (OfbizCsdlEntityType) csdlEntity;
                if (currET.getBaseTypeFQN() != null &&
                        csdlEntityType.getName().equals(currET.getBaseTypeFQN().getName()) &&
                        currET.getOfbizEntity().equals(derivedEntityName)) {
                    return currET;
                }
            }
            return null;
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }

    }

    public static OfbizCsdlEntityType getDerivedType(OfbizAppEdmProvider edmProvider, Delegator delegator,
                                                      OdataOfbizEntity ofbizEntity, OfbizCsdlEntityType csdlEntityType) throws OfbizODataException {
        return getDerivedType(edmProvider, delegator, ofbizEntity.getGenericValue(), csdlEntityType);

    }

    public static GenericValue getGenericValue(Delegator delegator, OfbizAppEdmProvider edmProvider,
                                               EdmEntitySet edmEntitySet, List<UriParameter> keyPredicates,
                                               String sapContextId)
            throws OfbizODataException {
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        return getGenericValue(delegator, edmProvider, edmEntityType, keyPredicates, sapContextId);
    }

    public static GenericValue getGenericValue(Delegator delegator, OfbizAppEdmProvider edmProvider,
                                               EdmEntityType edmEntityType, List<UriParameter> keyPredicates,
                                               String sapContextId)
            throws OfbizODataException {
        Map<String, Object> pk = Util.uriParametersToMap(keyPredicates, edmEntityType, edmProvider);
        return getGenericValue(delegator, edmProvider, edmEntityType, pk, sapContextId);
    }

    public static GenericValue getGenericValue(Delegator delegator, OfbizAppEdmProvider edmProvider,
                                               EdmEntityType edmEntityType, Map<String, Object> pk,
                                               String sapContextId)
            throws OfbizODataException {
        GenericValue genericValue = null;
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        String entityName = csdlEntityType.getOfbizEntity();
        String draftEntityName = csdlEntityType.getDraftEntityName();
        try {
            if (draftEntityName == null || UtilValidate.isEmpty(sapContextId)) { // 非StickySession模式
                genericValue = delegator.findOne(entityName, true, pk);
            } else { // StickySession模式
                ModelEntity draftModelEntity = delegator.getModelEntity(draftEntityName);
                ModelField modelField = draftModelEntity.getField("parentDraftUUID");
                if (modelField == null) { // it's main entity
                    genericValue = delegator.findOne(draftEntityName, true,
                            UtilMisc.toMap("draftUUID", sapContextId));
                } else { // it's sub entity
                    List<GenericValue> draftGenericValues = delegator.findByAnd(draftEntityName,
                            UtilMisc.toMap("parentDraftUUID", sapContextId), null, false);
                    if (UtilValidate.isEmpty(draftGenericValues)) {
                        genericValue = EntityUtil.getFirst(draftGenericValues);
                    }
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        return genericValue;
    }

    public static List<Entity> appendSemanticFields(HttpServletRequest httpServletRequest, Delegator delegator,
                                                    LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                                    Map<String, QueryOption> queryOptions, List<Entity> entityList,
                                                    Locale locale, GenericValue userLogin) throws OfbizODataException {
        OdataOfbizEntity firstEntity = (OdataOfbizEntity) entityList.get(0);
        String entityType = UtilValidate.isNotEmpty(firstEntity.getRequestEntityTypeFQN()) ?
                firstEntity.getRequestEntityTypeFQN() : firstEntity.getType();
        FullQualifiedName entityFqn = new FullQualifiedName(entityType);
        OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(entityFqn);
        if (ofbizCsdlEntityType.isHasRelField()) {
            try {
                entityList = proceEntityListWithRelField(delegator, ofbizCsdlEntityType, entityList);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
        }
        if (UtilValidate.isNotEmpty(ofbizCsdlEntityType.getAttrEntityName()) ||
                UtilValidate.isNotEmpty(ofbizCsdlEntityType.getAttrNumericEntityName()) ||
                UtilValidate.isNotEmpty(ofbizCsdlEntityType.getAttrDateEntityName())) {
            try {
                entityList = proceEntityListWithAttribute(delegator, ofbizCsdlEntityType, entityList);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
        }
        String handlerClass = ofbizCsdlEntityType.getHandlerClass();
        Debug.logInfo("start handling semantic fields with groovy", module);
        if (handlerClass != null) { // TODO: 今后要用存在数据库中的groovy代码来处理数据
            entityList = procEntityListWithHandler(httpServletRequest, delegator, dispatcher, queryOptions,
                    handlerClass, entityList, locale, userLogin);
        }
        return entityList;
    }

    public static List<Entity> appendNonEntityFields(HttpServletRequest httpServletRequest, Delegator delegator,
                                                     LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                                     Map<String, QueryOption> queryOptions, List<Entity> entityList,
                                                     Locale locale, GenericValue userLogin) throws OfbizODataException {
        if (UtilValidate.isEmpty(entityList)) {
            return entityList;
        }
        entityList = appendSemanticFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, entityList, locale, userLogin);
        return entityList;
    }

    public static List<Entity> proceEntityListWithRelField(Delegator delegator,
                                                           OfbizCsdlEntityType ofbizCsdlEntityType,
                                                           List<Entity> entityList) throws GenericEntityException {
        String entityName = ofbizCsdlEntityType.getOfbizEntity();
        ModelEntity modelEntity = delegator.getModelEntity(entityName);
        List<OfbizCsdlProperty> relFieldProperties = new ArrayList<>();
        List<CsdlProperty> csdlProperties = ofbizCsdlEntityType.getProperties();
        for (CsdlProperty csdlProperty : csdlProperties) {
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
            if (UtilValidate.isEmpty(ofbizCsdlProperty.getRelAlias())) {
                continue;
            }
            relFieldProperties.add(ofbizCsdlProperty);
        }
        for (Entity entity : entityList) {
            OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
            if (odataOfbizEntity.isDraft()) { // 这些entity数据来自draft，所以，不需要处理了
                return entityList;
            }
            GenericValue genericValue = odataOfbizEntity.getGenericValue();
            for (OfbizCsdlProperty relFieldProperty : relFieldProperties) {
                EntityTypeRelAlias relAlias = relFieldProperty.getRelAlias();
                List<String> relations = relAlias.getRelations();
                Iterator<String> it = relations.iterator();
                ModelEntity theModelEntity = modelEntity;
//                GenericValue relGenericValue = genericValue;
                GenericValue relGenericValue = delegator.findOne(theModelEntity.getEntityName(), true, genericValue.getPrimaryKey());
                while (it.hasNext()) {
                    String relation = it.next();
                    ModelRelation modelRelation = theModelEntity.getRelation(relation);
                    if (modelRelation.getType().contains("one")) {
                        relGenericValue = relGenericValue.getRelatedOne(relation, true);
                    } else {
                        Map<String, Object> relFieldMap = relAlias.getRelationsFieldMap().get(relation);
                        List<GenericValue> relGenericValues = relGenericValue.getRelated(relation, relFieldMap, null, true);
                        if (UtilValidate.isNotEmpty(relGenericValues) && relGenericValues.size() > 0) {
                            Map<String, Object> allFields = relGenericValues.get(0).getAllFields();
                            if (allFields.containsKey("fromDate") || allFields.containsKey("thruDate")) {
                                relGenericValues = EntityUtil.filterByDate(relGenericValues);
                            }
                        }
                        relGenericValue = EntityUtil.getFirst(relGenericValues);
                    }
                    if (relGenericValue == null) {
                        break;
                    }
                    theModelEntity = delegator.getModelEntity(modelRelation.getRelEntityName());
                }
                if (relGenericValue == null) {
                    continue;
                }
                Object fieldValue;
                if (relFieldProperty.getType().contains("Boolean")) {
                    fieldValue = relGenericValue.getBoolean(relFieldProperty.getOfbizFieldName());
                } else {
                    fieldValue = relGenericValue.get(relFieldProperty.getOfbizFieldName());
                }
                Property theProperty = new Property(relFieldProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(), relFieldProperty.getName(), ValueType.PRIMITIVE, fieldValue);
                entity.addProperty(theProperty);
            }
        }
        return entityList;
    }

    public static List<Entity> proceEntityListWithAttribute(Delegator delegator,
                                                            OfbizCsdlEntityType ofbizCsdlEntityType,
                                                            List<Entity> entityList) throws GenericEntityException {
        String attrEntityName = ofbizCsdlEntityType.getAttrEntityName();
        String attrNumericEntityName = ofbizCsdlEntityType.getAttrNumericEntityName();
        String attrDateEntityName = ofbizCsdlEntityType.getAttrDateEntityName();
        List<OfbizCsdlProperty> attrProperties = new ArrayList<>();
        List<CsdlProperty> csdlProperties = ofbizCsdlEntityType.getProperties();
        for (CsdlProperty csdlProperty : csdlProperties) {
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
            if (ofbizCsdlProperty.isAttribute() || ofbizCsdlProperty.isNumericAttribute() || ofbizCsdlProperty.isDateAttribute()) {
                attrProperties.add(ofbizCsdlProperty);
            }
        }
        for (Entity entity : entityList) {
            OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
            if (odataOfbizEntity.isDraft() || odataOfbizEntity.getGenericValue() == null) { // 这些entity数据来自draft，所以，不需要处理attribute了
                return entityList;
            }
            GenericValue genericValue = odataOfbizEntity.getGenericValue();
            for (OfbizCsdlProperty attrProperty : attrProperties) {
                Map<String, Object> attrMapKey = new HashMap<>(genericValue.getPrimaryKey());
                String attrPropertyName = attrProperty.getName();
                attrMapKey.put("attrName", attrPropertyName);
                //查询attrEntity 或者是attrNumericEntity 或者是attrDateEntity
                String attrEntity = attrProperty.isAttribute() ? attrEntityName :
                        attrProperty.isNumericAttribute() ? attrNumericEntityName : attrDateEntityName;
                GenericValue attrGenericValue = delegator.findOne(attrEntity, attrMapKey, true);
                if (attrGenericValue == null) {
                    continue;
                }

                if (attrProperty.isAttribute()) {
                    if (attrProperty.getType().contains("Boolean")) {
                        Boolean attrValue = attrGenericValue.getBoolean("attrValue");
                        entity.addProperty(new Property(attrProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(),
                                attrPropertyName, ValueType.PRIMITIVE, attrValue));
                    } else {
                        String attrValue = attrGenericValue.getString("attrValue");
                        entity.addProperty(new Property(attrProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(),
                                attrPropertyName, ValueType.PRIMITIVE, attrValue));
                    }
                } else if (attrProperty.isNumericAttribute()) {
                    BigDecimal attrValue = attrGenericValue.getBigDecimal("attrValue");
                    if ("Edm.Int64".equals(attrProperty.getType())) {
                        entity.addProperty(new Property(attrProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(),
                                attrPropertyName, ValueType.PRIMITIVE, attrValue.longValue()));
                    } else {
                        if (UtilValidate.isNotEmpty(attrProperty.getScale())) {
                            //根据Edm的定义设置精度
                            attrValue = attrValue.setScale(attrProperty.getScale(), BigDecimal.ROUND_DOWN);
                        }
                        entity.addProperty(new Property(attrProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(),
                                attrPropertyName, ValueType.PRIMITIVE, attrValue.stripTrailingZeros()));
                    }
                } else if (attrProperty.isDateAttribute()) {
                    Timestamp attrValue = attrGenericValue.getTimestamp("attrValue");
                    entity.addProperty(new Property(attrProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(),
                            attrPropertyName, ValueType.PRIMITIVE, attrValue));
                }
            }
        }
        return entityList;
    }

    public static List<Entity> procEntityListWithHandler(HttpServletRequest httpServletRequest,
                                                         Delegator delegator, LocalDispatcher dispatcher,
                                                         Map<String, QueryOption> queryOptions,
                                                         String handler, List<Entity> entityList,
                                                         Locale locale, GenericValue userLogin) throws OfbizODataException {
        SelectOption selectOption = null;
        if (queryOptions != null) {
            selectOption = (SelectOption) queryOptions.get("selectOption");
        }
        GroovyHelper groovyHelper = new GroovyHelper(delegator, dispatcher, userLogin, locale, httpServletRequest);
        try {
            entityList = groovyHelper.generateEntityListFields(handler, httpServletRequest, entityList, selectOption);
        } catch (MissingMethodExceptionNoStack e) {
            Debug.logInfo(e.getMessage(), module);
        }
        return entityList;
    }

    public static GenericValue createGenericValue(LocalDispatcher dispatcher, String serviceName, String entityName, Map<String, Object> fieldMap)
            throws GenericServiceException, GenericEntityException, OfbizODataException {
        Map<String, Object> result = dispatcher.runSync(serviceName, fieldMap);
        if (ServiceUtil.isError(result)) {
            throw new OfbizODataException(ServiceUtil.getErrorMessage(result));
        }
        // 光运行了创建entity的service，我们都还不知道是哪个具体的数据被创建了，所以需要获取新创建的entity的pk，然后从数据库获取这个新创建的GenericValue
        Map<String, Object> pkMap;
        if (result.containsKey("pkMap")) {
            pkMap = (Map<String, Object>) result.get("pkMap");
        } else {
            pkMap = Util.retrievePkMap(dispatcher.getDelegator(), fieldMap, result, entityName);
        }
        return dispatcher.getDelegator().findOne(entityName, pkMap, true);
    }

    public static GenericValue createGenericValue(LocalDispatcher dispatcher, Delegator delegator,
                                                  OfbizCsdlEntityType csdlEntityType, Entity entityToCreate,
                                                  OfbizAppEdmProvider edmProvider, GenericValue userLogin, HttpServletRequest request)
            throws OfbizODataException {
        GenericValue newGenericValue = null;
        String entityName = csdlEntityType.getOfbizEntity();
        try {
            /********** 获取系统所有的service **************************************************/
            String serviceName = Util.getEntityActionService(csdlEntityType, entityName, "create", delegator);
            ModelService modelService = dispatcher.getDispatchContext().getModelService(serviceName);
            Map<String, Object> propertyMap = Util.entityToMap(delegator, edmProvider, entityToCreate);
            //添加DefaultValue
            for (Map.Entry<String, Object> entry : csdlEntityType.getDefaultValueProperties().entrySet()) {
                propertyMap.putIfAbsent(entry.getKey(), entry.getValue());
            }
            //转成数据库字段
            Map<String, Object> fieldMap = Util.propertyToField(propertyMap, csdlEntityType);
            fieldMap = Util.prepareServiceParameters(modelService, fieldMap);
            if (serviceName != null) { // ofbiz存在创建这个对象的service，那就建议用户调用service，不要直接创建
                if (userLogin == null) {
                    Debug.logInfo("------------- using system userlogin to create object", module);
                    userLogin = delegator.findOne("UserLogin", true, UtilMisc.toMap("userLoginId", "system"));
                }
                fieldMap.put("userLogin", userLogin);
                Set<String> inParamNames = modelService.getInParamNames();
                if (inParamNames.contains("fromDate")) {
                    if (!fieldMap.containsKey("fromDate") || fieldMap.get("fromDate") == null) {
                        fieldMap.put("fromDate", UtilDateTime.nowTimestamp());
                    }
                }
                Map<String, Object> entityTypeConditionMap = Util.parseConditionMap(csdlEntityType.getEntityConditionStr(), request);
                if (UtilValidate.isNotEmpty(entityTypeConditionMap)) {
                    fieldMap.putAll(entityTypeConditionMap);
                }
                newGenericValue = createGenericValue(dispatcher, serviceName, csdlEntityType.getOfbizEntity(), fieldMap);
            } else {
                newGenericValue = delegator.makeValue(entityName, fieldMap);
                newGenericValue.create();
            }
        } catch (GenericServiceException | GenericEntityException e) {
            Throwable originalException = Util.getOriginalException(e);
            throw new OfbizODataException(originalException.getMessage());
        }

        if (newGenericValue == null) {
            throw new OfbizODataException("new entity was not created",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.toString());
        }
        return newGenericValue;
    }

    /**
     * 创建对象的Attribute
     *
     * @param csdlEntityType 定义entityType
     * @param entityToCreate 要创建的Entity
     * @param userLogin      userLogin
     * @param pkMap          主对象的主键
     * @return serviceResult
     */
    public static Map<String, Object> createAttrGenericValue(OfbizCsdlEntityType
                                                                     csdlEntityType, Entity entityToCreate,
                                                             GenericValue userLogin, Map<String, Object> pkMap, LocalDispatcher dispatcher) throws
            OfbizODataException {
        //获取Attribute service
        String attributeServiceName = null;
        String attributeNumericServiceName = null;
        String attributeDateServiceName = null;
        if (csdlEntityType.getAttrEntityName() != null) {
            attributeServiceName = Util.getEntityActionService(null, csdlEntityType.getAttrEntityName(), "create", dispatcher.getDelegator());
        }
        if (csdlEntityType.getAttrNumericEntityName() != null) {
            attributeNumericServiceName = Util.getEntityActionService(null, csdlEntityType.getAttrNumericEntityName(), "create", dispatcher.getDelegator());
        }
        if (csdlEntityType.getAttrDateEntityName() != null) {
            attributeDateServiceName = Util.getEntityActionService(null, csdlEntityType.getAttrDateEntityName(), "create", dispatcher.getDelegator());
        }
        Map<String, Object> resultMap = new HashMap<>();
        try {
            //获取Entity定义中所有为Attribute的Property
            List<String> csdlAttrPropertyNames = new ArrayList<>();
            for (CsdlProperty csdlProperty : csdlEntityType.getProperties()) {
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
                if (ofbizCsdlProperty.isAttribute() || ofbizCsdlProperty.isNumericAttribute() || ofbizCsdlProperty.isDateAttribute()) {
                    csdlAttrPropertyNames.add(ofbizCsdlProperty.getName());
                }
            }
            //如果创建的Entity中有Attribute字段 就创建一条Attr记录
            Map<String, Object> serviceMap = new HashMap<>(pkMap);
            List<Property> createProperties = entityToCreate.getProperties();
            for (Property createProperty : createProperties) {
                if (csdlAttrPropertyNames.contains(createProperty.getName()) && createProperty.getValue() != null) {
                    OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(createProperty.getName());
                    String serviceName = ofbizCsdlProperty.isAttribute() ? attributeServiceName :
                            ofbizCsdlProperty.isNumericAttribute() ? attributeNumericServiceName : attributeDateServiceName;
                    Object attrValue = createProperty.getValue();
                    if (attrValue != null && ofbizCsdlProperty.getType().contains("Boolean")) {
                        if (!"Y".equals(attrValue.toString()) && !"N".equals(attrValue.toString())) {
                            attrValue = Boolean.parseBoolean(attrValue.toString()) ? "Y" : "N";
                        }
                    }
                    serviceMap.put("attrName", createProperty.getName());
                    serviceMap.put("attrValue", attrValue);
                    serviceMap.put("userLogin", userLogin);
                    resultMap = dispatcher.runSync(serviceName, serviceMap);
                }
            }
        } catch (GenericServiceException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return resultMap;
    }

    public static void updateAttrGenericValue(OfbizCsdlEntityType
                                                      csdlEntityType, Map<String, Object> fieldMapToWrite,
                                              GenericValue userLogin, Map<String, Object> pkMap, LocalDispatcher dispatcher, Delegator delegator) throws
            OfbizODataException {
        if (UtilValidate.isEmpty(fieldMapToWrite)) {
            return;
        }
        try {
            //获取Entity定义中的AttributeProperty
            List<String> csdlAttrPropertyNames = new ArrayList<>();
            for (CsdlProperty csdlProperty : csdlEntityType.getProperties()) {
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
                if (ofbizCsdlProperty.isAttribute() || ofbizCsdlProperty.isNumericAttribute() || ofbizCsdlProperty.isDateAttribute()) {
                    csdlAttrPropertyNames.add(csdlProperty.getName());
                }
            }
            Set<Map.Entry<String, Object>> entrySet = fieldMapToWrite.entrySet();
            for (Map.Entry<String, Object> entry : entrySet) {
                //如果这个字段是Attribute
                if (csdlAttrPropertyNames.contains(entry.getKey())) {
                    OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(entry.getKey());
                    String attrEntityName = ofbizCsdlProperty.isAttribute() ? csdlEntityType.getAttrEntityName() :
                            ofbizCsdlProperty.isNumericAttribute() ? csdlEntityType.getAttrNumericEntityName() : csdlEntityType.getAttrDateEntityName();
                    Map<String, Object> attrPkMap = new HashMap<>(pkMap);
                    attrPkMap.put("attrName", entry.getKey());
                    GenericValue attributeEntity = delegator.findOne(attrEntityName, attrPkMap, true);
                    Object attrValue = entry.getValue();
                    if (attrValue != null && ofbizCsdlProperty.getType().contains("Boolean")) {
                        if (!"Y".equals(attrValue.toString()) && !"N".equals(attrValue.toString())) {
                            attrValue = Boolean.parseBoolean(attrValue.toString()) ? "Y" : "N";
                        }
                    }
                    Map<String, Object> paramMap = new HashMap<>(attrPkMap);
                    paramMap.put("attrValue", attrValue);
                    paramMap.put("userLogin", userLogin);

                    //这个Attribute不存在并且value有值, 创建
                    if (UtilValidate.isEmpty(attributeEntity) && entry.getValue() != null) {
                        String createService = Util.getEntityActionService(null, attrEntityName, "create", delegator);
                        dispatcher.runSync(createService, paramMap);
                    }
                    //Attribute已经存在, 更新或删除
                    if (UtilValidate.isNotEmpty(attributeEntity)) {
                        String updateService = Util.getEntityActionService(null, attrEntityName, "update", delegator);
                        String deleteService = Util.getEntityActionService(null, attrEntityName, "delete", delegator);
                        String serviceName = entry.getValue() == null ? deleteService : updateService;
                        dispatcher.runSync(serviceName, paramMap);
                    }
                }
            }
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }

    }

    public static void removeGenericValueFK(LocalDispatcher dispatcher, Delegator delegator, String entityName,
                                            Map<String, Object> keyMap, ModelRelation modelRelation, OfbizCsdlEntityType csdlEntityType,
                                            GenericValue userLogin) throws OfbizODataException {
        Map<String, Object> serviceMap = new HashMap<>();
        for (ModelKeyMap relationKeyMap : modelRelation.getKeyMaps()) {
            serviceMap.put(relationKeyMap.getFieldName(), null);
        }
        OdataProcessorHelper.updateGenericValue(dispatcher, delegator, entityName, keyMap, serviceMap, csdlEntityType, userLogin);
    }

    public static GenericValue updateGenericValue(LocalDispatcher dispatcher, Delegator delegator, String entityName,
                                                  Map<String, Object> keyMap, Map<String, Object> fieldMap, OfbizCsdlEntityType csdlEntityType,
                                                  GenericValue userLogin) throws OfbizODataException {
        try {
            GenericValue genericValue = delegator.findOne(entityName, keyMap, true);
            String serviceName = Util.getEntityActionService(csdlEntityType, entityName, "update", delegator);

            ModelService modelService = dispatcher.getDispatchContext().getModelService(serviceName);
            if (csdlEntityType != null) {
                fieldMap = Util.propertyToField(fieldMap, csdlEntityType);
            }
            Map<String, Object> serviceInMap = Util.prepareServiceParameters(modelService, fieldMap);
            serviceInMap.putAll(keyMap);
            serviceInMap.put("userLogin", userLogin);
            // 补全service的必填参数
            if (genericValue != null) {
                serviceInMap = addRequiredParams(modelService, genericValue, serviceInMap);
            }
            Map<String, Object> updateResult = dispatcher.runSync(serviceName, serviceInMap);
            if (ServiceUtil.isError(updateResult)) {
                throw new OfbizODataException(ServiceUtil.getErrorMessage(updateResult));
            }
            // 刷新genericValue
            genericValue = delegator.findOne(entityName, keyMap, true);
            return genericValue;
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            Throwable originalException = Util.getOriginalException(e);
            throw new OfbizODataException(originalException.getMessage());
        }
    }

    // 为create，update等service，补齐必须的IN参数
    public static Map<String, Object> addRequiredParams(ModelService modelService, GenericValue
            genericValue, Map<String, Object> fieldMap) {
        if (fieldMap == null) {
            fieldMap = new HashMap<String, Object>();
        }
        List<String> requiredParamNames = modelService.getParameterNames("IN", false, false);
        for (String requiredParamName : requiredParamNames) {
            if (!fieldMap.containsKey(requiredParamName) && genericValue.containsKey(requiredParamName)) {
                fieldMap.put(requiredParamName, genericValue.get(requiredParamName));
            }
        }
        return fieldMap;
    }


    public static Map<String, QueryOption> getQuernOptions(UriInfo uriInfo) {
        Map<String, QueryOption> queryOptions = new HashMap<>();
        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();
        SkipOption skipOption = uriInfo.getSkipOption();
        CountOption countOption = uriInfo.getCountOption();
        SearchOption searchOption = uriInfo.getSearchOption();
        FilterOption filterOption = uriInfo.getFilterOption();
        TopOption topOption = uriInfo.getTopOption();
        OrderByOption orderByOption = uriInfo.getOrderByOption();
        ApplyOption applyOption = uriInfo.getApplyOption();
        if (selectOption != null) {
            queryOptions.put("selectOption", selectOption);
        }
        if (expandOption != null) {
            queryOptions.put("expandOption", expandOption);
        }
        if (skipOption != null) {
            queryOptions.put("skipOption", skipOption);
        }
        if (countOption != null) {
            queryOptions.put("countOption", countOption);
        }
        if (searchOption != null) {
            queryOptions.put("searchOption", searchOption);
        }
        if (filterOption != null) {
            queryOptions.put("filterOption", filterOption);
        }
        if (topOption != null) {
            queryOptions.put("topOption", topOption);
        }
        if (orderByOption != null) {
            queryOptions.put("orderByOption", orderByOption);
        }
        if (applyOption != null) {
            queryOptions.put("applyOption", applyOption);
        }
        return queryOptions;
    }

    public static String processSapContextId(Delegator delegator, ODataRequest oDataRequest, ODataResponse
            oDataResponse,
                                             OfbizCsdlAction csdlAction, OfbizCsdlEntityType csdlEntityType) {
        if (csdlAction == null || !csdlAction.isStickySession()) {
            return null;
        }
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, csdlEntityType);
        if (UtilValidate.isEmpty(sapContextId)) {
            if (csdlAction.isStickySessionEdit() || csdlAction.isStickySessionNew()) {
                sapContextId = Util.generateDraftUUID();
            }
        }

        return sapContextId;
    }

    public static void updateSemanticFields(LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                            Entity entityToWrite, OdataOfbizEntity entityUpdated,
                                            Locale locale, GenericValue userLogin) throws OfbizODataException {
        String type = entityUpdated.getType(); // type是FullQualifiedName
        GenericValue genericValue = entityUpdated.getGenericValue();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(type));
        List<EntityTypeRelAlias> relAliases = csdlEntityType.getRelAliases();
        Iterator<EntityTypeRelAlias> it = relAliases.iterator();
        while (it.hasNext()) { // 轮询EntityType中所有的RelAlias
            EntityTypeRelAlias relAlias = it.next();
            if (!aliasPropertiesChanged(entityToWrite, entityUpdated, csdlEntityType, relAlias)) {
                continue;
            }
            updateRelAliasFields(entityToWrite, entityUpdated, csdlEntityType, relAlias, dispatcher, userLogin);
//                removeRelAliasProperty(entityUpdated, relAlias, dispatcher, userLogin);
//                List<String> relations = relAlias.getRelations();
//                createRelAliasFields(entityToWrite, entityUpdated, csdlEntityType, relAlias, dispatcher, userLogin);
        }
    }

//    public static void updateSemanticFields(LocalDispatcher dispatcher, OfbizCsdlEntityType csdlEntityType,
//                                            GenericValue draftGenericValue, OdataOfbizEntity entityUpdated,
//                                            Locale locale, GenericValue userLogin) throws OfbizODataException {
//        List<EntityTypeRelAlias> relAliases = csdlEntityType.getRelAliases();
//        Iterator<EntityTypeRelAlias> it = relAliases.iterator();
//        try {
//            while (it.hasNext()) { // 轮询EntityType中所有的RelAlias
//                EntityTypeRelAlias relAlias = it.next();
//                if (!aliasPropertiesChanged(draftGenericValue, entityUpdated, csdlEntityType, relAlias)) {
//                    continue;
//                }
//                removeRelAliasProperty(entityUpdated, relAlias, dispatcher, userLogin);
//                List<String> relations = relAlias.getRelations();
//                createRelAliasFields(draftGenericValue, entityUpdated, csdlEntityType, relAlias, dispatcher, userLogin);
//            }
//        } catch (GenericEntityException | GenericServiceException e) {
//            throw new OfbizODataException(e.getMessage());
//        }
//    }

    private static void removeRelAliasProperty(OdataOfbizEntity entityUpdated,
                                               EntityTypeRelAlias relAlias,
                                               LocalDispatcher dispatcher,
                                               GenericValue userLogin) throws GenericEntityException, GenericServiceException, OfbizODataException {
        GenericValue genericValue = entityUpdated.getGenericValue();
        String entityName = genericValue.getEntityName();
        List<String> relations = relAlias.getRelations();
        String firstRelation = relations.get(0);
        Delegator delegator = genericValue.getDelegator();
        ModelEntity modelEntity = delegator.getModelEntity(genericValue.getEntityName());
        ModelRelation modelRelation = modelEntity.getRelation(firstRelation);
        GenericValue relGenericValue;
        if (modelRelation.getType().contains("one")) {
            relGenericValue = genericValue.getRelatedOne(firstRelation, true);
        } else {
            Map<String, Object> relationFieldMap = relAlias.getRelationsFieldMap().get(firstRelation);
            List<GenericValue> relGenericValues = genericValue.getRelated(firstRelation, relationFieldMap, null, true);
            if (UtilValidate.isNotEmpty(relGenericValues) && relGenericValues.size() > 0) {
                Map<String, Object> allFields = relGenericValues.get(0).getAllFields();
                if (allFields.containsKey("fromDate") || allFields.containsKey("thruDate")) {
                    relGenericValues = EntityUtil.filterByDate(relGenericValues);
                }
            }
            if (UtilValidate.isEmpty(relGenericValues)) {
                return;
            }
            relGenericValue = EntityUtil.getFirst(relGenericValues);
        }
        String deleteService = Util.getEntityActionService(null, relGenericValue.getEntityName(), "delete", delegator);

        Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(deleteService), relGenericValue);
        serviceParams.put("userLogin", userLogin);
        dispatcher.runSync(deleteService, serviceParams);
    }

    private static boolean aliasPropertiesChanged(GenericValue draftGenericValue, OdataOfbizEntity originEntity,
                                                  OfbizCsdlEntityType csdlEntityType, EntityTypeRelAlias relAlias) {
        List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
        // 如果有两个Property对应一个relAlias，其中一个未提供，一个有值
        // 虽然代表只修改一个Property，但是我们会删除老的relAlias对应的对象，新建一个relAlias对应的对象，这时，那个未提供的Property就要从originEntity中获取
        boolean isChanged = false;
        for (CsdlProperty csdlProperty : csdlProperties) {
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
            EntityTypeRelAlias propertyRelAlias = ofbizCsdlProperty.getRelAlias();
            if (propertyRelAlias != null && propertyRelAlias.getName().equals(relAlias.getName())) {
                String propertyName = ofbizCsdlProperty.getName();
                Property originProperty = originEntity.getProperty(propertyName);
                Object valueToWrite = draftGenericValue.get(propertyName);
                if (valueToWrite == null) { // 未提供这个Property，说明这个Property不更新
                    continue;
                }
                // TODO: 以下判断，是不是要转换originProperty.getValue()的数据类型？
                if (!valueToWrite.equals(originProperty.getValue())) {
                    isChanged = true;
                }
            }
        }
        return isChanged;
    }

    private static boolean aliasPropertiesChanged(Entity entityToWrite, OdataOfbizEntity originEntity,
                                                  OfbizCsdlEntityType csdlEntityType, EntityTypeRelAlias relAlias) {
        List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
        // 如果有两个Property对应一个relAlias，其中一个未提供，一个有值
        // 虽然代表只修改一个Property，但是我们会删除老的relAlias对应的对象，新建一个relAlias对应的对象，这时，那个未提供的Property就要从originEntity中获取
        List<Property> propertiesToWrite = new ArrayList<>();
        boolean isChanged = false;
        for (CsdlProperty csdlProperty : csdlProperties) {
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
            EntityTypeRelAlias propertyRelAlias = ofbizCsdlProperty.getRelAlias();
            if (propertyRelAlias != null && propertyRelAlias.getName().equals(relAlias.getName())) {
                String propertyName = ofbizCsdlProperty.getName();
                Property originProperty = originEntity.getProperty(propertyName);
                Property propertyToWrite = entityToWrite.getProperty(propertyName);
                if (propertyToWrite == null) { // 未提供这个Property，说明这个Property不更新
                    if (originProperty != null) {
                        propertiesToWrite.add(originProperty); // 如果这个relAlias的其它Property要更新，这个Property要带上
                    }
                    continue;
                }
                if (!propertyToWrite.equals(originProperty)) {
                    isChanged = true;
                }
            }
        }
        if (isChanged) {
            for (Property property : propertiesToWrite) {
                entityToWrite.addProperty(property);
            }
        }
        return isChanged;
    }

    public static void createSemanticFields(HttpServletRequest httpServletRequest, Delegator delegator,
                                            LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                            Entity entityToWrite, OdataOfbizEntity entityCreated,
                                            Locale locale, GenericValue userLogin) throws OfbizODataException {
        String type = entityCreated.getType(); // type是FullQualifiedName
        GenericValue genericValue = entityCreated.getGenericValue();
        Debug.logInfo("the type is " + type, module);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(type));
        List<EntityTypeRelAlias> relAliases = csdlEntityType.getRelAliases();
        Iterator<EntityTypeRelAlias> it = relAliases.iterator();
        try {
            while (it.hasNext()) { // 轮询EntityType中所有的RelAlias
                EntityTypeRelAlias relAlias = it.next();
                createRelAliasFields(entityToWrite, entityCreated, csdlEntityType, relAlias, dispatcher, userLogin);
            }
        } catch (GenericEntityException | GenericServiceException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    // 清空某个navigation的所有link，通常用在删除某个非collection的navigation
    public static void clearNavigationLink(GenericValue genericValue,
                                           EntityTypeRelAlias relAlias,
                                           LocalDispatcher dispatcher,
                                           GenericValue userLogin) throws OfbizODataException {
        List<String> relations = relAlias.getRelations();
        Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
        String firstRelation = relations.get(0);
        Map<String, Object> relationFieldMap = relAlias.getRelationsFieldMap().get(firstRelation);
        try {
            List<GenericValue> relatedGenericValues = genericValue.getRelated(firstRelation, relationFieldMap, null, true);
            String deleteService = Util.getEntityActionService(null, relationsEntity.get(firstRelation).getRelEntityName(), "delete", dispatcher.getDelegator());
            for (GenericValue relatedGenericValue : relatedGenericValues) {
                Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(deleteService), relatedGenericValue);
                serviceParams.put("userLogin", userLogin);
                dispatcher.runSync(deleteService, serviceParams);
            }
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    public static void unbindNavigationLink(GenericValue genericValue, GenericValue nestedGenericValue,
                                            OfbizCsdlNavigationProperty csdlNavigationProperty,
                                            LocalDispatcher dispatcher,
                                            GenericValue userLogin) throws OfbizODataException {
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        List<String> relations = relAlias.getRelations();
        Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
        int relationSize = relations.size();
        Map<String, Object> deleteEntityMap = new HashMap<>();
        deleteEntityMap.put("userLogin", userLogin);
        // 删除第一个relation对应的entity，就算是删除了
        String firstRelation = relations.get(0);
        try {
            GenericValue firstGenericValue;
            ModelRelation firstModelRelation = relationsEntity.get(firstRelation);
            if (firstModelRelation.getType().contains("one")) {
                firstGenericValue = genericValue.getRelatedOne(firstRelation, true);
            } else {
                Map<String, Object> findFieldMap = new HashMap<>();
                Map<String, Object> relationFieldMap = relAlias.getRelationsFieldMap().get(firstRelation);
                if (relationFieldMap != null) {
                    findFieldMap.putAll(relationFieldMap);
                }
                if (relationSize > 1) {
                    ModelRelation modelRelation = relationsEntity.get(relations.get(1));
                    List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
                    for (ModelKeyMap keyMap : keyMaps) {
                        String relFieldName = keyMap.getRelFieldName();
                        if (nestedGenericValue.get(relFieldName) != null) {
                            findFieldMap.put(keyMap.getFieldName(), nestedGenericValue.get(relFieldName));
                        }
                    }
                } else { // relationSize == 1, 那第一个relation对应的entity，就是nestedGenericValue对应的entity
                    findFieldMap.putAll(nestedGenericValue.getPrimaryKey());
                }
                Delegator delegator = dispatcher.getDelegator();
                List<GenericValue> genericValues = delegator.findByAnd(firstModelRelation.getRelEntityName(), findFieldMap, null, false);
                if (csdlNavigationProperty.isFilterByDate()) {
                    genericValues = EntityUtil.filterByDate(genericValues);
                }
                firstGenericValue = EntityUtil.getFirst(genericValues);
            }
            if (firstGenericValue == null) {
                return;
            }
            deleteEntityMap.putAll(firstGenericValue);
            String deleteService = null;
            String modelEntityName = relationsEntity.get(firstRelation).getRelEntityName();
            try {
                deleteService = Util.getEntityActionService(null, modelEntityName, "delete", dispatcher.getDelegator());
            } catch (OfbizODataException e) {
                //如果没有定义service并且是个viewEntity, 调用viewEntity主对象的service
                ModelEntity modelEntity = dispatcher.getDelegator().getModelEntity(modelEntityName);
                if (!(modelEntity instanceof ModelViewEntity)) {
                    throw e;
                } else {
                    ModelViewEntity modelViewEntity = (ModelViewEntity) modelEntity;
                    ModelEntity memberModelEntity = modelViewEntity.getMemberModelEntity(modelViewEntity.getViewLink(0).getEntityAlias());
                    deleteService = Util.getEntityActionService(null, memberModelEntity.getEntityName(), "delete", dispatcher.getDelegator());
                }
            }

            Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(deleteService), deleteEntityMap);
            dispatcher.runSync(deleteService, serviceParams);
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            Throwable originalException = Util.getOriginalException(e);
            throw new OfbizODataException(originalException.getMessage());
        }
    }

    public static void bindNavigationLink(GenericValue genericValue, GenericValue nestedGenericValue,
                                          EntityTypeRelAlias relAlias,
                                          LocalDispatcher dispatcher,
                                          GenericValue userLogin) throws OfbizODataException {
        List<String> relations = relAlias.getRelations();
        int relationSize = relations.size();
        GenericValue createdGenericValue = null;
        ModelRelation nextModelRelation = null;
        if (relationSize < 2) {
            // TODO: 要设置entity与nestedEntity互相外键？先留白，也许不需要做什么
            return;
        }
        // 从最后一个relation开始，最后一个relation对应的entity就是nestedEntity
        for (int i = relationSize - 1; i >= 0; i--) { // 倒着轮询RelAlias中所有的relation
            String relation = relations.get(i);
            Map<String, Map<String, Object>> relationsFieldMap = relAlias.getRelationsFieldMap();
            Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
            ModelRelation modelRelation = relationsEntity.get(relation);
            if (i == relationSize - 1) { // 最后一个relation代表的entity，就是nestedGenericValue
                createdGenericValue = nestedGenericValue;
                nextModelRelation = modelRelation;
                continue;
            }
            Map<String, Object> relationFieldMap = relationsFieldMap.get(relation);
            String entityName = modelRelation.getRelEntityName();
            Map<String, Object> createEntityMap = new HashMap<>();
            if (UtilValidate.isNotEmpty(relationFieldMap)) {
                createEntityMap.putAll(relationFieldMap);
            }
            createEntityMap.put("userLogin", userLogin);
            if (i == 0) { // 第一个relation，要把main genericValue的pk放进来
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String fieldName = keyMap.getFieldName();
                    if (genericValue.get(fieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getRelFieldName(), genericValue.get(fieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            if (nextModelRelation != null && createdGenericValue != null) {
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = nextModelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String relFieldName = keyMap.getRelFieldName();
                    if (createdGenericValue.get(relFieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getFieldName(), createdGenericValue.get(relFieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
//            String createService = OfbizMapOdata.CREATE_SERVICE_MAP.get(entityName).get("create");
            String createService = Util.getEntityActionService(null, entityName, "create", dispatcher.getDelegator());
            try {
                Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(createService), createEntityMap);
                createdGenericValue = createGenericValue(dispatcher, createService, entityName, serviceParams);
            } catch (GenericServiceException | GenericEntityException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
            nextModelRelation = modelRelation;
        }
    }

    public static void bindNavigationLink(OdataOfbizEntity entity, OdataOfbizEntity nestedEntity,
                                          EntityTypeRelAlias relAlias,
                                          LocalDispatcher dispatcher,
                                          GenericValue userLogin) throws OfbizODataException {
        GenericValue genericValue = entity.getGenericValue();
        GenericValue nestedGenericValue = nestedEntity.getGenericValue();
        bindNavigationLink(genericValue, nestedGenericValue, relAlias, dispatcher, userLogin);
    }

    public static GenericValue createRelAliasFields(GenericValue draftGenericValue, OdataOfbizEntity entityCreated,
                                                    OfbizCsdlEntityType csdlEntityType,
                                                    EntityTypeRelAlias relAlias,
                                                    LocalDispatcher dispatcher,
                                                    GenericValue userLogin) throws GenericEntityException, GenericServiceException, OfbizODataException {
        GenericValue genericValue = entityCreated.getGenericValue();
        List<String> relations = relAlias.getRelations();
        int relationSize = relations.size();
        GenericValue destGenericValue = null; // 这是目标GenericValue，要保存用于返回
        GenericValue createdGenericValue = null;
        ModelRelation nextModelRelation = null;
        // 从最后一个relation开始，因为最后一个relation对应的entity最先创建
        for (int i = relationSize - 1; i >= 0; i--) { // 倒着轮询RelAlias中所有的relation
            String relation = relations.get(i);
            Map<String, Map<String, Object>> relationsFieldMap = relAlias.getRelationsFieldMap();
            Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
            ModelRelation modelRelation = relationsEntity.get(relation);
            Map<String, Object> relationFieldMap = relationsFieldMap.get(relation);
            String entityName = modelRelation.getRelEntityName();
            Map<String, Object> createEntityMap = new HashMap<>();
            if (UtilValidate.isNotEmpty(relationFieldMap)) {
                createEntityMap.putAll(relationFieldMap);
            }
            createEntityMap.put("userLogin", userLogin);
            // 如果是最后一个relation，要找到所有的property，把字段加入来创建这个entity
            if (i == relationSize - 1) {
                List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
                Map<String, Object> fieldMap = new HashMap<>();
                boolean allNull = true;
                for (CsdlProperty csdlProperty : csdlProperties) {
                    OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
                    EntityTypeRelAlias propertyRelAlias = ofbizCsdlProperty.getRelAlias();
                    if (propertyRelAlias != null && propertyRelAlias.getName().equals(relAlias.getName())) {
                        String fieldName = ofbizCsdlProperty.getOfbizFieldName();
                        Object draftValue = draftGenericValue.get(ofbizCsdlProperty.getName());
                        if (draftValue != null) {
                            allNull = false;
                            fieldMap.put(fieldName, draftValue);
                        }
                    }
                }
                if (allNull) { // 所有字段都是null，别建了
                    return null;
                }
                createEntityMap.putAll(fieldMap);
            } else if (i == 0) { // 第一个relation，要把main genericValue的pk放进来
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String fieldName = keyMap.getFieldName();
                    if (genericValue.get(fieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getRelFieldName(), genericValue.get(fieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            if (nextModelRelation != null && createdGenericValue != null) {
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = nextModelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String relFieldName = keyMap.getRelFieldName();
                    if (createdGenericValue.get(relFieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getFieldName(), createdGenericValue.get(relFieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            String createService = Util.getEntityActionService(null, entityName, "create", dispatcher.getDelegator());
            Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(createService), createEntityMap);
            createdGenericValue = createGenericValue(dispatcher, createService, entityName, serviceParams);
            if (destGenericValue == null) { // 第一个产生的createdGenericValue，就是目标GenericValue，要返回
                destGenericValue = createdGenericValue;
            }
            nextModelRelation = modelRelation;
        }
        return destGenericValue;
    }

    public static GenericValue createRelAliasFields(Entity entityToWrite, OdataOfbizEntity entityCreated,
                                                    OfbizCsdlEntityType csdlEntityType,
                                                    EntityTypeRelAlias relAlias,
                                                    LocalDispatcher dispatcher,
                                                    GenericValue userLogin) throws GenericEntityException, GenericServiceException, OfbizODataException {
        GenericValue genericValue = entityCreated.getGenericValue();
        List<String> relations = relAlias.getRelations();
        int relationSize = relations.size();
        GenericValue destGenericValue = null; // 这是目标GenericValue，要保存用于返回
        GenericValue createdGenericValue = null;
        ModelRelation nextModelRelation = null;
        // 从最后一个relation开始，因为最后一个relation对应的entity最先创建
        for (int i = relationSize - 1; i >= 0; i--) { // 倒着轮询RelAlias中所有的relation
            String relation = relations.get(i);
            Map<String, Map<String, Object>> relationsFieldMap = relAlias.getRelationsFieldMap();
            Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
            ModelRelation modelRelation = relationsEntity.get(relation);
            Map<String, Object> relationFieldMap = relationsFieldMap.get(relation);
            String entityName = modelRelation.getRelEntityName();
            Map<String, Object> createEntityMap = new HashMap<>();
            if (UtilValidate.isNotEmpty(relationFieldMap)) {
                createEntityMap.putAll(relationFieldMap);
            }
            createEntityMap.put("userLogin", userLogin);
            // 如果是最后一个relation，要找到所有的property，把字段加入来创建这个entity
            if (i == relationSize - 1) {
                List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
                Map<String, Object> fieldMap = new HashMap<>();
                boolean allNull = true;
                for (CsdlProperty csdlProperty : csdlProperties) {
                    OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
                    EntityTypeRelAlias propertyRelAlias = ofbizCsdlProperty.getRelAlias();
                    if (propertyRelAlias != null && propertyRelAlias.getName().equals(relAlias.getName())) {
                        String fieldName = ofbizCsdlProperty.getOfbizFieldName();
                        Property property = entityToWrite.getProperty(ofbizCsdlProperty.getName());
                        if (property != null) {
                            if (property.getValue() != null) {
                                allNull = false;
                            }
                            fieldMap.put(fieldName, property.getValue());
                        }
                    }
                }
                if (allNull) { // 所有字段都是null，别建了
                    return null;
                }
                createEntityMap.putAll(fieldMap);
            }
            if (i == 0) { // 第一个relation，要把main genericValue的pk放进来
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String fieldName = keyMap.getFieldName();
                    if (genericValue.get(fieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getRelFieldName(), genericValue.get(fieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            if (nextModelRelation != null && createdGenericValue != null) {
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = nextModelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String relFieldName = keyMap.getRelFieldName();
                    if (createdGenericValue.get(relFieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getFieldName(), createdGenericValue.get(relFieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            String createService = Util.getEntityActionService(null, entityName, "create", dispatcher.getDelegator());
            Map<String, Object> serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(createService), createEntityMap);
            ModelEntity modelEntity = dispatcher.getDelegator().getModelEntity(entityName);
            List<String> pkFieldNames = modelEntity.getPkFieldNames();
            //如果当前主键完整并且已经存在这条数据就不再创建
            Map<String, Object> primaryKey = new HashMap<>();
            boolean containsKey = true;
            for (String pkFieldName : pkFieldNames) {
                Object value = serviceParams.get(pkFieldName);
                if (UtilValidate.isEmpty(value)) {
                    containsKey = false;
                    continue;
                }
                primaryKey.put(pkFieldName, serviceParams.get(pkFieldName));
            }
            GenericValue existGenericValue = null;
            if (containsKey) {
                existGenericValue = dispatcher.getDelegator().findOne(entityName, primaryKey, false);
            }
            if (UtilValidate.isNotEmpty(existGenericValue)) {
                createdGenericValue = existGenericValue;
            } else {
                createdGenericValue = createGenericValue(dispatcher, createService, entityName, serviceParams);
            }
            if (destGenericValue == null) { // 第一个产生的createdGenericValue，就是目标GenericValue，要返回
                destGenericValue = createdGenericValue;
            }
            nextModelRelation = modelRelation;
        }
        //如果主genericValue有外键指向destGenericValue，则还需要更新genericValue的外键字段，这个通常发生在relationSize=1的时候
        if (relationSize == 1 && UtilValidate.isNotEmpty(destGenericValue)) {
            Map<String, Object> mainEntityFk = new HashMap<>();
            ModelRelation modelRelation = relAlias.getRelationsEntity().get(relAlias.getRelations().get(0));
            if (modelRelation.getType().contains("one")) { //relationOne 应该都会有外键
                for (ModelKeyMap keyMap : modelRelation.getKeyMaps()) {
                    mainEntityFk.put(keyMap.getFieldName(), destGenericValue.get(keyMap.getRelFieldName()));
                }
            }
            if (mainEntityFk.size() > 0) {
                updateGenericValue(dispatcher, dispatcher.getDelegator(), genericValue.getEntityName(), entityCreated.getKeyMap(), mainEntityFk, null, userLogin);
            }
        }
        return destGenericValue;
    }

    /**
     * 更新RelAlias字段
     */
    public static void updateRelAliasFields(Entity entityToWrite, OdataOfbizEntity entityCreated,
                                            OfbizCsdlEntityType csdlEntityType, EntityTypeRelAlias relAlias,
                                            LocalDispatcher dispatcher, GenericValue userLogin) throws OfbizODataException {
        try {
            Delegator delegator = dispatcher.getDelegator();
            GenericValue genericValue = entityCreated.getGenericValue();
            List<GenericValue> relAliasGenericValues = getGenericValuesFromRelations(dispatcher.getDelegator(), genericValue, relAlias, relAlias.getRelations(), false);
            GenericValue relGenericValue = EntityUtil.getFirst(relAliasGenericValues);
            if (UtilValidate.isNotEmpty(relGenericValue)) {
                Map<String, Object> relAliasField = getEntityRelAliasField(entityToWrite, csdlEntityType, relAlias);
                //更新
                Map<String, Object> serviceParameter = new HashMap<>(relGenericValue);
                serviceParameter.putAll(relAliasField);
                String updateService = Util.getEntityActionService(null, relGenericValue.getEntityName(), "update", delegator);
                ModelService modelService = dispatcher.getDispatchContext().getModelService(updateService);
                Map<String, Object> serviceInMap = Util.prepareServiceParameters(modelService, serviceParameter);
                serviceInMap.put("userLogin", userLogin);
                dispatcher.runSync(updateService, serviceInMap);
            } else {
                //创建
                createRelAliasFields(entityToWrite, entityCreated, csdlEntityType, relAlias, dispatcher, userLogin);
            }
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 获取一个Entity中某个RelAlias的所有字段
     */
    public static Map<String, Object> getEntityRelAliasField(Entity entity, OfbizCsdlEntityType csdlEntityType, EntityTypeRelAlias relAlias) {
        String relAliasName = relAlias.getName();
        Map<String, Object> fieldMap = new HashMap<>();
        for (Property property : entity.getProperties()) {
            OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(property.getName());
            if (UtilValidate.isEmpty(csdlProperty.getRelAlias())) {
                continue;
            }
            EntityTypeRelAlias propertyRelAlias = csdlProperty.getRelAlias();
            if (propertyRelAlias.getName().equals(relAliasName)) {
                fieldMap.put(csdlProperty.getOfbizFieldName(), property.getValue());
            }
        }
        return fieldMap;
    }


    public static GenericValue createRelatedGenericValue(Entity entityToWrite, OdataOfbizEntity mainEntity,
                                                         EntityTypeRelAlias relAlias, OfbizCsdlEntityType navCsdlEntityType,
                                                         OfbizAppEdmProvider edmProvider, LocalDispatcher dispatcher, Delegator delegator,
                                                         GenericValue userLogin, HttpServletRequest request) throws OfbizODataException {
        GenericValue genericValue = mainEntity.getGenericValue();
        List<String> relations = relAlias.getRelations();
        int relationSize = relations.size();
        GenericValue destGenericValue = null; // 这是目标GenericValue，要保存用于返回
        GenericValue createdGenericValue = null;
        ModelRelation nextModelRelation = null;
        // 从最后一个relation开始，因为最后一个relation对应的entity最先创建
        for (int i = relationSize - 1; i >= 0; i--) { // 倒着轮询RelAlias中所有的relation
            String relation = relations.get(i);
            OfbizCsdlEntityType navEntityType = null;
            Map<String, Map<String, Object>> relationsFieldMap = relAlias.getRelationsFieldMap();
            Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
            ModelRelation modelRelation = relationsEntity.get(relation);
            Map<String, Object> relationFieldMap = relationsFieldMap.get(relation);
            String entityName = modelRelation.getRelEntityName();
            Map<String, Object> createEntityMap = new HashMap<>();
            if (UtilValidate.isNotEmpty(relationFieldMap)) {
                createEntityMap.putAll(relationFieldMap);
            }
            createEntityMap.put("userLogin", userLogin);
            // 如果是最后一个relation，要把entityToWrite放进来
            if (i == relationSize - 1) {
                navEntityType = navCsdlEntityType;
                Map<String, Object> fieldMap = Util.entityToMap(entityToWrite);
                createEntityMap.putAll(fieldMap);
                //添加EntityType的Condition
                if (UtilValidate.isNotEmpty(navCsdlEntityType) && UtilValidate.isNotEmpty(navCsdlEntityType.getEntityConditionStr())) {
                    Map<String, Object> entityTypeConditionMap = Util.parseConditionMap(navCsdlEntityType.getEntityConditionStr(), request);
                    createEntityMap.putAll(entityTypeConditionMap);
                }
                //添加DefaultValue
                for (Map.Entry<String, Object> entry : navCsdlEntityType.getDefaultValueProperties().entrySet()) {
                    createEntityMap.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            if (i == 0) { // 第一个relation，要把main genericValue的pk放进来
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = modelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String fieldName = keyMap.getFieldName();
                    if (genericValue.get(fieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getRelFieldName(), genericValue.get(fieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            if (nextModelRelation != null && createdGenericValue != null) {
                Map<String, Object> relationPossibleKeyMap = new HashMap<>();
                List<ModelKeyMap> keyMaps = nextModelRelation.getKeyMaps();
                for (ModelKeyMap keyMap : keyMaps) {
                    String relFieldName = keyMap.getRelFieldName();
                    if (createdGenericValue.get(relFieldName) != null) {
                        relationPossibleKeyMap.put(keyMap.getFieldName(), createdGenericValue.get(relFieldName));
                    }
                }
                createEntityMap.putAll(relationPossibleKeyMap);
            }
            String createService;
            Map<String, Object> serviceParams;
            try {
                createService = Util.getEntityActionService(navEntityType, entityName, "create", dispatcher.getDelegator());
                if (UtilValidate.isNotEmpty(entityToWrite.getType())) {
                    OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(new FullQualifiedName(entityToWrite.getType()));
                    createEntityMap = Util.propertyToField(createEntityMap, csdlEntityType);
                }
                serviceParams = Util.prepareServiceParameters(dispatcher.getDispatchContext().getModelService(createService), createEntityMap);
            } catch (GenericServiceException e) {
                throw new OfbizODataException(e.getMessage());
            }
            try {
                //如果当前主键完整并且已经存在这条数据就不再创建
                Map<String, Object> primaryKey = new HashMap<>();
                boolean containsKey = true;
                for (String pkFieldName : delegator.getModelEntity(entityName).getPkFieldNames()) {
                    Object value = serviceParams.get(pkFieldName);
                    if (UtilValidate.isEmpty(value)) {
                        containsKey = false;
                        continue;
                    }
                    primaryKey.put(pkFieldName, serviceParams.get(pkFieldName));
                }
                GenericValue existGenericValue = null;
                if (containsKey) {
                    existGenericValue = delegator.findOne(entityName, primaryKey, false);
                }
                if (UtilValidate.isNotEmpty(existGenericValue)) {
                    createdGenericValue = existGenericValue;
                } else {
                    createdGenericValue = createGenericValue(dispatcher, createService, entityName, serviceParams);
                }
            } catch (GenericServiceException | GenericEntityException e) {
                throw new OfbizODataException(e.getMessage());
            }
            if (destGenericValue == null) { // 第一个产生的createdGenericValue，就是目标GenericValue，要返回
                destGenericValue = createdGenericValue;
            }
            nextModelRelation = modelRelation;
        }

        //如果主genericValue有外键指向destGenericValue，则还需要更新genericValue的外键字段，这个通常发生在relationSize=1的时候
        if (relationSize == 1 && UtilValidate.isNotEmpty(destGenericValue)) {
            Map<String, Object> mainEntityFk = new HashMap<>();
            ModelRelation modelRelation = relAlias.getRelationsEntity().get(relAlias.getRelations().get(0));
            if (modelRelation.getType().contains("one")) { //relationOne 应该都会有外键
                for (ModelKeyMap keyMap : modelRelation.getKeyMaps()) {
                    mainEntityFk.put(keyMap.getFieldName(), destGenericValue.get(keyMap.getRelFieldName()));
                }
            }
            if (mainEntityFk.size() > 0) {
                updateGenericValue(dispatcher, delegator, genericValue.getEntityName(), mainEntity.getKeyMap(), mainEntityFk, null, userLogin);
            }
        }
        return destGenericValue;
    }

    public static List<GenericValue> getRelatedGenericValues(Delegator delegator, GenericValue genericValue,
                                                             EntityTypeRelAlias relAlias,
                                                             boolean filterByDate) throws OfbizODataException {
        try {
            return getGenericValuesFromRelations(delegator, genericValue, relAlias, relAlias.getRelations(), filterByDate);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    private static List<GenericValue> getGenericValuesFromRelations(Delegator delegator, GenericValue
            genericValue, EntityTypeRelAlias relAlias,
                                                                    List<String> relations,
                                                                    boolean filterByDate) throws GenericEntityException {
        String relation = relations.get(0);
        ModelEntity theModelEntity = genericValue.getModelEntity();
        ModelRelation modelRelation = theModelEntity.getRelation(relation);
        if (modelRelation.getType().contains("one")) {
            GenericValue relGenericValue;
            if (delegator.getModelEntity(modelRelation.getRelEntityName()) instanceof ModelViewEntity) {
                relGenericValue = EntityUtil.getFirst(genericValue.getRelated(relation, null, null, true));
            } else {
                relGenericValue = genericValue.getRelatedOne(relation, true);
            }
            if (relGenericValue == null) {
                return null;
            }
            if (relations.size() == 1) {
                return UtilMisc.toList(relGenericValue);
            } else {
                return getGenericValuesFromRelations(delegator, relGenericValue, relAlias, relations.subList(1, relations.size()), filterByDate);
            }
        } else {
            Map<String, Object> relFieldMap = relAlias.getRelationsFieldMap().get(relation);
            List<GenericValue> relGenericValues = genericValue.getRelated(relation, relFieldMap, null, true);
            if (filterByDate) {
                relGenericValues = EntityUtil.filterByDate(relGenericValues);
            }
            if (UtilValidate.isEmpty(relGenericValues)) {
                return null;
            }
            if (relations.size() == 1) {
                return relGenericValues;
            } else {
                List<GenericValue> result = new ArrayList<>();
                for (GenericValue relGenericValue : relGenericValues) {
                    result.addAll(getGenericValuesFromRelations(delegator, relGenericValue, relAlias, relations.subList(1, relations.size()), filterByDate));
                }
                return result;
            }
        }
    }

}
