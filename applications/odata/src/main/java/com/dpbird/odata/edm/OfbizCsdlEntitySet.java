package com.dpbird.odata.edm;

import com.dpbird.odata.annotation.Term;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;

import java.util.List;
import java.util.Map;

public class OfbizCsdlEntitySet extends CsdlEntitySet {
    private EntityCondition entityCondition;
    // 这个Map是entityCondition的Map体现，目的是新建EntitySet时候要用
    private Map<String, Object> conditionMap;
    private String handler;
    private String conditionStr;
    private String deleteHidden;
    private String updateHidden;
    private String createHidden;

    public OfbizCsdlEntitySet(String conditionStr) {
        super();
        this.conditionStr = conditionStr;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public String getConditionStr() {
        return conditionStr;
    }

    public void setConditionStr(String conditionStr) {
        this.conditionStr = conditionStr;
    }

    public String getDeleteHidden() {
        return deleteHidden;
    }

    public void setDeleteHidden(String deleteHidden) {
        this.deleteHidden = deleteHidden;
    }

    public String getUpdateHidden() {
        return updateHidden;
    }

    public void setUpdateHidden(String updateHidden) {
        this.updateHidden = updateHidden;
    }

    public String getCreateHidden() {
        return createHidden;
    }

    public void setCreateHidden(String createHidden) {
        this.createHidden = createHidden;
    }
}
