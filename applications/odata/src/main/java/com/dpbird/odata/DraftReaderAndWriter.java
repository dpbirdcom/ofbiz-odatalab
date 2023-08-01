package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import com.dpbird.odata.handler.DraftHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.processor.DataModifyActions;
import com.dpbird.odata.services.ProcessorServices;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.LevelsOptionImpl;

import javax.servlet.http.HttpServletRequest;
import java.sql.Date;
import java.util.*;

/**
 * 负责调用Handler来完成所有的读写操作
 *
 * @date 2023/3/2
 */
public class DraftReaderAndWriter {
    final String module = DraftReaderAndWriter.class.getName();

    protected Delegator delegator;
    protected LocalDispatcher dispatcher;
    protected GenericValue userLogin;
    protected Locale locale;
    protected EntityCondition entityCondition = null;
    protected OfbizAppEdmProvider edmProvider;
    protected EdmEntityType edmEntityType;
    protected String sapContextId;
    protected HttpServletRequest httpServletRequest;
    protected Map<String, Object> odataContext;
    public static final int MAX_ROWS = 10000;

    public DraftReaderAndWriter(Map<String, Object> odataContext, String sapContextId, EdmEntityType edmEntityType) throws OfbizODataException {
        this.odataContext = odataContext;
        this.delegator = (Delegator) odataContext.get("delegator");
        this.dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        this.userLogin = (GenericValue) odataContext.get("userLogin");
        this.locale = (Locale) odataContext.get("locale");
        this.edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        this.edmEntityType = edmEntityType;
        this.sapContextId = sapContextId;
        this.httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
    }

    /**
     * 查询单个实体数据
     *
     * @param keyMap       实体主键
     * @param queryOptions queryOptions
     * @return Entity
     */
    public Entity findOne(Map<String, Object> keyMap, EdmBindingTarget edmBindingTarget, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        //从接口实例中读取数据
        DraftHandler draftHandler = HandlerFactory.getDraftHandler(edmEntityType, edmProvider, delegator);
        Map<String, Object> resultMap = draftHandler.finOne(odataContext, edmEntityType, keyMap, null);
        if (UtilValidate.isEmpty(resultMap)) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Not found.");
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(edmEntityType, resultMap);
        entity.setOdataParts(UtilMisc.toList(new OdataParts(edmBindingTarget, edmEntityType, null, entity)));
        //添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
        //expand
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, csdlEntityType, this.edmEntityType);
        }
//        entity.setKeyMap(keyMap);
        return entity;
    }

    public Entity findRelatedOne(OdataOfbizEntity mainEntity, EdmNavigationProperty edmNavigationProperty, Map<String, Object> navKeyMap, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        //从接口实例中读取数据
        Map<String, Object> navigationParam = new HashMap<>();
        navigationParam.put("entity", mainEntity);
        navigationParam.put("edmEntityType", edmEntityType);
        navigationParam.put("edmNavigationProperty", edmNavigationProperty);
        EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
        DraftHandler draftHandler = HandlerFactory.getDraftHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        Map<String, Object> result = draftHandler.finOne(odataContext, navEdmEntityType, navKeyMap, navigationParam);
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navEdmEntityType.getFullQualifiedName());
        OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(edmNavigationProperty.getType(), result);
        ArrayList<OdataParts> odataParts = new ArrayList<>(mainEntity.getOdataParts());
        odataParts.add(new OdataParts(null, edmNavigationProperty.getType(), null, entity));
        entity.setOdataParts(odataParts);
        //添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
        //expand
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            addExpandOption((ExpandOption) queryOptions.get("expandOption"), entity, navCsdlEntityType, navEdmEntityType);
        }
