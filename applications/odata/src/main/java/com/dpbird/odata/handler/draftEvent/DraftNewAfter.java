package com.dpbird.odata.handler.draftEvent;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;

import java.util.Map;

/**
 * StickySession newAction event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface DraftNewAfter {
    void newAfter(Map<String, Object> oDataContext, Map<String, Object> actionParameters, OdataOfbizEntity ofbizEntity) throws OfbizODataException;
}
