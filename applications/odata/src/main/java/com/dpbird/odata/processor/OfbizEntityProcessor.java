package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.fop.util.ListUtil;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.ContextURL.Suffix;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.FixedFormatDeserializer;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.MediaEntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class OfbizEntityProcessor implements MediaEntityProcessor {
    public static final String module = OfbizEntityProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final LocalDispatcher dispatcher;
    private final Delegator delegator;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;
    private final Locale locale;
    private final GenericValue userLogin;

    public OfbizEntityProcessor(HttpServletRequest httpServletRequest, Delegator delegator, LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.edmProvider = edmProvider;
        this.httpServletRequest = httpServletRequest;
        this.locale = locale;
        this.userLogin = userLogin;
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void createEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType requestContentType,
                             ContentType responseContentType) throws ODataApplicationException, ODataLibraryException {
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        Entity createdEntity;
        EdmBindingTarget edmBindingTarget;
        try {
            Map<String, Object> odataContext = getOdataContext();
            odataContext.put("rawServiceUri", oDataRequest.getRawBaseUri());
            List<UriResource> resourceParts = uriInfo.getUriResourceParts();
            UriResource lastUriResource = ListUtil.getLast(resourceParts);
            if (resourceParts.size() == 1) {
                //create
                edmBindingTarget = ((UriResourceEntitySet) lastUriResource).getEntitySet();
                ODataDeserializer deserializer = this.odata.createDeserializer(requestContentType, serviceMetadata);
                DeserializerResult result = deserializer.entity(oDataRequest.getBody(), edmBindingTarget.getEntityType());
                Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", odataContext, "edmBindingTarget", edmBindingTarget,
                        "entityToWrite", result.getEntity(), "sapContextId", sapContextId, "userLogin", userLogin);
                Map<String, Object> createdResult = dispatcher.runSync("dpbird.createEntity", serviceParms);
                createdEntity = (Entity) createdResult.get("createdEntity");
            } else {
                //多段式create
                UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), sapContextId);
                ArrayList<UriResource> uriResources = new ArrayList<>(resourceParts.subList(0, resourceParts.size() - 1));
                List<OdataParts> odataParts = uriResourceProcessor.readUriResource(uriResources, null);
                OdataParts lastResourceData = ListUtil.getLast(odataParts);
                Entity entity = (Entity) lastResourceData.getEntityData();
                edmBindingTarget = lastResourceData.getEdmBindingTarget();
                UriResourceNavigation resourceNavigation = (UriResourceNavigation) lastUriResource;
                EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
                ODataDeserializer deserializer = this.odata.createDeserializer(requestContentType, serviceMetadata);
                DeserializerResult result = deserializer.entity(oDataRequest.getBody(), edmNavigationProperty.getType());
                Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", odataContext, "entity", entity,
                        "edmBindingTarget", edmBindingTarget, "entityToWrite", result.getEntity(), "edmNavigationProperty", edmNavigationProperty,
                        "sapContextId", sapContextId, "userLogin", userLogin);
                Map<String, Object> createdResult = dispatcher.runSync("dpbird.createRelatedEntity", serviceParms);
                createdEntity = (Entity) createdResult.get("createdEntity");
                edmBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
            }
            if (createdEntity == null) {
                throw new ODataApplicationException("create entity failed", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
            // serialize the response (we have to return the created entity)
            serializeEntity(oDataRequest, oDataResponse, edmBindingTarget, edmBindingTarget.getEntityType(), responseContentType, null, null, createdEntity);
            oDataResponse.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        } catch (GenericServiceException | OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        } finally {
            if (UtilValidate.isNotEmpty(sapContextId)) {
                DataModifyActions.setResponseSessionContext(oDataResponse, sapContextId);
            }
        }
    }

    @Override
    public void deleteEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), sapContextId);
            List<OdataParts> odataParts = uriResourceProcessor.readUriResource(resourceParts, null);
            if (resourceParts.size() == 1 && resourceParts.get(0) instanceof UriResourceEntitySet) {
                //delete
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
                OdataParts lastResourceData = ListUtil.getLast(odataParts);
                OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) lastResourceData.getEntityData();
                //checkEtag
                AnnotationCheck.checkIfMatch(edmProvider, oDataRequest, ofbizEntity, uriResourceEntitySet.getEntitySet());
                Map<String, Object> serviceParms = UtilMisc.toMap("edmEntitySet", uriResourceEntitySet.getEntitySet(),
                        "odataContext", getOdataContext(), "entity", ofbizEntity, "sapContextId", sapContextId, "userLogin", userLogin);
                dispatcher.runSync("dpbird.deleteEntity", serviceParms);
            } else {
                //多段式delete
                OdataParts previousResourceData = odataParts.get(odataParts.size() - 2);
                OdataParts lastResourceData = ListUtil.getLast(odataParts);
                OdataOfbizEntity entity = (OdataOfbizEntity) previousResourceData.getEntityData();
                OdataOfbizEntity toDeleteEntity = (OdataOfbizEntity) lastResourceData.getEntityData();
                UriResourceNavigation resourceNavigation = (UriResourceNavigation) ListUtil.getLast(resourceParts);
                Map<String, Object> serviceParms = UtilMisc.toMap("edmBindingTarget", previousResourceData.getEdmBindingTarget(),
                        "odataContext", getOdataContext(), "entity", entity, "entityToDelete", toDeleteEntity,
                        "edmNavigationProperty", resourceNavigation.getProperty(), "userLogin", userLogin);
                dispatcher.runSync("dpbird.deleteRelatedEntity", serviceParms);
            }
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
        }
        // 3. configure the response object
        oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }

    @Override
    public void readEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseContentType)
            throws ODataApplicationException, ODataLibraryException {
        //Check SAP-ContextId
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), sapContextId);
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            OdataParts odataParts = resourceDataInfos.get(resourceDataInfos.size() - 1);
            Entity responseEntity = (Entity) odataParts.getEntityData();
            if (UtilValidate.isEmpty(responseEntity)) {
                throw new ODataApplicationException("Not found.", HttpStatus.SC_NOT_FOUND, locale);
            }
            //response
            serializeEntity(oDataRequest, oDataResponse, odataParts.getEdmBindingTarget(), odataParts.getEdmEntityType(),
                    responseContentType, uriInfo.getExpandOption(), uriInfo.getSelectOption(), responseEntity);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
        } finally {
            if (UtilValidate.isNotEmpty(sapContextId)) {
                DataModifyActions.setResponseSessionContext(oDataResponse, sapContextId);
            }
        }
    }

    private void serializeEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, EdmBindingTarget edmBindingTarget,
                                 EdmEntityType edmEntityType, ContentType contentType,
                                 ExpandOption expandOption, SelectOption selectOption, Entity entity)
            throws SerializerException {
        //Remove stream property
        entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
        String typeName = edmBindingTarget != null ? edmBindingTarget.getName() : edmEntityType.getName();
        try {
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                    .entitySetOrSingletonOrType(typeName).selectList(selectList).suffix(Suffix.ENTITY).build();
            // expand and select currently not supported
            EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).select(selectOption)
                    .expand(expandOption).build();
            ODataSerializer serializer = odata.createSerializer(contentType);
            SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, options);
            // configure the response object
            oDataResponse.setContent(serializerResult.getContent());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType requestContentType,
                             ContentType responseContentType) throws ODataApplicationException, ODataLibraryException {
        OdataOfbizEntity updatedEntity;
        ODataDeserializer deserializer = this.odata.createDeserializer(requestContentType, serviceMetadata);
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);

        EdmBindingTarget edmBindingTarget;
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        UriResource lastUriResource = ListUtil.getLast(resourceParts);
        try {
            if (resourceParts.size() == 1) {
                //update
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) lastUriResource;
                edmBindingTarget = uriResourceEntitySet.getEntitySet();
                EdmEntityType edmEntityType = edmBindingTarget.getEntityType();
                Map<String, Object> primaryKey = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntityType, edmProvider);
                DeserializerResult result = deserializer.entity(oDataRequest.getBody(), edmEntityType);
                Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", getOdataContext(), "edmBindingTarget", edmBindingTarget,
                        "primaryKey", primaryKey, "entityToWrite", result.getEntity(), "sapContextId", sapContextId, "userLogin", userLogin);
                Map<String, Object> createdResult = dispatcher.runSync("dpbird.updateEntity", serviceParms);
                updatedEntity = (OdataOfbizEntity) createdResult.get("entity");
            } else {
                //多段式update
                UriResourceNavigation resourceNavigation = (UriResourceNavigation) lastUriResource;
                EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
                Map<String, Object> primaryKey = null;
                if (UtilValidate.isNotEmpty(resourceNavigation.getKeyPredicates())) {
                    primaryKey = Util.uriParametersToMap(resourceNavigation.getKeyPredicates(), edmNavigationProperty.getType(), edmProvider);
                }
                UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), sapContextId);
                ArrayList<UriResource> uriResources = new ArrayList<>(resourceParts.subList(0, resourceParts.size() - 1));
                List<OdataParts> odataParts = uriResourceProcessor.readUriResource(uriResources, null);
                OdataParts lastResourceData = ListUtil.getLast(odataParts);
                Entity entity = (Entity) lastResourceData.getEntityData();
                edmBindingTarget = lastResourceData.getEdmBindingTarget();
                DeserializerResult result = deserializer.entity(oDataRequest.getBody(), edmNavigationProperty.getType());
                //在这里做一次查询，即使传递了主键也要确保当前的Navigation是合法的数据
                OdataReader reader = new OdataReader(getOdataContext(), new HashMap<>(), UtilMisc.toMap("edmEntityType", edmBindingTarget.getEntityType()));
                boolean isCreate = false;
                if (primaryKey != null) {
                    EntityCollection relatedList = reader.findRelatedList(entity, edmNavigationProperty, new HashMap<>(), primaryKey);
                    if (UtilValidate.isEmpty(relatedList) || UtilValidate.isEmpty(relatedList.getEntities())) {
                        throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_FOUND), "Entity not found.");
                    }
                } else {
                    //NoCollection或许是创建
                    OdataOfbizEntity relatedOne = (OdataOfbizEntity) reader.findRelatedOne(entity, edmBindingTarget.getEntityType(), edmNavigationProperty, new HashMap<>());
                    if (UtilValidate.isEmpty(relatedOne)) {
                        isCreate = true;
                    } else {
                        primaryKey = relatedOne.getKeyMap();
                    }
                }
                if (isCreate) {
                    Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", getOdataContext(), "entity", entity,
                            "edmBindingTarget", edmBindingTarget, "entityToWrite", result.getEntity(), "edmNavigationProperty", edmNavigationProperty,
                            "sapContextId", sapContextId, "userLogin", userLogin);
                    Map<String, Object> createdResult = dispatcher.runSync("dpbird.createRelatedEntity", serviceParms);
                    updatedEntity = (OdataOfbizEntity) createdResult.get("createdEntity");
                } else {
                    //update
                    Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", getOdataContext(), "primaryKey", primaryKey, "entity", entity,
                            "edmBindingTarget", edmBindingTarget, "entityToWrite", result.getEntity(), "edmNavigationProperty", edmNavigationProperty,
                            "sapContextId", sapContextId, "userLogin", userLogin);
                    Map<String, Object> createdResult = dispatcher.runSync("dpbird.updateRelatedEntity", serviceParms);
                    updatedEntity = (OdataOfbizEntity) createdResult.get("entity");
                }
                edmBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
            }
            if (updatedEntity == null) {
                throw new ODataApplicationException("update entity failed", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
            this.serializeEntity(oDataRequest, oDataResponse, edmBindingTarget, edmBindingTarget.getEntityType(), responseContentType,
                    null, null, updatedEntity);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        } catch (GenericServiceException | OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        } finally {
            if (UtilValidate.isNotEmpty(sapContextId)) {
                oDataResponse.setHeader("SAP-ContextId", sapContextId);
            }
        }
    }

    private List<UriParameter> getUriParameters(List<UriResource> resourceParts) {
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        return uriResourceEntitySet.getKeyPredicates();
    }

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale, "oData", odata, "serviceMetadata", serviceMetadata);
    }

    @Override
    public void readMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException {
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            OdataOfbizEntity responseEntity = (OdataOfbizEntity) odataParts.getEntityData();
            EdmEntityType responseEdmEntityType = odataParts.getEdmEntityType();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(responseEdmEntityType.getFullQualifiedName());
            CsdlProperty streamCsdlProperty = ofbizCsdlEntityType.getStreamProperty();
            if (streamCsdlProperty == null) {
                throw new ODataApplicationException("The media stream field is not defined.", HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
            }
            //响应给客户端，ContentType为字段mimeType的属性值，如果没有指定那么使用对应DateResource的mimeType
            String mimeType = streamCsdlProperty.getMimeType();
            if (mimeType == null) {
                mimeType = EntityQuery.use(delegator).from("DataResource")
                        .select("mimeTypeId").where(responseEntity.getKeyMap()).queryOne().getString("mimeTypeId");
            }
            //未找到媒体对应的响应类型mimeType
            if (mimeType == null) {
                throw new ODataApplicationException("The media response type was not specified.",
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
            Property property = responseEntity.getProperty(streamCsdlProperty.getName());
            if (property != null && property.getValue() != null) {
                final byte[] bytes = (byte[]) property.getValue();
                final InputStream responseContent = odata.createFixedFormatSerializer().binary(bytes);
                response.setContent(responseContent);
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, mimeType);
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale);
        } catch (SerializerException | GenericEntityException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    @Override
    public void createMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                  ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        try {
            //URIResource
            List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
            UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResourceParts.get(0);
            EdmBindingTarget edmBindingTarget = uriResourceEntitySet.getEntitySet();
            EdmEntityType edmEntityType = uriResourceEntitySet.getEntityType();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(uriResourceEntitySet.getEntityType().getFullQualifiedName());

            //获取request表单数据
            Map<String, Object> uploadFromData = getUploadFromData(httpServletRequest, odata);
            Entity entityToWrite = Util.mapToEntity(ofbizCsdlEntityType, uploadFromData);

            //create
            Map<String, Object> serviceParms = UtilMisc.toMap("odataContext", getOdataContext(),
                    "edmBindingTarget", edmBindingTarget, "entityToWrite", entityToWrite, "userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createEntity", serviceParms);
            OdataOfbizEntity createdEntity = (OdataOfbizEntity) serviceResult.get("createdEntity");
            URI entityId = createdEntity.getId();
            if (entityId == null) {
                entityId = Util.createId(edmBindingTarget.getName(), edmEntityType, ofbizCsdlEntityType, createdEntity.getGenericValue());
            }
            //return
            serializeEntity(request, response, edmBindingTarget, edmEntityType,
                    responseFormat, null, null, createdEntity);
            response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
            //Location
            response.setHeader("Location", entityId + "/$value");
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (OfbizODataException | GeneralException | FileUploadException | IOException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    @Override
    public void updateMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        try {
            //URIResource
            List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
            UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResourceParts.get(0);
            EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
            EdmEntityType edmEntityType = uriResourceEntitySet.getEntityType();
            List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
            Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, edmEntityType, edmProvider);
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(uriResourceEntitySet.getEntityType().getFullQualifiedName());

            //获取request表单数据
            Map<String, Object> uploadFromData = getUploadFromData(httpServletRequest, odata);
            Entity entityToWrite = Util.mapToEntity(ofbizCsdlEntityType, uploadFromData);

            //update
            Map<String, Object> serviceParams = UtilMisc.toMap("primaryKey", keyMap, "edmBindingTarget", edmEntitySet,
                    "entityToWrite", entityToWrite, "odataContext", getOdataContext(), "userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.updateEntity", serviceParams);
            OdataOfbizEntity entity = (OdataOfbizEntity) serviceResult.get("entity");

            //return
            serializeEntity(request, response, edmEntitySet, edmEntityType, responseFormat, null, null, entity);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (OfbizODataException | IOException | FileUploadException | GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }

    }

    @Override
    public void deleteMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<OdataParts> odataParts = uriResourceProcessor.readUriResource(resourceParts, null);
            OdataParts lastResourceData = ListUtil.getLast(odataParts);
            OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) lastResourceData.getEntityData();
            EdmEntitySet edmEntitySet = (EdmEntitySet) lastResourceData.getEdmBindingTarget();
            Map<String, Object> serviceParms = UtilMisc.toMap("edmEntitySet", edmEntitySet,
                    "odataContext", getOdataContext(), "entity", ofbizEntity,  "userLogin", userLogin);
            dispatcher.runSync("dpbird.deleteEntity", serviceParms);
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        } catch (GenericServiceException | OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
        }

    }

    /**
     * 获取请求体的表单数据
     */
    private Map<String, Object> getUploadFromData(HttpServletRequest httpServletRequest, OData odata) throws FileUploadException, IOException, DeserializerException {
        //解析媒体文件对象
        ServletFileUpload servletUpload = new ServletFileUpload(new DiskFileItemFactory());
        List<FileItem> fileItems = servletUpload.parseRequest(httpServletRequest);
        FixedFormatDeserializer formatDeserializer = odata.createFixedFormatDeserializer();
        //媒体数据
        Map<String, Object> paramMap = new HashMap<>();
        for (FileItem fileItem : fileItems) {
            if (fileItem.isFormField()) {
                //form表单的普通字段
                paramMap.put(fileItem.getFieldName(), fileItem.getString());
            } else {
                //媒体
                byte[] mediaData = formatDeserializer.binary(fileItem.getInputStream());
                paramMap.put(fileItem.getFieldName(), mediaData);
            }
        }
        return paramMap;
    }
}
