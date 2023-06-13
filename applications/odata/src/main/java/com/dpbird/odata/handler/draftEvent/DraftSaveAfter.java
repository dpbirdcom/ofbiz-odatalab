package com.dpbird.odata.handler.draftEvent;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;

import java.util.Map;

/**
 * StickySession saveAction event
 *
 * @author scy
 * @date 2023/6/9
 */
public interface DraftSaveAfter {
    void saveAfter(Map<String, Object> oDataContext, OdataOfbizEntity ofbizEntity) throws OfbizODataException;
}
