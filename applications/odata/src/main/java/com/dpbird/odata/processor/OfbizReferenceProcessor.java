package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.ReferenceCollectionProcessor;
import org.apache.olingo.server.api.processor.ReferenceProcessor;
import org.apache.olingo.server.api.serializer.*;
import org.apache.olingo.server.api.uri.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Reference CRUD.
 * ReferenceCollection R.
 *
 * @date 2022/12/13
 */
public class OfbizReferenceProcessor implements ReferenceProcessor, ReferenceCollectionProcessor {
    public static final String module = OfbizReferenceProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final LocalDispatcher dispatcher;
    private final Delegator delegator;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;
    private final Locale locale;
    private final GenericValue userLogin;

    public OfbizReferenceProcessor(HttpServletRequest httpServletRequest, Delegator delegator, LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider,
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

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale, "oData", odata, "serviceMetadata", serviceMetadata);
    }

    @Override
    public void readReferenceCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            EntityCollection entityCollection = (EntityCollection) odataParts.getEntityData();
            EdmBindingTarget edmBindingTarget = odataParts.getEdmBindingTarget();
            serializerReferenceCollection(contentType, edmBindingTarget, request, response, entityCollection);
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    @Override
    public void readReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        try {
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            EdmBindingTarget edmBindingTarget = odataParts.getEdmBindingTarget();
            Entity responseEntity = (Entity) odataParts.getEntityData();
            if (UtilValidate.isNotEmpty(responseEntity)) {
                serializerReference(contentType, edmBindingTarget, request, response, responseEntity);
            }
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    @Override
    public void createReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        //创建reference，只有Navigation为Collection时才会使用
        try {
            UriResourceEntitySet uriResourceEntitySet = getReference(request, contentType);
            // 读这个多段式请求获取主实体
            List<UriResource> uriResourceParts = new ArrayList<>(uriInfo.getUriResourceParts());
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
            List<OdataParts> odataPartsList = uriResourceProcessor.readUriResource(uriResourceParts.subList(0, uriResourceParts.size() - 2), null);
            OdataParts odataParts = ListUtil.getLast(odataPartsList);
            UriResourceNavigation resourceNavigation = (UriResourceNavigation) uriResourceParts.get(uriResourceParts.size() - 2);
            EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
            //执行service创建
            Map<String, Object> bindPrimaryKey = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmNavigationProperty.getType());
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createReference", UtilMisc.toMap("odataContext", getOdataContext(),
                    "edmBindingTarget", odataParts.getEdmBindingTarget(), "entity", odataParts.getEntityData(),
                    "edmNavigationProperty", edmNavigationProperty, "bindPrimaryKey", bindPrimaryKey, "userLogin", userLogin));
            Debug.logInfo("Create reference result: " + serviceResult, module);
            response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }

    }

    @Override
    public void updateReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        try {
            // 读这个多段式请求获取主实体
            List<UriResource> uriResourceParts = new ArrayList<>(uriInfo.getUriResourceParts());
            ListUtil.removeLast(uriResourceParts);
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
            List<OdataParts> odataPartsList = uriResourceProcessor.readUriResource(uriResourceParts.subList(0, uriResourceParts.size() - 1), null);
            OdataParts odataParts = ListUtil.getLast(odataPartsList);
            UriResourceNavigation resourceNavigation = (UriResourceNavigation) ListUtil.getLast(uriResourceParts);
            EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
            Map<String, Object> serviceMap = UtilMisc.toMap("odataContext", getOdataContext(), "edmBindingTarget", odataParts.getEdmBindingTarget(),
                    "entity", odataParts.getEntityData(), "edmNavigationProperty", resourceNavigation.getProperty(), "userLogin", userLogin);
            if (edmNavigationProperty.isCollection()) {
                //如果是Collection, 先执行unbind
                Map<String, Object> existPrimaryKey = Util.uriParametersToMap(resourceNavigation.getKeyPredicates(), edmNavigationProperty.getType());
                serviceMap.put("bindPrimaryKey", existPrimaryKey);
                dispatcher.runSync("dpbird.deleteReference", serviceMap);
            }
            // bind
            UriResourceEntitySet uriResourceEntitySet = getReference(request, contentType);
            Map<String, Object> bindPrimaryKey = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmNavigationProperty.getType());
            serviceMap.put("bindPrimaryKey", bindPrimaryKey);
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.createReference", serviceMap);
            Debug.logInfo("Update reference result: " + serviceResult, module);
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
    }

    @Override
    public void deleteReference(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        try {
            // 获取主实体和要解除绑定的实体
            List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
            List<OdataParts> odataPartsList = uriResourceProcessor.readUriResource(uriResourceParts.subList(0, uriResourceParts.size() - 1), null);
            OdataParts mainParts = odataPartsList.get(odataPartsList.size() - 2);
            OdataParts toUnbindParts = odataPartsList.get(odataPartsList.size() - 1);
            UriResourceNavigation resourceNavigation = (UriResourceNavigation) toUnbindParts.getUriResource();
            OdataOfbizEntity toUnbindEntity = (OdataOfbizEntity) toUnbindParts.getEntityData();
            //执行Service解除绑定
            Map<String, Object> serviceResult = dispatcher.runSync("dpbird.deleteReference", UtilMisc.toMap("odataContext", getOdataContext(),
                    "edmBindingTarget", mainParts.getEdmBindingTarget(), "entity", mainParts.getEntityData(),
                    "edmNavigationProperty", resourceNavigation.getProperty(), "bindPrimaryKey", toUnbindEntity.getKeyMap(), "userLogin", userLogin));
            Debug.logInfo("Remove reference result: " + serviceResult, module);
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    public void serializerReference(ContentType contentType, EdmBindingTarget edmBindingTarget, ODataRequest request,
                                    ODataResponse response, Entity responseEntity) throws OfbizODataException {
        try {
            ODataSerializer serializer = odata.createSerializer(contentType);
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(request.getRawBaseUri() + "/")).suffix(ContextURL.Suffix.REFERENCE).build();
            ReferenceSerializerOptions options = ReferenceSerializerOptions.with().contextURL(contextUrl).build();
            EdmEntitySet edmEntitySet = (EdmEntitySet) edmBindingTarget;
            SerializerResult reference = serializer.reference(serviceMetadata, edmEntitySet, responseEntity, options);
            response.setContent(reference.getContent());
        } catch (SerializerException | URISyntaxException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    public void serializerReferenceCollection(ContentType contentType, EdmBindingTarget edmBindingTarget, ODataRequest request,
                                              ODataResponse response, EntityCollection entityCollection) throws OfbizODataException {
        try {
            ODataSerializer serializer = odata.createSerializer(contentType);
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(request.getRawBaseUri() + "/")).suffix(ContextURL.Suffix.REFERENCE).build();
            ReferenceCollectionSerializerOptions options = ReferenceCollectionSerializerOptions.with().contextURL(contextUrl).build();
            EdmEntitySet edmEntitySet = (EdmEntitySet) edmBindingTarget;
            SerializerResult reference = serializer.referenceCollection(serviceMetadata, edmEntitySet, entityCollection, options);
            response.setContent(reference.getContent());
        } catch (SerializerException | URISyntaxException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 从body中取出Reference
     */
    private UriResourceEntitySet getReference(ODataRequest request, ContentType contentType) throws DeserializerException {
        UriHelper uriHelper = odata.createUriHelper();
        ODataDeserializer deserializer = odata.createDeserializer(contentType);
        DeserializerResult deserializerResult = deserializer.entityReferences(request.getBody());
        URI referenceUri = ListUtil.getLast(deserializerResult.getEntityReferences());
        return uriHelper.parseEntityId(serviceMetadata.getEdm(), referenceUri.getPath(), request.getRawBaseUri());
    }

}
