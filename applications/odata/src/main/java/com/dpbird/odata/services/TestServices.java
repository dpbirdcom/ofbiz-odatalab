package com.dpbird.odata.services;

import com.dpbird.odata.test.TestComplexType;
import com.dpbird.test.TestParentComplexType;
import com.dpbird.test.TestSubComplexType;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestServices {

    public final static String module = SetupServices.class.getName();

    public static Map<String, Object> testEntityTypeInput(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        
        GenericValue productFeatureAppl = (GenericValue) context.get("productFeatureAppl");
        Timestamp fromDate = productFeatureAppl.getTimestamp("fromDate");
        Debug.logInfo("------------------------- fromDate = " + fromDate, module);
        GenericValue gvFromDb = delegator.findOne("ProductFeatureAppl", false,
        											UtilMisc.toMap("productId", productFeatureAppl.get("productId"),
											        				"productFeatureId", productFeatureAppl.get("productFeatureId"),
											        				"fromDate", fromDate));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("productFeatureId", gvFromDb.get("productFeatureId"));
        return result;
    }

    // 如果service返回两个或以上的返回值，需要能够在edmConfig中指定哪个output为action的返回值
    public static Map<String, Object> testActionReturn(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("stringOne", "The first string");
        result.put("stringTwo", "The second string");
        result.put("decimalOne", BigDecimal.ONE);
        result.put("decimalTwo", BigDecimal.valueOf(2));
        return result;
    }

    // 如果service返回Primitive，测试Action的返回正确
    public static Map<String, Object> testActionPrimitive(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Delegator delegator = dctx.getDelegator();
    	Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("theReturn", "This is String returned");
        List<GenericValue> components = delegator.findAll("Component", false); 
        return result;
    }

    // 如果service返回Primitive Collection，测试Action的返回正确
    public static Map<String, Object> testActionPrimitiveCollection(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<String> theReturn = new ArrayList<String>();
        theReturn.add("the String one");
        theReturn.add("the String two");
        theReturn.add("the String three");
        result.put("theReturn", theReturn);
        return result;
    }

    // 如果service返回一个自定义的Class，测试Action正确返回对应的ComplexType
    public static Map<String, Object> testActionComplexType(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        TestParentComplexType testParentComplexType = (TestParentComplexType) context.get("parentComplexType");
        TestSubComplexType testSubComplexType = testParentComplexType.getTestSubComplexType();
        TestComplexType theReturn = new TestComplexType(testParentComplexType.getTestString() + testSubComplexType.getTestString(),
        													testParentComplexType.getTestDecimal().add(testSubComplexType.getTestDecimal()),
        													testParentComplexType.getTestLong() + testSubComplexType.getTestLong());
        result.put("theReturn", theReturn);
        return result;
    }

    // 如果service返回Primitive，测试Bound Action的返回正确
    public static Map<String, Object> testBoundActionPrimitive(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        GenericValue party = (GenericValue) context.get("party");
        String otherParm = (String) context.get("otherParm");
        result.put("theReturn", "This is String returned with partyId = " + party.getString("partyId") +", otherParm = " + otherParm);
        return result;
    }

    // 如果service返回Primitive，测试Bound Function的返回正确
    public static Map<String, Object> testBoundFunctionPrimitive(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue product = (GenericValue) context.get("product");
        String partyId = (String) context.get("partyId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Map<String, Object> serviceResult = dispatcher.runSync("calculateProductPrice", UtilMisc.toMap("product", product, "partyId", partyId, "userLogin", userLogin));
        BigDecimal price = (BigDecimal) serviceResult.get("price");
        result.put("price", price);
        return result;
    }

    public static Map<String, Object> testBoundActionWithCollection(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<GenericValue> parties = (List) context.get("parties");
        String otherParm = (String) context.get("otherParm");
        result.put("theReturn", "This is String returned with party size = " + parties.size() +", otherParm = " + otherParm);
        return result;
    }
       
    public static Map<String, Object> testBoundFunctionSuccess(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue product = (GenericValue) context.get("product");
        String otherParm = (String) context.get("otherParm");
        String otherParmMust = (String) context.get("otherParmMust");
        result.put("returnString", "这就是返回数据(otherParm="+otherParm+",otherParmMust="+otherParmMust+")!");
        return result;
    }
    
    public static Map<String, Object> testBoundFunctionError(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue product = (GenericValue) context.get("product");
        String otherParm = (String) context.get("otherParm");
        String otherParmMust = (String) context.get("otherParmMust");
        Map<String, Object> result = ServiceUtil.returnError("这就是返回数据(otherParm="+otherParm+",otherParmMust="+otherParmMust+")!");
        return result;
    }
    
    public static Map<String, Object> testBoundActionSuccess(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        GenericValue product = (GenericValue) context.get("product");
        String otherParm = (String) context.get("otherParm");
        String otherParmMust = (String) context.get("otherParmMust");
//        result.put("returnString", "这就是返回数据(otherParm="+otherParm+",otherParmMust="+otherParmMust+")!");
        result.put("product", product);
        return result;
    }
      
    public static Map<String, Object> testBoundActionError(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

        
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue product = (GenericValue) context.get("product");
        String otherParm = (String) context.get("otherParm");
        String otherParmMust = (String) context.get("otherParmMust");
        Map<String, Object> result = ServiceUtil.returnError("这就是返回数据(otherParm="+otherParm+",otherParmMust="+otherParmMust+")!");
        return result;
    }
    
    public static Map<String, Object> testComplexType(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

    	Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        TestComplexType complexType = (TestComplexType) context.get("complexTypeParameter");
        TestComplexType theReturn = new TestComplexType(complexType.getTestString(),complexType.getTestDecimal(),complexType.getTestLong());
        result.put("theReturn", theReturn);
        return result;
    }
    
    public static Map<String, Object> testBoundActionForParty(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

    	Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue party = (GenericValue) context.get("party");
        Timestamp otherParm = (Timestamp) context.get("otherParm");
        Timestamp otherParmMust = (Timestamp) context.get("otherParmMust");
        result.put("theReturn", otherParmMust);
        return result;
    }
    
    public static Map<String, Object> duplicateProductSvc(DispatchContext dctx, Map<String, ? extends Object> context)
    		throws GenericServiceException, GenericEntityException {

    	Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        GenericValue product = (GenericValue) context.get("product");        
        ModelService modelService = dctx.getModelService("duplicateProduct");
        List<ModelParam> modelParams = modelService.getInModelParamList();
        Map<String, Object> serviceParam = new HashMap<String, Object>();
        for (ModelParam modelParam : modelParams) {
        	String paramName = modelParam.getName();
        	if(paramName.equals("oldProductId")) {
        		serviceParam.put(paramName, product.getString("productId"));
        	}else {
        		if(context.get(paramName)==null) {
        			continue;
        		}else {
        			String ClassName = context.get(paramName).getClass().toString();
        			if(ClassName.equals("class java.lang.Boolean")) {
        				Boolean value = (Boolean)context.get(paramName);
            			serviceParam.put(paramName, value!=null?(value?"Y":"N"):null);
        			}else {
        				serviceParam.put(paramName, context.get(paramName));
        			}
        		}
        	}        	
		}		
        Map<String, Object> duplicateProductResult = dispatcher.runSync("duplicateProduct",serviceParam);
        if (ServiceUtil.isError(duplicateProductResult)) {
            return duplicateProductResult;
        }
        result.put("returnString", "复制成功!");
        return result;
    }


    public static Map<String, Object> testFindViewEntityFromCache(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericServiceException, GenericEntityException {

        Map<String, Object> result = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        String productId = (String) context.get("productId");
        String productFeatureId = (String) context.get("productFeatureId");
        Timestamp fromDate = (Timestamp) context.get("fromDate");
        Map<String, Object> pkMap = UtilMisc.toMap("productId", productId, "productFeatureId", productFeatureId, "fromDate", fromDate);
        GenericValue productFeatureAndAppl = delegator.findOne("ProductFeatureAndAppl", pkMap, true);
        result.put("viewEntity", productFeatureAndAppl);
        return result;
    }

    public static Map<String, Object> testUpdateViewEntity(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericServiceException, GenericEntityException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        Map<String, Object> serviceResult = dispatcher.runSync("updateFeatureToProductApplication", new HashMap<>(context));
        Debug.log(">>>>> updateFeatureToProductApplication result: " + serviceResult);
        return serviceResult;
    }

    public static Map<String, Object> syncProductDemo(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericServiceException, GenericEntityException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        Debug.log(">>>>> running  syncProductDemo!");
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> testWorkEffortAttributeTime(DispatchContext dctx, Map<String, ? extends Object> context)
            throws GenericServiceException, GenericEntityException {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        String workEffortId = (String) context.get("workEffortId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        List<String> attrNames = (List<String>) context.get("attrNames");
        List<String> attrValues = (List<String>) context.get("attrValues");
//        String attrOne = (String) context.get("attrOne");
//        String attrTwo = (String) context.get("attrTwo");
//        createOrUpdateAttribute(delegator, dispatcher, workEffortId, "attrOne", attrOne, userLogin);
//        createOrUpdateAttribute(delegator, dispatcher, workEffortId, "attrTwo", attrTwo, userLogin);
        for (int i = 0; i < attrNames.size(); i++) {
            String attrName = attrNames.get(i);
            String attrValue = attrValues.get(i);
            createOrUpdateAttribute(delegator, dispatcher, workEffortId, attrName, attrValue, userLogin);
        }

        Debug.logInfo(">>>>> running  testWorkEffortAttributeTime!", module);
        return ServiceUtil.returnSuccess();
    }

    private static void createOrUpdateAttribute(Delegator delegator, LocalDispatcher dispatcher,
                                                String workEffortId, String attrName, String attrValue, GenericValue userLogin)
            throws GenericEntityException, GenericServiceException {
        GenericValue workEffortAttribute = delegator.findOne("WorkEffortAttribute",
                UtilMisc.toMap("workEffortId", workEffortId, "attrName", attrName), false);
        if (workEffortAttribute == null) {
            dispatcher.runSync("createWorkEffortAttribute",
                    UtilMisc.toMap("workEffortId", workEffortId, "attrName", attrName, "attrValue", attrValue, "userLogin", userLogin));
        } else {
            dispatcher.runSync("updateWorkEffortAttribute",
                    UtilMisc.toMap("workEffortId", workEffortId, "attrName", attrName, "attrValue", attrValue, "userLogin", userLogin));
        }
    }
}
