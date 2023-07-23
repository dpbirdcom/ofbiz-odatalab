package com.dpbird.odata.handler.annotation;

import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

import java.util.Map;

/**
 * @author scy
 * @date 2023/7/12
 */
public class DraftEventContext {
    private final Delegator delegator;
    private final LocalDispatcher dispatcher;
    private final GenericValue userLogin;
    private final Map<String, Object> oDataContext;
    private final Map<String, Object> actionParameters;
    private final EdmBindingTarget edmBindingTarget;
    private final OdataOfbizEntity odataOfbizEntity;

    public DraftEventContext(Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin, Map<String, Object> oDataContext,
                             Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget, OdataOfbizEntity odataOfbizEntity) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.userLogin = userLogin;
        this.oDataContext = oDataContext;
        this.actionParameters = actionParameters;
        this.edmBindingTarget = edmBindingTarget;
        this.odataOfbizEntity = odataOfbizEntity;
    }

    public Delegator getDelegator() {
        return delegator;
    }

    public LocalDispatcher getDispatcher() {
        return dispatcher;
    }

    public GenericValue getUserLogin() {
        return userLogin;
    }

    public Map<String, Object> getoDataContext() {
        return oDataContext;
    }

    public Map<String, Object> getActionParameters() {
        return actionParameters;
    }

    public EdmBindingTarget getEdmBindingTarget() {
        return edmBindingTarget;
    }

    public OdataOfbizEntity getOdataOfbizEntity() {
        return odataOfbizEntity;
    }
}
