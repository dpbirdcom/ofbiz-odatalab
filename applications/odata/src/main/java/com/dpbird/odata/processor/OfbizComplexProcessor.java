package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.ComplexCollectionProcessor;
import org.apache.olingo.server.api.processor.ComplexProcessor;
import org.apache.olingo.server.api.serializer.ComplexSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceComplexProperty;
import org.apache.olingo.server.api.uri.UriResourceFunction;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class OfbizComplexProcessor implements ComplexProcessor, ComplexCollectionProcessor {

    public static final String module = OfbizComplexProcessor.class.getName();
    public static final int MAX_ROWS = 10000;
    public static final int DAYS_BEFORE = -100;
    private OData odata;
    private final Locale locale;
    private final Delegator delegator;
    private final LocalDispatcher dispatcher;
    private ServiceMetadata serviceMetadata;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;
    private final GenericValue userLogin;

    public OfbizComplexProcessor(HttpServletRequest request, Delegator delegator, LocalDispatcher dispatcher,
                                 OfbizAppEdmProvider edmProvider, GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.httpServletRequest = request;
        this.edmProvider = edmProvider;
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
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
    }

    @Override
    public void readComplexCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                      ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        try {
            EdmComplexType edmComplexType;
            Property property = readComplexProperty(uriInfo);
            UriResource lastUriResource = ListUtil.getLast(uriInfo.getUriResourceParts());
            if (lastUriResource instanceof UriResourceFunction) {
                //Function
                UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
                edmComplexType = (EdmComplexType) resourceFunction.getFunction().getReturnType().getType();
            } else {
                //Entity Property
                edmComplexType = ((UriResourceComplexProperty) lastUriResource).getComplexType();
            }
            // Deserialize the parameter
            final ContextURL contextUrl = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextUrl).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);
            final SerializerResult serializerResult = serializer.complexCollection(serviceMetadata, edmComplexType, property, opts);
            // configure the response object
            response.setContent(serializerResult.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
        }
    }

    @Override
    public void updateComplexCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                        ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Only ReadComplex is supported",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public void deleteComplexCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Only ReadComplex is supported",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public void readComplex(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        //当前返回Complex只考虑两种情况：Function的返回值、查询Entity的Complex类型字段
        try {
            EdmComplexType edmComplexType;
            Property property = readComplexProperty(uriInfo);
            UriResource lastUriResource = ListUtil.getLast(uriInfo.getUriResourceParts());
            if (lastUriResource instanceof UriResourceFunction) {
                //Function
                UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
                edmComplexType = (EdmComplexType) resourceFunction.getFunction().getReturnType().getType();
            } else {
                //Entity Property
                edmComplexType = ((UriResourceComplexProperty) lastUriResource).getComplexType();
            }
            final ContextURL contextUrl = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextUrl).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);
            final SerializerResult serializerResult = serializer.complex(serviceMetadata, edmComplexType, property, opts);
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
        }
    }

    private Property readComplexProperty(UriInfo uriInfo) throws OfbizODataException {
        Property property;
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResource lastUriResource = ListUtil.getLast(resourcePaths);
        UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
        if (lastUriResource instanceof UriResourceFunction) {
            //Function
            UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
            EdmFunction edmFunction = resourceFunction.getFunction();
            Map<String, Object> parameters = Util.uriParametersToMap(resourceFunction.getParameters(), edmFunction, uriInfo.getAliases());
            EdmBindingTarget edmBindingTarget = null;
            if (edmFunction.isBound()) {
                //添加Bound参数
                List<UriResource> uriResourceList = new ArrayList<>(resourcePaths.subList(0, resourcePaths.size() - 1));
                List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceList, uriInfo.getAliases());
                OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
                String boundParamName = edmFunction.getParameterNames().get(0);
                Object entityData = odataParts.getEntityData();
                edmBindingTarget = odataParts.getEdmBindingTarget();
                Object boundParam = null;
                if (entityData != null) {
                    boolean boundCollection = edmFunction.getParameter(boundParamName).isCollection();
                    boundParam = boundCollection ? ((EntityCollection) entityData).getEntities() : entityData;
                }
                parameters.put(boundParamName, boundParam);
            }
            FunctionProcessor functionProcessor = new FunctionProcessor(getOdataContext(), new HashMap<>(), null);
            property = functionProcessor.processFunctionComplex(resourceFunction, parameters, edmBindingTarget);
        } else {
            //Entity Property
            List<UriResource> uriResourceList = new ArrayList<>(resourcePaths.subList(0, resourcePaths.size() - 1));
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceList, uriInfo.getAliases());
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            Entity entity = (Entity) odataParts.getEntityData();
            property = entity.getProperty(lastUriResource.getSegmentValue());
        }
        return property;
    }

    @Override
    public void updateComplex(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
                              ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Only ReadComplex is supported",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    @Override
    public void deleteComplex(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Only ReadComplex is supported",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

}
