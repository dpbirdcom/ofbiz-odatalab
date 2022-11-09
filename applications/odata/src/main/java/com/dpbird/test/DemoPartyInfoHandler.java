package com.dpbird.test;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.handler.EntityHandler;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

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
    public List<Map<String, Object>> findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget) throws OfbizODataException {
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("partyId", "0000" + i);
            resultMap.put("displayName", "testName_" + "0000" + i);
            resultList.add(resultMap);
        }
        return resultList;
    }

}
