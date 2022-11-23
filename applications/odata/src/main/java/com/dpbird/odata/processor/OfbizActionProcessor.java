package com.dpbird.odata.processor;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import net.sf.json.JSONObject;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlAction;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
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
    // private OfbizOdataWriter ofbizOdataWriter;
    // private EdmWebConfig edmWebConfig;
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
        // this.ofbizOdataWriter = ofbizOdataWriter;
        httpServletRequest = requestConstr;
        // this.edmWebConfig = edmWebConfig;
        edmProvider = edmProviderConstr;
        userLogin = userLoginConstr;
        locale = localeConstr;
    }

    @Override
    public void init(OData initOdata, ServiceMetadata initServiceMetatdata) {
        serviceMetadata = initServiceMetatdata;
        odata = initOdata;
    }

    @Override
    public void processActionVoid(ODataRequest request, ODataResponse response, UriInfo uriInfo,
                                  ContentType requestFormat) throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionVoid", module);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        final EdmAction edmAction = ((UriResourceAction) resourcePaths.get(resourcePathSize - 1))
                .getAction();
        OfbizCsdlAction csdlAction = null;
        List<CsdlAction> csdlActions = null;
        //check If-Match
        checkActionIfMatch(uriInfo, request);
        try {
            csdlActions = edmProvider.getActions(edmAction.getFullQualifiedName());
            if (UtilValidate.isNotEmpty(csdlActions)) {
                csdlAction = (OfbizCsdlAction) csdlActions.get(0);
            }
            sapContextId = OdataProcessorHelper.processSapContextId(delegator, request, response, csdlAction, null);
            dispatcher.runSync("dpbird.processActionVoidService", UtilMisc.toMap("request", request,
                    "response", response, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            addSapMessageToResponse(response);
        } catch (OfbizODataException | GenericServiceException e) {
            e.printStackTrace();
            if (sapContextId != null) {
                DataModifyActions.setResponseSessionContext(response, sapContextId);
            }
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private void addSapMessageToResponse(ODataResponse response) {
        JSONObject jsonObject = new JSONObject();
        JSONObject messages = JSONObject.fromObject("{\"code\":\"200\",\"message\":\"Success!\",\"numericSeverity\":1,\"longtextUrl\":\"It's succeed.\"}");
        jsonObject.putAll(messages);
        response.addHeader("sap-messages", "[" + jsonObject.toString() + "]");
    }

    private static void processImportActionVoid(ODataRequest oDataRequest, ODataResponse oDataResponse, ContentType requestFormat,
                                         List<UriResource> resourcePaths) throws ODataApplicationException {
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        if (uriResourceAction.getAction().getName().equals("DiscardAction")) {
            Map<String, Object> editContext = UtilMisc.toMap("odataRequest", oDataRequest,
                    "httpServletRequest", httpServletRequest, "odataResponse", oDataResponse,
                    "locale", locale, "edmProvider", edmProvider, "delegator", delegator,
                    "dispatcher", dispatcher, "userLogin", userLogin);
            try {
                DataModifyActions.discardAction(editContext);
                oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                return;
            } catch (ODataException e) {
                throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
            }
        }
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            actionProcessor.processImportActionVoid(uriResourceAction, actionParameters);
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private static void processBoundActionVoid(ODataRequest oDataRequest, ODataResponse oDataResponse, ContentType requestFormat,
                                        List<UriResource> resourcePaths) throws ODataApplicationException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceNavigation uriResourceNavigation = null;
        if (resourcePaths.size() > 2) { // 先只支持2段
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        // TODO: need to fill out queryOptions and edmParams
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            actionProcessor.processBoundActionVoid(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
        } catch (ODataException e) {
            e.printStackTrace();
            if (e instanceof ODataApplicationException) {
                throw (ODataApplicationException) e;
            } else if (e instanceof OfbizODataException) {
                OfbizODataException e1 = (OfbizODataException) e;
                throw new ODataApplicationException("Cannot execute bound action void. " + e1.getMessage(),
                        Integer.parseInt(e1.getODataErrorCode()), locale, e1.getODataErrorCode());
            }
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private static Property processBoundActionPrimitive(ODataRequest oDataRequest, ODataResponse oDataResponse, ContentType requestFormat,
                                        List<UriResource> resourcePaths) throws ODataApplicationException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        UriResourceNavigation uriResourceNavigation = null;
        if (resourcePaths.size() > 2) { // 先只支持2段
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        // TODO: need to fill out queryOptions and edmParams
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            return actionProcessor.processBoundActionPrimitive(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    private static OFbizEntityActionResult processBoundActionEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, ContentType requestFormat,
                                                 List<UriResource> resourcePaths, Map<String, QueryOption> queryOptions)
            throws ODataApplicationException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceNavigation uriResourceNavigation = null;
        if (resourcePaths.size() > 2) { // 先只支持2段
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        EdmEntityType edmEntityType = (EdmEntityType) boundEntity.getType();
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        final Map<String, Object> edmParams = UtilMisc.toMap("edmEntityType", edmEntityType);
        try {
            if (UtilValidate.isNotEmpty(sapContextId)) {
                DraftActionProcessor draftActionProcessor = getDraftActionProcessor(oDataRequest, oDataResponse, queryOptions, edmParams);
                return draftActionProcessor.processBoundActionEntity(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
            } else {
                ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, queryOptions, edmParams);
                return actionProcessor.processBoundActionEntity(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
            }
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    private static ActionProcessor getActionProcessor(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                               Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "oDataRequest", oDataRequest, "oDataResponse", oDataResponse, "oData", odata,
                "serviceMetadata", serviceMetadata, "sapContextId", sapContextId, "locale", locale);
        ActionProcessor ofbizOdataWriter = new ActionProcessor(odataContext, queryOptions, edmParams);
        return ofbizOdataWriter;
    }

    private static DraftActionProcessor getDraftActionProcessor(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                               Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) throws OfbizODataException {
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "oDataRequest", oDataRequest, "oDataResponse", oDataResponse, "oData", odata,
                "serviceMetadata", serviceMetadata, "sapContextId", sapContextId, "locale", locale);
//        DraftActionProcessor ofbizOdataWriter = new DraftActionProcessor(odataContext, queryOptions, edmParams);
        EdmEntityType edmEntityType = (EdmEntityType) edmParams.get("edmEntityType");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        return new DraftActionProcessor(queryOptions, odataContext, csdlEntityType, edmEntityType);
    }

    private static UriResourceAction getUriResourceAction (List<UriResource> resourcePaths) {
        int size = resourcePaths.size();
        return (UriResourceAction) resourcePaths.get(size - 1);
    }

    private static Map<String, Parameter> getActionParameters (ODataRequest oDataRequest, UriResourceAction uriResourceAction,
                                                        ContentType requestFormat)
            throws ODataApplicationException {
        final ODataDeserializer deserializer;
        try {
            deserializer = odata.createDeserializer(requestFormat);
        final EdmAction edmAction = uriResourceAction.getAction();
        final Map<String, Parameter> actionParameters = deserializer.actionParameters(oDataRequest.getBody(), edmAction)
                .getActionParameters();
        return actionParameters;
        } catch (DeserializerException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot parse action parameters",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    /**
     * TODO 待实现 processActionEntityCollection
     * ofbiz目前并不存在有返回GenericValue的service，除了在specialpurpose里的getProductAndPrice，
     * 所以，这个processActionEntityCollection并没有被真正使用，以及完整测试。
     *
     * @param oDataRequest
     * @param oDataResponse
     * @param uriInfo
     * @param requestFormat
     * @param contentType
     * @throws ODataApplicationException
     * @throws ODataLibraryException
     */
    @Override
    public void processActionEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                              ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionEntityCollection",
                module);
        // Get the action from the resource path
        EdmAction action;
        Map<String, Parameter> parameters;
        UriResourceAction uriResourceAction;
        if (contentType == null) {
            throw new ODataApplicationException("The content type has not been set in the request.",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
        }
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);
        Map<String, QueryOption> queryOptions = OdataProcessorHelper.getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        CountOption countOption = (CountOption) queryOptions.get("countOption");
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "sapContextId", sapContextId, "locale", locale);
        final ODataDeserializer deserializer = odata.createDeserializer(contentType);
        Object invokeResult;
        if (resourcePaths.size() > 1) {
            // BoundAction
            uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        } else {
            // ImportAction
            uriResourceAction = (UriResourceAction) resourcePaths.get(0);
        }
        parameters = deserializer.actionParameters(oDataRequest.getBody(), uriResourceAction.getAction()).getActionParameters();
        try {
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionEntityCollectionService",
                    UtilMisc.toMap("resourcePaths", resourcePaths, "parameters", parameters, "odataContext", odataContext, "userLogin", userLogin));
            invokeResult = processResult.get("entityCollectionResult");
        } catch (GenericServiceException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR, locale);
        }
        try {
            //执行结果转换为EntityCollection
            EntityCollection entityCollection = null;
            if (invokeResult instanceof List && UtilValidate.isNotEmpty(invokeResult)) {
                entityCollection = new EntityCollection();
                List<Object> resultList = (List<Object>) invokeResult;
                EdmEntityType returnType = (EdmEntityType) uriResourceAction.getAction().getReturnType().getType();
                for (Object item : resultList) {
                    if (item instanceof GenericValue) {
                        OdataOfbizEntity entity = OdataProcessorHelper.genericValueToEntity(delegator, edmProvider,returnType, (GenericValue) item, locale);
                        if (entity != null) {
                            OdataProcessorHelper.appendNonEntityFields(httpServletRequest, delegator, dispatcher, edmProvider, queryOptions, UtilMisc.toList(entity), locale, userLogin);
                        }
                        entityCollection.getEntities().add(entity);
                    } else if (item instanceof Map) {
                        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(returnType.getFullQualifiedName());
                        entityCollection.getEntities().add(Util.mapToEntity(csdlEntityType, (Map<String, Object>) item));
                    }
                }
            } else if (invokeResult instanceof EntityCollection) {
                entityCollection = (EntityCollection) invokeResult;
            }
            if (entityCollection == null) {
                //如果允许为null
                if (uriResourceAction.getAction().getReturnType().isNullable()) {
                    entityCollection = new EntityCollection();
                } else {
                    throw new ODataApplicationException("The return entity collection cannot be null.",
                            HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
                }
            }
            serializeEntityCollection(oDataRequest, oDataResponse, null, uriResourceAction, contentType,
                    expandOption, selectOption, countOption, entityCollection);
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    private void serializeEntityCollection(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                           EdmBindingTarget edmBindingTarget, UriResourceAction uriResourceAction,
                                           ContentType contentType, ExpandOption expandOption,
                                           SelectOption selectOption, CountOption countOption,
                                           EntityCollection entityCollection)
            throws ODataApplicationException {
        //响应时排除二进制数据
        for (Entity entity : entityCollection.getEntities()) {
            entity.getProperties().removeIf(property -> "Edm.Stream".equals(property.getType()));
        }
        EdmEntityType edmEntityType = null;
        if (edmBindingTarget != null) {
            edmEntityType = edmBindingTarget.getEntityType();
        } else if (uriResourceAction != null) {
            edmEntityType = (EdmEntityType) uriResourceAction.getAction().getReturnType().getType();
        }
        SerializerResult serializerResult;
        try {
            String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntityType, expandOption, selectOption);
            ContextURL contextUrl;
            String id = null;
            if (edmBindingTarget != null) {
                contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                        .entitySetOrSingletonOrType(edmBindingTarget.getName())
                        .selectList(selectList).build();
                id = oDataRequest.getRawBaseUri() + "/" + edmBindingTarget.getName();
            } else {
                contextUrl = ContextURL.with().serviceRoot(new URI(oDataRequest.getRawBaseUri() + "/"))
                        .entitySetOrSingletonOrType(edmEntityType.getName())
                        .selectList(selectList).build();
                id = oDataRequest.getRawBaseUri() + "/" + uriResourceAction.getAction().getName();
            }
            URI nextUri = Util.getNextUri(oDataRequest.getRawRequestUri(), entityCollection.getCount());
            // expand and select currently not supported
            EntityCollectionSerializerOptions opts;
            if (id != null) {
                opts = EntityCollectionSerializerOptions.with().id(id).count(countOption)
                        .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            } else {
                opts = EntityCollectionSerializerOptions.with().count(countOption)
                        .contextURL(contextUrl).expand(expandOption).select(selectOption).build();
            }

            ODataSerializer serializer = odata.createSerializer(contentType);
            serializerResult = serializer.entityCollection(serviceMetadata, edmEntityType, entityCollection,
                    opts);
        } catch (SerializerException | URISyntaxException e) {
            e.printStackTrace();
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        InputStream serializedContent = serializerResult.getContent();

        // Finally: configure the response object: set the body, headers and status code
        oDataResponse.setContent(serializedContent);
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

    /**
     * Bound Actions will have the first segment in the resource path to be an
     * entity set.
     * ofbiz目前并不存在有返回GenericValue的service，除了在specialpurpose里的getProductAndPrice，
     * 所以，这个processActionEntity并没有被真正使用，以及完整测试。
     *
     * @param oDataRequest
     * @param oDataResponse
     * @param uriInfo
     * @param requestFormat
     * @param responseFormat
     * @throws ODataApplicationException
     * @throws ODataLibraryException
     */
    @Override
    public void processActionEntity(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                    ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {

        Debug.logInfo("------------------------------------------------------------ in processActionEntity", module);
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        Map<String, QueryOption> queryOptions = getQuernOptions(uriInfo);
        SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
        ExpandOption expandOption = (ExpandOption) queryOptions.get("expandOption");
        final EdmAction edmAction = ((UriResourceAction) resourcePaths.get(resourcePathSize - 1))
                .getAction();
        OFbizEntityActionResult entityResult = null;
        EdmEntitySet boundEntitySet = null;
        EdmEntitySet returnedEntitySet = null;
        try {
            OfbizCsdlAction csdlAction = null;
            List<CsdlAction> csdlActions = edmProvider.getActions(edmAction.getFullQualifiedName());
            if (UtilValidate.isNotEmpty(csdlActions)) {
                csdlAction = (OfbizCsdlAction) csdlActions.get(0);
            }
            //EditAction先检查当前数据是否由其他用户正在编辑
            if (csdlAction != null && csdlAction.isStickySessionEdit()) {
                if (resourcePaths.size() > 1 && otherUsersAreEditing(resourcePaths.get(0))) {
                    throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Editors are not allowed, because someone else is editing.");
                }
            }
            sapContextId = OdataProcessorHelper.processSapContextId(delegator, oDataRequest, oDataResponse, csdlAction, null);
            if (resourcePaths.size() > 1) { // bound action
                UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
                if (boundEntity instanceof UriResourceEntitySet) {
                    UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) boundEntity;
                    boundEntitySet = uriResourceEntitySet.getEntitySet();
                }
            }
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionEntityService", UtilMisc.toMap("request", oDataRequest,
                    "response", oDataResponse, "requestFormat", requestFormat, "resourcePaths", resourcePaths,
                    "queryOptions", queryOptions, "userLogin", userLogin));
            entityResult = (OFbizEntityActionResult) processResult.get("entityResult");

            final EdmEntityType type = (EdmEntityType) edmAction.getReturnType().getType();
            // Return Result...
            if (entityResult == null || entityResult.getEntity() == null) {
                if (edmAction.getReturnType().isNullable()) {
                    oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                } else {
                    // 返回值是必须的, code = 404
                    throw new ODataApplicationException("The action could not be executed. The return entity cannot be null.",
                            HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT, HttpStatusCode.NOT_FOUND.getStatusCode() + "");
                }
            } else {
                final Preferences.Return returnPreference = odata.createPreferences(oDataResponse.getHeaders(HttpHeader.PREFER))
                        .getReturn();
                if (returnPreference == null || returnPreference == Preferences.Return.REPRESENTATION) {
                    if (boundEntitySet != null && type.equals(boundEntitySet.getEntityType())) { // 如果绑定的类型和返回的类型是同一个的话，就假设是自己的set
                        returnedEntitySet = boundEntitySet;
                    } else {
                        if (resourcePaths.size() > 2) {
                            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
                            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                            returnedEntitySet = Util.getNavigationTargetEntitySet(boundEntitySet, edmNavigationProperty);
                        } else {
                            returnedEntitySet = edmAction.getReturnedEntitySet(boundEntitySet);
                        }
                    }
                    oDataResponse.setContent(odata.createSerializer(responseFormat)
                            .entity(serviceMetadata, type, entityResult.getEntity(), EntitySerializerOptions.with()
                                    .contextURL(isODataMetadataNone(responseFormat) ? null
                                            : getContextUrl(returnedEntitySet, type, true))
                                    .expand(expandOption).select(selectOption).build())
                            .getContent());
                    oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                    oDataResponse.setStatusCode(
                            (entityResult.isCreated() ? HttpStatusCode.CREATED : HttpStatusCode.OK).getStatusCode());
                } else {
                    oDataResponse.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                }
                if (returnPreference != null) {
                    oDataResponse.setHeader(HttpHeader.PREFERENCE_APPLIED,
                            PreferencesApplied.with().returnRepresentation(returnPreference).build().toValueString());
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
            }
        } catch (Exception e) {
            e.printStackTrace();
            //如果当前的request本身含有sapContextId, 发生异常也要响应回去
            if (UtilValidate.isNotEmpty(oDataRequest.getHeader("SAP-ContextId")) && sapContextId != null) {
                DataModifyActions.setResponseSessionContext(oDataResponse, sapContextId);
            }
            if (e instanceof ODataApplicationException) throw (ODataApplicationException) e;
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ROOT);
        }
    }

    private boolean otherUsersAreEditing(UriResource uriResource) throws OfbizODataException, GenericEntityException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) uriResource;
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) boundEntity;
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        Map<String, Object> keyMap = Util.uriParametersToMap(uriResourceEntitySet.getKeyPredicates(), edmEntitySet.getEntityType());
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntitySet.getEntityType().getFullQualifiedName());

        List<GenericValue> draftAdminDataList = delegator.findByAnd("DraftAdministrativeData", UtilMisc.toMap(
                "originEntityName", csdlEntityType.getOfbizEntity(), "draftEntityName", csdlEntityType.getDraftEntityName(),
                "entityKeyMap", Util.mapToStr(keyMap)), null, false);

        GenericValue draftAdminData = EntityUtil.getFirst(draftAdminDataList);
        if (UtilValidate.isNotEmpty(draftAdminData)) {
            String createdByUser = draftAdminData.getString("createdByUser");
            //createdByUser不是当前userLogin，说明是其他用户未提交的数据
            if (!createdByUser.equals(userLogin.get("userLoginId"))) {
                return true;
            }
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

    /**
     * 对于有ReturnType的Action
     *
     * @param oDataRequest
     * @param oDataResponse
     * @param uriInfo
     * @param requestFormat
     * @param responseFormat
     * @throws ODataApplicationException
     * @throws ODataLibraryException
     * @date 2019-1-4
     */
    @Override
    public void processActionPrimitive(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                       ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {

        Debug.logInfo("------------------------------------------------------------ in processActionPrimitive", module);
        Property property = null;
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        final EdmAction edmAction = ((UriResourceAction) resourcePaths.get(resourcePathSize - 1))
                .getAction();
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);
        try {
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionPrimitiveService", UtilMisc.toMap("request", oDataRequest,
                    "response", oDataResponse, "requestFormat", requestFormat, "resourcePaths", resourcePaths,"userLogin", userLogin));
            if (processResult.get("property") != null) {
                property = (Property) processResult.get("property");
            }
        } catch (GenericServiceException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        if (property == null) {
            Debug.logError("!!!!!!!!!!!!!!!!!:ODataApplicationException_property:null",module);
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(),
                    Locale.ROOT);
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

    private static Property processImportActionPrimitive(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                                  ContentType requestFormat, List<UriResource> resourcePaths)
            throws ODataApplicationException {
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            return actionProcessor.processImportActionPrimitive(uriResourceAction, actionParameters);
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private static OFbizEntityActionResult processImportActionEntity(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                                              ContentType requestFormat, List<UriResource> resourcePaths,
                                                              Map<String, QueryOption> queryOptions)
            throws ODataApplicationException {
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, queryOptions, null);
        try {
            return actionProcessor.processImportActionEntity(uriResourceAction, actionParameters);
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    /**
     * Process an action which has as return type a complex-type collection.
     *
     * @param oDataRequest        OData request object containing raw HTTP information
     * @param oDataResponse       OData response object for collecting response data
     * @param uriInfo        information about a parsed OData URI
     * @param requestFormat  content type of body sent with request
     * @param contentType requested content type after content negotiation
     * @throws ODataApplicationException if the service implementation encounters a failure
     * @throws ODataLibraryException
     */
    @Override
    public void processActionComplexCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                               ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionComplexCollection", module);
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        final EdmAction edmAction = ((UriResourceAction) resourcePaths.get(resourcePathSize - 1)).getAction();
        Property property = null;
        try {
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionComplexCollectionService", UtilMisc.toMap("request", oDataRequest,
                    "response", oDataResponse, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            if (processResult.get("property") != null) {
                property = (Property) processResult.get("property");
            }
        } catch (GenericServiceException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        try {
            // Serialize the response entity
            final EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType()
                    .getType();
            final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
            final ODataSerializer serializer = odata.createSerializer(contentType);
            final SerializerResult serializerResult = serializer.complexCollection(serviceMetadata, edmComplexType, property,
                    opts);

            // configure the response object
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Property processBoundActionComplexCollection(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                               ContentType requestFormat, List<UriResource> resourcePaths)
            throws ODataApplicationException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        UriResourceNavigation uriResourceNavigation = null;
        // 先只支持2段
        if (resourcePaths.size() > 2) {
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        try {
            return actionProcessor.processBoundActionComplex(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }
    private static Property processImportActionComplexCollection(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                                ContentType requestFormat, List<UriResource> resourcePaths)
            throws ODataApplicationException {
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            return actionProcessor.processImportActionComplex(uriResourceAction, actionParameters);
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    /**
     * Process an action which has as return type a complex type.
     *
     * @param oDataRequest        OData request object containing raw HTTP information
     * @param oDataResponse       OData response object for collecting response data
     * @param uriInfo        information about a parsed OData URI
     * @param requestFormat  content type of body sent with request
     * @param contentType requested content type after content negotiation
     * @throws ODataApplicationException if the service implementation encounters a failure
     * @throws ODataLibraryException
     */
    @Override
    public void processActionComplex(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                     ContentType requestFormat, ContentType contentType)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionComplex", module);
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        int resourcePathSize = resourcePaths.size();
        final EdmAction edmAction = ((UriResourceAction) resourcePaths.get(resourcePathSize - 1)).getAction();
        Property property = null;
        try {
            Map<String, Object> processResult = dispatcher.runSync("dpbird.processActionComplexService", UtilMisc.toMap("request", oDataRequest,
                    "response", oDataResponse, "requestFormat", requestFormat, "resourcePaths", resourcePaths, "userLogin", userLogin));
            if (processResult.get("property") != null) {
                property = (Property) processResult.get("property");
            }
        } catch (GenericServiceException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
        if (property == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        try {
            // Serialize the response entity
            final EdmComplexType edmComplexType = (EdmComplexType) edmAction.getReturnType()
                    .getType();
            final ContextURL contextURL = ContextURL.with().type(edmComplexType).build();
            final ComplexSerializerOptions opts = ComplexSerializerOptions.with().contextURL(contextURL).build();
            final ODataSerializer serializer = odata.createSerializer(contentType);
            final SerializerResult serializerResult = serializer.complex(serviceMetadata, edmComplexType, property,
                    opts);

            // configure the response object
            oDataResponse.setContent(serializerResult.getContent());
            oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
            oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO: 此方法需要重写
    private static Property processImportActionComplex(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                                ContentType requestFormat, List<UriResource> resourcePaths)
            throws ODataApplicationException {
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        try {
            return actionProcessor.processImportActionComplex(uriResourceAction, actionParameters);
        } catch (ODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private static Property processBoundActionComplex(ODataRequest oDataRequest, ODataResponse oDataResponse,
                                               ContentType requestFormat, List<UriResource> resourcePaths)
            throws ODataApplicationException {
        UriResourcePartTyped boundEntity = (UriResourcePartTyped) resourcePaths.get(0);
        UriResourceAction uriResourceAction = getUriResourceAction(resourcePaths);
        final Map<String, Parameter> actionParameters = getActionParameters(oDataRequest, uriResourceAction,
                requestFormat);
        ActionProcessor actionProcessor = getActionProcessor(oDataRequest, oDataResponse, null, null);
        UriResourceNavigation uriResourceNavigation = null;
        // 先只支持2段
        if (resourcePaths.size() > 2) {
            uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        }
        try {
            return actionProcessor.processBoundActionComplex(uriResourceAction, actionParameters, boundEntity, uriResourceNavigation);
        } catch (OfbizODataException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot execute bound action void. " + e.getMessage(),
                    Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
        }
    }

    @Override
    public void processActionPrimitiveCollection(ODataRequest oDataRequest, ODataResponse oDataResponse, UriInfo uriInfo,
                                                 ContentType contentType, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        Debug.logInfo("------------------------------------------------------------ in processActionPrimitiveCollection", module);
        //check If-Match
        checkActionIfMatch(uriInfo, oDataRequest);

        // Get the action from the resource path
        Map<String, Parameter> parameters;
        UriResourceAction uriResourceAction = null;
        if (contentType == null) {
            throw new ODataApplicationException("The content type has not been set in the request.",
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
        }
        final ODataDeserializer deserializer = odata.createDeserializer(contentType);
        final List<UriResource> resourcePaths = uriInfo.asUriInfoResource().getUriResourceParts();
        String sapContextId = oDataRequest.getHeader("SAP-ContextId");
        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest,
                "sapContextId", sapContextId, "locale", locale);
        if (resourcePaths.size() > 1) { // 这事bound action
            uriResourceAction = (UriResourceAction) resourcePaths.get(1);
        } else { // 这是actionImport
            uriResourceAction = (UriResourceAction) resourcePaths.get(0);
        }
        parameters = deserializer.actionParameters(oDataRequest.getBody(), uriResourceAction.getAction()).getActionParameters();
        Map<String, Object> processResult;
        try {
            processResult = dispatcher.runSync("dpbird.processActionPrimitiveCollectionService",
                    UtilMisc.toMap("resourcePaths", resourcePaths, "parameters", parameters, "odataContext", odataContext, "userLogin", userLogin));
        } catch (GenericServiceException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR,                    Locale.ROOT);
        }
        if (processResult.get("property") == null) {
            throw new ODataApplicationException("Nothing found.", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
        Property property = (Property) processResult.get("property");
        // Serialize the response property
        final EdmPrimitiveType edmPrimitiveType = (EdmPrimitiveType) uriResourceAction.getAction().getReturnType().getType();
        final ContextURL contextURL = ContextURL.with().type(edmPrimitiveType).build();
        final PrimitiveSerializerOptions opts = PrimitiveSerializerOptions.with().contextURL(contextURL).build();
        final ODataSerializer serializer = odata.createSerializer(contentType);
        final SerializerResult serializerResult = serializer.primitiveCollection(serviceMetadata, edmPrimitiveType, property, opts);

        // configure the response object
        oDataResponse.setContent(serializerResult.getContent());
        oDataResponse.setStatusCode(HttpStatusCode.OK.getStatusCode());
        oDataResponse.setHeader(HttpHeader.CONTENT_TYPE, contentType.toContentTypeString());
    }

    public static Map<String, Object> processActionVoidService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        ODataRequest request= (ODataRequest) context.get("request");
        ODataResponse response = (ODataResponse) context.get("response");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        if (resourcePaths.size() >= 2) { // bound action
            processBoundActionVoid(request, response, requestFormat, resourcePaths);
        } else {
            processImportActionVoid(request, response, requestFormat, resourcePaths);
        }
        return ServiceUtil.returnSuccess();

    }
    public static Map<String, Object> processActionEntityService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        ODataRequest request= (ODataRequest) context.get("request");
        ODataResponse response = (ODataResponse) context.get("response");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Map<String, QueryOption> queryOptions = (Map<String, QueryOption>) context.get("queryOptions");
        OFbizEntityActionResult entityResult;
        if (resourcePaths.size() > 1) { // bound action
            entityResult = processBoundActionEntity(request, response, requestFormat, resourcePaths, queryOptions);
        } else {
            entityResult = processImportActionEntity(request, response, requestFormat, resourcePaths, queryOptions);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("entityResult", entityResult);
        return resultMap;
    }

    public static Map<String, Object> processActionEntityCollectionService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Map<String, Parameter> parameters = (Map<String, Parameter>) context.get("parameters");
        Map<String, Object> odataContext = (Map<String, Object>) context.get("odataContext");
        Object entityResult;
        UriResourceAction uriResourceAction;
        ActionProcessor ofbizOdataWriter = new ActionProcessor(odataContext, null, null);
        if (resourcePaths.size() > 1) { // BoundAction
            UriResourcePartTyped uriResourcePartTyped = (UriResourcePartTyped) resourcePaths.get(0);
            UriResourceNavigation uriResourceNavigation = null;
            if (resourcePaths.size() > 2) {
                uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
            }
            uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
            entityResult = ofbizOdataWriter.processBoundActionEntityCollection(uriResourceAction,
                    parameters, uriResourcePartTyped, uriResourceNavigation);
        } else {
            // ImportAction
            uriResourceAction = (UriResourceAction) resourcePaths.get(0);
            entityResult = ofbizOdataWriter.processImportActionEntityCollection(uriResourceAction, parameters);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("entityCollectionResult", entityResult);
        return resultMap;
    }

    public static Map<String, Object> processActionPrimitiveService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        ODataRequest request= (ODataRequest) context.get("request");
        ODataResponse response = (ODataResponse) context.get("response");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Property property;
        if (resourcePaths.size() == 2) { // bound action
            property = processBoundActionPrimitive(request, response, requestFormat, resourcePaths);
        } else {
            property = processImportActionPrimitive(request, response, requestFormat, resourcePaths);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("property", property);
        return resultMap;
    }

    public static Map<String, Object> processActionPrimitiveCollectionService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Map<String, Parameter> parameters = (Map<String, Parameter>) context.get("parameters");
        Map<String, Object> odataContext = (Map<String, Object>) context.get("odataContext");
        Property property = null;
        UriResourceAction uriResourceAction = null;
        ActionProcessor ofbizOdataWriter = new ActionProcessor(odataContext, null, null);
        if (resourcePaths.size() > 1) { // 这事bound action
            UriResourcePartTyped uriResourcePartTyped = (UriResourcePartTyped) resourcePaths.get(0);
            if (resourcePaths.get(1) instanceof UriResourceAction) {
                uriResourceAction = (UriResourceAction) resourcePaths.get(1);
                try {
                    String actionName = uriResourceAction.getAction().getName();
                    List<CsdlAction> ofbizCsdlActions = edmProvider.getActions(uriResourceAction.getAction().getFullQualifiedName());
                    if (UtilValidate.isEmpty(ofbizCsdlActions)) {
                        throw new ODataApplicationException("Cannot find Action with name " + actionName, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
                    }
                    UriResourceNavigation uriResourceNavigation = null;
                    if (resourcePaths.size() > 2) { // 先只支持2段
                        uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
                    }
                    property = ofbizOdataWriter.processBoundActionPrimitive(uriResourceAction, parameters,
                            uriResourcePartTyped, uriResourceNavigation);
                } catch (OfbizODataException e) {
                    e.printStackTrace();
                    throw new ODataApplicationException(e.getMessage(),
                            Integer.parseInt(e.getODataErrorCode()), locale, e.getODataErrorCode());
                }
            }
        } else { // 这是actionImport
            uriResourceAction = (UriResourceAction) resourcePaths.get(0);
            try {
                String actionName = uriResourceAction.getAction().getName();
                List<CsdlAction> ofbizCsdlActions = edmProvider.getActions(uriResourceAction.getAction().getFullQualifiedName());
                OfbizCsdlAction ofbizCsdlAction;
                if (UtilValidate.isNotEmpty(ofbizCsdlActions)) {
                    ofbizCsdlAction = (OfbizCsdlAction) ofbizCsdlActions.get(0);
                } else {
                    throw new ODataApplicationException("Cannot find Action with name " + actionName,
                            HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ROOT);
                }
                String ofbizMethod = ofbizCsdlAction.getOfbizMethod();
                String returnName = ofbizCsdlAction.getReturnName();
                property = ofbizOdataWriter.processActionPrimitiveMethod(httpServletRequest, ofbizMethod, parameters);
            } catch (ODataException e) {
                throw new OfbizODataException(String.valueOf(HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode()),e.getMessage());
            }
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("property", property);
        return resultMap;
    }

    public static Map<String, Object> processActionComplexService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        ODataRequest request= (ODataRequest) context.get("request");
        ODataResponse response = (ODataResponse) context.get("response");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Property property;
        if (resourcePaths.size() == 2) { // bound action
            property = processBoundActionComplex(request, response, requestFormat, resourcePaths);
        } else {
            property = processImportActionComplex(request, response, requestFormat, resourcePaths);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("property", property);
        return resultMap;
    }

    public static Map<String, Object> processActionComplexCollectionService(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        ODataRequest request= (ODataRequest) context.get("request");
        ODataResponse response = (ODataResponse) context.get("response");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = (List<UriResource>) context.get("resourcePaths");
        Property property;
        if (resourcePaths.size() == 2) { // bound action
            property = processBoundActionComplexCollection(request, response, requestFormat, resourcePaths);
        } else {
            property = processImportActionComplexCollection(request, response, requestFormat, resourcePaths);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("property", property);
        return resultMap;
    }

    private void checkActionIfMatch(UriInfo uriInfo, ODataRequest oDataRequest) throws ODataApplicationException {
        List<UriResource> uriResourceParts = uriInfo.getUriResourceParts();
        //如果是BoundSetAction，检查If-Match
        if(uriResourceParts.get(0) instanceof UriResourceEntitySet) {
            EdmEntitySet edmEntitySet;
            List<UriParameter> keyPredicates;
            if (uriResourceParts.size() <= 2) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResourceParts.get(0);
                edmEntitySet = uriResourceEntitySet.getEntitySet();
                keyPredicates = uriResourceEntitySet.getKeyPredicates();
                AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, edmEntitySet, keyPredicates);
            } else {
                //TODO: 暂时仅支持两段式
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResourceParts.get(0);
                UriResourceNavigation resourceNavigation = (UriResourceNavigation) uriResourceParts.get(1);
                EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
                edmEntitySet = Util.getNavigationTargetEntitySet(uriResourceEntitySet.getEntitySet(), edmNavigationProperty);
                if (UtilValidate.isNotEmpty(resourceNavigation.getKeyPredicates())) {
                    keyPredicates = resourceNavigation.getKeyPredicates();
                    AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, edmEntitySet, keyPredicates);
                } else {
                    try {
                        Map<String, Object> odataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                                "edmProvider", edmProvider, "userLogin", userLogin, "httpServletRequest", httpServletRequest, "locale", locale);
                        Map<String, Object> edmParams = UtilMisc.toMap("edmBindingTarget", uriResourceEntitySet.getEntitySet(),
                                "edmNavigationProperty", edmNavigationProperty);
                        OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(odataContext, new HashMap<>(), edmParams);
                        Map<String, Object> primaryKey = Util.uriParametersToMap(resourceNavigation.getKeyPredicates(), edmNavigationProperty.getType());
                        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) ofbizOdataReader.getRelatedEntity(primaryKey, resourceNavigation.getProperty(), null);
                        AnnotationCheck.checkIfMatch(delegator, edmProvider, oDataRequest, edmEntitySet, ofbizEntity.getGenericValue());
                    } catch (OfbizODataException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
