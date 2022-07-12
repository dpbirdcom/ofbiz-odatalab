import java.sql.Time
import java.sql.Timestamp
import java.util.List
import java.util.*;
import org.apache.ofbiz.service.ServiceUtil;
import java.sql.Date;
import org.apache.olingo.commons.api.data.ComplexValue
import com.dpbird.odata.Util;
import org.apache.olingo.commons.api.data.Entity
import org.apache.olingo.commons.api.data.Property
import org.apache.olingo.commons.api.data.ValueType
import com.dpbird.odata.edm.OdataOfbizEntity
import com.dpbird.basecamp.util.BasecampUtils;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.party.party.PartyHelper
import org.apache.ofbiz.entity.util.EntityQuery

module = "demo.EnumerationType.generateFields.groovy";

//这个方法做为处理语义化字段
def generateFields(Map<String, Object> context){
    List<Entity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
        enumerationType = (GenericValue) entity.getGenericValue();
        //添加一个Property
        entity.addProperty(new Property(null, "testSemanticField", ValueType.PRIMITIVE, "testSemanticField-value"))
    }
    return entityList;
}

//创建Entity
def createEntity(Map<String, Object> context){
    enumEntity = context.parameters.entity;
    Map<String, Object> serviceMap = Util.entityToMap(enumEntity);
    serviceMap.put("userLogin", userLogin);
    Map<String, Object> serviceResult = dispatcher.runSync("createEnumerationType", serviceMap);
    return delegator.findOne("EnumerationType", UtilMisc.toMap("enumTypeId", serviceResult.get("enumTypeId")), false);
}

//更新Entity
def updateEntity(Map<String, Object> context){
    enumEntity = context.parameters.entity;
    Map<String, Object> serviceMap = Util.entityToMap(enumEntity);
    serviceMap.put("userLogin", userLogin);
    serviceMap.put("enumTypeId", enumEntity.getProperty("enumTypeId").getValue().toString());
    Map<String, Object> serviceResult = dispatcher.runSync("updateEnumerationType", serviceMap);
    return delegator.findOne("EnumerationType", UtilMisc.toMap("enumTypeId", serviceMap.get("enumTypeId")), false);
}
//删除Entity
def deleteEntity(Map<String, Object> context){
    enumGenericValue = context.parameters.genericValue;
    Map<String, Object> serviceMap = new HashMap<>();
    serviceMap.put("userLogin", userLogin);
    serviceMap.put("enumTypeId", enumGenericValue.getString("enumTypeId"));
    Map<String, Object> serviceResult = dispatcher.runSync("deleteEnumerationType", serviceMap);

}


