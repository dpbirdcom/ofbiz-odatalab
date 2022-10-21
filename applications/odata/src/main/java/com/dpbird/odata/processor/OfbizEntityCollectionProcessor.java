package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.CountEntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.*;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizEntityCollectionProcessor implements EntityCollectionProcessor, CountEntityCollectionProcessor {

    public static final String module = OfbizEntityCollectionProcessor.class.getName();
    public static final int MAX_ROWS = 10000;
    public static final int DAYS_BEFORE = -100;
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private Delegator delegator;
    private LocalDispatcher dispatcher;
    private Locale locale = Locale.ROOT;
    private OfbizAppEdmProvider edmProvider;
    private HttpServletRequest httpServletRequest;
    private GenericValue userLogin;

    public OfbizEntityCollectionProcessor(HttpServletRequest httpServletRequest, Delegator delegator,
                                          LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
                                          GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.edmProvider = edmProvider;
        this.userLogin = userLogin;
        this.locale = locale;
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                     ContentType responseContentType) throws ODataApplicationException, ODataLibraryException {
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        if (responseContentType.getSubtype().equalsIgnoreCase("json")) {
            responseContentType = ContentType.JSON_FULL_METADATA;
        }
        EdmEntitySet edmEntitySet = null; // for building ContextURL
        UriResourceFunction uriResourceFunction = null; // 有可能是function

        /******** all options ***********/
        Map<String, QueryOption> queryOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        SkipOption skipOption = (SkipOption) queryOptions.get("skipOption");
        CountOption countOption = (CountOption) queryOptions.get("countOption");
        SearchOption searchOption = (SearchOption) queryOptions.get("searchOption");
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        TopOption topOption = (TopOption) queryOptions.get("topOption");
        OrderByOption orderByOption = (OrderByOption) queryOptions.get("orderByOption");
        ApplyOption applyOption = (ApplyOption) queryOptions.get("applyOption");
        EdmFunction edmFunction;
        EdmEntityType edmEntityType = null; // 这个是返回值的EntityType
        UriResource lastUriResource = resourcePaths.get(resourcePathSize - 1);
        UriResource firstUriResource = resourcePaths.get(0);
        if (lastUriResource instanceof UriResourceFunction) {
            edmFunction = (EdmFunction) ((UriResourceFunction) lastUriResource).getFunction();
            edmEntityType = (EdmEntityType) edmFunction.getReturnType().getType();
        }

        EntityCollection entityCollection = null; // for the response body
        if (firstUriResource instanceof UriResourceSingleton) { // it's Singleton
            Debug.logInfo("====================== in readEntityCollection, it's UriResourceSingleton", module);
            UriResourceSingleton uriResourceSingleton = (UriResourceSingleton) firstUriResource;
            EdmSingleton edmSingleton = uriResourceSingleton.getSingleton();
            UriResource lastSegment = resourcePaths.get(resourcePaths.size() - 1); // don't support more complex URIs
            if (lastSegment instanceof UriResourceNavigation) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                edmEntitySet = Util.getNavigationTargetEntitySet(edmSingleton, edmNavigationProperty);
                entityCollection = processSingleton(edmSingleton, edmNavigationProperty, selectOption, expandOption, countOption, searchOption,
                        filterOption, topOption, skipOption, orderByOption, resourcePaths, resourcePathSize);
            } else if (lastSegment instanceof UriResourceFunction) {
                uriResourceFunction = (UriResourceFunction) lastSegment;
                entityCollection = processSingletonBoundFunction(uriResourceFunction, uriResourceSingleton, selectOption, expandOption, countOption, searchOption,
                        filterOption, topOption, skipOption, orderByOption, resourcePaths, resourcePathSize, uriInfo);
            }
        } else if (firstUriResource instanceof UriResourceFunction) {
            // it's ImportFunction
            uriResourceFunction = (UriResourceFunction) firstUriResource;
            entityCollection = processImportFunction(uriResourceFunction, selectOption, expandOption, countOption, searchOption,
                    filterOption, topOption, skipOption, orderByOption, uriInfo);
        } else { // it's EntitySet
            UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) firstUriResource; // first segment is the EntitySet
            EdmEntitySet startEdmEntitySet = uriResourceEntitySet.getEntitySet();
            EdmType edmTypeFilter = uriResourceEntitySet.getTypeFilterOnCollection();

            //处理apply 最多支持两段式的apply
            if (applyOption != null) {
                if (resourcePathSize > 2) {
                    throw new ODataApplicationException("Not supported", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
                try {
                    readEntityCollectionWithApply(oDataResponse, uriInfo, startEdmEntitySet, responseContentType);
                } catch (ODataException e) {
                    throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
                return;
            }

            if (resourcePathSize == 1) {
                edmEntitySet = startEdmEntitySet; // first (and only) entitySet
                try {
                    Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                            "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                            "locale", locale);
                    Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", startEdmEntitySet,
                            "edmTypeFilter", edmTypeFilter);
                    OfbizOdataReader ofbizOdataReader = new ExtraOdataReader(odataContext, queryOptions, edmParams);
                    entityCollection = ofbizOdataReader.findList();
                } catch (ODataException e) {
                    e.printStackTrace();
                    throw new ODataApplicationException(e.getMessage(),
                            HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
            } else if (resourcePathSize == 2 || resourcePathSize == 3) {
                UriResource lastSegment = resourcePaths.get(resourcePathSize - 1); // don't support more complex URIs
                if (lastSegment instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) lastSegment;
                    EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();

                    UriResource prevUriResource = resourcePaths.get(resourcePathSize - 2);
                    UriResourceEntitySet prevUriResourceEntitySet;
                    EdmEntitySet prevEdmEntitySet;
                    List<UriParameter> keyPredicates;
                    if (prevUriResource instanceof UriResourceEntitySet) { // 两段式
                        prevUriResourceEntitySet = (UriResourceEntitySet) prevUriResource; // 两段式，第一段是EntitySet
                        prevEdmEntitySet = prevUriResourceEntitySet.getEntitySet();
                        keyPredicates = prevUriResourceEntitySet.getKeyPredicates();
                    } else { // 三段式
                        UriResourceNavigation prevUriResourceNavigation = (UriResourceNavigation) prevUriResource;
                        EdmNavigationProperty prevEdmNavigationProperty = prevUriResourceNavigation.getProperty();
                        prevEdmEntitySet = Util.getNavigationTargetEntitySet(startEdmEntitySet, prevEdmNavigationProperty);
                        keyPredicates = prevUriResourceNavigation.getKeyPredicates();
                    }
                    edmEntitySet = Util.getNavigationTargetEntitySet(prevEdmEntitySet, edmNavigationProperty);
                    try {
                        Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, prevEdmEntitySet.getEntityType());
                        if (UtilValidate.isEmpty(keyMap) && resourcePathSize == 3) {
                            //:People('10000')/Party/ReturnHeader
                            //三段式查询第二段不带id 需要把第二段的id查出来再去查询第三段
                            List<UriParameter> firstUriParameter = ((UriResourceEntitySet) firstUriResource).getKeyPredicates();
                            String navigationName = ((UriResourceNavigation) resourcePaths.get(1)).getProperty().getName();
                            keyMap = Util.getNavigationKey(startEdmEntitySet.getEntityType(), firstUriParameter, navigationName, edmProvider, delegator);
                        }
                        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(prevEdmEntitySet.getEntityType().getFullQualifiedName());
                        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(edmNavigationProperty.getName());
                        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(csdlNavigationProperty.getTypeFQN());
                        if (UtilValidate.isNotEmpty(sapContextId) && UtilValidate.isNotEmpty(navCsdlEntityType.getDraftEntityName())) {
                            EdmEntityType entityType = UtilValidate.isNotEmpty(edmEntitySet) ? edmEntitySet.getEntityType() : null;
                            DraftHandler draftHandler = new DraftHandler(delegator, dispatcher, edmProvider, csdlEntityType, sapContextId, userLogin, locale, entityType);
                            entityCollection = draftHandler.findRelatedEntityCollection(csdlEntityType, keyMap, edmNavigationProperty, queryOptions);
                        } else {
                            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                                    "locale", locale);
                            Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", prevEdmEntitySet,
                                    "edmTypeFilter", edmTypeFilter, "edmNavigationProperty", edmNavigationProperty);
                            OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, queryOptions, edmParams);
                            entityCollection = ofbizOdataReader.findRelatedEntityCollection(keyMap, edmNavigationProperty, queryOptions, null);
                        }
                    } catch (ODataException e) {
                        e.printStackTrace();
                        throw new ODataApplicationException(e.getMessage(),
                                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), locale);
                    }
                } else if (lastSegment instanceof UriResourceFunction) {
                    uriResourceFunction = (UriResourceFunction) lastSegment;
                    Map<String, Object> functionResult = processBoundFunction(resourcePaths, selectOption, expandOption, countOption, searchOption,
                            filterOption, topOption, skipOption, orderByOption, resourcePathSize, uriInfo);
                    entityCollection = (EntityCollection) functionResult.get("entityCollection");
                }
            } else { // this would be the case for e.g. Products(1)/Category/Products
                throw new ODataApplicationException("Not supported",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), locale);
            }
        }

        serializeEntityCollection(oDataRequest, oDataResponse, edmEntitySet, uriResourceFunction, responseContentType,
                expandOption, selectOption, countOption, entityCollection);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseContentType.toContentTypeString());
    }

    private EntityCollection processSingleton(EdmSingleton edmSingleton, EdmNavigationProperty edmNavigationProperty,
                                              SelectOption selectOption, ExpandOption expandOption, CountOption countOption, SearchOption searchOption,
                                              FilterOption filterOption, TopOption topOption, SkipOption skipOption, OrderByOption orderByOption,
                                              List<UriResource> resourceParts, int segmentCount)
            throws ODataApplicationException {
        OfbizOdataReader ofbizOdataReader;
        EntityCollection responseEntityCollection;
        try {
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            Map<String, QueryOption> queryOptions = UtilMisc.toMap("filterOption", filterOption,
                    "selectOption", selectOption, "expandOption", expandOption, "skipOption", skipOption,
                    "topOption", topOption, "orderByOption", orderByOption,
                    "searchOption", searchOption);
            Map<String, QueryOption> queryContext = UtilMisc.toMap("filterOption", filterOption,
                    "selectOption", selectOption, "expandOption", expandOption, "skipOption", skipOption,
                    "topOption", topOption, "orderByOption", orderByOption,
                    "searchOption", searchOption, "countOption", countOption);
            Map<String, Object> edmParams = UtilMisc.toMap("edmSingleton", edmSingleton,
                    "edmNavigationProperty", edmNavigationProperty);
            ofbizOdataReader = new OfbizOdataReader(odataContext, queryContext, edmParams);
            responseEntityCollection = ofbizOdataReader.findSingletonRelatedEntityCollection(edmSingleton, edmNavigationProperty, queryOptions);
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
        return responseEntityCollection;
    }

    private EntityCollection processSingletonBoundFunction(UriResourceFunction uriResourceFunction, UriResourceSingleton boundEntity,
                                                           SelectOption selectOption, ExpandOption expandOption, CountOption countOption, SearchOption searchOption,
                                                           FilterOption filterOption, TopOption topOption, SkipOption skipOption, OrderByOption orderByOption,
                                                           List<UriResource> resourceParts, int segmentCount, UriInfo uriInfo)
            throws ODataApplicationException {
        FunctionProcessor ofbizOdataReader;
        EntityCollection responseEntityCollection;
        try {
            UriResourceNavigation uriResourceNavigation = null;
            // 先只支持2段
            if (resourceParts.size() > 2) {
                uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
            }
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            Map<String, QueryOption> queryOptions = UtilMisc.toMap("filterOption", filterOption,
                    "selectOption", selectOption, "expandOption", expandOption, "skipOption", skipOption,
                    "topOption", topOption, "orderByOption", orderByOption,
                    "searchOption", searchOption, "countOption", countOption);
            Map<String, Object> edmParams = UtilMisc.toMap("edmSingleton", boundEntity.getSingleton());
            ofbizOdataReader = new FunctionProcessor(odataContext, queryOptions, edmParams);
            responseEntityCollection = (EntityCollection) ofbizOdataReader.boundFunctionEntity(uriResourceFunction.getFunction(),
                    boundEntity, uriResourceFunction.getParameters(), uriResourceNavigation, uriInfo.getAliases());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
        return responseEntityCollection;
    }

    private EntityCollection processImportFunction(UriResourceFunction uriResourceFunction,
                                                   SelectOption selectOption, ExpandOption expandOption, CountOption countOption, SearchOption searchOption,
                                                   FilterOption filterOption, TopOption topOption, SkipOption skipOption, OrderByOption orderByOption,
                                                   UriInfo uriInfo)
            throws ODataApplicationException {
        try {
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            Map<String, QueryOption> queryOptions = UtilMisc.toMap("filterOption", filterOption,
                    "selectOption", selectOption, "expandOption", expandOption, "skipOption", skipOption,
                    "topOption", topOption, "orderByOption", orderByOption,
                    "searchOption", searchOption, "countOption", countOption);
            FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, queryOptions, null);
            return ofbizOdataReader.processImportFunctionEntityCollection(uriResourceFunction, uriResourceFunction.getParameters(), uriInfo.getAliases());
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private void readEntityCollectionWithApply(ODataResponse oDataResponse, UriInfo uriInfo,
                                               EdmEntitySet edmEntitySet, ContentType responseFormat) throws ODataException {
        List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
        if (uriResourceParts.size() > 1) {
            odataContext.put("uriResourceParts", uriResourceParts);
        }
        OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, OdataProcessorHelper.getQuernOptions(uriInfo), UtilMisc.toMap("edmBindingTarget", edmEntitySet));
        EntityCollection entityCollection = ofbizOdataReader.findList();
        StringBuilder selectList = new StringBuilder();
        if (entityCollection.getEntities().size() > 0) {
            Entity entity = entityCollection.getEntities().get(0);
            for (Property property : entity.getProperties()) {
                selectList.append(selectList.toString().length() == 0 ? property.getName() : "," + property.getName());
            }
        }
        //return
        final ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).selectList(selectList.toString()).build();
        EdmAssistedSerializerOptions serializerOptions = EdmAssistedSerializerOptions.with()
                .contextURL(contextUrl).build();
        final EdmAssistedSerializer edmAssistedSerializer;
        SerializerResult serializerResult;
        edmAssistedSerializer = odata.createEdmAssistedSerializer(responseFormat);
        serializerResult = edmAssistedSerializer.entityCollection(serviceMetadata, edmEntitySet.getEntityType(), entityCollection, serializerOptions);

        // 3rd configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    /**
     * Counts entities from persistence and puts serialized content and status into the response.
     * Response content type is <code>text/plain</code> by default.
     *
     * @param oDataRequest  OData request object containing raw HTTP information.
     * @param oDataResponse OData response object for collecting response data
     * @param uriInfo       information of a parsed OData URI
     * @throws ODataApplicationException if the service implementation encounters a failure
     */
    @Override
    public void countEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo)
            throws ODataApplicationException {
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        if (!(resourceParts.get(0) instanceof UriResourceEntitySet)) {
            return;
        }
        resourceParts = resourceParts.subList(0, resourceParts.size() - 1);
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "sapContextId", sapContextId, "locale", locale);
        Map<String, QueryOption> quernOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0); // first segment is the EntitySet
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        Long count;
        try {
            if (resourceParts.size() == 1) {
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet);
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, quernOptions, edmParams);
                count = ofbizOdataReader.readEntitySetCount(edmEntitySet);
            } else {
                //多段式的查询 获取到最终要查询的数据
                Map<String, Object> resourceMap = OfbizOdataReader.getEntityAndNavigationFromResource(resourceParts, odataContext);
                if (UtilValidate.isEmpty(resourceMap)) {
                    return;
                }
                //EntitySet
                edmEntitySet = (EdmEntitySet) resourceMap.get("edmEntitySet");
                Map<String, Object> keyMap = (Map<String, Object>) resourceMap.get("keyMap");
                OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());
                GenericValue genericValue = delegator.findOne(ofbizCsdlEntityType.getOfbizEntity(), keyMap, true);
                //Navigation
                EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) resourceMap.get("edmNavigation");
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, null, UtilMisc.toMap("edmBindingTarget", edmEntitySet));
                //可能不是原生的relation，先查询出数据，再根据这些数据去做查询和filter，返回最终的count
                OdataOfbizEntity ofbizEntity = ofbizOdataReader.makeEntityFromGv(genericValue);
                EntityCollection relatedEntityCollection = ofbizOdataReader.findRelatedEntityCollection(ofbizEntity, edmNavigationProperty, null);
                edmEntitySet = Util.getNavigationTargetEntitySet(edmEntitySet, edmNavigationProperty);
                if (edmEntitySet == null) {
                    return;
                }
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet);
                ofbizOdataReader = new OfbizOdataReader(odataContext, quernOptions, edmParams);
                count = ofbizOdataReader.readRelatedEntityCount(edmEntitySet, relatedEntityCollection);
            }
        } catch (ODataException | GenericEntityException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        String countStr = count.toString();
        InputStream countInputStream = new ByteArrayInputStream(countStr.getBytes());
        oDataResponse.setContent(countInputStream);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        // response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    private void serializeEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                           EdmBindingTarget edmBindingTarget, UriResourceFunction uriResourceFunction,
                                           ContentType contentType, ExpandOption expandOption,
                                           SelectOption selectOption, CountOption countOption,
                                           EntityCollection entityCollection)
            throws ODataApplicationException {
        //响应时排除二进制数据
        for (Entity entity : entityCollection.getEntities()) {
            entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
        }
        EdmEntityType edmEntityType = null;
        if (edmBindingTarget != null) {
            edmEntityType = edmBindingTarget.getEntityType();
        } else if (uriResourceFunction != null) {
            edmEntityType = (EdmEntityType) uriResourceFunction.getFunction().getReturnType().getType();
        }
        SerializerResult serializerResult;
        try {
            String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
            ContextURL contextUrl;
            String id = null;
            if (edmBindingTarget != null) {
                contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                        .entitySetOrSingletonOrType(edmBindingTarget.getName())
                        .selectList(selectList).build();
                id = oDataRequest.getRawBaseUri() + "/" + edmBindingTarget.getName();
            } else {
                contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                        .entitySetOrSingletonOrType(edmEntityType.getName())
                        .selectList(selectList).build();
                id = oDataRequest.getRawBaseUri() + "/" + uriResourceFunction.getFunction().getName();
            }
            URI nextUri = Util.getNextUri(oDataRequest.getRawRequestUri(), entityCollection.getCount());
            // expand and select currently not supported
            EntityCollectionSerializerOptions opts;
            if (id != null) {
                opts = EntityCollectionSerializerOptions.with().id(id).count(countOption)
                        .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            } else {
                opts = EntityCollectionSerializerOptions.with().count(countOption)
                        .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            }

            ODataSerializer serializer = odata.createSerializer(contentType);
            serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entityCollection,
                    opts);
        } catch (SerializerException | URISyntaxException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        InputStream serializedContent = serializerResult.getContent();

        // Finally: configure the response object: set the body, headers and status code
        oDataResponse.setContent(serializedContent);
    }

    private Map<String, Object> processBoundFunction(List<UriResource> resourceParts, SelectOption selectOption, ExpandOption expandOption, CountOption countOption, SearchOption searchOption,
                                                     FilterOption filterOption, TopOption topOption, SkipOption skipOption, OrderByOption orderByOption,
                                                     int segmentCount, UriInfo uriInfo)
            throws ODataApplicationException {
        Map<String, Object> result = new HashMap<>();
        EntityCollection entityCollection;
        EdmEntityType responseEdmEntityType;
        UriResourceNavigation uriResourceNavigation = null;
        // 先只支持2段
        if (resourceParts.size() > 2) {
            uriResourceNavigation = (UriResourceNavigation) resourceParts.get(1);
        }
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourceParts.get(0);
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourceParts.get(resourceParts.size() - 1);
        List<UriParameter> parameters = uriResourceFunction.getParameters();
        try {
            Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                    "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                    "locale", locale);
            Map<String, QueryOption> queryOptions = UtilMisc.toMap("skipOption", skipOption, "topOption", topOption, "filterOption", filterOption);
            FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, queryOptions, null);
            entityCollection = ofbizOdataReader.processBoundFunctionEntityCollection(uriResourceFunction, parameters,
                    boundEntity, uriResourceNavigation, uriInfo.getAliases());
            responseEdmEntityType = (EdmEntityType) uriResourceFunction.getType();
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
        }
        result.put("entityCollection", entityCollection);
        result.put("entityType", responseEdmEntityType);
        return result;
    }

}
