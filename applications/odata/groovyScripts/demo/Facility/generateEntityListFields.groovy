package com.dpbird.basecamp

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
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.util.EntityUtil
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.party.party.PartyHelper
import org.apache.ofbiz.entity.util.EntityQuery

module = "demo.Facility.generateFields.groovy";

//这个方法做为处理语义化字段
def generateFields(Map<String, Object> context){
    List<Entity> entityList = context.parameters.entityList;
    def each = entityList.each { entity ->
        OdataOfbizEntity odataOfbizEntity = (OdataOfbizEntity) entity;
        facility = (GenericValue) entity.getGenericValue();

        List<GenericValue> list = EntityQuery.use(delegator).from("Product").queryPagedList(0, 2).getData();
        String productId1 = list.get(0).getString("productId")
        String productId2 = list.get(1).getString("productId")
        entity.addProperty(new Property(null, "semanticProductId1", ValueType.PRIMITIVE, productId1))
        entity.addProperty(new Property(null, "semanticProductId2", ValueType.PRIMITIVE, productId2))

        GenericValue party = EntityQuery.use(delegator).from("Party").queryFirst()
        entity.addProperty(new Property(null, "semanticPartyId1", ValueType.PRIMITIVE, party.getString("partyId")))

        //添加一个复杂类型字段
        ComplexValue complexValue = new ComplexValue();
        List<Property> value = complexValue.getValue();
        value.add(new Property(null, "testProperty1", ValueType.PRIMITIVE, "test_String"));
        value.add(new Property(null, "testProperty2", ValueType.PRIMITIVE, new BigDecimal("11.22")));
        entity.addProperty(new Property("com.dpbird.TestComplexProperty", "testComplex", ValueType.COMPLEX, complexValue))

    }
    return entityList;
}



