package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.PrimitiveCollectionProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceFunction;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class OfbizPrimitiveCollectionProcessor implements PrimitiveCollectionProcessor {

    public static final String MODULE = OfbizPrimitiveCollectionProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final LocalDispatcher dispatcher;
    private final Delegator delegator;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;
    private final Locale locale;
    private final GenericValue userLogin;

    public OfbizPrimitiveCollectionProcessor(HttpServletRequest request, Delegator delegator, LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider, GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.httpServletRequest = request;
        this.edmProvider = edmProvider;
        this.locale = locale;
        this.userLogin = userLogin;
    }

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
    }

    @Override
    public void readPrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("readPrimitiveCollection =>", MODULE);
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        Property property;
        EdmPrimitiveType edmPrimitiveType;
        try {
            UriResource lastUriResource = ListUtil.getLast(resourcePaths);
            UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
            if (lastUriResource instanceof UriResourceFunction) {
                //Function
                UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
                EdmFunction edmFunction = resourceFunction.getFunction();
                edmPrimitiveType = (EdmPrimitiveType) edmFunction.getReturnType().getType();
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
                property = functionProcessor.processFunctionPrimitive(resourceFunction, parameters, edmBindingTarget);
            } else {
                //Entity Property
                UriResourcePrimitiveProperty resourceProperty = (UriResourcePrimitiveProperty) lastUriResource;
                edmPrimitiveType = (EdmPrimitiveType) resourceProperty.getType();
                List<UriResource> uriResourceList = new ArrayList<>(resourcePaths.subList(0, resourcePaths.size() - 1));
                List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceList, uriInfo.getAliases());
                OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
                Entity entity = (Entity) odataParts.getEntityData();
                property = entity.getProperty(lastUriResource.getSegmentValue());
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()) , Locale.ENGLISH);
        }
        // serialize property
        if (property != null) {
            final ContextURL contextUrl = ContextURL.with().type(edmPrimitiveType).build();
            final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextUrl).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);
            final SerializerResult serializerResult = serializer.primitiveCollection(serviceMetadata, edmPrimitiveType, property, opts);

            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } else {
            // in case there's no value for the property, we can skip the serialization
            oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        }

    }

    @Override
    public void updatePrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType contentType, ContentType contentType1) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("updatePrimitiveCollection =>", MODULE);

    }

    @Override
    public void deletePrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("deletePrimitiveCollection =>", MODULE);

    }

    @Override
    public void init(OData oData, ServiceMetadata serviceMetadata) {
        this.odata = oData;
        this.serviceMetadata = serviceMetadata;
    }

}
