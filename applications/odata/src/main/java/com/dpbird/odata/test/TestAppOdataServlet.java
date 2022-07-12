package com.dpbird.odata.test;


import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.edmx.EdmxReferenceInclude;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;

import com.dpbird.odata.processor.CustomDefaultProcessor;
import com.dpbird.odata.processor.OfbizActionProcessor;
import com.dpbird.odata.processor.OfbizBatchProcessor;
import com.dpbird.odata.processor.OfbizComplexProcessor;
import com.dpbird.odata.processor.OfbizEntityCollectionProcessor;
import com.dpbird.odata.processor.OfbizEntityProcessor;
import com.dpbird.odata.processor.OfbizPrimitiveCollectionProcessor;
import com.dpbird.odata.processor.OfbizPrimitiveProcessor;


public class TestAppOdataServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    public static final String module = TestAppOdataServlet.class.getName();
    public static final String resource = "odataUiLabels";

    @Override
    public void init() throws ServletException {
        Debug.log("----------------+++++++++++++++++++++++++++++========================= init OpenuiOdataServlet", module);
    }

    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        Delegator delegator = (Delegator) getServletContext().getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) getServletContext().getAttribute("dispatcher");
        GenericValue userLogin = null;
        String odataApp = req.getParameter("app");
        String reloadStr = req.getParameter("reload");
        try {
            userLogin = delegator.findOne("UserLogin", false, UtilMisc.toMap("userLoginId", "system"));
            Locale locale = UtilHttp.getLocale(req);
            ServletContext servletCtx = (ServletContext) req.getAttribute("servletContext");
            String componentName =  Util.getRequestComponentName(req);
            String componentPath =Util.getRequestComponentPath(req,componentName);

            // create odata handler and configure it with CsdlEdmProvider and Processor
            String servletPath = req.getServletPath();
            Debug.logInfo("------------------------------------ request servletPath = " + servletPath, module);

            if (odataApp == null) { // 对老openui5应用的兼容，那时候的app是放在路径里的
                odataApp = servletPath.substring(servletPath.indexOf('/') + 1, servletPath.indexOf('/', 1));
            }
            Debug.logInfo("------------------------------------ odataApp = " + odataApp, module);
            Debug.logInfo("------------------------------------ componentName = " + componentName, module);
            boolean reload = false; // always reload metadata from xml file and database
            if ("true".equals(reloadStr)) {
                reload = true;
            }

//        InputStream edmConfigInputStream = getServletContext().getResourceAsStream("/WEB-INF/edmconfig/" + odataApp + "EdmConfig.xml");
//		List<InputStream> edmReferenceInputStreams = new ArrayList<InputStream>();
//		Map<String, InputStream> edmReferenceInputStreamMap = new HashMap<String, InputStream>();
            /**
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/Org.Odata.Validation.V1.xml"));
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/Communication.xml"));
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/Org.Odata.Core.V1.xml"));
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/Capabilities.xml"));
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/Common.xml"));
             edmReferenceInputStreams.add(getServletContext().getResourceAsStream("/vocabularies/UI.xml"));
             **/
//		edmReferenceInputStreamMap.put("Core", getServletContext().getResourceAsStream("/vocabularies/Core.xml"));
//		edmReferenceInputStreamMap.put("Capabilities", getServletContext().getResourceAsStream("/vocabularies/Capabilities.xml"));
//		edmReferenceInputStreamMap.put("Common", getServletContext().getResourceAsStream("/vocabularies/Common.xml"));
//		edmReferenceInputStreamMap.put("UI", getServletContext().getResourceAsStream("/vocabularies/UI.xml"));
            OData odata = OData.newInstance();
            OfbizAppEdmProvider edmProvider =
                    new OfbizAppEdmProvider(delegator, dispatcher, odataApp, reload, userLogin, locale,componentName,componentPath);

            List<EdmxReference> edmxReferences = new ArrayList<EdmxReference>();

            EdmxReference edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/vocabularies/Org.OData.Measures.V1.xml"));
            EdmxReferenceInclude referenceInclude = new EdmxReferenceInclude("Org.OData.Measures.V1", "Measures");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);
            /**
             edmxReference = new EdmxReference(URI.create("https://sap.github.io/odata-vocabularies/vocabularies/UI.xml"));
             referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.UI.v1", "UI");
             edmxReference.addInclude(referenceInclude);
             referenceInclude = new EdmxReferenceInclude("Org.OData.Validation.V1", "Validation");
             edmxReference.addInclude(referenceInclude);
             referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.Communication.v1", "Communication");
             edmxReference.addInclude(referenceInclude);

             edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata-data-aggregation-ext/v4.0/cs02/vocabularies/Org.OData.Aggregation.V1.xml"));
             referenceInclude = new EdmxReferenceInclude("Org.OData.Aggregation.V1", "Aggregation");
             edmxReference.addInclude(referenceInclude);
             **/
            edmxReference = new EdmxReference(URI.create("/o3/vocabularies/Core.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Core.V1", "Core");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/o3/vocabularies/Capabilities.xml"));
            referenceInclude = new EdmxReferenceInclude("Org.OData.Capabilities.V1", "Capabilities");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/o3/vocabularies/Common.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.Common.v1", "Common");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            edmxReference = new EdmxReference(URI.create("/o3/vocabularies/UI.xml"));
            referenceInclude = new EdmxReferenceInclude("com.sap.vocabularies.UI.v1", "UI");
            edmxReference.addInclude(referenceInclude);
            edmxReferences.add(edmxReference);

            ServiceMetadata edm = odata.createServiceMetadata(edmProvider, edmxReferences);

            ODataHttpHandler handler = odata.createHandler(edm);
            handler.register(new CustomDefaultProcessor());
            handler.register(new OfbizEntityCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizEntityProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizActionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizBatchProcessor());
            handler.register(new OfbizPrimitiveProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizPrimitiveCollectionProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
            handler.register(new OfbizComplexProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));

            // let the handler do the work
            handler.process(req, resp);
        } catch (GenericEntityException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
