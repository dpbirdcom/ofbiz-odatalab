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

module = "Party.groovy";

def getNavigationData(Map<String, Object> context) {
    OdataOfbizEntity entity =  (OdataOfbizEntity) context.parameters.entity;
    GenericValue genericValue = entity.getGenericValue();
    //使用语义化字段做Navigation
    String partyId = entity.getProperty("semanticPartyId1").getValue().toString();
    EntityCondition cond = EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, partyId);
    return EntityQuery.use(delegator).from("Party").where(cond).queryList();
}
