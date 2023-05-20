package com.dpbird.odata.test;

import com.dpbird.odata.OdataEntityQuery;
import com.dpbird.odata.OfbizMapOdata;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.collections.PagedList;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.ex.ODataException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

public class ActionFunctionEvents {
    public static Object testBoundSetActionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("party");
        GenericValue party = entity.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        return party.getString("partyId") + otherParm;
    }

    public static Object testBoundSetActionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity boundEntity = (OdataOfbizEntity) actionParameters.get("party");

        GenericValue party = boundEntity.getGenericValue();
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetActionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) actionParameters.get("party");

        GenericValue party = odataOfbizEntity.getGenericValue();
        return party.getRelated("PartyRole", null, null, false);
    }

    //Semantic Entity
    public static Object testBoundSetActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        return UtilMisc.toMap("partyId", party.getGenericValue().getString("partyId"), "displayName", otherParm);
    }

    //Semantic Entity
    public static Object testBoundSetActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        GenericValue genericValue = party.getGenericValue();
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", genericValue.getString("partyId").toString() + i, "displayName", otherParm));
        }
        return mapList;
    }

    //Semantic Entity
    public static Object testBoundSingletonActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("partyId", edmBindingTarget.getName(), "displayName", otherParm);
    }

    //Semantic Entity
    public static Object testBoundSingletonActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", edmBindingTarget.getName() + i, "displayName", otherParm));
        }
        return mapList;
    }

    public static Object testBoundSingletonActionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("userLogin");
        GenericValue userLogin = entity.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        return userLogin.getString("partyId") + otherParm;
    }

    public static Object testBoundSingletonActionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity boundObject = (OdataOfbizEntity) actionParameters.get("userLogin");

        GenericValue userLogin = boundObject.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        return userLogin.getRelatedOne("Party", false);
    }

     public static Object testBoundSingletonActionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        String otherParm = (String) actionParameters.get("otherParm");
         OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) actionParameters.get("userLogin");
        GenericValue userLogin = ofbizEntity.getGenericValue();
        GenericValue party = userLogin.getRelatedOne("Party", false);
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetFunctionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return party.getPropertyValue("partyId") + otherParm;
    }

    public static Object testBoundCollectionFunctionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        List<OdataOfbizEntity> partyRoles = UtilGenerics.checkList(actionParameters.get("partyRole"));
        StringBuilder stringBuilder = new StringBuilder();
        for (OdataOfbizEntity partyRole : partyRoles) {
            stringBuilder.append(partyRole.getPropertyValue("roleTypeId"));
        }
        return stringBuilder.toString();
    }

    public static Object testBoundCollectionActionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        List<OdataOfbizEntity> partyRoles = UtilGenerics.checkList(actionParameters.get("partyRole"));
        StringBuilder stringBuilder = new StringBuilder();
        for (OdataOfbizEntity partyRole : partyRoles) {
            stringBuilder.append(partyRole.getPropertyValue("roleTypeId"));
        }
        return stringBuilder.toString();
    }

    public static Object testBoundSetActionComplexParam(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        ComplexValue complex = (ComplexValue) actionParameters.get("testComplex");
        return complex.toString();
    }

    public static Object testBoundSetActionComplexCollectionParam(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        List<ComplexValue> complexValues = UtilGenerics.checkList(actionParameters.get("testComplex"));
        return complexValues.toString();
    }

    public static Object testBoundSetFunctionOdataQuery(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

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

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return party.getPropertyValue("partyId") + otherParm;
    }

    public static Object testBoundSetFunctionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList(party.getPropertyValue("partyId"), "param1", "param2"));
    }

    public static Object testBoundSetParamAliasPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        List<BigDecimal> otherParm = (List<BigDecimal>) actionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm);
        return party.getPropertyValue("partyId") + otherParm.toString();
    }

    public static Object testBoundSetParamAliasPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        List<String> otherParm = (List<String>) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        Debug.log(">>>>>>otherParm = " + otherParm);
        return new ArrayList<>(Arrays.asList(party.getPropertyValue("partyId"), "param1", "param2"));
    }

    public static Object testBoundSetParamAliasEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        List<String> otherParm = (List<String>) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        GenericValue partygv = party.getGenericValue();
        return partygv.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetParamAliasEntityCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        GenericValue party = delegator.findOne("Party", UtilMisc.toMap("partyId", "10030"), false);
        List<String> otherParm = (List<String>) functionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetParamAliasComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("party");
        List<String> otherParm = (List<String>) actionParameters.get("otherParm");
        Debug.log(">>>>>>otherParm = " + otherParm.toString());
        GenericValue party = entity.getGenericValue();
        return UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetParamAliasComplexCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity entity = (OdataOfbizEntity) functionParameters.get("party");
        GenericValue party = entity.getGenericValue();
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

    public static Object testBoundSetDateParamAliasComplexCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OdataOfbizEntity entity = (OdataOfbizEntity) functionParameters.get("party");
        GenericValue party = (GenericValue) entity.getGenericValue();
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



    public static Object testBoundSingletonFunctionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList("param1", "param2", "param3"));
    }

    public static Object testBoundSingletonFunctionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        ComplexValue complexValue = new ComplexValue();
        List<Property> value = complexValue.getValue();
        value.add(new Property(null, "testProperty1", ValueType.PRIMITIVE, "test_String"));
        value.add(new Property(null, "testProperty2", ValueType.PRIMITIVE, new BigDecimal("11.22")));
        new Property("com.dpbird.TestComplexProperty", "testComplex", ValueType.COMPLEX, complexValue);
        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        return userLogin.getPropertyValue("partyId") + otherParm;
    }

    public static Object testBoundSetFunctionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException {
        Debug.log(">>>>>>>> testBoundSetFunctionEntity edmBindingTarget: " + edmBindingTarget);
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) actionParameters.get("party");
        GenericValue party = ofbizEntity.getGenericValue();
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetCollectionFunctionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException {
        Debug.log(">>>>>>>> testBoundSetCollectionFunctionEntity edmBindingTarget: " + edmBindingTarget);

        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        List<Entity> entityList = (List<Entity>) actionParameters.get("party");
        if (UtilValidate.isEmpty(entityList)) {
            return null;
        }
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) entityList.get(0);
        GenericValue party = ofbizEntity.getGenericValue();
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testBoundSetFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        //PartyInfo
        return UtilMisc.toMap("partyId", party.getKeyMap().get("partyId"), "displayName", otherParm);
    }

    public static Object testBoundSetFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", party.getKeyMap().get("partyId"), "displayName", otherParm));
        }
        return mapList;
    }
    public static Object testBoundSingletonFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        return UtilMisc.toMap("partyId", edmBindingTarget.getName(), "displayName", otherParm);
    }

    public static Object testBoundSingletonFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String otherParm = (String) actionParameters.get("otherParm");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", edmBindingTarget.getName() + i, "displayName", otherParm));
        }
        return mapList;
    }

    public static Object testBoundSetFunctionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Debug.log(">>>>>>>> testBoundSetFunctionEntityCollection edmBindingTarget: " + edmBindingTarget);

        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) functionParameters.get("otherParm");
        OdataOfbizEntity party = (OdataOfbizEntity) functionParameters.get("party");

        return party.getGenericValue().getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetFunctionEntityCollectionFilter(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("Product").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testImportFunctionEntityCollectionFilter(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("OrderHeader").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testImportFunctionEntityCollectionFilterManyKey(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        PagedList<GenericValue> pagedList = EntityQuery.use(delegator).from("OrderItem").queryPagedList(1, 100);
        return pagedList.getData();
    }

    public static Object testBoundSingletonFunctionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Debug.log(">>>>>>testBoundSingletonFunctionEntity edmBindingTarget: " + edmBindingTarget);
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity singleton = (OdataOfbizEntity) actionParameters.get("userLogin");
        GenericValue userLogin = singleton.getGenericValue();
        return userLogin.getRelatedOne("Party", false);
    }

    public static Object testBoundSingletonFunctionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        GenericValue party = userLogin.getGenericValue().getRelatedOne("Party", false);
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundSetActionVoid(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return null;
    }

    public static Object testBoundSingletonActionVoid(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        return null;
    }

    public static Object testImportActionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return "passed in " + otherParm + ", " + partyId;
    }

    public static Object testImportActionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        String partyId = (String) actionParameters.get("partyId");
        return new ArrayList<>(Arrays.asList(partyId,"param2","param3"));
    }

    public static Object testImportActionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }
    public static Object testImportActionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
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

    public static Object testImportActionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testImportActionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelated("PartyRole", null, null, false);
    }


    public static Object testImportActionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        return UtilMisc.toMap("partyId", partyId, "displayName", otherParm);
    }

    public static Object testImportActionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        //PartyInfo
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mapList.add(UtilMisc.toMap("partyId", partyId + i, "displayName", otherParm));
        }
        return mapList;
    }


    public static Object testImportActionVoid(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        Debug.log(">>>>>>>>>>>>> edmBindingTarget= " + edmBindingTarget);
        Debug.log(">>>>>>>>>>>>> otherParm= " + otherParm);
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return null;
    }

    public static Object testBoundSetActionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        GenericValue party = entity.getGenericValue();
        return UtilMisc.toMap("testObjectOneId", party.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetActionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("party");
        GenericValue party = entity.getGenericValue();
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
    public static Object testBoundSetActionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget){
//        GenericValue product = (GenericValue) boundObject;
        return new ArrayList<>(Arrays.asList("param1","param2","param3"));
    }
    public static Object testBoundSingletonActionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget){
//        GenericValue product = (GenericValue) boundObject;
        return new ArrayList<>(Arrays.asList("param1","param2","param3"));
    }

    public static Object testBoundSingletonActionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("userLogin");
        GenericValue userLogin = entity.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", userLogin.getString("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSingletonActionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("userLogin");
        GenericValue userLogin = entity.getGenericValue();
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

    public static Object testChangeSetBoundSetAction(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity product = (OdataOfbizEntity) actionParameters.get("product");
        String otherParm = (String) actionParameters.get("otherParm");
        return product.getGenericValue();
    }

    public static Object testChangeSetBoundSetActionOne(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException, OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity product = (OdataOfbizEntity) actionParameters.get("product");
        GenericValue genericValue = product.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        if ("err".equals(otherParm)) throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Test exception.");
        Map<String, Object> serviceMap = new HashMap<>();
        serviceMap.put("productId", genericValue.getString("productId"));
        serviceMap.put("productName", otherParm);
        serviceMap.put("userLogin", Util.getSystemUser(delegator));
        Map<String, Object> updateProductOne = dispatcher.runSync("updateProduct", serviceMap);
        Debug.log(">>>>>>>>>>> updateProduct One = " + updateProductOne);
        return delegator.findOne("Product", genericValue.getPrimaryKey(), false);
    }

    public static Object testChangeSetBoundSetActionTwo(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException, OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity product = (OdataOfbizEntity) actionParameters.get("product");
        GenericValue genericValue = product.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        if ("err".equals(otherParm)) throw new OfbizODataException(String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR), "Test exception.");
        Map<String, Object> serviceMap = new HashMap<>();
        serviceMap.put("productId", genericValue.getString("productId"));
        serviceMap.put("productName", otherParm);
        serviceMap.put("userLogin", Util.getSystemUser(delegator));
        Map<String, Object> updateProductTwo = dispatcher.runSync("updateProduct", serviceMap);
        Debug.log(">>>>>>>>>>> updateProduct Two = " + updateProductTwo);
        return delegator.findOne("Product", genericValue.getPrimaryKey(), false);
    }


    public static Object testBoundSetFunctionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", party.getPropertyValue("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSetFunctionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity product = (OdataOfbizEntity) actionParameters.get("product");
        GenericValue productGV = product.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        List<GenericValue> productFeatureAndAppls = productGV.getRelated("ProductFeatureAndAppl", null, null, false);
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

    public static Object testBoundSingletonFunctionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", userLogin.getPropertyValue("partyId"),
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testBoundSingletonFunctionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OdataOfbizEntity userLogin = (OdataOfbizEntity) actionParameters.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", "system",
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportFunctionPrimitive(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return "passed in " + otherParm + ", " + partyId;
    }

    public static Object testImportFunctionPrimitiveCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return new ArrayList<>(Arrays.asList(partyId,"param1","param2"));
    }

    public static Object testCollectionParamEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<BigDecimal> strings = (List<BigDecimal>) otherParm1;
        for (BigDecimal string : strings) {
            Debug.log(">>>>>>>>>>参数： " + string);
        }
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testCollectionParamEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<String> strings = (List<String>) otherParm1;
        for (String string : strings) {
            Debug.log(">>>>>>>>>>参数：" + string);
        }
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));
        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testCollectionParamComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");
        Object otherParm1 = actionParameters.get("otherParm");
        List<BigDecimal> strings = (List<BigDecimal>) otherParm1;
        for (BigDecimal string : strings) {
            Debug.log(">>>>>>>>>>参数： " + string);
        }
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testCollectionParamComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
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

    public static Object testImportFunctionComplex(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String partyId = (String) actionParameters.get("partyId");

        String otherParm = (String) actionParameters.get("otherParm");
        return UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
    }

    public static Object testImportFunctionComplexCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        String partyId = (String) actionParameters.get("partyId");
        String otherParm = (String) actionParameters.get("otherParm");
        Map<String, Object> map1 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        Map<String, Object> map2 = UtilMisc.toMap("testObjectOneId", partyId,
                "amount", BigDecimal.TEN, "testDate", UtilDateTime.nowTimestamp());
        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(map1);
        resultList.add(map2);
        return resultList;
    }

    public static Object testImportFunctionEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Debug.log(">>>>>>>>> testImportFunctionEntity edmBindingTarget: " + edmBindingTarget);
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));

        return party.getRelatedOne("CreatedByUserLogin", false);
    }

    public static Object testImportFunctionSemanticEntity(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) functionParameters.get("otherParm");
        String partyId = (String) functionParameters.get("partyId");
        return UtilMisc.toMap("partyId", partyId, "displayName", otherParm);
    }

   public static Object testImportFunctionSemanticEntityCollection(Map<String, Object> oDataContext, Map<String, Object> functionParameters, EdmBindingTarget edmBindingTarget) {
        String otherParm = (String) functionParameters.get("otherParm");
        String partyId = (String) functionParameters.get("partyId");
       //PartyInfo
       List<Map<String, Object>> mapList = new ArrayList<>();
       for (int i = 0; i < 5; i++) {
           mapList.add(UtilMisc.toMap("partyId", partyId + i, "displayName", otherParm));
       }
       return mapList;
    }

   public static Object testImportFunctionEntityCollection(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericEntityException, GenericServiceException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        String partyId = (String) actionParameters.get("partyId");
        GenericValue party = delegator.findOne("Party", false, UtilMisc.toMap("partyId", partyId));

        return party.getRelated("PartyRole", null, null, false);
    }

    public static Object testBoundActionEntitySetPath(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws GenericEntityException,ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        String otherParm = (String) actionParameters.get("otherParm");
        OdataOfbizEntity product = (OdataOfbizEntity) actionParameters.get("product");

        GenericValue genericValue = product.getGenericValue();
        GenericValue primaryProductCategory = genericValue.getRelatedOne("PrimaryProductCategory", false);
        return primaryProductCategory;
    }

    public static Object testBoundActionError(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        if ("1".equals(otherParm)) {
            throw new OfbizODataException(OfbizMapOdata.ERROR_CODE_ONE, "get error message 1");
        }
        return party.getGenericValue().getString("partyId") + otherParm;
    }

    public static Object testBoundActionBaseType(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity party = (OdataOfbizEntity) actionParameters.get("party");
        String otherParm = (String) actionParameters.get("otherParm");
        return party.getGenericValue();
    }

    public static Object testBoundFunctionBaseType(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity entity = (OdataOfbizEntity) actionParameters.get("party");
        GenericValue party = entity.getGenericValue();
        String otherParm = (String) actionParameters.get("otherParm");
        return party;
    }

}
