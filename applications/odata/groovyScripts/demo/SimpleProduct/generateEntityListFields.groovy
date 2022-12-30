package com.dpbird.test

import org.apache.ofbiz.base.util.UtilDateTime

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


//这个方法做为处理语义化字段
def generateFields(Map<String, Object> context) {
    List<Entity> entityList = context.parameters.entityList;
    for (int i = 0; i < entityList.size(); i++) {
        Entity entity = entityList.get(i);
        //Decimal
        BigDecimal b1 = new BigDecimal(new Random().nextInt(50)).setScale(3);
        BigDecimal b2 = new BigDecimal(new Random().nextInt(50)).setScale(3);
        entity.addProperty(new Property(null, "semanticDecimal1", ValueType.PRIMITIVE, b1))
        if (i > 7 && i < 15) {
            entity.addProperty(new Property(null, "semanticDecimal2", ValueType.PRIMITIVE, null))
        } else {
            entity.addProperty(new Property(null, "semanticDecimal2", ValueType.PRIMITIVE, b2))
        }

        //String
        entity.addProperty(new Property(null, "semanticString1", ValueType.PRIMITIVE, b1.toString()))
        entity.addProperty(new Property(null, "semanticString2", ValueType.PRIMITIVE, b2.toString()))

        //time
        entity.addProperty(new Property(null, "semanticDateTime1", ValueType.PRIMITIVE, UtilDateTime.nowTimestamp()))
        entity.addProperty(new Property(null, "semanticDateTime2", ValueType.PRIMITIVE, UtilDateTime.nowTimestamp()))

    }
    return entityList;
}



