package  com.dpbird.odata.processor;

import com.dpbird.odata.*;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.PrimitiveProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizPrimitiveProcessor implements PrimitiveProcessor {

	public static final String module = OfbizPrimitiveProcessor.class.getName();
	private OData odata;
	private ServiceMetadata serviceMetadata;
	private LocalDispatcher dispatcher;
	private Delegator delegator;
    // private EdmWebConfig edmWebConfig;
	// private OfbizOdataReader ofbizOdataReader;
	private OfbizAppEdmProvider edmProvider;
	private HttpServletRequest httpServletRequest = null;
	private Locale locale = Locale.ROOT;
	private GenericValue userLogin;

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
		this.serviceMetadata = serviceMetadata;
	}

	@Override
	public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo)
			throws ODataApplicationException, ODataLibraryException {
		throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
	}

	@Override
	public void readPrimitive(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo, ContentType responseFormat)
			throws ODataApplicationException, ODataLibraryException {
		List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
		Property property = null;
		EdmPrimitiveType edmPrimitiveType = null;
		EdmProperty edmProperty = null;
		EdmFunction edmFunction;
		EdmReturnType edmReturnType = null;
		Integer scale = null;
		Integer precision = null;
		if (resourcePaths.size() == 1) {
			UriResource uriResource = resourcePaths.get(0);
			if(uriResource instanceof UriResourceFunction) { // 感觉只有ImportFunction才会进入到这里
				edmFunction = ((UriResourceFunction) uriResource).getFunction();
				edmReturnType = edmFunction.getReturnType();
				edmPrimitiveType = (EdmPrimitiveType) edmReturnType.getType();
				property = processImportFunctionPrimitive(uriInfo);
			} else {
				throw new ODataApplicationException("Only ImportFunction is supported",
						HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
			}
		} else if (resourcePaths.size() >= 2) {
	        final ODataDeserializer deserializer = odata.createDeserializer(responseFormat);
			UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
			UriResourceNavigation uriResourceNavigation = null;
			if (resourcePaths.size() > 2) { // 先只支持2段
				uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
			}
            if (resourcePaths.get(resourcePaths.size() - 1) instanceof UriResourceFunction) {
            	UriResourceFunction uriResourceFunction = (UriResourceFunction) resourcePaths.get(1);
                edmFunction = uriResourceFunction.getFunction();
				edmReturnType = edmFunction.getReturnType();
				edmPrimitiveType = (EdmPrimitiveType) edmReturnType.getType();
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
        					Integer.parseInt(e.getODataErrorCode()), Locale.ENGLISH);
				}
            } else if (resourcePaths.get(1) instanceof UriResourcePrimitiveProperty) {
            	UriResourcePrimitiveProperty uriResourcePrimitiveProperty = (UriResourcePrimitiveProperty) resourcePaths.get(1);
            	edmPrimitiveType = (EdmPrimitiveType) uriResourcePrimitiveProperty.getType();
				edmProperty = uriResourcePrimitiveProperty.getProperty();
				Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
						"edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
						"locale", locale);
				OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, null, null);
				try {
					property = ofbizOdataReader.readPrimitiveProperty(uriResourcePrimitiveProperty, boundEntity);
				} catch (ODataException e) {
					throw new ODataApplicationException(e.getMessage(),
							HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), locale);
				}
			}
		} else {
			throw new ODataApplicationException("Doesn't support resource has more than 2 parts",
					HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
		}

		// serialize property
		if (property != null) {
			Object value = property.getValue();
			if (edmReturnType != null) {
				scale = edmReturnType.getScale();
				precision = edmReturnType.getPrecision();
			} else if (edmProperty != null) {
				scale = edmProperty.getScale();
				precision = edmProperty.getPrecision();
			}
			final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
			final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with()
					.scale(scale).precision(precision).contextURL(contextURL).build();
			final ODataSerializer serializer = odata.createSerializer(responseFormat);
			final SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPrimitiveType, property, opts);

			oDataResponse.setContent(serializerResult.getContent());
			oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
			oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
		} else {
			// in case there's no value for the property, we can skip the serialization
			oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
		}

	}

	private Property processImportFunctionPrimitive(UriInfo uriInfo)
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

	@Override
	public void updatePrimitive(ODataRequest arg0, ODataResponse arg1, UriInfo arg2, ContentType arg3, ContentType arg4)
			throws ODataApplicationException, ODataLibraryException {
		throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
	}

	private void readEntityInternal(final ODataRequest request, final ODataResponse response,final UriInfo uriInfo,
									final ContentType responseFormat)
			throws ODataApplicationException, SerializerException {
		List<UriResource> resourceParts = uriInfo.getUriResourceParts();
		UriResource uriResource = resourceParts.get(0);
		// Note: only in our example we can rely that the first segment is the EntitySet
		UriResourceEntitySet uriEntityset = (UriResourceEntitySet) resourceParts.get(0);
		EdmEntitySet edmEntitySet = uriEntityset.getEntitySet();
		// the key for the entity
		List<UriParameter> keyPredicates = uriEntityset.getKeyPredicates();

		// 1.2. retrieve the requested (Edm) property
		// the last segment is the Property
		UriResourceProperty uriProperty = (UriResourceProperty) resourceParts.get(resourceParts.size() -1);
		EdmProperty edmProperty = uriProperty.getProperty();
		String edmPropertyName = edmProperty.getName();
		// in our example, we know we have only primitive types in our model
		EdmPrimitiveType edmPropertyType = (EdmPrimitiveType) edmProperty.getType();

		// 2. retrieve data from backend
		// 2.1. retrieve the entity data, for which the property has to be read
		Entity entity = null;
		try {
			Map<String, Object> keyMap = Util.uriParametersToMap(keyPredicates, edmEntitySet.getEntityType());
			Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
					"edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
					"locale", locale);
			Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", edmEntitySet);
			OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, null, edmParams);
			entity = ofbizOdataReader.readEntityData(keyMap, null);
		} catch (ODataException e) {
			e.printStackTrace();
			throw new ODataApplicationException(e.getMessage(),
					HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
		}

		if (entity == null) { // Bad request
			throw new ODataApplicationException("Entity not found",
					HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
		}

		// 2.2. retrieve the property data from the entity
		Property property = entity.getProperty(edmPropertyName);
		if (property == null) {
			throw new ODataApplicationException("Property not found",
					HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
		}

		// 3. serialize
		// 3. serialize
		Object value = property.getValue();
		if (value != null) {
			// 3.1. configure the serializer
			ODataSerializer serializer = odata.createSerializer(responseFormat);

			ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).navOrPropertyPath(edmPropertyName).build();
			PrimitiveSerializerOptions options = PrimitiveSerializerOptions.with().contextURL(contextUrl).build();
			// 3.2. serialize
			SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPropertyType, property, options);
			InputStream propertyStream = serializerResult.getContent();

			//4. configure the response object
			response.setContent(propertyStream);
			response.setStatusCode(HttpStatusCode.OK.getStatusCode());
			response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
		}else{
			// in case there's no value for the property, we can skip the serialization
			response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
		}
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
					locale);
		}

		Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
				"edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
				"locale", locale);
		FunctionProcessor ofbizOdataReader = new FunctionProcessor(odataContext, null, null);
		final UriResourceFunction uriResourceFunction = (UriResourceFunction) firstSegment;
		Property property = null;
		try {
			property = ofbizOdataReader.readFunctionImportPrimitive(uriResourceFunction);
		} catch (ODataException e) {
			e.printStackTrace();
		}

		if (property == null) {
			throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(),
					locale);
		}

		// 2nd step: Serialize the response entity
		final EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) uriResourceFunction.getFunction().getReturnType().getType();
		final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
		final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
		final ODataSerializer serializer = odata.createSerializer(responseFormat);
		final SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPrimitiveType, property, opts);

		// 3rd configure the response object
		response.setContent(serializerResult.getContent());
		response.setStatusCode(HttpStatusCode.OK.getStatusCode());
		response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
	}

	private FunctionProcessor getFunctionProcessor() {
		Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
				"edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
				"locale", locale);
		return new FunctionProcessor(odataContext, null, null);
	}

}
