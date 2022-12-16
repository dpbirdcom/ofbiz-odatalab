package com.dpbird.odata;

import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntitySet;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerFactory;
import com.dpbird.odata.handler.NavigationHandler;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceValidationException;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class OdataWriter extends OfbizOdataProcessor {
    public static final String module = OdataWriter.class.getName();

    private EdmBindingTarget edmBindingTarget;
    EdmNavigationProperty edmNavigationProperty;
    private Entity entityToWrite;
    private final String rawServiceUri;
    private final ServiceMetadata serviceMetadata;
    private boolean isCreatable = true;

    public OdataWriter(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
        if (UtilValidate.isNotEmpty(edmParams)) {
            this.edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            this.edmNavigationProperty = (EdmNavigationProperty) edmParams.get("edmNavigationProperty");
            this.entityToWrite = (Entity) edmParams.get("entityToWrite");
        }
        this.oData = (OData) odataContext.get("oData");
        this.serviceMetadata = (ServiceMetadata) odataContext.get("serviceMetadata");
        this.edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        this.rawServiceUri = (String) odataContext.get("rawServiceUri");
        if (this.entityToWrite != null) {
            processEntitySetValues();
        }
    }

    public OdataOfbizEntity createEntityData(Entity entityToWrite) throws OfbizODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        addEntitySetCondition(entityToWrite);
        //通过接口实例创建实体数据
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        Map<String, Object> created = entityHandler.create(entityToWrite, odataContext, edmBindingTarget, null);
        OdataOfbizEntity entityCreated = resultToEntity(edmEntityType, created);
        //因为要返回所创建的Entity，所以添加语义化字段
        OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, this.edmProvider,
                queryOptions, Collections.singletonList(entityCreated), locale, userLogin);

        //BindingLinks_@odata.bind
        applyBindingLinks(entityCreated, entityToWrite);
        //Create nested entities
        createNestedEntities(entityCreated, entityToWrite, edmBindingTarget);
        return entityCreated;
    }


    public Entity createRelatedEntity(Entity entity, Entity entityToWrite) throws OfbizODataException {
        EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
        //获取创建参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> insertParam = navigationHandler.getInsertParam(odataContext, (OdataOfbizEntity) entity, edmEntityType,
                edmNavigationProperty, null);
        //创建
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        Map<String, Object> created = entityHandler.create(entityToWrite, odataContext, edmBindingTarget, insertParam);
        OdataOfbizEntity entityCreated = resultToEntity(edmNavigationProperty.getType(), created);

        //BindingLinks_@odata.bind
        applyBindingLinks(entityCreated, entityToWrite);
        //Create nested entities
        EdmBindingTarget relatedBindingTarget = edmBindingTarget.getRelatedBindingTarget(edmNavigationProperty.getName());
        createNestedEntities(entityCreated, entityToWrite, relatedBindingTarget);
        return entityCreated;
    }

    public OdataOfbizEntity updateEntityData(Map<String, Object> primaryKey, Entity entityToWrite)
            throws OfbizODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        Map<String, Object> update = entityHandler.update(primaryKey, entityToWrite, odataContext, edmBindingTarget, null);
        OdataOfbizEntity updatedEntity = resultToEntity(edmEntityType, update);
        //BindingLinks_@odata.bind
        Util.mergeEntity(updatedEntity, entityToWrite);
        applyBindingLinks(updatedEntity, entityToWrite);

        // Create nested entities
        createNestedEntities(updatedEntity, entityToWrite, edmBindingTarget);
        return updatedEntity;
    }

    public Entity updateRelatedEntity(Entity entity, Entity entityToWrite, Map<String, Object> primaryKey) throws OfbizODataException {
        EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
        //获取更新参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> updateParam = navigationHandler.getUpdateParam(odataContext, (OdataOfbizEntity) entity, edmEntityType,
                edmNavigationProperty, null);
        //更新
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        Map<String, Object> updateResult = entityHandler.update(primaryKey, entityToWrite, odataContext, edmBindingTarget, updateParam);
        return resultToEntity(edmNavigationProperty.getType(), updateResult);
    }

    public void deleteEntity(OdataOfbizEntity entity) throws OfbizODataException {
        EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmEntityType, edmProvider, delegator);
        entityHandler.delete(entity, odataContext, edmBindingTarget, null);
    }

    public void deleteRelatedEntity(Entity entity, Entity entityToDelete) throws OfbizODataException {
        EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
        //获取删除参数
        NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
        Map<String, Object> deleteParam = navigationHandler.getDeleteParam(odataContext, (OdataOfbizEntity) entity, edmEntityType,
                edmNavigationProperty, null);
        //删除
        EntityHandler entityHandler = HandlerFactory.getEntityHandler(edmNavigationProperty.getType(), edmProvider, delegator);
        entityHandler.delete(entityToDelete, odataContext, edmBindingTarget, deleteParam);
    }

    private void processEntitySetValues() {
        EntitySetHandler entitySetHandler = EntitySetHandlerFactory.getEntitySetHandler(this.edmBindingTarget.getName());
        String fullQualifiedTypeName = this.edmBindingTarget.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
        if (!entityToWrite.getType().equals(fullQualifiedTypeName)) {
            return;
        }
        this.isCreatable = entitySetHandler.isCreatable();
        Map<String, Object> defaultValues = entitySetHandler.defaultFieldValues();
        if (UtilValidate.isNotEmpty(defaultValues)) {
            Set<Entry<String, Object>> entrySet = defaultValues.entrySet();
            for (Entry<String, Object> entry : entrySet) {
                Property property = new Property();
                property.setName(entry.getKey());
                EdmElement edmProperty = this.edmBindingTarget.getEntityType().getProperty(entry.getKey());
                if (UtilValidate.isEmpty(edmProperty)) {
                    entityToWrite.addProperty(new Property(null, entry.getKey(), ValueType.PRIMITIVE, entry.getValue()));
                    continue;
                }
                String typeFullQualifiedNameStr = edmProperty.getType().getFullQualifiedName().toString();
                property.setType(typeFullQualifiedNameStr);
                property.setValue(ValueType.PRIMITIVE, entry.getValue());
                entityToWrite.addProperty(property);
            }
        }
    }

    private void addEntitySetCondition(Entity entityToWrite) throws OfbizODataException {
        String fullQualifiedTypeName = this.edmBindingTarget.getEntityType().getFullQualifiedName().getFullQualifiedNameAsString();
        if (!entityToWrite.getType().equals(fullQualifiedTypeName)) {
            return;
        }
        OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) this.edmProvider.getEntityContainer()
                .getEntitySet(edmBindingTarget.getName());
        Map<String, Object> conditionMap = Util.parseConditionMap(csdlEntitySet.getConditionStr(), userLogin);
        if (UtilValidate.isNotEmpty(conditionMap)) {
            Set<Entry<String, Object>> entrySet = conditionMap.entrySet();
            for (Entry<String, Object> entry : entrySet) {
                Property property = new Property();
                property.setName(entry.getKey());
                EdmElement edmProperty = this.edmBindingTarget.getEntityType().getProperty(entry.getKey());
                if (UtilValidate.isEmpty(edmProperty)) {
                    entityToWrite.addProperty(new Property(null, entry.getKey(), ValueType.PRIMITIVE, entry.getValue()));
                    continue;
                }
                String typeFullQualifiedNameStr = edmProperty.getType().getFullQualifiedName().toString();
                property.setType(typeFullQualifiedNameStr);
                property.setValue(ValueType.PRIMITIVE, entry.getValue());
                entityToWrite.addProperty(property);
            }
        }
    }

    private void createNestedEntities(OdataOfbizEntity entityCreated, Entity entityToWrite, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        for (final Link link : entityToWrite.getNavigationLinks()) {
            if (UtilValidate.isEmpty(link.getInlineEntity()) && UtilValidate.isEmpty(link.getInlineEntitySet())) {
                continue;
            }
            EdmNavigationProperty edmNavigationProperty = edmBindingTarget.getEntityType().getNavigationProperty(link.getTitle());
            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget,
                    "edmNavigationProperty", edmNavigationProperty, "entityToWrite", entityCreated);
            OdataWriter writer = new OdataWriter(odataContext, null, edmParams);
            List<Entity> inLineEntities = edmNavigationProperty.isCollection() ?
                    link.getInlineEntitySet().getEntities() : UtilMisc.toList(link.getInlineEntity());
            for (Entity nestedEntityToCreate : inLineEntities) {
                Entity nestedEntityCreated = writer.createRelatedEntity(entityCreated, nestedEntityToCreate);
                setLink(entityCreated, edmNavigationProperty.getName(), nestedEntityCreated);
            }
        }
    }

    private void setLink(Entity entity, String navigationPropertyName, Entity nestedEntity) {
        Link link = entity.getNavigationLink(navigationPropertyName);
        if (link == null) {
            link = new Link();
            link.setRel(Constants.NS_NAVIGATION_LINK_REL + navigationPropertyName);
            link.setType(Constants.ENTITY_SET_NAVIGATION_LINK_TYPE);
            link.setTitle(navigationPropertyName);
            if (entity.getId() != null) {
                link.setHref(entity.getId().toASCIIString() + "/" + navigationPropertyName);
            }
            link.setInlineEntity(nestedEntity);

            entity.getNavigationLinks().add(link);
        } else {
            link.setInlineEntity(nestedEntity);
        }
    }

    private void applyBindingLinks(OdataOfbizEntity entityCreated, Entity entityToWrite) throws OfbizODataException {
        EdmEntityType edmEntityType = this.edmBindingTarget.getEntityType();
        for (final Link link : entityToWrite.getNavigationBindings()) {
            if (link == null) {
                continue;
            }
            EdmNavigationProperty edmNavigationProperty = edmEntityType.getNavigationProperty(link.getTitle());
            EdmEntitySet navigationEntitySet = (EdmEntitySet) edmBindingTarget.getRelatedBindingTarget(link.getTitle());
            //通过Handler实例处理BindingLink
            NavigationHandler navigationHandler = HandlerFactory.getNavigationHandler(edmEntityType, edmNavigationProperty, edmProvider, delegator);
            List<String> bindLinks = edmNavigationProperty.isCollection() ? link.getBindingLinks() : UtilMisc.toList(link.getBindingLink());
            for (String bindLink : bindLinks) {
                Map<String, Object> bindingLinkPrimaryKey = getBindingLinkPrimaryKey(bindLink, navigationEntitySet, rawServiceUri);
                navigationHandler.bindNavigationLink(odataContext, entityCreated, edmEntityType, edmNavigationProperty, bindingLinkPrimaryKey);
            }
        }
    }

    private Map<String, Object> getBindingLinkPrimaryKey(final String entityId, final EdmEntitySet edmEntitySet,
                                                         final String rawServiceUri) throws OfbizODataException {
        try {
            UriResourceEntitySet entitySetResource = oData.createUriHelper().parseEntityId(serviceMetadata.getEdm(), entityId, rawServiceUri);
            if (!entitySetResource.getEntitySet().getName().equals(edmEntitySet.getName())) {
                throw new OfbizODataException("Execpted an entity-id for entity set " + edmEntitySet.getName()
                        + " but found id for entity set " + entitySetResource.getEntitySet().getName());
            }
            return Util.uriParametersToMap(entitySetResource.getKeyPredicates(), entitySetResource.getEntityType());
        } catch (DeserializerException e) {
            throw new OfbizODataException(entityId + " is not a valid entity-Id");
        }

    }

    private OdataOfbizEntity resultToEntity(EdmEntityType edmEntityType, Map<String, Object> resultMap) throws OfbizODataException {
        if (resultMap instanceof GenericValue) {
            return OdataProcessorHelper.genericValueToEntity(dispatcher, edmProvider, edmEntityType, (GenericValue) resultMap, locale);
        } else {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            return (OdataOfbizEntity) Util.mapToEntity(csdlEntityType, resultMap);
        }
    }

}
