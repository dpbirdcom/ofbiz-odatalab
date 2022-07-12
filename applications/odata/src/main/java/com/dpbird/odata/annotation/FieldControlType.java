package com.dpbird.odata.annotation;

public enum FieldControlType {
    Hidden("Common.FieldControlType/Hidden", 0),
    Inapplicable("Common.FieldControlType/Inapplicable", 0),
    ReadOnly("Common.FieldControlType/ReadOnly", 1),
    Optional("Common.FieldControlType/Optional", 3),
    Mandatory("Common.FieldControlType/Mandatory", 7);

    private String name;
    private int index;

    private FieldControlType(String name, int index) {
        this.name = name;
        this.index = index;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
