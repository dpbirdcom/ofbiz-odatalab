package com.dpbird.odata.events;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author scy
 * @date 2023/10/10
 */
public class ActionEvents {
    /**
     * Create entity
     */
    public static Object createEntity(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        OfbizAppEdmProvider edmProvider = (OfbizAppEdmProvider) oDataContext.get("edmProvider");
        HttpServletRequest request = (HttpServletRequest) oDataContext.get("httpServletRequest");
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmBindingTarget.getEntityType().getFullQualifiedName());
        //get service
        String createService = Util.getEntityActionService(csdlEntityType, csdlEntityType.getOfbizEntity(), "create", delegator);
        try {
            for (Map.Entry<String, Object> entry : csdlEntityType.getDefaultValueProperties().entrySet()) {
                actionParameters.putIfAbsent(entry.getKey(), Util.parseVariable(entry.getValue(), request));
            }
            Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, createService, actionParameters, userLogin, null, null);
            Map<String, Object> result = dispatcher.runSync(createService, validFieldsForService);
            //Return Entity
            ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
            List<String> primaryKeys = modelEntity.getPkFieldNames();
            if (result.keySet().containsAll(primaryKeys)) {
                Map<String, Object> pkMap = new HashMap<>();
                for (String primaryKey : primaryKeys) {
                    pkMap.put(primaryKey, result.get(primaryKey));
                }
                return delegator.findOne(csdlEntityType.getOfbizEntity(), pkMap, false);
            }
        } catch (GeneralException e) {
            throw new OfbizODataException(e.getMessage());
        }
        return null;
    }

}
