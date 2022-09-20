import com.dpbird.odata.Util
import com.dpbird.odata.edm.OdataOfbizEntity
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.UtilMisc
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.Delegator
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.product.product.ProductWorker
import org.apache.ofbiz.product.store.ProductStoreWorker
import org.apache.ofbiz.service.ServiceUtil
import org.apache.olingo.commons.api.data.Entity
import org.apache.olingo.commons.api.data.ComplexValue
import org.apache.olingo.commons.api.data.Property
import org.apache.olingo.commons.api.data.ValueType
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.olingo.server.api.uri.queryoption.SelectOption

module = "demo.Product.generateFields.groovy";

//这个方法做为处理语义化字段
def generateFields(Map<String, Object> context){
    SelectOption selectOption = context.parameters.get("selectOption");
    String[] fieldsToSelect = selectOption != null && selectOption.getText() != null ? selectOption.getText().split(",") : ["testIds"]
    List<Entity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
        product = (GenericValue) entity.getGenericValue();
        priceCalculated = false;
        //now
        nowTimestamp = UtilDateTime.nowTimestamp()
        for (String selectFieldName : fieldsToSelect) {
            Property property = null;
            if (selectFieldName == "testIds") {
                List<String> ids = ["12345","54321","88888","66666","00000"];
                property = new Property(null, "testIds", ValueType.COLLECTION_PRIMITIVE, ids);
            }
            if (property != null) {
                entity.addProperty(property);
            }
        }
    }
    return entityList;
}

//List<Entity> entityList = parameters.get("entityList");
//entityList.each { entity ->
//    OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
//    product = odataOfbizEntity.getGenericValue();
//    priceCalculated = false;
//    //now
//    nowTimestamp = UtilDateTime.nowTimestamp()
//    for (String selectFieldName : fieldsToSelect) {
//        Property property = null;
//        if (selectFieldName == "testIds") {
//            List<String> ids = ["12345","54321","88888","66666","00000"];
//            property = new Property(null, "testIds", ValueType.COLLECTION_PRIMITIVE, ids);
//        }
//        if (property != null) {
//            entity.addProperty(property);
//        }
//    }
//}
//context.put("entityList", entityList);
