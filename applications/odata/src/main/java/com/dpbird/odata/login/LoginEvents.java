package com.dpbird.odata.login;

import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.ofbiz.webapp.webdav.WebDavUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhuwenchao
 * externalLoginKey login
 */
public class LoginEvents {
    public static final String module = LoginEvents.class.getName();
    private static Map<String, GenericValue> externalLoginKeys = new ConcurrentHashMap<String, GenericValue>();

    public static String login(HttpServletRequest request, HttpServletResponse response) {
        String loginResult = LoginWorker.login(request, response);
        if ("success".equals(loginResult)) {
            // fix for upgrade，looks like nothing should do here
            // getExternalLoginKey(request);
        }
        return loginResult;
    }

    public static String logInUser(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
        GenericValue userLogin;
        Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
        HttpSession httpSession = request.getSession(true);
        if (serviceMap == null) {
            userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            request.setAttribute("userLogin", userLogin);
            return "success";
        }
        serviceMap.put("locale", UtilHttp.getLocale(request));
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Map<String, Object> result = dispatcher.runSync("userLogin", serviceMap);
        if (ServiceUtil.isError(result) || ServiceUtil.isFailure(result)) {
            return "error";
        }
        userLogin = (GenericValue) result.get("userLogin");
        request.setAttribute("userLogin", userLogin);
        httpSession.setAttribute("userLogin", userLogin);
        return "success";
    }

    public static String extensionCheckLogin(HttpServletRequest request, HttpServletResponse response) {
        return checkLogin(request, response);
    }

    public static String checkLogin(HttpServletRequest request, HttpServletResponse response) {
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        if (userLogin == null) {
            response.setStatus(401);
            return "error";
        }
        return "success";
    }

}
