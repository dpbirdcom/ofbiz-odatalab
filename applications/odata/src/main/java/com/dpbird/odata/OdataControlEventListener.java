package com.dpbird.odata;

import com.dpbird.odata.processor.DataModifyActions;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.serialize.XmlSerializer;
import org.apache.ofbiz.entity.transaction.TransactionUtil;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.webapp.control.ControlActivationEventListener;
import org.apache.ofbiz.webapp.control.ControlEventListener;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * @author scy
 * @date 2022/2/8
 */
public class OdataControlEventListener extends ControlEventListener {

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();

        // Finalize the Visit
        boolean beganTransaction = false;
        try {
            beganTransaction = TransactionUtil.begin();

            // instead of using this message, get directly from session attribute so it won't create a new one: GenericValue visit = VisitHandler.getVisit(session);
            GenericValue visit = (GenericValue) session.getAttribute("visit");
            if (visit != null) {
                Delegator delegator = visit.getDelegator();
                visit = EntityQuery.use(delegator).from("Visit").where("visitId", visit.get("visitId")).queryOne();
                if (visit != null) {
                    visit.set("thruDate", new Timestamp(session.getLastAccessedTime()));
                    visit.store();
                }
            } else {
                Debug.logInfo("Could not find visit value object in session [" + ControlActivationEventListener.showSessionId(session) + "] that is being destroyed", module);
            }

            // Store the UserLoginSession
            String userLoginSessionString = getUserLoginSession(session);
            GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
            if (userLogin != null && userLoginSessionString != null) {
                GenericValue userLoginSession = null;
                userLoginSession = userLogin.getRelatedOne("UserLoginSession", false);

                if (userLoginSession == null) {
                    userLoginSession = userLogin.getDelegator().makeValue("UserLoginSession",
                            UtilMisc.toMap("userLoginId", userLogin.getString("userLoginId")));
                    userLogin.getDelegator().create(userLoginSession);
                }
                userLoginSession.set("savedDate", UtilDateTime.nowTimestamp());
                userLoginSession.set("sessionData", userLoginSessionString);
                userLoginSession.store();
            }

            countDestroySession();
            if (userLogin != null){
                clearUserDraftData(userLogin.getDelegator(), userLogin);
            }
            Debug.logInfo("Destroying session: " + ControlActivationEventListener.showSessionId(session), module);
            this.logStats(session, visit);
        } catch (GenericEntityException | OfbizODataException e) {
            try {
                // only rollback the transaction if we started one...
                TransactionUtil.rollback(beganTransaction, "Error saving information about closed HttpSession", e);
            } catch (GenericEntityException e2) {
                Debug.logError(e2, "Could not rollback transaction: " + e2.toString(), module);
            }

            Debug.logError(e, "Error in session destuction information persistence", module);
        } finally {
            // only commit the transaction if we started one... this will throw an exception if it fails
            try {
                TransactionUtil.commit(beganTransaction);
            } catch (GenericEntityException e) {
                Debug.logError(e, "Could not commit transaction for update visit for session destuction", module);
            }
        }
    }

    private String getUserLoginSession(HttpSession session) {
        Map<String, ?> userLoginSession = UtilGenerics.cast(session.getAttribute("userLoginSession"));

        String sessionData = null;
        if (UtilValidate.isNotEmpty(userLoginSession)) {
            try {
                sessionData = XmlSerializer.serialize(userLoginSession);
            } catch (Exception e) {
                Debug.logWarning(e, "Problems serializing UserLoginSession", module);
            }
        }
        return sessionData;
    }

    /**
     * 清除当前用户所有正在编辑的draft数据
     */
    private void clearUserDraftData(Delegator delegator, GenericValue userLogin) throws OfbizODataException {
        Debug.log("====================== Clear current draft data");
        EntityCondition entityCondition = EntityCondition.makeCondition(UtilMisc.toList(
                EntityCondition.makeCondition("createdByUser", EntityOperator.EQUALS, userLogin.getString("userLoginId")),
                EntityCondition.makeCondition("parentDraftUUID", EntityOperator.EQUALS, null)));
        try {
            List<GenericValue> draftAdministrativeData = EntityQuery.use(delegator).from("DraftAdministrativeData").where(entityCondition).queryList();
            for (GenericValue draftAdministrativeDatum : draftAdministrativeData) {
                DataModifyActions.clearEntityDraft(UtilMisc.toMap("delegator", delegator), draftAdministrativeDatum.getString("draftUUID"));
            }
        } catch (GenericEntityException | OfbizODataException e) {
            throw new OfbizODataException(e.getMessage());
        }

    }

}