//        entity.setKeyMap(navKeyMap);
        return entity;
    }

    public EntityCollection findRelatedList(OdataOfbizEntity mainEntity, EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        //从接口实例中读取数据
        EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navEdmEntityType.getFullQualifiedName());
        DraftHandler draftHandler = HandlerFactory.getDraftHandler(navEdmEntityType, edmProvider, delegator);
        List<GenericValue> resultList = draftHandler.findList(odataContext, mainEntity, edmEntityType, edmNavigationProperty, queryOptions);
        EntityCollection entityCollection = new EntityCollection();
        List<Entity> entities = entityCollection.getEntities();
        entityCollection.setCount(resultList.size());
        for (GenericValue genericValue : resultList) {
            OdataOfbizEntity entity = (OdataOfbizEntity) findResultToEntity(navEdmEntityType, genericValue);
            ArrayList<OdataParts> odataParts = new ArrayList<>(mainEntity.getOdataParts());
            odataParts.add(new OdataParts(null, edmNavigationProperty.getType(), null, entity));
            entity.setOdataParts(odataParts);
            entities.add(entity);
        }
        //分页
        Util.pageEntityCollection(entityCollection, getSkipOption(queryOptions), getTopOption(queryOptions));
        //添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, entityCollection.getEntities(), locale, userLogin);
        //expand
        if (queryOptions != null && queryOptions.get("expandOption") != null) {
            for (Entity entity : entityCollection.getEntities()) {
                addExpandOption((ExpandOption) queryOptions.get("expandOption"), (OdataOfbizEntity) entity, navCsdlEntityType, edmNavigationProperty.getType());
            }
        }
        return entityCollection;
    }

    public Entity updateEntityData(Map<String, Object> keyMap, Entity entityToWrite) throws OfbizODataException {
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
        Map<String, Object> toUpdateFields = Util.entityToMap(entityToWrite);
        keyMap = Util.fieldToProperty(keyMap, csdlEntityType);
        for (Map.Entry<String, Object> entry : toUpdateFields.entrySet()) {
            //FE清空字段是传递的空串，这里转换成null
            if ("".equals(entry.getValue())) {
                entry.setValue(null);
                continue;
            }
            //如果传递过来的时间格式不对,根据字段类型转换格式
            ModelField field = modelEntity.getField(entry.getKey());
            if (UtilValidate.isNotEmpty(field) && "date".equals(field.getType()) && entry.getValue() instanceof GregorianCalendar) {
                GregorianCalendar calendar = (GregorianCalendar) entry.getValue();
                toUpdateFields.put(entry.getKey(), new Date(calendar.getTime().getTime()));
            }
        }
        toUpdateFields.put("isActiveEntity", "N");
        toUpdateFields.put("hasDraftEntity", "Y");
        //更新Draft 使用接口实例更新
        DraftHandler draftHandler = HandlerFactory.getDraftHandler(edmEntityType, edmProvider, delegator);
        GenericValue genericValue = draftHandler.updateEntity(odataContext, edmEntityType, keyMap, toUpdateFields);
        //更新DraftAdmin
        try {
            GenericValue draftAdministrativeData = genericValue.getRelatedOne("DraftAdministrativeData", false);
            draftAdministrativeData.set("lastChangedByUser", userLogin.get("userLoginId"));
            draftAdministrativeData.set("lastChangeDateTime", UtilDateTime.nowTimestamp());
            draftAdministrativeData.store();
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        //添加语义化字段
        Entity resultToEntity = findResultToEntity(edmEntityType, genericValue);
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, null, UtilMisc.toList(resultToEntity), locale, userLogin);
        return resultToEntity;
    }

    /**
     * Draft创建实体
     * 在编辑模式下主实体是通过NewAction创建，所以这个方法只有多段式创建会进入
     */
    public Entity createEntityData(Entity mainEntity, Entity entityToWrite, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        EdmEntityType navEdmEntityType = edmNavigationProperty.getType();
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        //所有要写入的字段
        Map<String, Object> toCreatePropertyMap = Util.entityToMap(entityToWrite);
        //根据主对象的主键 补全一下当前子对象的字段
        toCreatePropertyMap.putAll(Util.getRelatedFieldMap(delegator, csdlEntityType.getOfbizEntity(), csdlNavigationProperty, Util.entityToMap(mainEntity), edmProvider));
        //添加Navigation Condition字段
        Map<String, Object> relatedConditionMap = Util.getRelatedConditionMap(csdlNavigationProperty);
        if (UtilValidate.isNotEmpty(relatedConditionMap)) {
            toCreatePropertyMap.putAll(relatedConditionMap);
        }
        //添加Entity DefaultProperty
        OfbizCsdlEntityType navOfbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
        for (Map.Entry<String, Object> entry : navOfbizCsdlEntityType.getDefaultValueProperties().entrySet()) {
            toCreatePropertyMap.putIfAbsent(entry.getKey(), entry.getValue());
        }
        String entityName = navCsdlEntityType.getOfbizEntity();
        String draftEntityName = navCsdlEntityType.getDraftEntityName();
        ModelEntity modelEntity = delegator.getModelEntity(entityName);
        ModelEntity draftModelEntity = delegator.getModelEntity(draftEntityName);
        //补充seqId
        addDraftNextSeqId(delegator, navOfbizCsdlEntityType, toCreatePropertyMap);
        //补充fromDate
        Util.makeupFromDate(toCreatePropertyMap, modelEntity);
        //如果是单主键并且不存在这个值 获取一个自增主键
        if (modelEntity.getPkFieldNames().size() == 1) {
            CsdlPropertyRef csdlPropertyRef = navOfbizCsdlEntityType.getKey().get(0);
            Object primaryKeyValue = toCreatePropertyMap.get(csdlPropertyRef.getName());
            if (UtilValidate.isEmpty(primaryKeyValue)) {
                String pkValue = "ID" + delegator.getNextSeqId(DataModifyActions.NEXT_ID_KEY);
                toCreatePropertyMap.put(csdlPropertyRef.getName(), pkValue);
            }
        }
        //添加draft字段
        String newDraftUUId = Util.generateDraftUUID();
        toCreatePropertyMap.put("draftUUID", newDraftUUId);
        toCreatePropertyMap.putAll(UtilMisc.toMap("isActiveEntity", "N", "hasActiveEntity", "N", "hasDraftEntity", "Y"));
        toCreatePropertyMap = new HashMap<>(delegator.makeValidValue(draftEntityName, toCreatePropertyMap));
        try {
            //createDraftAdmin
            ProcessorServices.createDraftAdminData(delegator, newDraftUUId, (String) mainEntity.getProperty("draftUUID").getValue(), entityName,
                    draftEntityName, navCsdlEntityType.getFullQualifiedNameString(), null, edmNavigationProperty.getName(), userLogin);
            //createDraft 通过接口实例创建
            DraftHandler draftHandler = HandlerFactory.getDraftHandler(navEdmEntityType, edmProvider, delegator);
            GenericValue createdResult = draftHandler.createEntity(odataContext, navEdmEntityType, mainEntity, toCreatePropertyMap, edmNavigationProperty);
            Entity resultToEntity = findResultToEntity(navEdmEntityType, createdResult);
            //添加语义化字段
            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, null, UtilMisc.toList(resultToEntity), locale, userLogin);
            return resultToEntity;
        } catch (GenericEntityException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 删除Draft数据
     *
     * @param primaryKey 要删除的数据主键
     */
    public void deleteEntityData(Map<String, Object> primaryKey) throws OfbizODataException {
        //将Draft数据标记为删除
        DraftHandler draftHandler = HandlerFactory.getDraftHandler(edmEntityType, edmProvider, delegator);
        draftHandler.deleteEntity(odataContext, edmEntityType, primaryKey);
    }



    /**
     * 查询结果转成Entity
     */
    private Entity findResultToEntity(EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        Entity entity;
        if (resultMap instanceof GenericValue) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            entity = OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, csdlEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            entity = Util.mapToEntity(csdlEntityType, resultMap);
        }
        if (entity != null && resultMap.containsKey("draftUUID") && UtilValidate.isNotEmpty(resultMap.get("draftUUID"))) {
            entity.addProperty(new Property(null, "draftUUID", ValueType.PRIMITIVE, resultMap.get("draftUUID")));
        }
        return entity;
    }

    protected void addExpandOption(ExpandOption expandOption, OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType, EdmEntityType currEdmEntityType)
            throws OfbizODataException {
        if (expandOption == null) {
            return;
        }
        List<ExpandItem> expandItems = expandOption.getExpandItems();
        for (ExpandItem expandItem : expandItems) {
            addExpandItem(entity, expandItem, csdlEntityType, currEdmEntityType);
        } // end for (ExpandItem expandItem : expandItems)
        Debug.logInfo("finished adding all expand items", module);
    }

    private void addExpandItem(OdataOfbizEntity entity, ExpandItem expandItem, OfbizCsdlEntityType csdlEntityType, EdmEntityType currEdmEntityType) throws OfbizODataException {
        EdmNavigationProperty edmNavigationProperty = null;
        LevelsExpandOption levelsExpandOption = expandItem.getLevelsOption();
        int expandLevel = 1;
        if (levelsExpandOption != null) {
            expandLevel = levelsExpandOption.getValue();
        }
        UriResource uriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
        if (uriResource instanceof UriResourceNavigation) {
            edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
        }
        if (edmNavigationProperty == null) {
            return;
        }
        String navPropName = edmNavigationProperty.getName();
        Debug.logInfo("adding expand option with name = " + navPropName, module);
        if (edmNavigationProperty.isCollection()) { // expand的对象是collection
            FilterOption filterOption = expandItem.getFilterOption();
            OrderByOption orderByOption = expandItem.getOrderByOption();
            ExpandOption nestedExpandOption = expandItem.getExpandOption(); // expand nested in expand
            if (nestedExpandOption == null && expandLevel > 1) {
                ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
                LevelsOptionImpl levelsOptionImpl = (LevelsOptionImpl) levelsExpandOption;
                levelsOptionImpl.setValue(expandLevel--);
                expandOptionImpl.addExpandItem(expandItem);
                nestedExpandOption = expandOptionImpl;
            }
            expandCollection(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        } else { // expand对象不是collection
            // 此处改过
            FilterOption filterOption = expandItem.getFilterOption();
            OrderByOption orderByOption = expandItem.getOrderByOption();
            ExpandOption nestedExpandOption = expandItem.getExpandOption(); // expand nested in expand
            expandNonCollection(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        } // end expand对象不是collection
    }

    private void expandCollection(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                  EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                  FilterOption filterOption, OrderByOption orderByOption,
                                  ExpandOption nestedExpandOption) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, csdlEntityType, edmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        String navPropName = edmNavigationProperty.getName();
        Link link = new Link();
        link.setTitle(navPropName);
        link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
        link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
        link.setInlineEntitySet(expandEntityCollection);
        if (entity.getId() != null) {
            String linkHref = entity.getId().toString() + "/" + navPropName;
            link.setHref(linkHref);
        }
        entity.getNavigationLinks().add(link);
    }

    private void expandNonCollection(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                     EdmEntityType currEdmEntityType, EdmNavigationProperty edmNavigationProperty,
                                     FilterOption filterOption, OrderByOption orderByOption,
                                     ExpandOption nestedExpandOption) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, csdlEntityType, currEdmEntityType, edmNavigationProperty, filterOption, orderByOption, nestedExpandOption);
        if (null != expandEntityCollection && UtilValidate.isNotEmpty(expandEntityCollection.getEntities())) {
            Entity expandEntity = expandEntityCollection.getEntities().get(0);
            Link link = new Link();
            String navPropName = edmNavigationProperty.getName();
            link.setTitle(navPropName);
            link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
            link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
            link.setInlineEntity(expandEntity);
            if (entity.getId() != null) {
                String linkHref = entity.getId().toString() + "/" + navPropName;
                link.setHref(linkHref);
            }
            entity.getNavigationLinks().add(link);
        }
    }

    private EntityCollection getExpandData(OdataOfbizEntity entity, OfbizCsdlEntityType csdlEntityType,
                                           EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                           FilterOption filterOption, OrderByOption orderByOption,
                                           ExpandOption nestedExpandOption) throws OfbizODataException {
        DraftReaderAndWriter draftReaderAndWriter = new DraftReaderAndWriter(odataContext, sapContextId, edmEntityType);
        Map<String, QueryOption> queryOptions = UtilMisc.toMap("expandOption", nestedExpandOption);
        Map<String, Object> keyMap = Util.getKeyMapFromEntity(edmProvider, entity);
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
        if (navCsdlEntityType.getDraftEntityName() != null && entity.getKeyMap().containsKey("draftUUID") && !csdlNavigationProperty.isReadOnly()) {
            return draftReaderAndWriter.findRelatedList(entity, edmEntityType, edmNavigationProperty, queryOptions);
        } else {
            try {
                //子对象不使用draft, 或者是saveAction的返回, 读真实数据库
                //pk是id就是第二段Draft数据的expand
                Object pkValue = keyMap.size() == 1 && keyMap.containsKey("id") ? keyMap.get("id") : sapContextId;
                GenericValue mainGenericValue = delegator.findOne(csdlEntityType.getDraftEntityName(), UtilMisc.toMap("draftUUID", pkValue), false);
                //saveAction之后的expand
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    Map<String, Object> fieldKey = Util.propertyToField(keyMap, csdlEntityType);
                    mainGenericValue = delegator.findOne(csdlEntityType.getOfbizEntity(), fieldKey, false);
                }
                //draft未保存的数据
                if (UtilValidate.isEmpty(mainGenericValue)) {
                    //尝试根据现有主键查询Draft数据 用Draft数据的GenericValue去查询expand
                    Map<String, Object> fieldKey = Util.propertyToField(keyMap, csdlEntityType);
                    List<GenericValue> findByAnd = delegator.findByAnd(csdlEntityType.getDraftEntityName(), fieldKey, null, false);
                    if (UtilValidate.isNotEmpty(findByAnd)) {
                        mainGenericValue = EntityUtil.getFirst(findByAnd);
                    } else {
                        return new EntityCollection();
                    }
                }

                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
                Map<String, Object> edmParams = UtilMisc.toMap("edmEntityType", edmEntityType);
                OdataReader reader = new OdataReader(odataContext, queryOptions, edmParams);
                OdataOfbizEntity ofbizEntity = reader.makeEntityFromGv(mainGenericValue);
                EntityCollection relatedList = reader.findRelatedList(ofbizEntity, edmNavigationProperty, queryOptions, null);
                for (Entity related : relatedList.getEntities()) {
                    if (navCsdlEntityType.hasStream()) {
                        related.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
                    }
                }
                return relatedList;
            } catch (GenericEntityException e) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), e.getMessage());
            }
        }
    }

    protected int getTopOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("topOption") != null
                && ((TopOption) queryOptions.get("topOption")).getValue() > 0) {
            return ((TopOption) queryOptions.get("topOption")).getValue();
        }
        return MAX_ROWS;
    }

    protected int getSkipOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("skipOption") != null
                && ((SkipOption) queryOptions.get("skipOption")).getValue() > 0) {
            return ((SkipOption) queryOptions.get("skipOption")).getValue();
        }
        return 0;
    }

    /**
     * 再向Draft添加数据时 获取一个SeqId
     */
    public static void addDraftNextSeqId(Delegator delegator, OfbizCsdlEntityType ofbizCsdlEntityType, Map<String, Object> fieldMap) {
        //位数
        final int numericPadding = 5;
        //递增数
        final int incrementBy = 1;
        ModelEntity modelEntity = delegator.getModelEntity(ofbizCsdlEntityType.getOfbizEntity());
        ModelEntity modelDraftEntity = delegator.getModelEntity(ofbizCsdlEntityType.getDraftEntityName());
        Map<String, Object> pkMap = new HashMap<>();
        //获取seqId以外的主键
        for (String pkFieldName : modelEntity.getPkFieldNames()) {
            if (!pkFieldName.endsWith("SeqId")) {
                pkMap.put(pkFieldName, fieldMap.get(pkFieldName));
            }
        }
        //获取下一个seqId 添加到fieldMap
        for (String pkFieldName : modelEntity.getPkFieldNames()) {
            if (pkFieldName.endsWith("SeqId") && modelDraftEntity.isField(pkFieldName) && UtilValidate.isEmpty(fieldMap.get("SeqId"))) {
                GenericValue genericValue = delegator.makeValue(modelDraftEntity.getEntityName(), pkMap);
                delegator.setNextSubSeqId(genericValue, pkFieldName, numericPadding, incrementBy);
                fieldMap.put(pkFieldName, genericValue.get(pkFieldName));
            }
        }
    }

}
