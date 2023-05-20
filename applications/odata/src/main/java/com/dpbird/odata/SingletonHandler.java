package com.dpbird.odata;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.ex.ODataException;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

public class SingletonHandler {
    public static Object getMe(HttpSession httpSession, Map<String, Object> singletonContext)
            throws ODataException {
        Delegator delegator = (Delegator) singletonContext.get("delegator");
        GenericValue userLogin = (GenericValue) singletonContext.get("userLogin");
        try {
            if (UtilValidate.isNotEmpty(userLogin)) {
                List<GenericValue> partyUserLogin = delegator.findByAnd("PartyUserLogin", userLogin.getPrimaryKey(), null, true);
                return EntityUtil.getFirst(partyUserLogin);
            }
            return null;
        } catch (GenericEntityException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }
    public static Object productSingleton(HttpSession httpSession, Map<String, Object> singletonContext)
            throws ODataException, GenericEntityException {
        Delegator delegator = (Delegator) singletonContext.get("delegator");
        return EntityQuery.use(delegator).from("Product").queryFirst();

    }

}
