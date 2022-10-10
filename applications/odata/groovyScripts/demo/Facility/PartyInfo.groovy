import com.dpbird.odata.edm.OdataOfbizEntity
import org.apache.ofbiz.entity.GenericValue;
import com.dpbird.odata.Util;

import org.apache.olingo.commons.api.data.Property
import org.apache.olingo.commons.api.data.ValueType
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

def getSemanticNavigationData(Map<String, Object> context) {
    OdataOfbizEntity entity =  (OdataOfbizEntity) context.parameters.entity;
    GenericValue facility = entity.getGenericValue();
    List<Entity> entityList = new ArrayList<>();
    OdataOfbizEntity newEntity = new OdataOfbizEntity();
    newEntity.addProperty(new Property(null, "partyId", ValueType.PRIMITIVE, "testId"))
    newEntity.addProperty(new Property(null, "displayName", ValueType.PRIMITIVE, "testName"))
    entityList.add(newEntity);
    return entityList;
}
