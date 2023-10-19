package com.dpbird.odata;

import com.dpbird.odata.processor.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.edmx.EdmxReferenceInclude;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.PushBuilder;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppOdataEvents {
    public static final String module = AppOdataEvents.class.getName();

    public static String odataSvc(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LocalDispatcher dispatcher = (LocalDispatcher) req.getAttribute("dispatcher");
        final Delegator delegator = (Delegator) req.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) req.getAttribute("userLogin");
        Map<String, Object> ctx = UtilHttp.getParameterMap(req);
        String odataApp = req.getParameter("app");
        boolean isAppParam = true;
        String reloadStr = req.getParameter("reload");
        try {
            Locale locale = Util.getLocale(req);

            // create odata handler and configure it with CsdlEdmProvider and Processor
            String servletPath = req.getServletPath();
            Debug.logInfo("------------------------------------ request servletPath = " + servletPath, module);

            ServletContext servletCtx = (ServletContext) req.getAttribute("servletContext");
            String componentName =  Util.getRequestComponentName(req);
            String componentPath =Util.getRequestComponentPath(req,componentName);

            if (UtilValidate.isEmpty(odataApp)) {
                String pathInfo = req.getPathInfo();
                List<String> pathSegments = StringUtil.split(pathInfo, "/");
                if (pathSegments.size() < 2) {
                    return "error";
                }
                odataApp = pathSegments.get(1);
                isAppParam = false;
            }
            Debug.logInfo("------------------------------------ odataApp = " + odataApp, module);
            Debug.logInfo("------------------------------------ componentName = " + componentName, module);
            if (!hasOdataPermission(req, odataApp)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                try(ServletOutputStream outputStream = resp.getOutputStream()) {
                    outputStream.print("The current user does not have access");
                }
                return "error";
            }

            boolean reload = false; // always reload metadata from xml file and database
            if ("true".equals(reloadStr)) {
                reload = true;
            }
            OData odata = OData.newInstance();
            OfbizAppEdmProvider edmProvider =
                    new OfbizAppEdmProvider(delegator, dispatcher, odataApp, reload, userLogin, locale,componentName,componentPath);

            List<EdmxReference> edmxReferences = new ArrayList<EdmxReference>();

            EdmxReference edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/vocabularies/Org.OData.Measures.V1.xml"));
            EdmxReferenceInclude referenceInclude = new EdmxReferenceInclude("Org.OData.Measures.V1", "Measures");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Core.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Core.V1", "Core");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Capabilities.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Capabilities.V1", "Capabilities");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Common.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.Common.v1", "Common");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/UI.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.UI.v1", "UI");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            ServiceMetadataETagSupport eTagSupport = new ETagSupportImpl(edmProvider.getETag());
            ServiceMetadata edm = odata.createServiceMetadata(edmProvider, edmxReferences, eTagSupport);

            ODataHttpHandler handler = odata.createHandler(edm);
            handler.register(new CustomDefaultProcessor());
            handler.register(new OfbizEntityCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizEntityProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizActionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizBatchProcessor());
            handler.register(new OfbizPrimitiveProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizPrimitiveCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizComplexProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizReferenceProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            // let the handler do the work
            if (isAppParam) {
                handler.setSplit(1);
            } else {
                handler.setSplit(2);
            }
            handler.process(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
        return "success";
    }

    public static String odataAppSvc(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LocalDispatcher dispatcher = (LocalDispatcher) req.getAttribute("dispatcher");
        final Delegator delegator = (Delegator) req.getAttribute("delegator");
        ServletContext servletCtx = (ServletContext) req.getAttribute("servletContext");
        String componentName =  Util.getRequestComponentName(req);
        String componentPath =Util.getRequestComponentPath(req,componentName);
        GenericValue userLogin = (GenericValue) req.getAttribute("userLogin");
        String propertyValue = EntityUtilProperties.getPropertyValue("odata.properties", "service.WorkEffort.create", delegator);
        if (userLogin == null) {
            try {
                userLogin = delegator.findOne("UserLogin", true, UtilMisc.toMap("userLoginId", "admin"));
                req.setAttribute("userLogin", userLogin);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                return "error";
            }
        }
        String reloadStr = req.getParameter("reload");
        try {
            Locale locale = Util.getLocale(req);
            String pathInfo = req.getPathInfo();
            List<String> pathSegments = StringUtil.split(pathInfo, "/");
            if (pathSegments.size() < 2) {
                return "error";
            }
            String odataApp = pathSegments.get(1);
            Debug.logInfo("------------------------------------ odataApp = " + odataApp, module);
            boolean reload = false; // always reload metadata from xml file and database
            if ("true".equals(reloadStr)) {
                reload = true;
            }
            OData odata = OData.newInstance();
            OfbizAppEdmProvider edmProvider =
                    new OfbizAppEdmProvider(delegator, dispatcher, odataApp, reload, userLogin, locale,componentName,componentPath);

            List<EdmxReference> edmxReferences = new ArrayList<EdmxReference>();

            EdmxReference edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/vocabularies/Org.OData.Measures.V1.xml"));
            EdmxReferenceInclude referenceInclude = new EdmxReferenceInclude("Org.OData.Measures.V1", "Measures");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);
            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Core.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Core.V1", "Core");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Capabilities.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Capabilities.V1", "Capabilities");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Common.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.Common.v1", "Common");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/UI.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.UI.v1", "UI");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Aggregation.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Aggregation.V1", "Aggregation");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/odata/vocabularies/Session.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.Session.v1", "Session");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            ServiceMetadataETagSupport eTagSupport = new ETagSupportImpl(edmProvider.getETag());
            ServiceMetadata edm = odata.createServiceMetadata(edmProvider, edmxReferences, eTagSupport);

            ODataHttpHandler handler = odata.createHandler(edm);
            handler.register(new CustomDefaultProcessor());
            handler.register(new OfbizEntityCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizEntityProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizActionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizBatchProcessor());
            handler.register(new OfbizPrimitiveProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizPrimitiveCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizComplexProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizReferenceProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));

            // let the handler do the work
            handler.setSplit(2);
            handler.process(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
        return "success";
    }

    public static String getcsrftoken(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        response.addHeader("X-CSRF-Token", "abcd1234");
        return "success";
    }

    /**
     * 检查当前用户是否有权限访问当前的Edm
     */
    private static boolean hasOdataPermission(HttpServletRequest request, String odataApp) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        //访问当前edm需要的权限
        GenericValue edmService = EntityQuery.use(delegator).from("EdmService")
                .where("serviceName", odataApp).queryFirst();
        if (UtilValidate.isEmpty(edmService)) {
            return true;
        }
        List<String> requiredPermissions = EntityQuery.use(delegator).from("EdmServicePermission")
                .where(edmService.getPrimaryKey()).getFieldList("permissionId");
        if (UtilValidate.isEmpty(requiredPermissions)) {
            //不需要权限
            return true;
        }
        Security security = (Security) request.getAttribute("security");
        for (String permission : requiredPermissions) {
            if (!security.hasPermission(permission, userLogin)) {
                return false;
            }
        }
        return true;
    }

    public static String changes(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        try {
            JSON json = JSON.from(request.getInputStream());
            JSONConverters.JSONToList jsonToList = new JSONConverters.JSONToList();
            List<Object> objectList = jsonToList.convert(json);
            for (Object variantFileObj : objectList) {
                Map variantFileMap = (Map) variantFileObj;
                GenericValue variantFile = delegator.findOne("VariantFile",
                        UtilMisc.toMap("fileName", variantFileMap.get("fileName"),
                                "userLoginId", userLogin.getString("userLoginId")),
                        false);
                if (variantFile == null) {
                    variantFile = delegator.makeValue("VariantFile");
                    variantFile.set("fileName", variantFileMap.get("fileName"));
                    variantFile.set("userLoginId", userLogin.getString("userLoginId"));
                    variantFile.create();
                }
                variantFile.setAllFields(variantFileMap, false, null, false);
                JSONConverters.MapToJSON mapToJSON = new JSONConverters.MapToJSON();
                JSON fileJSON = mapToJSON.convert(variantFileMap);
                variantFile.set("variantData", fileJSON.toString());
                variantFile.store();
            }
            Debug.logInfo("done with convert", module);
        } catch (IOException | ConversionException | GenericEntityException e) {
            e.printStackTrace();
        }

        return "success";
    }

    public static String flex(HttpServletRequest request, HttpServletResponse response)
            throws IOException, GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        if (userLogin == null) {
            userLogin = (GenericValue) request.getSession(false).getAttribute("userLogin");
        }
        String pathInfo = request.getPathInfo();
        String reference = pathInfo.substring(pathInfo.lastIndexOf("/") + 1);
        JSONObject jsonObject = new JSONObject();
        JSONObject loadModules = JSONObject.fromObject("{\"loadModules\":false}");
        JSONObject changes = JSONObject.fromObject("{\"changes\":[]}");
        JSONObject contexts = JSONObject.fromObject("{\"contexts\":[]}");
        JSONObject ui2personalization = JSONObject.fromObject("{\"ui2personalization\":[]}");
        JSONObject settings = JSONObject.fromObject("{\"settings\":[]}");
        jsonObject.putAll(loadModules);
        jsonObject.putAll(changes);
        jsonObject.putAll(contexts);
        addVariantSection(delegator, jsonObject, reference, userLogin);
        jsonObject.putAll(ui2personalization);
        jsonObject.putAll(settings);
        JSONArray jsonArray = jsonObject.getJSONArray("changes");
        addChanges(delegator, jsonArray, reference, userLogin);
        response.setContentType("application/json");
        IOUtils.write(jsonObject.toString(), response.getOutputStream());
        return "success";
    }

    private static void addVariantSection(Delegator delegator, JSONObject jsonObject,
                                          String reference, GenericValue userLogin) throws GenericEntityException {
        final String variantSectionKey = "variantSection";
        List<GenericValue> ctrlVariants = delegator.findByAnd("VariantFile",
                UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId"),
                        "fileType", "ctrl_variant", "reference", reference),
                null, false);
        if (ctrlVariants == null) {
            jsonObject.put(variantSectionKey, "{}");
            return;
        }
        String variantReference = ctrlVariants.get(0).getString("variantReference");
        JSONObject variantSection = new JSONObject();
        JSONObject variants = JSONObject.fromObject("{\"variants\":[]}");
        JSONArray jsonArray = variants.getJSONArray("variants");
        for (GenericValue ctrVariant : ctrlVariants) {
            jsonArray.add(ctrVariant.getString("variantData"));
        }
        variantSection.put(variantReference, variants);
        jsonObject.put(variantSectionKey, variantSection);
    }

    private static void addChanges(Delegator delegator, JSONArray jsonArray, String reference, GenericValue userLogin)
            throws GenericEntityException {
        List<GenericValue> changes = delegator.findByAnd("VariantFile",
                UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId"),
                        "fileType", "change", "reference", reference),
                null, false);
        for (GenericValue change : changes) {
            jsonArray.add(change.getString("variantData"));
        }
    }

    public static String annotations(HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        final Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        Map<String, Object> ctx = UtilHttp.getParameterMap(request);
        String odataApp = request.getParameter("app");
        String reloadStr = request.getParameter("reload");

        try {
            Locale locale = UtilHttp.getLocale(request);

            // create odata handler and configure it with CsdlEdmProvider and Processor
            String servletPath = request.getServletPath();
            Debug.logInfo("------------------------------------ request servletPath = " + servletPath, module);
            ServletContext servletCtx = (ServletContext) request.getAttribute("servletContext");
            String componentName =  Util.getRequestComponentName(request);
            String componentPath =Util.getRequestComponentPath(request,componentName);


            Debug.logInfo("------------------------------------ odataApp = " + odataApp, module);
            Debug.logInfo("------------------------------------ componentName = " + componentName, module);
            boolean reload = false; // always reload metadata from xml file and database
            if ("true".equals(reloadStr)) {
                reload = true;
            }
            // InputStream edmConfigInputStream = getFileInputStream(odataApp + "EdmConfig.xml");
            OData odata = OData.newInstance();
            OfbizAppEdmProvider edmProvider =
                    new OfbizAppEdmProvider(delegator, dispatcher, odataApp, reload, userLogin, locale,componentName,componentPath);


            ServiceMetadataETagSupport eTagSupport = new ETagSupportImpl(edmProvider.getETag());
            ServiceMetadata serviceMetadata = odata.createServiceMetadata(edmProvider, null, eTagSupport);

//            boolean isNotModified = false;
            // Send the correct response
            // HTTP HEAD requires no payload but a 200 OK response
            if ("HEAD" == request.getMethod()) {
                response.setStatus(HttpStatusCode.OK.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "success";
    }

    public static String testStream(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        PushBuilder pushBuilder = request.newPushBuilder();
        if (pushBuilder != null) {
            // 发送首次响应，通知客户端建立持久连接
            pushBuilder.path("/stream").push();
        }
        try (PrintWriter writer = response.getWriter()) {
            // 模拟每秒发送一次响应
            for (int i = 0; i < 30; i++) {
                String message = "当前时间是: " + UtilDateTime.nowTimestamp() + "\n\n";
                writer.write(message);
                writer.flush();
                // 等待1秒
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Debug.log(">>>>>>>>>>>>>>>>>>>>>>> ok ");
        return "success";
    }


}
