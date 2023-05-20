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
            EntityCollection entityCollection = new EntityCollection();
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), OdataProcessorHelper.getQuernOptions(uriInfo), null);
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            if (odataParts.getEntityData() != null) {
                entityCollection = (EntityCollection) odataParts.getEntityData();
            }
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
            Map<String, Object> bindPrimaryKey = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmNavigationProperty.getType(), edmProvider);
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
            //获取要bind的primaryKey
            List<UriParameter> keyPredicates = getReference(request, contentType).getKeyPredicates();
            Map<String, Object> bindPrimaryKey = Util.uriParametersToMap(keyPredicates, edmNavigationProperty.getType(), edmProvider);

            Map<String, Object> serviceMap = UtilMisc.toMap("odataContext", getOdataContext(), "edmBindingTarget", odataParts.getEdmBindingTarget(),
                    "entity", odataParts.getEntityData(), "edmNavigationProperty", resourceNavigation.getProperty(), "userLogin", userLogin);
            if (edmNavigationProperty.isCollection()) {
                //如果是Collection, 先执行unbind, 每一条对应的数据都要做处理
                Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmEntityType", odataParts.getEdmEntityType(), "edmNavigationProperty", edmNavigationProperty);
                OdataReader reader = new OdataReader(getOdataContext(), new HashMap<>(), embeddedEdmParams);
                EntityCollection relatedList = reader.findRelatedList((Entity) odataParts.getEntityData(), edmNavigationProperty, new HashMap<>(), null);
                //这个是要删除的key，每条跟这个key匹配的都要删除并新建一条新的数据
                Map<String, Object> unBindPrimaryKey = Util.uriParametersToMap(resourceNavigation.getKeyPredicates(), edmNavigationProperty.getType(), edmProvider);
                for (Entity entity : relatedList) {
                    OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
                    Map<String, Object> existPrimaryKey = ofbizEntity.getKeyMap();
                    if (unBindPrimaryKey.equals(existPrimaryKey)) {
                        //delete
                        serviceMap.put("bindPrimaryKey", existPrimaryKey);
                        dispatcher.runSync("dpbird.deleteReference", serviceMap);
                        serviceMap.put("bindPrimaryKey", bindPrimaryKey);
                        //create
                        dispatcher.runSync("dpbird.createReference", serviceMap);
                    }
                }
            } else {
                //create
                serviceMap.put("bindPrimaryKey", bindPrimaryKey);
                dispatcher.runSync("dpbird.createReference", serviceMap);
            }
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
            List<OdataParts> odataPartsList = uriResourceProcessor.readUriResource(uriResourceParts.subList(0, uriResourceParts.size() - 2), null);
            OdataParts mainParts = ListUtil.getLast(odataPartsList);
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResourceParts.get(uriResourceParts.size() - 2);
            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
            //要删除的key
            Map<String, Object> unBindPrimaryKey = Util.uriParametersToMap(uriResourceNavigation.getKeyPredicates(), edmNavigationProperty.getType(), edmProvider);
            Map<String, Object> serviceMap = UtilMisc.toMap("odataContext", getOdataContext(), "edmBindingTarget", mainParts.getEdmBindingTarget(), "entity", mainParts.getEntityData(),
                    "edmNavigationProperty", uriResourceNavigation.getProperty(), "bindPrimaryKey", unBindPrimaryKey, "userLogin", userLogin);
            if (edmNavigationProperty.isCollection()) {
                //Collection 删除所有匹配的数据
                Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmEntityType", mainParts.getEdmEntityType(), "edmNavigationProperty", edmNavigationProperty);
                OdataReader reader = new OdataReader(getOdataContext(), new HashMap<>(), embeddedEdmParams);
                EntityCollection relatedList = reader.findRelatedList((Entity) mainParts.getEntityData(), edmNavigationProperty, new HashMap<>(), null);
                 for (Entity entity : relatedList) {
                    OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entity;
                    //delete
                    if (unBindPrimaryKey.equals(ofbizEntity.getKeyMap())) {
                        dispatcher.runSync("dpbird.deleteReference", serviceMap);
                    }
                }
            } else {
                //delete
                dispatcher.runSync("dpbird.deleteReference", serviceMap);
            }
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
