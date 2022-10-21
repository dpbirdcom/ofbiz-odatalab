package com.dpbird.odata.test;

import com.dpbird.odata.OdataEntityQuery;
import com.dpbird.odata.OfbizMapOdata;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.collections.PagedList;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityFindOptions;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.order.shoppingcart.CartItemModifyException;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.Parameter;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.ex.ODataException;
import org.joda.time.DateTimeUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

public class ActionFunctionEvents {
    public static Object testBoundSetActionPrimitive(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return party.getString("partyId") + otherParm;
    }

    public static Object testBoundSetActionEntity(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");

        GenericValue party = (GenericValue) boundObject;
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetActionEntityCollection(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");

        GenericValue party = (GenericValue) boundObject;
        return party.getRelated("PartyRole", null, null, false);
    }

    //Semantic Entity
    public static Object testBoundSetActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                          EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                          Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("partyId", keyMap.get("partyId"), "displayName", otherParm);
    }

    //Semantic Entity
    public static Object testBoundSetActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", keyMap.get("partyId").toString() + i, "displayName", otherParm));
        }
        return mapList;
    }

    //Semantic Entity
    public static Object testBoundSingletonActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                          EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                          Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("partyId", edmBindingTarget.getName(), "displayName", otherParm);
    }

    //Semantic Entity
    public static Object testBoundSingletonActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", edmBindingTarget.getName() + i, "displayName", otherParm));
        }
        return mapList;
    }

    public static Object testBoundSingletonActionPrimitive(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return userLogin.getString("partyId") + otherParm;
    }

    public static Object testBoundSingletonActionEntity(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return userLogin.getRelatedOne("Party", false);
    }

     public static Object testBoundSingletonActionEntityCollection(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        GenericValue party = userLogin.getRelatedOne("Party", false);
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetFunctionPrimitive(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return party.getString("partyId") + otherParm;
    }

    public static Object testBoundSetFunctionOdataQuery(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        List<GenericValue> genericValues = OdataEntityQuery.use(delegator).from("OrderHeader")
                .groupBy("statusId", "orderTypeId", "currencyUom")
                .function("grandTotal", "sum", "grandTotal_SUM")
                .function("grandTotal", "max","grandTotal_MAX")
                .function("currencyUom", "count-distinct", "currencyUom_CD")
                .where(EntityCondition.makeCondition())
                .queryList();

        for (GenericValue genericValue : genericValues) {
            for (Map.Entry<String, Object> entry : genericValue.entrySet()) {
                Debug.log(entry.toString());
            }
            Debug.log("=======");
        }

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return party.getString("partyId") + otherParm;
    }

    public static Object testBoundSetFunctionPrimitiveCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList(party.getString("partyId"), "param1", "param2"));
    }

    public static Object testBoundSetParamAliasPrimitive(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        List<BigDecimal> otherParm = (List<BigDecimal>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm);
        return party.getString("partyId") + otherParm.toString();
    }

    public static Object testBoundSetParamAliasPrimitiveCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm);
        return new ArrayList<>(Arrays.asList(party.getString("partyId"), "param1", "param2"));
    }

    public static Object testBoundSetParamAliasEntity(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        GenericValue party = (GenericValue) boundObject;
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetParamAliasEntityCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

//        GenericValue party = (GenericValue) boundObject;
        GenericValue party = delegator.findOne("Party", UtilMisc.toMap("partyId", "10030"), false);
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetParamAliasComplex(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        GenericValue party = (GenericValue) boundObject;
        return UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetParamAliasComplexCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testBoundSetDateParamAliasComplexCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        List<Timestamp> otherParm = (List<Timestamp>) functionParameters.get("otherParm");
        for (Timestamp timestamp : otherParm) {
            Debug.log(">>>>>>timestamp = " + timestamp.toString());
        }
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }



    public static Object testBoundSingletonFunctionPrimitiveCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList("param1", "param2", "param3"));
    }

    public static Object testBoundSingletonFunctionPrimitive(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return userLogin.getString("partyId") + otherParm;
    }

    public static Object testBoundSetFunctionEntity(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) functionParameters.get("otherParm");

        GenericValue party = (GenericValue) boundObject;
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                      EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                      Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        return UtilMisc.toMap("partyId", keyMap.get("partyId"), "displayName", otherParm);
    }

    public static Object testBoundSetFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                      EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                      Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", keyMap.get("partyId"), "displayName", otherParm));
        }
        return mapList;
    }
    public static Object testBoundSingletonFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                      EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                      Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        return UtilMisc.toMap("partyId", edmBindingTarget.getName(), "displayName", otherParm);
    }

    public static Object testBoundSingletonFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                      EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                      Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", edmBindingTarget.getName() + i, "displayName", otherParm));
        }
        return mapList;
    }

    public static Object testBoundSetFunctionEntityCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) functionParameters.get("otherParm");

        GenericValue party = (GenericValue) boundObject;
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetFunctionEntityCollectionFilter(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("Product").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testImportFunctionEntityCollectionFilter(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("OrderHeader").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testImportFunctionEntityCollectionFilterManyKey(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("OrderItem").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testBoundSingletonFunctionEntity(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        return userLogin.getRelatedOne("Party", false);
    }

    public static Object testBoundSingletonFunctionEntityCollection(HttpServletRequest request, Map<String, Object> functionParameters, Object boundObject)
            throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) functionParameters.get("otherParm");
        GenericValue party = userLogin.getRelatedOne("Party", false);
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetActionVoid(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return null;
    }

    public static Object testBoundSingletonActionVoid(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return null;
    }

    public static Object testImportActionPrimitive(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return "passed in " + otherParm + ", " + partyId;
    }

    public static Object testImportActionPrimitiveCollection(HttpServletRequest request, Map<String, Object> actionParameters) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        String partyId = (String) actionParameters.get("partyId");
        return new ArrayList<>(Arrays.asList(partyId,"param2","param3"));
    }

    public static Object testImportActionComplex(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }
    public static Object testImportActionComplexCollection(HttpServletRequest request, Map<String, Object> actionParameters) {
        String partyId = (String) actionParameters.get("partyId");
        String otherParm = (String) actionParameters.get("otherParm");
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN);
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN);
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportActionEntity(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));

        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testImportActionEntityCollection(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelated("PartyRole", null, null, false);
    }


    public static Object testImportActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                        EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                        Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        return UtilMisc.toMap("partyId", partyId, "displayName", otherParm);
    }

    public static Object testImportActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                                  EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                  Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", partyId + i, "displayName", otherParm));
        }
        return mapList;
    }


    public static Object testImportActionVoid(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return null;
    }

    public static Object testBoundSetActionComplex(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetActionComplexCollection(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN);
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN);
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }
    public static Object testBoundSetActionPrimitiveCollection(HttpServletRequest request, Map<String, Object> actionParameters , Object boundObject){
        GenericValue product = (GenericValue) boundObject;
        return new ArrayList<>(Arrays.asList("param1","param2","param3"));
    }
    public static Object testBoundSingletonActionPrimitiveCollection(HttpServletRequest request, Map<String, Object> actionParameters , Object boundObject){
        GenericValue product = (GenericValue) boundObject;
        return new ArrayList<>(Arrays.asList("param1","param2","param3"));
    }

    public static Object testBoundSingletonActionComplex(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", userLogin.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSingletonActionComplexCollection(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");

        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", null);
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", null);
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testChangeSetBoundSetAction(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue product = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return product;
    }

    public static Object testChangeSetBoundSetActionOne(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, OfbizODataException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue product = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        if ("err".equals(otherParm)) throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Test exception.");
        Map<String, Object> serviceMap = new HashMap<>();
        serviceMap.put("productId", product.getString("productId"));
        serviceMap.put("productName", otherParm);
        serviceMap.put("userLogin", Util.getSystemUser(delegator));
        Map<String, Object> updateProductOne = dispatcher.runSync("updateProduct", serviceMap);
        Debug.log(">>>>>>>>>>> updateProduct One = " + updateProductOne);
        return delegator.findOne("Product", product.getPrimaryKey(), false);
    }

    public static Object testChangeSetBoundSetActionTwo(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, OfbizODataException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue product = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        if ("err".equals(otherParm)) throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Test exception.");
        Map<String, Object> serviceMap = new HashMap<>();
        serviceMap.put("productId", product.getString("productId"));
        serviceMap.put("productName", otherParm);
        serviceMap.put("userLogin", Util.getSystemUser(delegator));
        Map<String, Object> updateProductTwo = dispatcher.runSync("updateProduct", serviceMap);
        Debug.log(">>>>>>>>>>> updateProduct Two = " + updateProductTwo);
        return delegator.findOne("Product", product.getPrimaryKey(), false);
    }


    public static Object testBoundSetFunctionComplex(HttpServletRequest request, Map<String, Object> parameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) parameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetFunctionComplexCollection(HttpServletRequest request, Map<String, Object> parameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue product = (GenericValue) boundObject;
        String otherParm = (String) parameters.get("otherParm");
        List<GenericValue> productFeatureAndAppls = product.getRelated("ProductFeatureAndAppl", null, null, false);
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> data1 = new HashMap<>();
        List<GenericValue> features = new ArrayList<>();
        features.add(delegator.findOne("ProductFeature", UtilMisc.toMap("productFeatureId", "1634"), false));
        features.add(delegator.findOne("ProductFeature", UtilMisc.toMap("productFeatureId", "1635"), false));
        data1.put("productFeatures", features);
        data1.put("variantProductIds", UtilMisc.toList("BG_1500634", "BG_1500635"));
        Map<String, Object> data2 = new HashMap<>();
        features = new ArrayList<>();
        features.add(delegator.findOne("ProductFeature", UtilMisc.toMap("productFeatureId", "1636"), false));
        features.add(delegator.findOne("ProductFeature", UtilMisc.toMap("productFeatureId", "1637"), false));
        data2.put("productFeatures", features);
        data2.put("variantProductIds", UtilMisc.toList("BG_1500636", "BG_1500637"));
        result.add(data1);
        result.add(data2);
        return result;
    }

    public static Object testBoundSingletonFunctionComplex(HttpServletRequest request, Map<String, Object> parameters, Object boundObject)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) parameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", userLogin.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSingletonFunctionComplexCollection(HttpServletRequest request, Map<String, Object> parameters, Object boundObject) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) boundObject;
        String otherParm = (String) parameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportFunctionPrimitive(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return "passed in " + otherParm + ", " + partyId;
    }

    public static Object testImportFunctionPrimitiveCollection(HttpServletRequest request, Map<String, Object> actionParameters) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList(partyId,"param1","param2"));
    }

    public static Object testCollectionParamEntity(HttpServletRequest request, Map<String, Object> actionParameters) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<BigDecimal> strings = (List<BigDecimal>) otherParm1;
        for (BigDecimal string : strings) {
            Debug.log(">>>>>>>>>>参数： " + string);
        }
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testCollectionParamEntityCollection(HttpServletRequest request, Map<String, Object> actionParameters) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<String> strings = (List<String>) otherParm1;
        for (String string : strings) {
            Debug.log(">>>>>>>>>>参数：" + string);
        }
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testCollectionParamComplex(HttpServletRequest request, Map<String, Object> actionParameters) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<BigDecimal> strings = (List<BigDecimal>) otherParm1;
        for (BigDecimal string : strings) {
            Debug.log(">>>>>>>>>>参数： " + string);
        }
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testCollectionParamComplexCollection(HttpServletRequest request, Map<String, Object> actionParameters) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<String> strings = (List<String>) otherParm1;
        for (String string : strings) {
            Debug.log(">>>>>>>>>>参数：" + string);
        }
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportFunctionComplex(Map<String, Object> oDataContext, Map<String, Object> functionParameters,
                                                    EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                    Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) functionParameters.get("partyId");

        String otherParm = (String) functionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }
    public static Object testImportFunctionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters,
                                                   EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                   Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        String partyId = (String) functionParameters.get("partyId");
        String otherParm = (String) functionParameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportFunctionEntity(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));

        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testImportFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> functionParameters,
                                                          EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                          Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) functionParameters.get("otherParm");
        String partyId = (String) functionParameters.get("partyId");
        return UtilMisc.toMap("partyId", partyId, "displayName", otherParm);
    }

   public static Object testImportFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters,
                                                                   EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                                   Map<String, Object> keyMap, Map<String, Object> navKeyMap) {
        String otherParm = (String) functionParameters.get("otherParm");
        String partyId = (String) functionParameters.get("partyId");
       //PartyInfo
       List<Map<String, Object>> mapList = new ArrayList<>();
       for (int i = 0; i < 5; i++) {
           mapList.add(UtilMisc.toMap("partyId", partyId + i, "displayName", otherParm));
       }
       return mapList;
    }

   public static Object testImportFunctionEntityCollection(HttpServletRequest request, Map<String, Object> actionParameters)
            throws GenericEntityException, GenericServiceException, CartItemModifyException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));

        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundActionEntitySetPath(Map<String, Object> oDataContext, Map<String, Object> actionParameters,
                                                             EdmBindingTarget edmBindingTarget, EdmNavigationProperty edmNavigationProperty,
                                                             Map<String, Object> keyMap, Map<String, Object> navKeyMap) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");

        GenericValue product = delegator.findOne("Product", keyMap, false);
        GenericValue primaryProductCategory = product.getRelatedOne("PrimaryProductCategory", false);
        return primaryProductCategory;
    }

    public static Object testBoundActionError(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws ODataException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        if ("1".equals(otherParm)) {
            throw new OfbizODataException(OfbizMapOdata.ERROR_CODE_ONE, "get error message 1");
        }
        return party.getString("partyId") + otherParm;
    }

    public static Object testBoundActionBaseType(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws ODataException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return party;
    }

    public static Object testBoundFunctionBaseType(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)
            throws ODataException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");

        GenericValue party = (GenericValue) boundObject;
        String otherParm = (String) actionParameters.get("otherParm");
        return party;
    }

}
