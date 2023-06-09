package com.dpbird.odata.handler.draftEvent;

import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

import java.util.Map;

/**
 * StickySession newAction event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface NewEvent {

    void before(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget);

    void after(OdataOfbizEntity ofbizEntity);
}
