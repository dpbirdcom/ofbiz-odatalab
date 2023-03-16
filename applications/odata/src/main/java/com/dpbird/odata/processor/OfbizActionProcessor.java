package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import net.sf.json.JSONObject;
import org.apache.fop.util.ListUtil;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlAction;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.prefer.Preferences;
import org.apache.olingo.server.api.prefer.PreferencesApplied;
import org.apache.olingo.server.api.processor.*;
import org.apache.olingo.server.api.serializer.*;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OfbizActionProcessor
        implements ActionVoidProcessor, ActionEntityCollectionProcessor, ActionEntityProcessor,
        ActionPrimitiveProcessor, ActionPrimitiveCollectionProcessor, ActionComplexProcessor, ActionComplexCollectionProcessor {

    public static final String module = OfbizActionProcessor.class.getName();
    private static OData odata;
    private static ServiceMetadata serviceMetadata;
    private static LocalDispatcher dispatcher;
    private static Delegator delegator;
    private static OfbizAppEdmProvider edmProvider;
    private static HttpServletRequest httpServletRequest = null;
    private static Locale locale = Locale.ROOT;
    private static GenericValue userLogin;
    private static String sapContextId = null;

    public OfbizActionProcessor(HttpServletRequest requestConstr, Delegator delegatorConstr, LocalDispatcher dispatcherConstr,
                                OfbizAppEdmProvider edmProviderConstr, GenericValue userLoginConstr, Locale localeConstr) {
        super();
        delegator = delegatorConstr;
        dispatcher = dispatcherConstr;
        httpServletRequest = requestConstr;
        edmProvider = edmProviderConstr;
        userLogin = userLoginConstr;
        locale = localeConstr;
    }

    @Override
    public void init(OData initOdata, ServiceMetadata initServiceMetatdata) {
        serviceMetadata = initServiceMetatdata;
        odata = initOdata;
    }

    private Map<String, Object> getOdataContext(ODataRequest request, ODataResponse response, OfbizCsdlAction csdlAction) {
//        sapContextId = OdataProcessorHelper.processSapContextId(delegator, request, response, csdlAction, null);
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher, "edmProvider", edmProvider, "userLogin",
                userLogin, "httpServletRequest", httpServletRequest, "oDataRequest", request, "oDataResponse", response, "oData",
                odata, "serviceMetadata", serviceMetadata, "locale", locale);
    }

    private OfbizCsdlAction getAction(UriResourceAction resourceAction) throws OfbizODataException {
        OfbizCsdlAction csdlAction = null;
        List<CsdlAction> csdlActions = edmProvider.getActions(resourceAction.getAction().getFullQualifiedName());
        if (UtilValidate.isNotEmpty(csdlActions)) {
            csdlAction = (OfbizCsdlAction) csdlActions.get(0);
        }
        return csdlAction;
    }

    @Override
    public void processActionVoid(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                  ContentType requestFormat) throws ODataApplicationException, ODataLibraryException {
        try {
            UriResource lastResource = ListUtil.getLast(uriInfo.getUriResourceParts());
            OfbizCsdlAction csdlAction = getAction((UriResourceAction) lastResource);
            Map<String, Object> odataContext = getOdataContext(request, response, csdlAction);
            dispatcher.runSync("dpbird.processActionVoidService", UtilMisc.toMap("odataContext", odataContext,
                    "requestFormat", requestFormat, "resourcePaths", uriInfo.getUriResourceParts(), "userLogin", userLogin));
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            addSapMessageToResponse(response);
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            if (sapContextId != null) {
                DataModifyActions.setResponseSessionContext(response, sapContextId);
            }
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes, HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private void addSapMessageToResponse(ODataResponse response) {
        JSONObject jsonObject = new JSONObject();
        JSONObject messages = JSONObject.fromObject("{\"code\":\"200\",\"message\":\"Success!\",\"numericSeverity\":1,\"longtextUrl\":\"It's succeed.\"}");
        jsonObject.putAll(messages);
        response.addHeader("sap-messages", "[" + jsonObject.toString() + "]");
    }

//    private static DraftActionProcessor getDraftActionProcessor(ODataRequest oDataRequest, ODataResponse oDataResponse,
//                                                                Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) throws OfbizODataException {
//        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
//                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
//                "oDataRequest", oDataRequest, "oDataResponse", oDataResponse, "oData", odata,
//                "serviceMetadata", serviceMetadata, "sapContextId", sapContextId, "locale", locale);
//        EdmEntityType edmEntityType = (EdmEntityType) edmParams.get("edmEntityType");
//        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
//        return new DraftActionProcessor(queryOptions, odataContext, csdlEntityType, edmEntityType);
//    }


    @Override
    public void processActionEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                              ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionEntityCollection", module);
        // Get the action from the resource path
        if (contentType == null) {
            throw new ODataApplicationException("The content type has not been set in the request.",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
        }
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        Map<String, QueryOption> queryOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        CountOption countOption = (CountOption) queryOptions.get("countOption");
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        try {
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, getAction(uriResourceAction));
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionEntityCollectionService",
                    UtilMisc.toMap("resourcePaths", resourcePaths, "queryOptions", queryOptions, "odataContext", odataContext,
                            "requestFormat", requestFormat, "userLogin", userLogin));
            EntityCollection entityCollection = (EntityCollection) processResult.get("entityCollection");
            //如果返回值不能为空，抛出一个异常
            if (UtilValidate.isEmpty(entityCollection) || UtilValidate.isEmpty(entityCollection.getEntities())) {
                if (!uriResourceAction.getAction().getReturnType().isNullable()) {
                    throw new ODataApplicationException("The return entity collection cannot be null.", HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
            }
            serializeEntityCollection(oDataRequest, oDataResponse, uriResourceAction, contentType,
                    expandOption, selectOption, countOption, entityCollection);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes,
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private void serializeEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                           UriResourceAction uriResourceAction,
                                           ContentType contentType, ExpandOption expandOption,
                                           SelectOption selectOption, CountOption countOption,
                                           EntityCollection entityCollection)
            throws ODataApplicationException {
        //响应时排除二进制数据
        for (Entity entity : entityCollection.getEntities()) {
            entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
        }
        EdmEntityType edmEntityType = (EdmEntityType) uriResourceAction.getAction().getReturnType().getType();
        try {
            String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
            ContextURL contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                    .entitySetOrSingletonOrType(edmEntityType.getName()).selectList(selectList).build();
            String id = oDataRequest.getRawBaseUri() + "/" + uriResourceAction.getAction().getName();
            // expand and select currently not supported
            EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().id(id).count(countOption)
                    .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            ODataSerializer serializer = odata.createSerializer(contentType);
            SerializerResult serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entityCollection, opts);
            InputStream serializedContent = serializerResult.getContent();
            // Finally: configure the response object: set the body, headers and status code
            oDataResponse.setContent(serializedContent);
        } catch (SerializerException | URISyntaxException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }


    // This method fetches the context URL
    private ContextURL getContextUrl(final EdmEntitySet entitySet, final EdmEntityType entityType,
                                     final boolean isSingleEntity) throws ODataLibraryException {
        ContextURL.Builder builder = ContextURL.with();
        builder = entitySet == null
                ? isSingleEntity ? builder.type(entityType) : builder.asCollection().type(entityType)
                : builder.entitySet(entitySet);
        builder = builder.suffix(isSingleEntity && entitySet != null ? ContextURL.Suffix.ENTITY : null);
        return builder.build();
    }

    protected boolean isODataMetadataNone(final ContentType contentType) {
        return contentType.isCompatible(ContentType.APPLICATION_JSON) && ContentType.VALUE_ODATA_METADATA_NONE
                .equalsIgnoreCase(contentType.getParameter(ContentType.PARAMETER_ODATA_METADATA));
    }

    @Override
    public void processActionEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                    ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionEntity", module);
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        Map<String, QueryOption> queryOptions = getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        final EdmAction edmAction = uriResourceAction.getAction();
        final EdmEntityType edmReturnEntityType = (EdmEntityType) edmAction.getReturnType().getType();
        OFbizEntityActionResult entityResult;
        EdmEntitySet boundEntitySet = null;
        EdmEntitySet returnedEntitySet = null;
        try {
            OfbizCsdlAction csdlAction = getAction(uriResourceAction);
            //EditAction先检查当前数据是否由其他用户正在编辑
            if (csdlAction != null && csdlAction.isStickySessionEdit()
                    && resourcePaths.size() > 1 && otherUsersAreEditing(resourcePaths.get(0))) {
                throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Editors are not allowed, because someone else is editing.");
            }
            sapContextId = OdataProcessorHelper.processSapContextId(delegator, oDataRequest, oDataResponse, csdlAction, null);
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, csdlAction);
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionEntityService", UtilMisc.toMap("odataContext", odataContext,
                    "requestFormat", requestFormat, "resourcePaths", resourcePaths, "queryOptions", queryOptions, "sapContextId", sapContextId, "userLogin", userLogin));
            entityResult = (OFbizEntityActionResult) processResult.get("entityResult");

            // Return
            if (entityResult == null || entityResult.getEntity() == null) {
                if (edmAction.getReturnType().isNullable()) {
                    oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                } else {
                    // 返回值是必须的, code = 404
                    throw new ODataApplicationException("The action could not be executed. The return entity cannot be null.",
                            HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT, HttpStatusCode.NOT_FOUND.getStatusCode() + "");
                }
                return;
            }
            final Preferences.Return returnPreference = odata.createPreferences(oDataResponse.getHeaders(HttpHeader.PREFER)).getReturn();
            if (returnPreference == null || returnPreference == Preferences.Return.REPRESENTATION) {
                returnedEntitySet = edmAction.getReturnedEntitySet(boundEntitySet);
                oDataResponse.setContent(odata.createSerializer(responseFormat)
                        .entity(serviceMetadata, edmReturnEntityType, entityResult.getEntity(), EntitySerializerOptions.with()
                                .contextURL(isODataMetadataNone(responseFormat) ? null
                                        : getContextUrl(null, edmReturnEntityType, true))
                                .expand(expandOption).select(selectOption).build())
                        .getContent());
                oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                oDataResponse.setStatusCode((entityResult.isCreated() ? HttpStatusCode.CREATED : HttpStatusCode.OK).getStatusCode());
            } else {
                //return=minimal 不响应数据
                oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            }
            if (returnPreference != null) {
                oDataResponse.setHeader(HttpHeader.PREFERENCE_APPLIED, PreferencesApplied.with().returnRepresentation(returnPreference).build().toValueString());
            }
            if (entityResult.isCreated()) {
                final String location = oDataRequest.getRawBaseUri() + '/'
                        + odata.createUriHelper().buildCanonicalURL(returnedEntitySet, entityResult.getEntity());
                oDataResponse.setHeader(HttpHeader.LOCATION, location);
                if (returnPreference == Preferences.Return.MINIMAL) {
                    oDataResponse.setHeader(HttpHeader.ODATA_ENTITY_ID, location);
                }
            }
            if (entityResult.getEntity().getETag() != null) {
                oDataResponse.setHeader(HttpHeader.ETAG, entityResult.getEntity().getETag());
            }
            //所有的代码成功执行之后，返回sapContextId
            if (entityResult.getEntity() instanceof OdataOfbizEntity) {
                OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entityResult.getEntity();
                if (UtilValidate.isNotEmpty(ofbizEntity.getKeyMap()) && ofbizEntity.getKeyMap().containsKey("draftUUID")) {
                    DataModifyActions.setResponseSessionContext(oDataResponse, (String) ofbizEntity.getKeyMap().get("draftUUID"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            //如果当前的request本身含有sapContextId, 发生异常也要响应回去
            if (UtilValidate.isNotEmpty(oDataRequest.getHeader("SAP-ContextId")) && sapContextId != null) {
                DataModifyActions.setResponseSessionContext(oDataResponse, sapContextId);
            }
            String exceptionMeg = e instanceof GenericServiceException ? e.getCause().getMessage() : e.getMessage();
            throw new ODataApplicationException(exceptionMeg, HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ROOT);
        }
    }

    private boolean otherUsersAreEditing(UriResource uriResource) throws OfbizODataException, GenericEntityException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) uriResource;
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) boundEntity;
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        Map<String, Object> keyMap = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntitySet.getEntityType(), edmProvider);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());

        List<GenericValue> draftAdminDataList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap(
                "originEntityName", csdlEntityType.getOfbizEntity(), "draftEntityName", csdlEntityType.getDraftEntityName(),
                "entityKeyMap", Util.mapToStr(keyMap)), null, false);
        GenericValue draftAdminData = EntityUtil.getFirst(draftAdminDataList);
        if (UtilValidate.isNotEmpty(draftAdminData)) {
            String createdByUser = draftAdminData.getString("createdByUser");
            //createdByUser不是当前userLogin，说明是其他用户未提交的数据
            return !createdByUser.equals(userLogin.get("userLoginId"));
        }
        return false;
    }

    private Map<String, QueryOption> getQuernOptions(UriInfo uriInfo) {
        Map<String, QueryOption> queryOptions = new HashMap<>();
        SelectOption selectOption = uriInfo.getSelectOption();
        ExpandOption expandOption = uriInfo.getExpandOption();
        if (selectOption != null) {
            queryOptions.put("selectOption", selectOption);
        }
        if (expandOption != null) {
            queryOptions.put("expandOption", expandOption);
        }
        return queryOptions;
    }
    @Override
    public void processActionPrimitive(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                       ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        Property property = null;
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceAction uriResourceAction = (UriResourceAction) ListUtil.getLast(resourcePaths);
        final EdmAction edmAction = uriResourceAction.getAction();
        try {
            OfbizCsdlAction csdlAction = getAction(uriResourceAction);
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, csdlAction);
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionPrimitiveService",
                    UtilMisc.toMap("odataContext", odataContext, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            property = (Property) processResult.get("property");
        } catch (GenericServiceException | OfbizODataException e) {
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes, HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        if (property == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }

        // Serialize the response property
        final EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) edmAction.getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
        final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(requestFormat);
        final SerializerResult serializerResult = serializer.primitive(serviceMetadata, edmPrimitiveType, property, opts);

        // configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    @Override
    public void processActionComplexCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                               ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionComplexCollection", module);
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceAction uriResourceAction = (UriResourceAction) ListUtil.getLast(resourcePaths);
        final EdmAction edmAction = uriResourceAction.getAction();
        Property property;
        try {
            OfbizCsdlAction csdlAction = getAction(uriResourceAction);
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, csdlAction);
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionPrimitiveService",
                    UtilMisc.toMap("odataContext", odataContext, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            property = (Property) processResult.get("property");
        } catch (GenericServiceException | OfbizODataException e) {
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes, HttpStatus.SC_INTERNAL_SERVER_ERROR, Locale.ROOT);
        }
        if (UtilValidate.isEmpty(property)) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        try {
            // Serialize the response entity
            final EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType().getType();
            final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
            final ODataSerializer serializer = odata.createSerializer(contentType);
            final SerializerResult serializerResult = serializer.complexCollection(serviceMetadata, edmComplexType, property, opts);

            // configure the response object
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processActionComplex(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                     ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionComplex", module);
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceAction uriResourceAction = (UriResourceAction) ListUtil.getLast(resourcePaths);
        final EdmAction edmAction = uriResourceAction.getAction();
        Property property;
        try {
            OfbizCsdlAction csdlAction = getAction(uriResourceAction);
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, csdlAction);
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionPrimitiveService",
                    UtilMisc.toMap("odataContext", odataContext, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            property = (Property) processResult.get("property");
        } catch (GenericServiceException | OfbizODataException e) {
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes, HttpStatus.SC_INTERNAL_SERVER_ERROR, Locale.ROOT);
        }
        if (UtilValidate.isEmpty(property)) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        // Serialize the response entity
        final EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
        final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(contentType);
        final SerializerResult serializerResult = serializer.complex(serviceMetadata, edmComplexType, property, opts);

        // configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
    }

    @Override
    public void processActionPrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                                 ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionPrimitiveCollection", module);
        if (requestFormat == null) {
            throw new ODataApplicationException("The content type has not been set in the request.", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
        }
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        final List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceAction uriResourceAction = (UriResourceAction) ListUtil.getLast(resourcePaths);
        Property property;
        try {
            OfbizCsdlAction csdlAction = getAction(uriResourceAction);
            Map<String, Object> odataContext = getOdataContext(oDataRequest, oDataResponse, csdlAction);
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionPrimitiveService",
                    UtilMisc.toMap("odataContext", odataContext, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            property = (Property) processResult.get("property");
        } catch (GenericServiceException | OfbizODataException e) {
            String errMes = e.getMessage();
            if (e instanceof GenericServiceException) {
                errMes = e.getCause().getMessage();
            }
            throw new ODataApplicationException(errMes, HttpStatus.SC_INTERNAL_SERVER_ERROR, Locale.ROOT);
        }
        if (UtilValidate.isEmpty(property)) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        // Serialize the response property
        final EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) uriResourceAction.getAction().getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
        final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(requestFormat);
        final SerializerResult serializerResult = serializer.primitiveCollection(serviceMetadata, edmPrimitiveType, property, opts);

        // configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, requestFormat.toContentTypeString());
    }


}
