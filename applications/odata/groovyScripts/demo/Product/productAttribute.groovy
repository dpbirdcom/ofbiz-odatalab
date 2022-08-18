import com.dpbird.odata.edm.OdataOfbizEntity
import org.apache.ofbiz.entity.GenericValue;
import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.party.party.PartyHelper
import org.apache.ofbiz.entity.util.EntityQuery

module = "Enumeration.groovy";

def getNavigationData(Map<String, Object> context) {
    genericValue = context.parameters.genericValue;
    List<GenericValue> attributes = delegator.findByAnd("ProductAttribute", [productId: genericValue.getString("productId"),attrType: "TEST_TYPE"], null, false);
    if (attributes != null) {
        return attributes;
    }
}

def createNestedData(Map<String, Object> context) {
    GenericValue entityCreated = context.parameters.entity.getGenericValue();
    Entity nestedEntity = context.parameters.nestedEntity;
    Debug.log(">>>>>>>>>> start create productAttr")
    Map<String, Object> serviceParams = Util.entityToMap(nestedEntity);
    Debug.log(">>>>>>>>>> serviceParams = " + serviceParams)
    return null;
}

def deleteNavigationData(Map<String, Object> context) {
    GenericValue genericValue = context.parameters.genericValue;
    GenericValue toDeleteGenericValue = context.parameters.toDeleteGenericValue;

}
