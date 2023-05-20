package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.handler.EntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @date 2022/11/7
 */
public class DemoPartyInfoHandler implements EntityHandler {
    @Override
    public Map<String, Object> findOne(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("partyId", primaryKey.get("partyId"));
        resultMap.put("displayName", "testName_" + primaryKey.get("partyId"));
        return resultMap;
    }

    @Override
    public HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, QueryOption> queryOption, Map<String, Object> navigationParam) throws OfbizODataException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        if (UtilValidate.isEmpty(navigationParam)) {
            for (int i = 0; i < 5; i++) {
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("partyId", "0000" + i);
                resultMap.put("displayName", "testName_" + "0000" + i);
                resultList.add(resultMap);
            }
            return new HandlerResults(5, resultList);
        } else {
            //from Navigation
            List<String> infoNames = (List<String>)navigationParam.get("infoNames");
            for (int i = 0; i < infoNames.size(); i++) {
                resultList.add(UtilMisc.toMap("partyId", i, "displayName", infoNames.get(i)));
            }
            return new HandlerResults(infoNames.size(), resultList);
        }
    }

    @Override
    public HandlerResults findApply(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, QueryOption> queryOptions, EntityCondition applyCondition) throws OfbizODataException {
        return null;
    }

    @Override
    public Map<String, Object> create(Entity entityToWrite, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> createParam) {
        return null;
    }

    @Override
    public Map<String, Object> update(Map<String, Object> primaryKey, Entity entityToWrite, Map<String, Object> odataContext,
                                      EdmBindingTarget edmBindingTarget, Map<String, Object> createParam) {
        return null;
    }

    @Override
    public void delete(Entity entityToDelete, Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> deleteParam) throws OfbizODataException {

    }


}
