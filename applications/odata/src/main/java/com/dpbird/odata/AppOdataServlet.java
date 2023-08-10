package  com.dpbird.odata;


import com.dpbird.odata.processor.*;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
//import org.apache.ofbiz.marketing.tracking.TrackingCodeEvents;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceContainer;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.stats.ServerHitBin;
import org.apache.ofbiz.webapp.stats.VisitHandler;
import org.apache.ofbiz.webapp.webdav.WebDavUtil;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.edmx.EdmxReferenceInclude;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class AppOdataServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	public static final String module = AppOdataServlet.class.getName();
	public static final String resource = "odataUiLabels";
	public static final String resourceError = "odataErrorUiLabels";
	@Override
	public void init() throws ServletException {
		Debug.log("----------------+++++++++++++++++++++++++++++========================= init OpenuiOdataServlet", module);
	}

	protected void service(final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
		try {
			logInUser(req, resp);
		} catch (GenericServiceException | GenericEntityException e1) {
			e1.printStackTrace();
			throw new ServletException("Cannot authorize user!");
		}
		long requestStartTime = System.currentTimeMillis();
		LocalDispatcher dispatcher = (LocalDispatcher) req.getAttribute("dispatcher");
		Delegator delegator = (Delegator) req.getAttribute("delegator");
		HttpSession session = req.getSession();
		String componentName =  Util.getRequestComponentName(req);
		String componentPath =Util.getRequestComponentPath(req,componentName);
		if (delegator == null) {
			String delegatorName = (String) session.getAttribute("delegatorName");
			if (UtilValidate.isNotEmpty(delegatorName)) {
				delegator = DelegatorFactory.getDelegator(delegatorName);
			}
			dispatcher = ServiceContainer.getLocalDispatcher(delegator.getDelegatorName(), delegator);
			if (UtilValidate.isEmpty(delegator) || UtilValidate.isEmpty(dispatcher)) {
				Debug.logError("----------------+++++++++++++++++++++++++++++========================= delegator dispatcher isEmpty", module);
				//直接返回 不做处理 zhuwenchao
				return;
			}
			Debug.logInfo("----------------+++++++++++++++++++++++++++++========================= delegatorName: " + delegatorName + " dispatcher:" + dispatcher.getName(), module);
		}
		GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
		String odataApp = req.getParameter("app");
		String reloadStr = req.getParameter("reload");

		Locale locale = UtilHttp.getLocale(req);

		resp.setContentType("text/xml; charset=utf-8");
		resp.setCharacterEncoding("utf-8");

		if (null == userLogin) {
			Debug.log("----------------+++++++++++++++++++++++++++++========================= userLogin not found in session", module);
			Writer out;
			resp.setStatus(401);
			out = resp.getWriter();
			try {
				out.write("{\"message\":" +
						UtilProperties.getMessage(resourceError, "WorkEffortInterestingSure", locale) + "\"}");
				out.flush();
			} catch (IOException e) {
				Debug.logError(e, module);
			}
			return;
		}
		//处理trackingCode的方法,不处理任何返回数据，反正都要调用
//		TrackingCodeEvents.checkTrackingCodeUrlParam(req, resp);

		// create odata handler and configure it with CsdlEdmProvider and Processor
		String servletPath = req.getServletPath();
		Debug.logInfo("------------------------------------ request servletPath = " + servletPath, module);

		if (odataApp == null) { // 对老openui5应用的兼容，那时候的app是放在路径里的
			odataApp = servletPath.substring(servletPath.indexOf('/') + 1, servletPath.indexOf('/', 1));
		}
		Debug.logInfo("------------------------------------ odataApp = " + odataApp, module);
		boolean reload = false;
		if ("true".equals(reloadStr)) {
			reload = true;
		}

		/**
		 InputStream edmConfigInputStream = getServletContext().getResourceAsStream("/WEB-INF/edmconfig/" + odataApp + "EdmConfig.xml");
		 List<InputStream> edmReferenceInputStreams = new ArrayList<InputStream>();
		 Map<String, InputStream> edmReferenceInputStreamMap = new HashMap<String, InputStream>();
		 edmReferenceInputStreamMap.put("Core", getServletContext().getResourceAsStream("/vocabularies/Core.xml"));
		 edmReferenceInputStreamMap.put("Capabilities", getServletContext().getResourceAsStream("/vocabularies/Capabilities.xml"));
		 edmReferenceInputStreamMap.put("Common", getServletContext().getResourceAsStream("/vocabularies/Common.xml"));
		 edmReferenceInputStreamMap.put("UI", getServletContext().getResourceAsStream("/vocabularies/UI.xml"));
		 **/
		OData odata = OData.newInstance();
		OfbizAppEdmProvider edmProvider = null;
		try {
			edmProvider = new OfbizAppEdmProvider(delegator, dispatcher, odataApp, reload, userLogin,
					locale,componentName,componentPath);
		} catch (OfbizODataException e) {
			e.printStackTrace();
		}

		List<EdmxReference> edmxReferences = new ArrayList<EdmxReference>();

		EdmxReference edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/vocabularies/Org.OData.Measures.V1.xml"));
		EdmxReferenceInclude referenceInclude = new EdmxReferenceInclude("Org.OData.Measures.V1", "Measures");
		edmxReference.addInclude(referenceInclude);
		edmxReferences.add(edmxReference);
		/**
		 edmxReference = new EdmxReference(URI.create("http://docs.oasis-open.org/odata/odata/v4.0/errata03/os/complete/vocabularies/Org.OData.Core.V1.xml"));
		 referenceInclude = new EdmxReferenceInclude("Org.OData.Core.V1", "Core");
		 edmxReference.addInclude(referenceInclude);
		 edmxReferences.add(edmxReference);
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
		handler.register(new OfbizReferenceProcessor(req, delegator, dispatcher, edmProvider, userLogin, locale));
		// let the handler do the work
		handler.process(req, resp);
		// workaraound if we are in the root webapp
		String webappName = UtilHttp.getApplicationName(req);
		String rname = "";
		if (req.getPathInfo() != null) {
			rname = req.getPathInfo().substring(1);
		}
		if (rname.indexOf('/') > 0) {
			rname = rname.substring(0, rname.indexOf('/'));
		}
		//RequestHandler.getRequestHandler(getServletContext());
		ServerHitBin.countRequest(webappName + "." + rname, req, requestStartTime, System.currentTimeMillis() - requestStartTime, userLogin);
	}

	private static void logInUser(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
		Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
		if (serviceMap == null) {
			return;
		}
		serviceMap.put("locale", UtilHttp.getLocale(request));
		GenericValue userLogin;
		HttpSession session = request.getSession();
		LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
		Map<String, Object> result = dispatcher.runSync("userLogin", serviceMap);
		if (ServiceUtil.isError(result) || ServiceUtil.isFailure(result)) {
			return;
		}
		userLogin = (GenericValue) result.get("userLogin");
		request.setAttribute("userLogin", userLogin);
		session.setAttribute("userLogin", userLogin);
		VisitHandler.getVisitor(request, response);
		GenericValue person = userLogin.getRelatedOne("Person", false);
		if (person != null) {
			request.setAttribute("person", person);
		} else {
			GenericValue partyGroup = userLogin.getRelatedOne("PartyGroup", false);
			if (partyGroup != null) {
				request.setAttribute("partyGroup", partyGroup);
			}
		}
	}

}
