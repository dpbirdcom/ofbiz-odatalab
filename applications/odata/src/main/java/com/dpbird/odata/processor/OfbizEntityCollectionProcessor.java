package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.CountEntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.*;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceSingleton;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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
            List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
            if (queryOptions.get("applyOption") == null) {
                UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), queryOptions, sapContextId);
                List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceParts, uriInfo.getAliases());
                OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
                EntityCollection entityCollection = (EntityCollection) odataParts.getEntityData();
                serializeEntityCollection(oDataRequest, oDataResponse, odataParts.getEdmBindingTarget(), odataParts.getEdmEntityType(),
                        responseContentType, entityCollection, queryOptions);
            } else {
                //apply
                EdmEntitySet edmEntitySet;
                EntityCondition applyCondition = null;
                if (uriResourceParts.size() > 1) {
                    //多段式apply 先查询出最终的数据范围 再查询apply
                    UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), sapContextId);
                    List<OdataParts> odataPartsList = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), null);
                    OdataParts lastParts = ListUtil.getLast(odataPartsList);
                    edmEntitySet = (EdmEntitySet) lastParts.getEdmBindingTarget();
                    EntityCollection applyEntities = (EntityCollection) lastParts.getEntityData();
                    if (UtilValidate.isEmpty(applyEntities) || UtilValidate.isEmpty(applyEntities.getEntities())) {
                        //没有数据
                        oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                        return;
                    }
                    applyCondition = Util.getEntityCollectionQueryCond(applyEntities);
                } else {
                    edmEntitySet = ((UriResourceEntitySet) uriResourceParts.get(0)).getEntitySet();
                }
                OdataReader reader = new OdataReader(getOdataContext(), new HashMap<>(), UtilMisc.toMap("edmBindingTarget", edmEntitySet));
                EntityCollection resultEntityCollection = reader.findApply(applyCondition, queryOptions);
                serializeApplyEntityCollection(oDataResponse, edmEntitySet, resultEntityCollection, responseContentType);
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
        }
    }

    /**
     * Counts entities from persistence and puts serialized content and status into the response.
     * Response content type is <code>text/plain</code> by default.
     */
    @Override
    public void countEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo)
            throws ODataApplicationException {
        String sapContextId = DataModifyActions.checkSapContextId(delegator, oDataRequest, null);
        if (UtilValidate.isNotEmpty(sapContextId)) {
            oDataResponse.setHeader("SAP-ContextId", sapContextId);
        }
        try {
            Long count;
            Map<String, QueryOption> quernOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
            List<UriResource> resourceParts = new ArrayList<>(uriInfo.getUriResourceParts());
            resourceParts = resourceParts.subList(0, resourceParts.size() - 1);
            UriResource lastUriResource = ListUtil.getLast(resourceParts);
            if (lastUriResource instanceof UriResourceEntitySet) {
                //只是简单的EntitySet 直接查询count
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) lastUriResource;
                OdataReader reader = new OdataReader(getOdataContext(), quernOptions, UtilMisc.toMap("edmBindingTarget", uriResourceEntitySet.getEntitySet()));
                count = reader.findCount(uriResourceEntitySet.getEntitySet().getEntityType());
            } else {
                //多段式查询
                UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), quernOptions, sapContextId);
                List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(resourceParts, uriInfo.getAliases());
                OdataParts lastResourceData = ListUtil.getLast(resourceDataInfos);
                EntityCollection entityCollection = (EntityCollection) lastResourceData.getEntityData();
                count = (long) entityCollection.getEntities().size();
            }
            oDataResponse.setContent(new ByteArrayInputStream(count.toString().getBytes()));
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), locale);
        }
    }

    /**
     * Serialize data
     */
    private void serializeEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, EdmBindingTarget edmBindingTarget, EdmEntityType edmEntityType,
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
            String uriSetName = edmBindingTarget != null ? edmBindingTarget.getName() : edmEntityType.getName();
            String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                    .entitySetOrSingletonOrType(uriSetName).selectList(selectList).build();
            String id = oDataRequest.getRawBaseUri() + "/" + edmEntityType.getName();
            EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().id(id).count(countOption)
                    .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            InputStream serializedContent = odata.createSerializer(contentType)
                    .entityCollection(serviceMetadata, edmEntityType, entityCollection, opts).getContent();
            oDataResponse.setContent(serializedContent);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (URISyntaxException | SerializerException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    /**
     * Serialize apply data
     */
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

}