package com.dpbird.odata.annotation;

public class DataFieldForAnnotation extends DataFieldAbstract{
    private String target;
    private OperationGroupingType invocationGrouping;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public OperationGroupingType getInvocationGrouping() {
        return invocationGrouping;
    }

    public void setInvocationGrouping(OperationGroupingType invocationGrouping) {
        this.invocationGrouping = invocationGrouping;
    }
}
