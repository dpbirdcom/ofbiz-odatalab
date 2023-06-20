package com.dpbird.odata.handler.event;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;

import java.util.Map;

/**
 * OData read event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface ReadAfter {
    void readAfter(Map<String, Object> oDataContext, OdataOfbizEntity ofbizEntity) throws OfbizODataException;
}
