import com.dpbird.odata.edm.OdataOfbizEntity
import com.dpbird.basecamp.util.BasecampUtils;
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
    enumerations = delegator.findByAnd("Enumeration", [enumTypeId: genericValue.getString("enumTypeId")], null, false);
    if (enumerations != null) {
        return enumerations;
    }
}

def createNestedData(Map<String, Object> context) {
    GenericValue entityCreated = context.parameters.entity.getGenericValue();
    Entity nestedEntity = context.parameters.nestedEntity;
    Map<String, Object> serviceParams = Util.entityToMap(nestedEntity);
    serviceParams.put("userLogin", userLogin);
    serviceParams.put("enumTypeId", entityCreated.getString("enumTypeId"));
    Map<String, Object> result;
    if (serviceParams.get("enumId") == null) {
        result = dispatcher.runSync("createEnumeration", serviceParams)
    } else {
        result = dispatcher.runSync("updateEnumeration", serviceParams)
    }
    String newEnumId = serviceParams.get("enumId") == null ? result.get("enumId") : serviceParams.get("enumId")
    return delegator.findOne("Enumeration", UtilMisc.toMap("enumId", newEnumId), false);
}

def deleteNavigationData(Map<String, Object> context) {
    GenericValue genericValue = context.parameters.genericValue;
    GenericValue toDeleteGenericValue = context.parameters.toDeleteGenericValue;
    dispatcher.runSync("deleteEnumeration", UtilMisc.toMap("enumId", toDeleteGenericValue.getString("enumId"), "userLogin", userLogin))

}
