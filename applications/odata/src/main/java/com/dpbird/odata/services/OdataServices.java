package com.dpbird.odata.services;

import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import java.sql.Timestamp;
import java.util.*;

/**
 * openUI的service
 **/
public class OdataServices {


    public final static String module = OdataServices.class.getName();
    public static final String resource = "OrderUiLabels";
    public static final String resourceError = "odataErrorUiLabels";

    public static Map<String, Object> getCategoryTree(Delegator delegator, String parentId, int limitSize, int level) {
        Map<String, Object> categoryMap = new HashMap<>();
        try {
            List<GenericValue> rollups = EntityQuery.use(delegator).from("ProductCategoryRollup").where("parentProductCategoryId", parentId).queryList();
            rollups = EntityUtil.filterByDate(rollups);

            categoryMap.put("category", delegator.findOne("ProductCategory", true, UtilMisc.toMap("productCategoryId", parentId)));
            List<Map<String, Object>> children = new ArrayList<>();
            if (level + 1 < limitSize) {
                if (UtilValidate.isNotEmpty(rollups)) {
                    for (GenericValue parent : rollups) {
                        // 16 method
                        GenericValue cv = parent.getRelatedOne("CurrentProductCategory", true);
                        if (UtilValidate.isNotEmpty(cv)) {
                            children.add(getCategoryTree(delegator, cv.getString("productCategoryId"), limitSize, level + 1));
                        }
                    }
                    categoryMap.put("children", children);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }

        return categoryMap;
    }

    public static Map<String, Object> createNoteData(DispatchContext ctx, Map<String, ?> context) {
        Delegator delegator = ctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Timestamp noteDateTime = (Timestamp) context.get("noteDateTime");
        String noteParty = (String) context.get("noteParty");
        String noteName = (String) context.get("noteName");
        String noteInfo = (String) context.get("noteInfo");
        String partyNoteTypeId = (String) context.get("partyNoteTypeId");
        String statusId = (String) context.get("statusId");
        String description = (String) context.get("description");
        String noteId = delegator.getNextSeqId("NoteData");
        Locale locale = (Locale) context.get("locale");

        if (noteDateTime == null) {
        	noteDateTime = UtilDateTime.nowTimestamp();
        }
        // check for a party id
        if (noteParty == null) {
            if (userLogin != null && userLogin.get("partyId") != null)
            	noteParty = userLogin.getString("partyId");
        }
        Map<String, Object> fields = UtilMisc.toMap("partyNoteTypeId",partyNoteTypeId,
                "statusId",statusId,"description",description,"noteId", noteId, "noteName", noteName, "noteInfo", noteInfo,
                "noteParty", noteParty, "noteDateTime", noteDateTime);
        try {
            GenericValue newValue = delegator.makeValue("NoteData", fields);
            delegator.create(newValue);
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError(UtilProperties.getMessage(resource, "CommonNoteCannotBeUpdated", UtilMisc.toMap("errorString", e.getMessage()), locale));
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("noteId", noteId);
        result.put("noteParty", noteParty);
        return result;
    }
}
