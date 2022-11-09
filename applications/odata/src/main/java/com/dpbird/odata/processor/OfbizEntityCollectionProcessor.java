package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import org.apache.fop.util.ListUtil;
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
    public static final String MODULE = OfbizEntityCollectionProcessor.class.getName();
    private OData odata;
    private final Locale locale;
    private final Delegator delegator;
    private final GenericValue userLogin;
    private ServiceMetadata serviceMetadata;
    private final LocalDispatcher dispatcher;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;

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

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
    }

    @Override
    public void readEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                     ContentType responseContentType) throws ODataApplicationException, ODataLibraryException {
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        if ("json".equalsIgnoreCase(responseContentType.getSubtype())) {
            responseContentType = ContentType.JSON_FULL_METADATA;
        }
        try {
            Map<String, QueryOption> queryOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
            if (queryOptions.get("applyOption") == null) {
                OdataReader reader = new OdataReader(getOdataContext(), queryOptions, null);
                List<UriResourceDataInfo> resourceDataInfos = reader.readUriResource(uriInfo);
                UriResourceDataInfo uriResourceDataInfo = resourceDataInfos.get(resourceDataInfos.size() - 1);
                EntityCollection entityCollection = (EntityCollection) uriResourceDataInfo.getEntityData();
                serializeEntityCollection(oDataRequest, oDataResponse, uriResourceDataInfo.getEdmEntityType(),
                        responseContentType, entityCollection, queryOptions);
            } else {
                //apply
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
                Map<String, Object> edmParam = UtilMisc.toMap("edmBindingTarget", uriResourceEntitySet.getEntitySet());
                OdataReader reader = new OdataReader(getOdataContext(), queryOptions, edmParam);
                EntityCollection entityCollection = reader.findApply(uriInfo);
                serializeApplyEntityCollection(oDataResponse, uriResourceEntitySet.getEntitySet(), entityCollection, responseContentType);
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
        }
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
        //TODO: 无线段$count查询、Singleton多段式查询$count
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        if (!(resourceParts.get(0) instanceof UriResourceEntitySet)) {
            return;
        }
        resourceParts = resourceParts.subList(0, resourceParts.size() - 1);
        Map<String, Object> odataContext = getOdataContext();
        Map<String, QueryOption> quernOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourceParts.get(0); // first segment is the EntitySet
        Long count = 0L;
        try {
            if (resourceParts.size() == 1) {
                EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet);
                OdataReader reader = new OdataReader(odataContext, quernOptions, edmParams);
                count = reader.findCount(uriResourceEntitySet.getEntitySet(), null);
            } else {
//                //多段式的查询 获取到最终要查询的数据
//                Map<String, Object> resourceMap = OfbizOdataReader.getEntityAndNavigationFromResource(resourceParts, odataContext);
//                if (UtilValidate.isEmpty(resourceMap)) {
//                    return;
//                }
//                //EdmBindingTarget
//                EdmBindingTarget edmBindingTarget = (EdmBindingTarget) resourceMap.get("edmBindingTarget");
//                Map<String, Object> keyMap = (Map<String, Object>) resourceMap.get("keyMap");
//                OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
//                GenericValue genericValue = delegator.findOne(ofbizCsdlEntityType.getOfbizEntity(), keyMap, true);
//                //Navigation
//                EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) resourceMap.get("edmNavigation");
//                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, null, UtilMisc.toMap("edmBindingTarget", edmBindingTarget));
//                //可能不是原生的relation，先查询出数据，再根据这些数据去做查询和filter，返回最终的count
//                OdataOfbizEntity ofbizEntity = ofbizOdataReader.makeEntityFromGv(genericValue);
//                EntityCollection relatedEntityCollection = ofbizOdataReader.findRelatedEntityCollection(ofbizEntity, edmNavigationProperty, null);
//                edmBindingTarget = Util.getNavigationTargetEntitySet(edmBindingTarget, edmNavigationProperty);
//                if (edmBindingTarget == null) {
//                    return;
//                }
//                Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget);
//                OdataReader reader = new OdataReader(odataContext, quernOptions, edmParams);
//                count = reader.findCount(edmBindingTarget, relatedEntityCollection);
            }
        } catch (ODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        String countStr = count.toString();
        InputStream countInputStream = new ByteArrayInputStream(countStr.getBytes());
        oDataResponse.setContent(countInputStream);
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
    }

    private void serializeEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, EdmEntityType edmEntityType,
                                           ContentType contentType, EntityCollection entityCollection, Map<String, QueryOption> queryOptions)
            throws ODataApplicationException {
        try {
            //响应时排除二进制数据
            for (Entity entity : entityCollection.getEntities()) {
                entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
            }
            ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
            SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
            CountOption countOption = (CountOption) queryOptions.get("countOption");
            String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                    .entitySetOrSingletonOrType(edmEntityType.getName()).selectList(selectList).build();
            String id = oDataRequest.getRawBaseUri() + "/" + edmEntityType.getName();
            EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().id(id).count(countOption)
                    .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            InputStream serializedContent = odata.createSerializer(contentType)
                    .entityCollection(serviceMetadata, edmEntityType, entityCollection, opts)
                    .getContent();
            oDataResponse.setContent(serializedContent);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (URISyntaxException | SerializerException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private void serializeApplyEntityCollection(ODataResponse oDataResponse, EdmEntitySet edmEntitySet, EntityCollection entityCollection, ContentType responseFormat)
            throws ODataApplicationException {
        try {
            final ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
            EdmAssistedSerializerOptions serializerOptions = EdmAssistedSerializerOptions.with()
                    .contextURL(contextUrl).build();
            EdmAssistedSerializer edmAssistedSerializer = odata.createEdmAssistedSerializer(responseFormat);
            SerializerResult serializerResult = edmAssistedSerializer.entityCollection(serviceMetadata, edmEntitySet.getEntityType(), entityCollection, serializerOptions);
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (SerializerException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }

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
}
