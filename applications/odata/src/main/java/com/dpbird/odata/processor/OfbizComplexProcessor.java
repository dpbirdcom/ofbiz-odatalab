package com.dpbird.odata.processor;

import com.dpbird.odata.FunctionProcessor;
import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OfbizCsdlFunction;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmFunction;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.ComplexCollectionProcessor;
import org.apache.olingo.server.api.processor.ComplexProcessor;
import org.apache.olingo.server.api.serializer.ComplexSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizComplexProcessor implements ComplexProcessor, ComplexCollectionProcessor {

    public static final String module = OfbizComplexProcessor.class.getName();
    public static final int MAX_ROWS = 10000;
    public static final int DAYS_BEFORE = -100;
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private Delegator delegator;
    private LocalDispatcher dispatcher;
    // private OfbizOdataReader ofbizOdataReader;
    // private EdmWebConfig edmWebConfig;
    private OfbizAppEdmProvider edmProvider;
    private HttpServletRequest httpServletRequest = null;
    private Locale locale = Locale.ROOT;
    private GenericValue userLogin;

    public OfbizComplexProcessor(HttpServletRequest request, Delegator delegator, LocalDispatcher dispatcher,
                                 OfbizAppEdmProvider edmProvider, GenericValue userLogin, Locale locale) {
        super();
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.httpServletRequest = request;
        // this.ofbizOdataReader = ofbizOdataReader;
        // this.edmWebConfig = edmWebConfig;
        this.edmProvider = edmProvider;
        this.locale = locale;
        this.userLogin = userLogin;
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    // readComplexCollection仅针对于Function的返回为Complex Collection
    @Override
    public void readComplexCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                      ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ readComplexCollection", module);
        EdmFunction edmFunction = null;
        Map<String, Parameter> parameters = new HashMap<String, Parameter>();
        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();
        TopOption topOption = uriInfo.getTopOption();
        SkipOption skipOption = uriInfo.getSkipOption();
        EdmEntitySet edmEntitySet = null;
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        UriResource firstSegment = resourcePaths.get(0);
        Property property = null;

        if (resourcePaths.size() == 1) {
            final UriResourceFunction uriResourceFunction = (UriResourceFunction) uriInfo.asUriInfoResource()
                    .getUriResourceParts().get(0);
            edmFunction = uriResourceFunction.getFunction();
            try {
                property = processImportFunctionComplex(uriInfo);
            } catch (ODataException e) {
                e.printStackTrace();
            }

            if (property == null) {
                throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
            }
        } else if (resourcePaths.size() == 2) {
            edmFunction = ((UriResourceFunction) resourcePaths.get(1)).getFunction();
            Map<String, QueryOption> queryOptions = UtilMisc.toMap("skipOption", skipOption, "topOption", topOption);
            property = processBoundFunctionComplex(uriInfo, queryOptions);
        }

        // Deserialize the parameter
        try {
            final EdmComplexType edmComplexType = (EdmComplexType) edmFunction.getReturnType().getType();
            final ContextURL contextUrl = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextUrl).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);
            final SerializerResult serializerResult = serializer.complexCollection(serviceMetadata, edmComplexType, property, opts);
            // configure the response object
            response.setContent(serializerResult.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (Exception e) {
            e.printStackTrace();
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

    // readComplex仅针对于Function的返回为ComplexType
    @Override
    public void readComplex(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ readComplex", module);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        final EdmFunction edmFunction = ((UriResourceFunction) resourcePaths.get(resourcePathSize - 1))
                .getFunction();
        Property property;
        if (resourcePaths.size() == 2) { // bound action
            property = processBoundFunctionComplex(uriInfo, null);
        } else {
            property = processImportFunctionComplex(uriInfo);
        }
        if (property.getValue() == null) {
            oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        } else {
            final EdmComplexType edmComplexType = (EdmComplexType) edmFunction.getReturnType().getType();
            final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);

            final SerializerResult serializerResult = serializer.complex(serviceMetadata, edmComplexType, property, opts);
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        }
    }

    private Property processImportFunctionComplex(UriInfo uriInfo) throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(0);
        List<UriParameter> uriParameters = uriResourceFunction.getParameters();
        FunctionProcessor functionProcessor = getFunctionProcessor(null);
        try {
            return functionProcessor.processImportFunctionComplex(uriResourceFunction, uriParameters, uriInfo.getAliases());
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private Property processBoundFunctionComplex(UriInfo uriInfo,
                                                 Map<String, QueryOption> queryOptions) throws ODataApplicationException {
        List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceNavigation uriResourceNavigation = null;
        if (resourcePaths.size() > 2) { // 先只支持2段
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(resourcePaths.size() - 1);
        List<UriParameter> uriParameters = uriResourceFunction.getParameters();
        FunctionProcessor functionProcessor = getFunctionProcessor(queryOptions);
        try {
            return functionProcessor.processBoundFunctionComplex(uriResourceFunction, uriParameters, boundEntity, uriResourceNavigation, uriInfo.getAliases());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound function complex. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    private FunctionProcessor getFunctionProcessor(Map<String, QueryOption> queryOptions) {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        return new FunctionProcessor(odataContext, queryOptions, null);
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

    private void readFunctionImportInternal(final ODataRequest request, final ODataResponse response,
                                            final UriInfo uriInfo, final ContentType responseFormat)
            throws ODataException {
        final UriResource firstSegment = uriInfo.getUriResourceParts().get(0);
        final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
        EdmFunction edmFunction = uriResourceFunction.getFunction();
        String functionName = uriResourceFunction.getFunctionImport().getName();
        FullQualifiedName functionFqn = uriResourceFunction.getFunctionImport().getFunctionFqn();
        OfbizCsdlFunction ofbizCsdlFunction = (OfbizCsdlFunction) edmProvider.getFunctions(functionFqn);
        String ofbizMethod = ofbizCsdlFunction.getOfbizMethod();
        Property property;
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "locale", locale);
        FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
        // 这个function是ofbiz的service
        if (ofbizCsdlFunction.isService()) {
            property = ofbizOdataReader.readFunctionImportComplex(uriResourceFunction);
        } else {
            // 这个function是某个java class的method
            List<UriParameter> parameters = uriResourceFunction.getParameters();
            property = ofbizOdataReader.processFunctionComplexMethod(httpServletRequest, edmFunction, ofbizMethod, parameters);
        }
        if (property == null) {
            throw new ODataApplicationException("No property found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        } else {
            if (property.getValue() == null) {
                response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            } else {
                final EdmComplexType edmComplexType = (EdmComplexType) uriResourceFunction.getFunction().getReturnType().getType();
                final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
                final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
                final ODataSerializer serializer = odata.createSerializer(responseFormat);

                final SerializerResult serializerResult = serializer.complex(serviceMetadata, edmComplexType, property, opts);
                response.setContent(serializerResult.getContent());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
            }
        }
    }
}
