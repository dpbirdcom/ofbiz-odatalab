package com.dpbird.odata.annotation;

public enum SelectionRangeSignType {
    I("UI.SelectionRangeSignType/I"),
    E("UI.SelectionRangeOptionType/E");

    private final String name;

    private SelectionRangeSignType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
