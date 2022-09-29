package com.dpbird.app

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

module = "demo.PartyInfo.findSemanticEntities.groovy";

def findSemanticEntities(Map<String, Object> context) {
    List<Entity> entityList = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
        OdataOfbizEntity entity = new OdataOfbizEntity();
        entity.addProperty(new Property(null,"partyId",ValueType.PRIMITIVE,"t_id_" + i))
        entity.addProperty(new Property(null,"displayName",ValueType.PRIMITIVE,"t_dnABC"))
        entityList.add(entity);
    }
    return entityList;
}



