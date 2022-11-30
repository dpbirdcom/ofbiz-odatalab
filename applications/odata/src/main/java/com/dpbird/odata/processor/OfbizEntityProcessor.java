package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.ContextURL.Suffix;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.ex.ODataException;
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
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
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
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        Map<String, QueryOption> queryOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        EdmFunction edmFunction;
        EdmEntityType edmEntityType = null;
        UriResource lastUriResource = resourcePaths.get(resourcePathSize - 1);
        UriResource firstUriResource = resourcePaths.get(0);
        if (lastUriResource instanceof UriResourceFunction) {
            edmFunction = (EdmFunction) ((UriResourceFunction) lastUriResource).getFunction();
            edmEntityType = (EdmEntityType) edmFunction.getReturnType().getType();
        }

        Entity entity = null;
        if (firstUriResource instanceof UriResourceEntitySet) {
            try {
                readEntityInternal(oDataRequest, oDataResponse, uriInfo, responseContentType);
            } catch (OfbizODataException e) {
                e.printStackTrace();
                throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(),
                        locale);
            }
        } else if (firstUriResource instanceof UriResourceFunction) {
            UriResourceFunction importFunction = (UriResourceFunction) firstUriResource;
            entity = processImportFunctionEntity(uriInfo, queryOptions);
            //允许没有返回值
            if (entity == null && importFunction.getFunction().getReturnType().isNullable()) {
                oDataResponse.setContent(null);
                oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
                return;
            }
        } else if (firstUriResource instanceof UriResourceSingleton) {
            readSingleton(oDataRequest, oDataResponse, uriInfo, responseContentType);
        } else {
            throw new ODataApplicationException("Only EntitySet is supported",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), locale);
        }

        // serialize entity
        if (entity != null) {
            ContextURL contextURL;
            try {
                contextURL = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/")).type(edmEntityType)
                        .build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new ODataApplicationException("Something wrong", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(),
                        locale);
            }
            final EntitySerializerOptions opts = EntitySerializerOptions.with()
                    .contextURL(contextURL)
                    .expand(expandOption)
                    .select(selectOption).build();
            final ODataSerializer serializer = odata.createSerializer(responseContentType);
            final SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, opts);

            // 3rd configure the response object
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
        }
    }

    private void readSingleton(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                               ContentType responseContentType) throws ODataApplicationException, SerializerException {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();
        UriResourceSingleton uriResourceSingleton = (UriResourceSingleton) resourceParts.get(0);
        EdmSingleton edmSingleton = uriResourceSingleton.getSingleton();
        EdmEntityType responseEdmEntityType = uriResourceSingleton.getEntityType(); // we'll need this to build the ContextURL

        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();
        CountOption countOption = uriInfo.getCountOption();
        OdataOfbizEntity responseEntity = null;
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        Map<String, QueryOption> queryOptions = UtilMisc.toMap("selectOption", selectOption,
                "expandOption", expandOption, "countOption", countOption);
        Map<String, Object> edmParams = UtilMisc.toMap("edmSingleton", edmSingleton);
        OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryOptions, edmParams);
        try {
            if (segmentCount == 2) {
                //两段式navigation
                if (resourceParts.get(1) instanceof UriResourceNavigation) {
                    responseEntity = ofbizOdataReader.readSingletonData(true);
                    Map<String, Object> keyMap = responseEntity.getKeyMap();

                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
                    EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                    responseEdmEntityType = edmNavigationProperty.getType();
                    responseEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(keyMap, edmNavigationProperty, queryOptions);
                } else {
                    //两段式Function
                    Map<String, Object> boundFunctionResult = processBoundFunction(uriInfo);
                    responseEntity = (OdataOfbizEntity) boundFunctionResult.get("entity");
                    responseEdmEntityType = (EdmEntityType) boundFunctionResult.get("entityType");
                }
            } else if (segmentCount == 1) {
                responseEntity = ofbizOdataReader.readSingletonData(true);
            }
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }

        // 以下serialize的代码，可以和readEntityInternal中相应的代码合并
        if (responseEntity == null) {
            throw new ODataApplicationException("The request resource is not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), locale);
        }
        // serialize
        serializeEntity(oDataRequest, oDataResponse, edmSingleton, responseEdmEntityType, responseContentType,
                expandOption, selectOption, responseEntity);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
    }

    private void readEntityInternal(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                    ContentType responseContentType) throws ODataApplicationException, SerializerException, OfbizODataException {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        int segmentCount = resourceParts.size();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        EdmEntitySet startEdmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntitySet responseEdmEntitySet = null; // we need this for building the contextUrl
        EdmEntityType responseEdmEntityType = null; // we'll need this to build the ContextURL

        List<UriParameter> keyPredicates = uriResourceEntitySet.getKeyPredicates();
        EdmEntityType startEdmEntityType = startEdmEntitySet.getEntityType();
        Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, startEdmEntityType);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(startEdmEntityType.getFullQualifiedName());
        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();
        CountOption countOption = uriInfo.getCountOption();
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, csdlEntityType);
        Entity responseEntity = null;
        if (segmentCount == 1) { // no navigation
            responseEdmEntitySet = startEdmEntitySet;
            try {
                Map<String, QueryOption> queryParams = UtilMisc.toMap("selectOption", selectOption,
                        "expandOption", expandOption, "countOption", countOption);
                Map<String, QueryOption> queryOptions = UtilMisc.toMap("selectOption", selectOption, "expandOption", expandOption);
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", startEdmEntitySet);
                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                        "sapContextId", sapContextId, "locale", locale);
                if (UtilValidate.isNotEmpty(sapContextId) && UtilValidate.isNotEmpty(csdlEntityType.getDraftEntityName())) {
                    DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, startEdmEntityType);
                    responseEntity = draftHandler.readEntityData(csdlEntityType, keyMap, queryOptions);
                } else {
                    OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                    responseEntity = ofbizOdataReader.readEntityData(keyMap, queryOptions);
                }
            } catch (OfbizODataException e) {
                e.printStackTrace();
                throw new ODataApplicationException(e.getMessage(),
                        Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
            }
            responseEdmEntityType = responseEdmEntitySet.getEntityType();
        }
        if (segmentCount == 2 || segmentCount == 3) {
            Debug.logInfo("Entering the second segment", module);
            if (segmentCount == 3) {
                //如果是三段式 startEdmEntitySet要调整为第二段
                startEdmEntitySet = (EdmEntitySet) startEdmEntitySet.getRelatedBindingTarget(resourceParts.get(1).toString());
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
                keyMap = Util.uriParametersToMap(uriResourceNavigation.getKeyPredicates(), startEdmEntitySet.getEntityType());
                if (UtilValidate.isEmpty(keyMap)) {
                    //第二段不含主键 查询获取第二段的主键
                    keyMap = Util.getNavigationKey(uriResourceEntitySet.getEntityType(), uriResourceEntitySet.getKeyPredicates(), uriResourceNavigation.getSegmentValue(), edmProvider, delegator);
                }
            }

            UriResource navSegment = resourceParts.get(segmentCount - 1);
            if (navSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) navSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
                responseEdmEntityType = edmNavigationProperty.getType();
                if (!edmNavigationProperty.containsTarget()) {
                    responseEdmEntitySet = Util.getNavigationTargetEntitySet(startEdmEntitySet, edmNavigationProperty);
                } else {
                    responseEdmEntitySet = startEdmEntitySet;
                }
                List<UriParameter> navKeyPredicates = uriResourceNavigation.getKeyPredicates();
                if (navKeyPredicates.isEmpty()) { // e.g. DemoService.svc/Products(1)/Category
                    Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                            "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                            "locale", locale);

                    Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", startEdmEntitySet,
                            "edmNavigationProperty", edmNavigationProperty);
                    try {
                        Map<String, QueryOption> queryParams = UtilMisc.toMap("keyMap", keyMap,
                                "selectOption", selectOption, "expandOption", expandOption, "countOption", countOption);
                        Map<String, QueryOption> queryOptions = UtilMisc.toMap("selectOption", selectOption, "expandOption", expandOption);
                        OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                        responseEntity = ofbizOdataReader.getRelatedEntity(keyMap, edmNavigationProperty, queryOptions);
                    } catch (OfbizODataException e) {
                        e.printStackTrace();
                        throw new ODataApplicationException(e.getMessage(),
                                Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
                    }
                } else {
                    try {
                        Map<String, QueryOption> queryOptions = UtilMisc.toMap("selectOption", selectOption, "expandOption", expandOption);
                        Map<String, Object> navKeyMap = Util.uriParametersToMap(navKeyPredicates, responseEdmEntityType);
                        if (UtilValidate.isNotEmpty(sapContextId) && UtilValidate.isNotEmpty(navCsdlEntityType.getDraftEntityName())) {
                            DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, startEdmEntityType);
                            responseEntity = draftHandler.getRelatedEntityData(keyMap, edmNavigationProperty, navKeyMap, queryOptions);
                        } else {
                            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                                    "sapContextId", sapContextId, "locale", locale);
                            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", responseEdmEntitySet);
                            Map<String, QueryOption> queryParams = UtilMisc.toMap("keyMap", navKeyMap,
                                    "selectOption", selectOption, "expandOption", expandOption,
                                    "countOption", countOption);
                            OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                            responseEntity = ofbizOdataReader.readEntityData(navKeyMap, queryOptions);
                        }
                    } catch (OfbizODataException e) {
                        e.printStackTrace();
                        throw new ODataApplicationException(e.getMessage(),
                                Integer.parseInt(e.getODataErrorCode()), locale);
                    }
                    responseEdmEntityType = responseEdmEntitySet.getEntityType();
                }
            } // end of if (navSegment instanceof UriResourceNavigation)
            if (navSegment instanceof UriResourceFunction) {
                Map<String, Object> boundFunctionResult = processBoundFunction(uriInfo);
                responseEntity = (Entity) boundFunctionResult.get("entity");
                responseEdmEntityType = (EdmEntityType) boundFunctionResult.get("entityType");
            }
        }
        if (responseEntity == null) {
            throw new ODataApplicationException("The request resource is not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), locale, HttpStatusCode.NOT_FOUND.getStatusCode() + "");
        }
        serializeEntity(oDataRequest, oDataResponse, responseEdmEntitySet, responseEdmEntityType,
                responseContentType, expandOption, selectOption, responseEntity);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
    }

    private Map<String, Object> processBoundFunction(UriInfo uriInfo)
            throws ODataApplicationException {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        Map<String, Object> result = new HashMap<>();
        Entity responseEntity;
        EdmEntityType responseEdmEntityType;
        UriResourceNavigation uriResourceNavigation = null;
        if (resourceParts.size() > 2) { // 先只支持2段
            uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
        }
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourceParts.get(0);
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourceParts.get(resourceParts.size() - 1);
        List<UriParameter> parameters = uriResourceFunction.getParameters();
        try {
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
            responseEntity = ofbizOdataReader.processBoundFunctionEntity(uriResourceFunction, parameters,
                    boundEntity, uriResourceNavigation, uriInfo.getAliases());
            responseEdmEntityType = (EdmEntityType) uriResourceFunction.getType();

        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH, e.getODataErrorCode());
        }
        EdmFunction function = uriResourceFunction.getFunction();
        if (responseEntity == null && !function.getReturnType().isNullable()) {
            // 返回值是必须的, code = 404
            throw new ODataApplicationException("The action could not be executed. The return entity cannot be null.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT, HttpStatusCode.NOT_FOUND.getStatusCode() + "");
        }
        result.put("entity", responseEntity);
        result.put("entityType", responseEdmEntityType);
        return result;
    }

    private void serializeEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, EdmBindingTarget edmBindingTarget,
                                 EdmEntityType edmEntityType, ContentType contentType,
                                 ExpandOption expandOption, SelectOption selectOption, Entity entity)
            throws SerializerException {
        //响应时排除二进制数据
        entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
        // serialize
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption,
                selectOption);
        InputStream entityStream = null;
        try {
            ContextURL contextUrl;
            String typeName = null;
            if (edmBindingTarget != null) {
                typeName = edmBindingTarget.getName();
            } else {
                typeName = edmEntityType.getName();
            }
            contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                    .entitySetOrSingletonOrType(typeName).selectList(selectList).suffix(Suffix.ENTITY).build();
            // expand and select currently not supported
            EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).select(selectOption)
                    .expand(expandOption).build();

            ODataSerializer serializer = odata.createSerializer(contentType);
            SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType,
                    entity, options);
            entityStream = serializerResult.getContent();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // configure the response object
        oDataResponse.setContent(entityStream);
    }

    private Entity processImportFunctionEntity(UriInfo uriInfo,
                                               Map<String, QueryOption> queryOptions)
            throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(0);
        List<UriParameter> uriParameters = uriResourceFunction.getParameters();
        FunctionProcessor functionProcessor = getFunctionProcessor(queryOptions);
        try {
            return functionProcessor.processImportFunctionEntity(uriResourceFunction, uriParameters, uriInfo.getAliases());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute ImportFunction Entity. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    private FunctionProcessor getFunctionProcessor(Map<String, QueryOption> queryOptions) {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        return new FunctionProcessor(odataContext, queryOptions, null);
    }

    private void readFunctionImportInternal(final ODataRequest oDataRequest, final ODataResponse oDataResponse,
                                            final UriInfo uriInfo, final ContentType responseContentType)
            throws ODataApplicationException, SerializerException {

        // 1st step: Analyze the URI and fetch the entity returned by the function
        // import
        // Function Imports are always the first segment of the resource path
        final UriResource firstSegment = uriInfo.getUriResourceParts().get(0);

        if (!(firstSegment instanceof UriResourceFunction)) {
            throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                    locale);
        }

        final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
        Entity entity = null;
        try {
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
            entity = ofbizOdataReader.readFunctionImportEntity(uriResourceFunction);
        } catch (ODataException e) {
            e.printStackTrace();
        }

        if (entity == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), locale);
        }

        // 2nd step: Serialize the response entity
        final EdmEntityType edmEntityType = (EdmEntityType) uriResourceFunction.getFunction().getReturnType().getType();
        ContextURL contextURL;
        try {
            contextURL = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/")).type(edmEntityType)
                    .build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Something wrong", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(),
                    locale);
        }
        final EntitySerializerOptions opts = EntitySerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(responseContentType);
        final SerializerResult serializerResult = serializer.entity(serviceMetadata, edmEntityType, entity, opts);

        // 3rd configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
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
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, UtilMisc.toMap("keyMap", keyMap), edmParams);
                if (UtilValidate.isNotEmpty(keyPredicates)) {
                    //Collection  即使传递了子对象id，也要确认是否是当前主对象的关联数据
                    Map<String, Object> navigationKeyMap = Util.uriParametersToMap(keyPredicates, responseEdmEntityType);
                    EntityCollection relEntity = ofbizOdataReader.findRelatedEntityCollectionByCondition(keyMap, edmNavigationProperty, EntityCondition.makeCondition(navigationKeyMap));
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
                    OdataOfbizEntity relatedEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(keyMap, edmNavigationProperty, null);
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
        } finally {
            if (UtilValidate.isNotEmpty(sapContextId)) {
                oDataResponse.setHeader("SAP-ContextId", sapContextId);
            }
        }
        if (updatedEntity == null) {
            throw new ODataApplicationException("The request resource is not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), locale);
        }

        this.serializeEntity(oDataRequest, oDataResponse, responseEdmEntitySet, responseEdmEntityType, responseContentType,
                null, null, updatedEntity);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
    }

    private List<UriParameter> getUriParameters(List<UriResource> resourceParts) {
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
        return uriResourceEntitySet.getKeyPredicates();
    }

    @Override
    public void readMediaEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException {
        try {
            List<UriResource> resourceParts = uriInfo.getUriResourceParts();
            resourceParts = resourceParts.subList(0, resourceParts.size() - 1);
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            Property property;
            CsdlProperty streamCsdlProperty;
            Map<String, Object> dataResourcePK;
            if (resourceParts.size() == 1) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0);
                EdmEntityType edmEntityType = uriResourceEntitySet.getEntityType();
                dataResourcePK = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntityType);
                OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
                //根据定义查询媒体数据字段
                streamCsdlProperty = ofbizCsdlEntityType.getStreamProperty();
                if (streamCsdlProperty == null) {
                    throw new ODataApplicationException("The media stream field is not defined.", HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
                }
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", uriResourceEntitySet.getEntitySet());
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, null, edmParams);
                Entity responseEntity = ofbizOdataReader.readEntityData(dataResourcePK, null);
                property = responseEntity.getProperty(streamCsdlProperty.getName());
            } else {
                //多段式查询媒体数据
                Map<String, Object> resourceMap = OfbizOdataReader.getEntityAndNavigationFromResource(resourceParts, odataContext);
                //Entity
                EdmEntitySet edmEntitySet = (EdmEntitySet) resourceMap.get("edmEntitySet");
                Map<String, Object> keyMap = (Map<String, Object>) resourceMap.get("keyMap");
                //Navigation
                EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) resourceMap.get("edmNavigation");
                Map<String, Object> navKeyMap = (Map<String, Object>) resourceMap.get("navKeyMap");
                OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
                //根据定义的字段查询媒体数据
                streamCsdlProperty = ofbizCsdlEntityType.getStreamProperty();
                if (streamCsdlProperty == null) {
                    throw new ODataApplicationException("The media stream field is not defined.", HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
                }
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet,
                        "edmNavigationProperty", edmNavigationProperty);
                Map<String, QueryOption> queryParams = UtilMisc.toMap("keyMap", keyMap);
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryParams, edmParams);
                Entity responseEntity = ofbizOdataReader.readRelatedEntityOne(keyMap, edmNavigationProperty, navKeyMap);
                property = responseEntity.getProperty(streamCsdlProperty.getName());
                Property dataResourceId = responseEntity.getProperty("dataResourceId");
                dataResourcePK = UtilMisc.toMap("dataResourceId", dataResourceId.getValue());
            }
            //响应给客户端，ContentType为字段mimeType的属性值，如果没有指定那么使用对应DateResource的mimeType
            String mimeType = streamCsdlProperty.getMimeType();
            if (mimeType == null) {
                mimeType = EntityQuery.use(delegator).from("DataResource")
                        .select("mimeTypeId").where(dataResourcePK).queryOne().getString("mimeTypeId");
            }
            //未找到媒体对应的响应类型mimeType
            if (mimeType == null) {
                throw new ODataApplicationException("The media response type was not specified.",
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
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
