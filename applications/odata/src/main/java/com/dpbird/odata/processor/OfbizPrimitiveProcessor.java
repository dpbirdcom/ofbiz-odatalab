package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlProperty;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.PrimitiveValueProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceFunction;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.AliasQueryOption;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OfbizPrimitiveProcessor implements PrimitiveValueProcessor {

    public static final String MODULE = OfbizPrimitiveProcessor.class.getName();
    private OData odata;
    private ServiceMetadata serviceMetadata;
    private final LocalDispatcher dispatcher;
    private final Delegator delegator;
    private final OfbizAppEdmProvider edmProvider;
    private final HttpServletRequest httpServletRequest;
    private final Locale locale;
    private final GenericValue userLogin;

    public OfbizPrimitiveProcessor(HttpServletRequest request, Delegator delegator, LocalDispatcher dispatcher,
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
    public void init(OData odata, ServiceMetadata serviceMetatdata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetatdata;
    }

    @Override
    public void readPrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        EdmPrimitiveType edmPrimitiveType;
        EdmReturnType returnType = null;
        EdmProperty edmProperty = null;
        Property property;
        String mimeType;
        try {
            Map<String, Object> propertyInfo = readProperty(uriInfo.getUriResourceParts(), uriInfo.getAliases());
            property = (Property) propertyInfo.get("property");
            mimeType = (String) propertyInfo.get("mimeTypeId");
            UriResource lastUriResource = ListUtil.getLast(uriInfo.getUriResourceParts());
            if (lastUriResource instanceof UriResourceFunction) {
                //Function
                UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
                returnType = resourceFunction.getFunction().getReturnType();
                edmPrimitiveType = (EdmPrimitiveType) returnType.getType();
            } else {
                //Entity Property
                UriResourcePrimitiveProperty resourceProperty = (UriResourcePrimitiveProperty) lastUriResource;
                edmProperty = resourceProperty.getProperty();
                edmPrimitiveType = (EdmPrimitiveType) resourceProperty.getType();
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
        }
        // serialize property
        if (property != null) {
            //Stream property
            if ("Edm.Stream".equals(property.getType()) && property.getValue() != null) {
                final byte[] bytes = (byte[]) property.getValue();
                final InputStream responseContent = odata.createFixedFormatSerializer().binary(bytes);
                response.setContent(responseContent);
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, mimeType);
                return;
            }
            Integer scale = returnType != null ? returnType.getScale() :  edmProperty.getScale();
            Integer precision = returnType != null ? returnType.getPrecision() : edmProperty.getPrecision();
            final ContextURL contextUrl = ContextURL.with().type(edmPrimitiveType).build();
            final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with()
                    .scale(scale).precision(precision).contextURL(contextUrl).build();
            final ODataSerializer serializer = odata.createSerializer(responseFormat);
            final SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPrimitiveType, property, opts);

            response.setContent(serializerResult.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } else {
            // in case there's no value for the property, we can skip the serialization
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
        }
    }

    @Override
    public void readPrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        try {
            List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
            uriResourceParts = uriResourceParts.subList(0, uriResourceParts.size() - 1);
            Map<String, Object> propertyInfo = readProperty(uriResourceParts, uriInfo.getAliases());

            Property property = (Property) propertyInfo.get("property");
            if (property != null && property.getValue() != null) {
                InputStream resultContent = new ByteArrayInputStream(property.getValue().toString().getBytes(StandardCharsets.UTF_8));
                response.setContent(resultContent);
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
        }
    }

    private Map<String, Object> readProperty(List<UriResource> resourcePaths, List<AliasQueryOption> aliases) throws OfbizODataException {
        Property property;
        UriResource lastUriResource = ListUtil.getLast(resourcePaths);
        UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(getOdataContext(), new HashMap<>(), null);
        Map<String, Object> propertyInfo = new HashMap<>();
        if (lastUriResource instanceof UriResourceFunction) {
            //Function
            UriResourceFunction resourceFunction = (UriResourceFunction) lastUriResource;
            EdmFunction edmFunction = resourceFunction.getFunction();
            Map<String, Object> parameters = Util.uriParametersToMap(resourceFunction.getParameters(), edmFunction, aliases);
            EdmBindingTarget edmBindingTarget = null;
            if (edmFunction.isBound()) {
                //添加Bound参数
                List<UriResource> uriResourceList = new ArrayList<>(resourcePaths.subList(0, resourcePaths.size() - 1));
                List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceList, aliases);
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
            propertyInfo.put("property", property);
        } else {
            //Entity Property
            List<UriResource> uriResourceList = new ArrayList<>(resourcePaths.subList(0, resourcePaths.size() - 1));
            List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(uriResourceList, aliases);
            OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
            Entity entity = (Entity) odataParts.getEntityData();
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(odataParts.getEdmEntityType().getFullQualifiedName());
            OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(lastUriResource.getSegmentValue());
            property = entity.getProperty(lastUriResource.getSegmentValue());
            propertyInfo.put("property", property);
            try {
                if (UtilValidate.isNotEmpty(property) && "Edm.Stream".equals(property.getType())) {
                    String mimeType = csdlProperty.getMimeType();
                    if (UtilValidate.isEmpty(mimeType)) {
                        String dataResourceId = (String) entity.getProperty("dataResourceId").getValue();
                        mimeType = EntityQuery.use(delegator).from("DataResource")
                                .select("mimeTypeId").where(UtilMisc.toMap("dataResourceId", dataResourceId)).queryOne().getString("mimeTypeId");
                    }
                    if (UtilValidate.isEmpty(mimeType)) {
                        throw new OfbizODataException("MimeType not found");
                    }
                    propertyInfo.put("mimeTypeId", mimeType);
                }
            } catch (GenericEntityException e) {
                throw new OfbizODataException(e.getMessage());
            }

        }
        return propertyInfo;
    }

    private Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
    }

    @Override
    public void updatePrimitive(ODataRequest arg0, ODataResponse arg1, UriInfo arg2, ContentType arg3, ContentType arg4)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    @Override
    public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    @Override
    public void updatePrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    @Override
    public void deletePrimitiveValue(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, ODataLibraryException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

}
