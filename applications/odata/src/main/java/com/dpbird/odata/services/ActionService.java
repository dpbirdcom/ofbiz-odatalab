package com.dpbird.odata.services;

import com.dpbird.odata.*;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.edm.OfbizCsdlAction;
import com.dpbird.odata.edm.ParameterContext;
import com.dpbird.odata.processor.DataModifyActions;
import com.dpbird.odata.processor.UriResourceProcessor;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.fop.util.ListUtil;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.FixedFormatDeserializer;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceAction;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import static org.apache.ofbiz.base.util.UtilGenerics.checkList;

/**
 * 使用Service实现Action
 *
 * @date 2022/11/25
 */
public class ActionService {

    public static Map<String, Object> actionVoid(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OData oData = (OData) odataContext.get("oData");
        Locale locale = (Locale) odataContext.get("locale");
        ODataRequest request = (ODataRequest) odataContext.get("oDataRequest");
        Delegator delegator = (Delegator) odataContext.get("delegator");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        ODataResponse response = (ODataResponse) odataContext.get("oDataResponse");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        List<UriResource> resourcePaths = UtilGenerics.checkList(context.get("resourcePaths"));
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        EdmAction edmAction = uriResourceAction.getAction();
        if ("DiscardAction".equals(edmAction.getName())) {
            DataModifyActions.discardAction(odataContext);
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            return ServiceUtil.returnSuccess();
        }
//        Map<String, Object> parameters = getActionParameters(request, uriResourceAction, requestFormat, oData, locale);
        Map<String, Object> parameters = getActionParameters(request, uriResourceAction, requestFormat, oData, locale, httpServletRequest);
        EdmBindingTarget edmBindingTarget = null;
        if (edmAction.isBound()) {
            List<OdataParts> odataParts = addBoundParam(resourcePaths, odataContext, parameters, edmAction, null);
            edmBindingTarget = ListUtil.getLast(odataParts).getEdmBindingTarget();
            odataContext.put("odataParts", odataParts);
        }
        ActionProcessor actionProcessor = new ActionProcessor(odataContext, null, null);
        actionProcessor.processActionVoid(uriResourceAction, parameters, edmBindingTarget);
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> actionEntity(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        OData oData = (OData) odataContext.get("oData");
        Locale locale = (Locale) odataContext.get("locale");
        ODataRequest request = (ODataRequest) odataContext.get("oDataRequest");
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        String sapContextId = (String) context.get("sapContextId");
        ODataResponse response = (ODataResponse) odataContext.get("oDataResponse");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        List<UriResource> resourcePaths = UtilGenerics.checkList(context.get("resourcePaths"));
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        Map<String, QueryOption> queryOptions = UtilGenerics.checkMap(context.get("queryOptions"));
        EdmAction edmAction = uriResourceAction.getAction();
        Map<String, Object> parameters = getActionParameters(request, uriResourceAction, requestFormat, oData, locale, httpServletRequest);
        EdmBindingTarget edmBindingTarget = null;
        EdmEntityType edmEntityType = null;
        if (uriResourceAction.getAction().isBound()) {
            OfbizCsdlAction csdlAction = (OfbizCsdlAction) edmProvider.getActions(edmAction.getFullQualifiedName()).get(0);
            List<OdataParts> odataParts;
            if (csdlAction.isStickySessionEdit()) {
                odataParts = addBoundParam(resourcePaths, odataContext, parameters, edmAction, null);
            } else {
                odataParts = addBoundParam(resourcePaths, odataContext, parameters, edmAction, sapContextId);
            }
            odataContext.put("odataParts", odataParts);
            OdataParts parts = ListUtil.getLast(odataParts);
            edmBindingTarget = parts.getEdmBindingTarget();
            edmEntityType = parts.getEdmEntityType();
        }
        Map<String, Object> edmParams = UtilMisc.toMap("edmEntityType", edmEntityType);
        OFbizEntityActionResult entityResult;
        if (UtilValidate.isNotEmpty(sapContextId)) {
            //draft
            odataContext.put("sapContextId", sapContextId);
            DraftActionProcessor draftActionProcessor = new DraftActionProcessor(queryOptions, odataContext, sapContextId, edmEntityType);
            entityResult = draftActionProcessor.processActionEntity(uriResourceAction, parameters, edmBindingTarget);
        } else {
            ActionProcessor actionProcessor = new ActionProcessor(odataContext, queryOptions, edmParams);
            entityResult = actionProcessor.processActionEntity(uriResourceAction, parameters, edmBindingTarget);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("entityResult", entityResult);
        return resultMap;
    }

    public static Map<String, Object> actionEntityCollection(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        List<UriResource> resourcePaths = UtilGenerics.checkList(context.get("resourcePaths"));
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        Map<String, QueryOption> queryOptions = UtilGenerics.checkMap(context.get("queryOptions"));
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        ODataRequest request = (ODataRequest) odataContext.get("oDataRequest");
        OData oData = (OData) odataContext.get("oData");
        Locale locale = (Locale) odataContext.get("locale");
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        Map<String, Object> parameters = getActionParameters(request, uriResourceAction, requestFormat, oData, locale, httpServletRequest);
        EdmAction edmAction = uriResourceAction.getAction();
        EdmBindingTarget edmBindingTarget = null;
        if (edmAction.isBound()) {
            List<OdataParts> odataParts = addBoundParam(resourcePaths, odataContext, parameters, edmAction, null);
            edmBindingTarget = ListUtil.getLast(odataParts).getEdmBindingTarget();
            odataContext.put("odataParts", odataParts);
        }
        ActionProcessor ofbizOdataWriter = new ActionProcessor(odataContext, queryOptions, null);
        EntityCollection resultCollection = ofbizOdataWriter.processActionEntityCollection(uriResourceAction, parameters, edmBindingTarget);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("entityCollection", resultCollection);
        return resultMap;
    }

    public static Map<String, Object> actionProperty(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException, ODataApplicationException {
        List<UriResource> resourcePaths = UtilGenerics.checkList(context.get("resourcePaths"));
        Map<String, Object> odataContext = UtilGenerics.checkMap(context.get("odataContext"));
        ContentType requestFormat = (ContentType) context.get("requestFormat");
        ODataRequest request = (ODataRequest) odataContext.get("oDataRequest");
        HttpServletRequest httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        OData oData = (OData) odataContext.get("oData");
        Locale locale = (Locale) odataContext.get("locale");
        UriResourceAction uriResourceAction = (UriResourceAction) resourcePaths.get(resourcePaths.size() - 1);
        Map<String, Object> parameters = getActionParameters(request, uriResourceAction, requestFormat, oData, locale, httpServletRequest);
        EdmAction edmAction = uriResourceAction.getAction();
        EdmBindingTarget edmBindingTarget = null;
        if (edmAction.isBound()) {
            List<OdataParts> odataParts = addBoundParam(resourcePaths, odataContext, parameters, edmAction, null);
            edmBindingTarget = ListUtil.getLast(odataParts).getEdmBindingTarget();
            odataContext.put("odataParts", odataParts);
        }
        ActionProcessor ofbizOdataWriter = new ActionProcessor(odataContext, null, null);
        Property property = ofbizOdataWriter.processActionProperty(uriResourceAction, parameters, edmBindingTarget);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("property", property);
        return resultMap;
    }


    /**
     * 获取action参数
     */
    private static Map<String, Object> getActionParameters(ODataRequest oDataRequest, UriResourceAction uriResourceAction,
                                                           ContentType requestFormat, OData oData, Locale locale, HttpServletRequest request)
            throws ODataApplicationException {
        try {
            //上传文件暂时支持单个文件
            if (requestFormat.isCompatible(ContentType.MULTIPART_FORM_DATA)) {
                return getFromDataParameters(request, uriResourceAction);
            }
            final ODataDeserializer deserializer = oData.createDeserializer(requestFormat);
            InputStream body = oDataRequest.getBody();
            if (oDataRequest.getBody().available() == 0) {
                body = new ByteArrayInputStream("{}".getBytes());
            }
            Map<String, Parameter> actionParameters = deserializer.actionParameters(body, uriResourceAction.getAction()).getActionParameters();
            return Util.parametersToMap(actionParameters);
        } catch (DeserializerException | IOException e) {
            e.printStackTrace();
            throw new ODataApplicationException("Cannot parse action parameters",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), locale);
        }
    }

    private static Map<String, Object> getFromDataParameters(HttpServletRequest request, UriResourceAction uriResourceAction) {
        Map<String, Object> formParam = new HashMap<>();
        EdmAction action = uriResourceAction.getAction();
        Map<String, Object> multiPartMap = UtilHttp.getMultiPartParameterMap(request);
        ParameterContext parameterContext = new ParameterContext();
        for (Map.Entry<String, Object> entry : multiPartMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            EdmParameter parameter = action.getParameter(key);
            if (key.startsWith("_") && key.endsWith("size")) {
                parameterContext.setFileSize((Long) value);
                continue;
            }
            if (key.startsWith("_") && key.endsWith("fileName")) {
                parameterContext.setFileName((String) value);
                continue;
            }
            if (key.startsWith("_") && key.endsWith("contentType")) {
                parameterContext.setFileMimeType((String) value);
                continue;
            }
            if (UtilValidate.isNotEmpty(parameter)) {
                if (parameter.getType().getName().contains("Stream")) {
                    parameterContext.setParameterName(key);
                    parameterContext.setFile((ByteBuffer) value);
                } else {
                    formParam.put(key, value);
                }
            }
        }
        Long fileSize = parameterContext.getFileSize();
        if (UtilValidate.isNotEmpty(fileSize) && fileSize > 0) {
            formParam.put(parameterContext.getParameterName(), parameterContext);
        }
        return formParam;
    }


    /**
     * 向action中添加bound参数，返回EdmBindingTarget
     */
    private static List<OdataParts> addBoundParam(List<UriResource> resourcePaths, Map<String, Object> odataContext,
                                            Map<String, Object> parameters, EdmAction edmAction, String sapContextId) throws OfbizODataException, ODataApplicationException {
        UriResourceProcessor uriResourceProcessor = new UriResourceProcessor(odataContext, new HashMap<>(), sapContextId);
        List<OdataParts> resourceDataInfos = uriResourceProcessor.readUriResource(resourcePaths, null);
        OdataParts odataParts = ListUtil.getLast(resourceDataInfos);
        Object entityData = odataParts.getEntityData();
        String boundParamName = edmAction.getParameterNames().get(0);
        Object boundParam = null;
        if (entityData != null) {
            checkActionIfMatch(odataParts, odataContext);
            boolean boundCollection = edmAction.getParameter(boundParamName).isCollection();
            boundParam = boundCollection ? ((EntityCollection) entityData).getEntities() : entityData;
        }
        parameters.put(boundParamName, boundParam);
        return resourceDataInfos;
    }

    private static void checkActionIfMatch(OdataParts odataParts, Map<String, Object> odataContext) throws ODataApplicationException {
        EdmBindingTarget edmBindingTarget = odataParts.getEdmBindingTarget();
        if (edmBindingTarget instanceof EdmEntitySet) {
            EdmEntitySet edmEntitySet = (EdmEntitySet) edmBindingTarget;
            Object entityData = odataParts.getEntityData();
            OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
            ODataRequest request = (ODataRequest) odataContext.get("oDataRequest");
            if (entityData instanceof Entity) {
                OdataOfbizEntity entity = (OdataOfbizEntity) entityData;
                AnnotationCheck.checkIfMatch(edmProvider, request, entity, edmEntitySet);
            }
        }
    }

}
