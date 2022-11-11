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
import org.apache.ofbiz.base.util.Debug;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizEntityProcessor implements MediaEntityProcessor {

    public static final String module = OfbizEntityProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private LocalDispatcher dispatcher;
    private Delegator delegator;
    private OfbizAppEdmProvider edmProvider;
    private HttpServletRequest httpServletRequest;
    private Locale locale = Locale.ROOT;
    private GenericValue userLogin;

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
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        UriResourcePartTyped uriResourcePartTyped = (UriResourcePartTyped) resourceParts.get(0); // first segment is the EntitySet

        Entity requestEntity;
        Entity createdEntity = null;
        EdmEntitySet targetEdmEntitySet = null;
        EdmEntityType targetEdmEntityType;
        if (!(uriResourcePartTyped instanceof UriResourceEntitySet) && !(uriResourcePartTyped instanceof UriResourceSingleton)) {
            return;
        }
        int segmentCount = resourceParts.size();
        List<UriParameter> keyPredicates = null;
        EdmNavigationProperty edmNavigationProperty = null;
        EdmEntityType edmTypeFilter = null;
        EdmBindingTarget edmBindingTarget = null;
        try {
            if (uriResourcePartTyped instanceof UriResourceEntitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResourcePartTyped;
                edmBindingTarget = uriResourceEntitySet.getEntitySet();
                edmTypeFilter = (EdmEntityType) uriResourceEntitySet.getTypeFilterOnCollection();
                targetEdmEntitySet = (EdmEntitySet) edmBindingTarget;
            } else if (uriResourcePartTyped instanceof UriResourceSingleton) {
                UriResourceSingleton uriResourceSingleton = (UriResourceSingleton) uriResourcePartTyped;
                edmBindingTarget = uriResourceSingleton.getSingleton();
            }
            if (segmentCount == 2) {
                UriResource lastSegment = resourceParts.get(1); // don't support more complex URIs
                if (lastSegment instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                    edmNavigationProperty = uriResourceNavigation.getProperty();
                    targetEdmEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
                    if (uriResourcePartTyped instanceof UriResourceEntitySet) {
                        keyPredicates = getUriParameters(resourceParts);
                    }
                }
            }
            if (segmentCount == 3) {
                UriResourceNavigation uriResourceSecondNavigation = (UriResourceNavigation) resourceParts.get(1);
                EdmNavigationProperty edmNavigationSecondProperty = uriResourceSecondNavigation.getProperty();
                edmBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationSecondProperty);
                UriResource lastSegment = resourceParts.get(2);
                if (lastSegment instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                    edmNavigationProperty = uriResourceNavigation.getProperty();
                    targetEdmEntitySet = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
                    keyPredicates = uriResourceSecondNavigation.getKeyPredicates();
                }
            }

            if (edmTypeFilter != null) {
                targetEdmEntityType = (EdmEntityType) edmTypeFilter;
            } else {
                targetEdmEntityType = targetEdmEntitySet.getEntityType();
            }

            InputStream requestInputStream = oDataRequest.getBody();
            ODataDeserializer deserializer = this.odata.createDeserializer(requestContentType);
            DeserializerResult result = deserializer.entity(requestInputStream, targetEdmEntityType);
            requestEntity = result.getEntity();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        try {
            if (segmentCount == 1) {
                Map<String, Object> serviceResult;
                Map<String, Object> serviceParms = UtilMisc.toMap("edmEntitySet", targetEdmEntitySet,
                        "edmTypeFilter", edmTypeFilter, "entityToWrite", requestEntity,
                        "rawServiceUri", oDataRequest.getRawBaseUri(), "oData", odata,
                        "serviceMetadata", serviceMetadata, "edmProvider", edmProvider,
                        "sapContextId", sapContextId, "userLogin", userLogin);
                serviceResult = dispatcher.runSync("dpbird.createEntityData", serviceParms);
                createdEntity = (Entity) serviceResult.get("createdEntity");
            } else if (segmentCount == 2 || segmentCount == 3) {
                Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, edmBindingTarget.getEntityType());
                Map<String, Object> serviceParms = UtilMisc.toMap("edmBindingTarget", edmBindingTarget,
                        "edmTypeFilter", edmTypeFilter, "edmNavigationProperty", edmNavigationProperty,
                        "keyMap", keyMap, "entityToWrite", requestEntity,
                        "rawServiceUri", oDataRequest.getRawBaseUri(), "oData", odata,
                        "serviceMetadata", serviceMetadata, "edmProvider", edmProvider,
                        "sapContextId", sapContextId, "httpServletRequest", httpServletRequest, "userLogin", userLogin);
                Map<String, Object> serviceResult;
                serviceResult = dispatcher.runSync("dpbird.createRelatedEntityData", serviceParms);
                createdEntity = (Entity) serviceResult.get("createdEntity");
            }
        } catch (GenericServiceException | OfbizODataException e) {
            e.printStackTrace();
            if (e.getCause() instanceof OfbizODataException) {
                OfbizODataException e1 = (OfbizODataException) e.getCause();
                throw new ODataApplicationException(e1.getMessage(), Integer.parseInt(e1.getODataErrorCode()), locale, e1.getODataErrorCode());
            }
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        if (createdEntity == null) {
            throw new ODataApplicationException("create entity failed",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        // serialize the response (we have to return the created entity)
        serializeEntity(oDataRequest, oDataResponse, targetEdmEntitySet, targetEdmEntityType, responseContentType,
                null, null, createdEntity);

        oDataResponse.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        if (UtilValidate.isNotEmpty(sapContextId)) {
            DataModifyActions.setResponseSessionContext(oDataResponse, sapContextId);
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
        // 1. Retrieve the entity set which belongs to the requested entity
        int segmentCount = resourceParts.size();
        UriResourcePartTyped uriResourcePartTyped = (UriResourcePartTyped) resourceParts.get(0);
        EdmBindingTarget edmBindingTarget;
        List<UriParameter> keyPredicates = null;
        if (uriResourcePartTyped instanceof UriResourceEntitySet) {
            UriResourceEntitySet resourceEntitySet = (UriResourceEntitySet) uriResourcePartTyped;
            edmBindingTarget = resourceEntitySet.getEntitySet();
            keyPredicates = resourceEntitySet.getKeyPredicates();
            //check If-Match
            AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, resourceEntitySet.getEntitySet(), keyPredicates);
        } else {
            edmBindingTarget = ((UriResourceSingleton) uriResourcePartTyped).getSingleton();
        }
        if (segmentCount == 1) { // no navigation
            // 2. delete the data in backend
            Map<String, Object> serviceParms = UtilMisc.toMap("edmEntitySet", edmBindingTarget,
                    "keyParams", keyPredicates, "rawServiceUri", oDataRequest.getRawBaseUri(), "oData", odata,
                    "serviceMetadata", serviceMetadata, "edmProvider", edmProvider,
                    "sapContextId", sapContextId, "userLogin", userLogin);
            try {
                dispatcher.runSync("dpbird.deleteEntityData", serviceParms);
            } catch (GenericServiceException e) {
                e.printStackTrace();
                if (e.getCause() instanceof OfbizODataException) {
                    OfbizODataException e1 = (OfbizODataException) e.getCause();
                    throw new ODataApplicationException(e1.getMessage(), Integer.parseInt(e1.getODataErrorCode()), locale, e1.getODataErrorCode());
                }
                throw new ODataApplicationException(e.getMessage(),
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
        }
        if (segmentCount == 2) { // 好像在FE的情形下，不会发生
            Debug.logInfo("Entering Delete operation the second segment", module);
            UriResource navSegment = resourceParts.get(1);
            if (navSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) navSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();

                Map<String, Object> serviceParms = UtilMisc.toMap("edmBindingTarget", edmBindingTarget,
                        "edmNavigationProperty", edmNavigationProperty, "keyParams", keyPredicates,
                        "navKeyParams", navKeyPredicates, "rawServiceUri", oDataRequest.getRawBaseUri(),
                        "oData", odata, "serviceMetadata", serviceMetadata, "edmProvider", edmProvider,
                        "sapContextId", sapContextId, "httpServletRequest", httpServletRequest, "userLogin", userLogin);
                try {
                    dispatcher.runSync("dpbird.deleteRelatedEntityData", serviceParms);
                } catch (GenericServiceException e) {
                    e.printStackTrace();
                    if (e.getCause() instanceof OfbizODataException) {
                        OfbizODataException e1 = (OfbizODataException) e.getCause();
                        throw new ODataApplicationException(e1.getMessage(), Integer.parseInt(e1.getODataErrorCode()), locale, e1.getODataErrorCode());
                    }
                    throw new ODataApplicationException(e.getMessage(),
                            HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
            }
        }

        // 3. configure the response object
        oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }

    @Override
    public void readEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseContentType)
            throws ODataApplicationException, ODataLibraryException {
        //Check SAP-ContextId
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        try {
            OdataReader reader = new OdataReader(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<UriResourceDataInfo> resourceDataInfos = reader.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            UriResourceDataInfo uriResourceDataInfo = resourceDataInfos.get(resourceDataInfos.size() - 1);
            Entity responseEntity = (Entity) uriResourceDataInfo.getEntityData();
            //response
            if (responseEntity != null) {
                serializeEntity(oDataRequest, oDataResponse, uriResourceDataInfo.getEdmBindingTarget(), uriResourceDataInfo.getEdmEntityType(),
                        responseContentType, uriInfo.getExpandOption(), uriInfo.getSelectOption(), responseEntity);
            }
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
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
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        EdmEntitySet responseEdmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntityType responseEdmEntityType = edmEntitySet.getEntityType();
        ODataDeserializer deserializer = this.odata.createDeserializer(requestContentType);
        Entity requestEntity;
        OdataOfbizEntity updatedEntity;
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        try {
            Map<String, Object> keyMap = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntityType);
            Map<String, Object> serviceParms = UtilMisc.toMap("edmBindingTarget", edmEntitySet,
                    "rawServiceUri", oDataRequest.getRawBaseUri(), "keyMap", keyMap,
                    "oData", odata, "serviceMetadata", serviceMetadata, "edmProvider", edmProvider,
                    "sapContextId", sapContextId, "userLogin", userLogin, "request", httpServletRequest);
            if (resourcePaths.size() == 1) {
                requestEntity = deserializer.entity(oDataRequest.getBody(), edmEntityType).getEntity();
                serviceParms.put("entityToWrite", requestEntity);
                //check If-Match
                AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, edmEntitySet, uriResourceEntitySet.getKeyPredicates());
                Map<String, Object> serviceResult = dispatcher.runSync("dpbird.updateEntityData", serviceParms);
                updatedEntity = (OdataOfbizEntity) serviceResult.get("entity");
            } else if (resourcePaths.size() == 2) {
                //两段式更新
                UriResourceNavigation resourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
                EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
                requestEntity = deserializer.entity(oDataRequest.getBody(), edmNavigationProperty.getType()).getEntity();
                //把response对象改为Navigation
                responseEdmEntityType = edmNavigationProperty.getType();
                responseEdmEntitySet = Util.getNavigationTargetEntitySet(edmEntitySet, edmNavigationProperty);
                List<UriParameter> keyPredicates = resourceNavigation.getKeyPredicates();
                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet, "edmNavigationProperty", edmNavigationProperty);
//                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, UtilMisc.toMap("keyMap", keyMap), edmParams);
                if (UtilValidate.isNotEmpty(keyPredicates)) {
                    //Collection  即使传递了子对象id，也要确认是否是当前主对象的关联数据
                    Map<String, Object> navigationKeyMap = Util.uriParametersToMap(keyPredicates, responseEdmEntityType);
                    OdataReader reader = new OdataReader(getOdataContext(), UtilMisc.toMap("keyMap", keyMap), edmParams);
                    Entity entity = reader.findOne(keyMap, null);
                    EntityCollection relEntity = reader.findRelatedList(entity, edmNavigationProperty, new HashMap<>(), navigationKeyMap);
                    if (relEntity == null || relEntity.getEntities().size() < 1) {
                        //不存在的子对象
                        throw new ODataApplicationException("Relation data not found: " + navigationKeyMap, HttpStatusCode.NOT_FOUND.getStatusCode(), locale);
                    }
                    //check If-Match
                    AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, responseEdmEntitySet, keyPredicates);
                    serviceParms.put("edmBindingTarget", responseEdmEntitySet);
                    serviceParms.put("keyMap", navigationKeyMap);
                    serviceParms.put("entityToWrite", requestEntity);
                    Map<String, Object> serviceResult = dispatcher.runSync("dpbird.updateEntityData", serviceParms);
                    updatedEntity = (OdataOfbizEntity) serviceResult.get("entity");
                } else {
                    //NoCollection 先查询，判断要创建还是更新
//                    OdataOfbizEntity relatedEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(keyMap, edmNavigationProperty, null);
                    OdataReader reader = new OdataReader(odataContext, null, UtilMisc.toMap("edmBindingTarget", edmEntitySet));
                    OdataOfbizEntity entity = (OdataOfbizEntity) reader.findOne(keyMap, null);
                    OdataOfbizEntity relatedEntity = (OdataOfbizEntity) reader.findRelatedOne(entity, edmNavigationProperty);
                    if (relatedEntity == null) {
                        //create
                        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
                        updatedEntity = OdataWriterHelper.createEntitySetRelatedEntityData(delegator, dispatcher, httpServletRequest, edmProvider,
                                csdlEntityType, keyMap, edmNavigationProperty.getName(), requestEntity,
                                null, userLogin, locale);
                    } else {
                        //update
                        AnnotationCheck.checkIfMatch(edmProvider, oDataRequest, relatedEntity, responseEdmEntitySet);
                        serviceParms.put("edmBindingTarget", responseEdmEntitySet);
                        serviceParms.put("keyMap", relatedEntity.getKeyMap());
                        serviceParms.put("entityToWrite", requestEntity);
                        Map<String, Object> serviceResult = dispatcher.runSync("dpbird.updateEntityData", serviceParms);
                        updatedEntity = (OdataOfbizEntity) serviceResult.get("entity");
                    }
                }
            } else {
                throw new ODataApplicationException("Not supported",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), locale);
            }
        } catch (GenericServiceException | OfbizODataException e) {
            e.printStackTrace();
            if (e.getCause() instanceof OfbizODataException) {
                OfbizODataException e1 = (OfbizODataException) e.getCause();
                throw new ODataApplicationException(e1.getMessage(), Integer.parseInt(e1.getODataErrorCode()), locale, e1.getODataErrorCode());
            }
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        if (updatedEntity == null) {
            throw new ODataApplicationException("The request resource is not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), locale);
        }

        this.serializeEntity(oDataRequest, oDataResponse, responseEdmEntitySet, responseEdmEntityType, responseContentType,
                null, null, updatedEntity);
        // 3. configure the response object
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
    }

    private List<UriParameter> getUriParameters(List<UriResource> resourceParts) {
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        return uriResourceEntitySet.getKeyPredicates();
    }

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
    }

    @Override
    public void readMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException {
        try {
            OdataReader reader = new OdataReader(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<UriResourceDataInfo> resourceDataInfos = reader.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            UriResourceDataInfo uriResourceDataInfo = ListUtil.getLast(resourceDataInfos);
            OdataOfbizEntity responseEntity = (OdataOfbizEntity) uriResourceDataInfo.getEntityData();
            EdmEntityType responseEdmEntityType = uriResourceDataInfo.getEdmEntityType();
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
            EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
            EdmEntityType edmEntityType = uriResourceEntitySet.getEntityType();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(uriResourceEntitySet.getEntityType().getFullQualifiedName());

            //获取request表单数据
            Map<String, Object> uploadFromData = getUploadFromData(httpServletRequest, odata);
            Entity entityToWrite = Util.mapToEntity(ofbizCsdlEntityType, uploadFromData);

            //create
            Map<String, Object> serviceParams = UtilMisc.toMap("edmEntitySet", uriResourceEntitySet.getEntitySet(),
                    "entityToWrite", entityToWrite, "edmProvider", edmProvider, "userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createMediaEntityData", serviceParams);
            OdataOfbizEntity createdEntity = (OdataOfbizEntity) serviceResult.get("createdEntity");
            URI entityId = createdEntity.getId();
            if (entityId == null) {
                entityId = Util.createId(edmEntitySet.getName(), edmEntityType, ofbizCsdlEntityType, createdEntity.getGenericValue());
            }
            //return
            serializeEntity(request, response, edmEntitySet, edmEntityType,
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
            Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, edmEntityType);
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(uriResourceEntitySet.getEntityType().getFullQualifiedName());

            //获取request表单数据
            Map<String, Object> uploadFromData = getUploadFromData(httpServletRequest, odata);
            Entity entityToWrite = Util.mapToEntity(ofbizCsdlEntityType, uploadFromData);

            //update
            Map<String, Object> serviceParams = UtilMisc.toMap("keyMap", keyMap, "edmEntitySet", edmEntitySet,
                    "entityToWrite", entityToWrite, "edmProvider", edmProvider, "httpServletRequest", httpServletRequest,
                    "userLogin", userLogin);
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.updateMediaEntityData", serviceParams);
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
        UriResourceEntitySet resourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        Map<String, Object> serviceParms = UtilMisc.toMap("edmEntitySet", resourceEntitySet.getEntitySet(),
                "keyParams", resourceEntitySet.getKeyPredicates(), "rawServiceUri", request.getRawBaseUri(), "oData", odata,
                "serviceMetadata", serviceMetadata, "edmProvider", edmProvider, "userLogin", userLogin);
        try {
            dispatcher.runSync("dpbird.deleteEntityData", serviceParms);
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
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
