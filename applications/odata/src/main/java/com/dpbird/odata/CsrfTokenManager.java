package com.dpbird.odata;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.webapp.control.LoginWorker;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CsrfTokenManager {
    private static final String module = CsrfTokenManager.class.getName();
    private static final String CSRF_TOKEN = "X-CSRF-Token";
    // This Map is keyed by the randomly generated externalLoginKey and the value is a UserLogin GenericValue object
    private static final Map<String, GenericValue> csrfTokens = new ConcurrentHashMap<>();

    /**
     * Gets (and creates if necessary) an authentication token to be used for an external login parameter.
     * When a new token is created, it is persisted in the web session and in the web request and map entry keyed by the
     * token and valued by a userLogin object is added to a map that is looked up for subsequent requests.
     *
     * @param request - the http request in which the authentication token is searched and stored
     * @return the authentication token as persisted in the session and request objects
     */
    public static String getCsrfToken(HttpServletRequest request) {
        String csrfToken = (String) request.getAttribute(CSRF_TOKEN);
        if (csrfToken != null) return csrfToken;

        HttpSession session = request.getSession();
        synchronized (session) {
            // if the session has a previous key in place, remove it from the master list
            String sesExtKey = (String) session.getAttribute(CSRF_TOKEN);

            if (sesExtKey != null) {
//                if (isAjax(request)) return sesExtKey;
                csrfTokens.remove(sesExtKey);
            }

            GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
            //check the userLogin here, after the old session setting is set so that it will always be cleared
            if (userLogin == null) return "";

            //no key made yet for this request, create one
            while (csrfToken == null || csrfTokens.containsKey(csrfToken)) {
                UUID uuid = UUID.randomUUID();
                csrfToken = "EL" + uuid.toString();
            }

            request.setAttribute(CSRF_TOKEN, csrfToken);
            session.setAttribute(CSRF_TOKEN, csrfToken);
            csrfTokens.put(csrfToken, userLogin);
            return csrfToken;
        }
    }

    /**
     * Removes the authentication token, if any, from the session.
     *
     * @param session - the http session from which the authentication token is removed
     */
    static void cleanupCsrfToken(HttpSession session) {
        String sesExtKey = (String) session.getAttribute(CSRF_TOKEN);
        if (sesExtKey != null) {
            csrfTokens.remove(sesExtKey);
        }
    }

    /**
     * OFBiz controller event that performs the user authentication using the authentication token.
     * The methods is designed to be used in a chain of controller preprocessor event: it always return &amp;success&amp;
     * even when the authentication token is missing or the authentication fails in order to move the processing to the
     * next event in the chain.
     *
     * @param request - the http request object
     * @param response - the http response object
     * @return - &amp;success&amp; in all the cases
     */
    public static String checkCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        String csrfToken = request.getHeader(CSRF_TOKEN);
        if (csrfToken == null || "fetch".equalsIgnoreCase(csrfToken)) return "success";

        GenericValue userLogin = csrfTokens.get(csrfToken);
        if (userLogin != null) {
            //to check it's the right tenant
            //in case username and password are the same in different tenants
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            String oldDelegatorName = delegator.getDelegatorName();
            if (!oldDelegatorName.equals(userLogin.getDelegator().getDelegatorName())) {
//                delegator = DelegatorFactory.getDelegator(userLogin.getDelegator().getDelegatorName());
//                LocalDispatcher dispatcher = WebAppUtil.makeWebappDispatcher(request.getServletContext(), delegator);
//                LoginWorker.setWebContextObjects(request, response, delegator, dispatcher);
                return "error";
            }
            // found userLogin, do the external login...

            // if the user is already logged in and the login is different, logout the other user
            HttpSession session = request.getSession();
            GenericValue currentUserLogin = (GenericValue) session.getAttribute("userLogin");
            if (currentUserLogin != null) {
                if (currentUserLogin.getString("userLoginId").equals(userLogin.getString("userLoginId"))) {
                    // same user, just make sure the autoUserLogin is set to the same and that the client cookie has the correct userLoginId
                    LoginWorker.autoLoginSet(request, response);
                    return "success";
                }

                // logout the current user and login the new user...
                LoginWorker.logout(request, response);
                // ignore the return value; even if the operation failed we want to set the new UserLogin
            }

            LoginWorker.doBasicLogin(userLogin, request);
        } else {
            Debug.logWarning("Could not find userLogin for external login key: " + csrfToken, module);
        }

        // make sure the autoUserLogin is set to the same and that the client cookie has the correct userLoginId
        LoginWorker.autoLoginSet(request, response);
        request.setAttribute("userLogin", userLogin);

        return "success";
    }

    private static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

}
