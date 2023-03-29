package com.dpbird.odata;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.ex.ODataException;

import javax.servlet.http.HttpSession;
import java.util.Map;

public class SingletonHandler {
    public static Object getMe(HttpSession httpSession, Map<String, Object> singletonContext)
            throws ODataException {
        return singletonContext.get("userLogin");

    }
    public static Object productSingleton(HttpSession httpSession, Map<String, Object> singletonContext)
            throws ODataException, GenericEntityException {
        Delegator delegator = (Delegator) singletonContext.get("delegator");
        return EntityQuery.use(delegator).from("Product").queryFirst();

    }

}
