package com.dpbird.odata.handler.event;

import com.dpbird.odata.OfbizODataException;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;

import java.util.Map;

/**
 * OData read event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface ReadBefore {
    void readBefore(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget) throws OfbizODataException;
}
