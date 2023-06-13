package com.dpbird.odata.handler.draftEvent;

import com.dpbird.odata.OfbizODataException;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

import java.util.Map;

/**
 * StickySession newAction event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface DraftNewBefore {
    void newBefore(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws OfbizODataException;
}
