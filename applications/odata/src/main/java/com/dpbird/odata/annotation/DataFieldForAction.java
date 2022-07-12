package com.dpbird.odata.annotation;

public class DataFieldForAction extends DataFieldForActionAbstract{
    private String action;
    private OperationGroupingType invocationGrouping;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public OperationGroupingType getInvocationGrouping() {
        return invocationGrouping;
    }

    public void setInvocationGrouping(OperationGroupingType invocationGrouping) {
        this.invocationGrouping = invocationGrouping;
    }
}
