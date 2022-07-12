package com.dpbird.odata.annotation;

public enum OperationGroupingType {
    Isolated("UI.OperationGroupingType/Isolated"),
    ChangeSet("UI.OperationGroupingType/ChangeSet");

    private String name;

    private OperationGroupingType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
