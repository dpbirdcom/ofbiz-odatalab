package com.dpbird.odata;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class LrepEvents {
    public static final String module = LrepEvents.class.getName();

    public static String getcsrftoken(HttpServletRequest request, HttpServletResponse response) {
        String csrfToken = CsrfTokenManager.getCsrfToken(request);
        response.addHeader("X-CSRF-Token", csrfToken);
        return "success";
    }

    public static String changes(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        try {
            JSON json = JSON.from(request.getInputStream());
//            JSONConverters converters = new JSONConverters();
//            converters.loadConverters();
            JSONConverters.JSONToList jsonToList = new JSONConverters.JSONToList();
            List<Object> objectList = jsonToList.convert(json);
            for (Object variantFileObj:objectList) {
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
            response.setContentType("application/json");
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(json.toString().getBytes(StandardCharsets.UTF_8));
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
        IOUtils.write(jsonObject.toString(), response.getOutputStream(), StandardCharsets.UTF_8);
        return "success";
    }

    private static void addVariantSection(Delegator delegator, JSONObject jsonObject,
                                          String reference, GenericValue userLogin) throws GenericEntityException {
        final String variantSectionKey = "variantSection";
        List<GenericValue> ctrlVariants = delegator.findByAnd("VariantFile",
                UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId"),
                        "fileType", "ctrl_variant", "reference", reference),
                null, false);
        if (UtilValidate.isEmpty(ctrlVariants)) {
            jsonObject.put(variantSectionKey, "{}");
            return;
        }
        String variantReference = ctrlVariants.get(0).getString("variantReference");
        JSONObject variantSection = new JSONObject();
        JSONObject variants = JSONObject.fromObject("{\"variants\":[]}");
//        JSONArray jsonArray = variants.getJSONArray("variants");
//        for (GenericValue ctrVariant:ctrlVariants) {
//            jsonArray.add(ctrVariant.getString("variantData"));
//        }
        variantSection.put(variantReference, variants);
        jsonObject.put(variantSectionKey, variantSection);
    }

    private static void addChanges(Delegator delegator, JSONArray jsonArray, String reference, GenericValue userLogin)
            throws GenericEntityException {
//        List<GenericValue> changes = delegator.findByAnd("VariantFile",
//                UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId"),
//                        "fileType", "change", "reference", reference),
//                null, false);
        List<GenericValue> changes = delegator.findByAnd("VariantFile",
                UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId"),
                        "reference", reference),
                null, false);
        for (GenericValue change:changes) {
            jsonArray.add(change.getString("variantData"));
        }
    }

}
