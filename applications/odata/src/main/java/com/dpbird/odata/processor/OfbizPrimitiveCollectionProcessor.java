package com.dpbird.odata.processor;

import com.dpbird.odata.FunctionProcessor;
import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.PrimitiveCollectionProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizPrimitiveCollectionProcessor implements PrimitiveCollectionProcessor {

    public static final String module = OfbizPrimitiveCollectionProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private LocalDispatcher dispatcher;
    private Delegator delegator;
    // private OfbizOdataReader ofbizOdataReader;
    private OfbizAppEdmProvider edmProvider;
    private HttpServletRequest httpServletRequest = null;
    private Locale locale = Locale.ROOT;
    private GenericValue userLogin;

    public OfbizPrimitiveCollectionProcessor(HttpServletRequest request, Delegator delegator, LocalDispatcher dispatcher, OfbizAppEdmProvider edmProvider, GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.httpServletRequest = request;
        // this.ofbizOdataReader = ofbizOdataReader;
        this.edmProvider = edmProvider;
        this.locale = locale;
        this.userLogin = userLogin;
    }

    @Override
    public void readPrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

        Debug.logInfo("readPrimitiveCollection =>", module);
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        Property property;
        EdmPrimitiveType edmPrimitiveType;
        EdmFunction edmFunction;
        if (resourcePaths.size() == 1) {
            UriResource uriResource = resourcePaths.get(0);
            if (uriResource instanceof UriResourceFunction) {
                edmFunction = ((UriResourceFunction) uriResource).getFunction();
                edmPrimitiveType = (EdmPrimitiveType) edmFunction.getReturnType().getType();
                property = processImportFunctionPrimitiveCollection(uriInfo);
            } else {
                throw new ODataApplicationException("Only ImportFunction is supported",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
        } else if (resourcePaths.size() >= 2 && resourcePaths.get(resourcePaths.size() - 1) instanceof UriResourceFunction) {
            //第一个是bound的对象 最后一个是Action,如果长度 > 2就是有navigation对象
            UriResourceNavigation uriResourceNavigation = null;
            // 先只支持2段
            if (resourcePaths.size() > 2) {
                uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
            }
            UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
            UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(resourcePaths.size() - 1);
            edmFunction = uriResourceFunction.getFunction();
            edmPrimitiveType = (EdmPrimitiveType) edmFunction.getReturnType().getType();
            List<UriParameter> parameters = uriResourceFunction.getParameters();
            try {
                Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                        "locale", locale);
                FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
                property = ofbizOdataReader.processBoundFunctionPrimitive(uriResourceFunction, parameters,
                        boundEntity, uriResourceNavigation, uriInfo.getAliases());
            } catch (OfbizODataException e) {
                throw new ODataApplicationException(e.getMessage(),
                        Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH, e.getODataErrorCode());
            }
        } else {
            throw new ODataApplicationException("Doesn't support resource has more than 2 parts",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }

        // serialize property
        if (property != null) {
            final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
            final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
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

    private Property processImportFunctionPrimitiveCollection(UriInfo uriInfo)
            throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(0);
        List<UriParameter> uriParameters = uriResourceFunction.getParameters();
        FunctionProcessor functionProcessor = getFunctionProcessor();
        try {
            return functionProcessor.processImportFunctionPrimitive(uriResourceFunction, uriParameters, uriInfo.getAliases());
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private FunctionProcessor getFunctionProcessor() {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        return new FunctionProcessor(odataContext, null, null);
    }

    @Override
    public void updatePrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType contentType, ContentType contentType1) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("updatePrimitiveCollection =>", module);

    }

    @Override
    public void deletePrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("deletePrimitiveCollection =>", module);

    }

    @Override
    public void init(OData oData, ServiceMetadata serviceMetadata) {
        this.odata = oData;
        this.serviceMetadata = serviceMetadata;
    }

    private void readFunctionImportInternal(final ODataRequest request, final ODataResponse response,
                                            final UriInfo uriInfo, final ContentType responseFormat)
            throws ODataApplicationException, SerializerException {
        Debug.logInfo("------------------------------------------------------------ in readFunctionImportInternal", module);

        // 1st step: Analyze the URI and fetch the entity returned by the function
        // import
        // Function Imports are always the first segment of the resource path
        final UriResource firstSegment = uriInfo.getUriResourceParts().get(0);

        if (!(firstSegment instanceof UriResourceFunction)) {
            throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                    Locale.ENGLISH);
        }

        final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
        Property property = null;
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
        try {
            property = ofbizOdataReader.readFunctionImportPrimitiveCollection(uriResourceFunction);
        } catch (ODataException e) {
            e.printStackTrace();
        }

        if (property == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(),
                    Locale.ROOT);
        }

        // 2nd step: Serialize the response entity
        EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) uriResourceFunction.getFunction().getReturnType().getType();
        ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
        PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
        //todo fix me
//         ODataSerializer serializer = odata.createSerializer(responseFormat);
        SerializerResult serializerResult = odata.createSerializer(responseFormat).primitiveCollection(serviceMetadata,
                edmPrimitiveType, property, opts);

        // 3rd configure the response object
        response.setContent(serializerResult.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }
}
