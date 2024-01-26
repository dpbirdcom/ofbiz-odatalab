package com.dpbird.odata.annotation;

public enum SelectionRangeOptionType {
    EQ("UI.SelectionRangeOptionType/EQ"),
    BT("UI.SelectionRangeOptionType/BT"),
    LE("UI.SelectionRangeOptionType/LE"),
    GE("UI.SelectionRangeOptionType/GE"),
    NE("UI.SelectionRangeOptionType/NE"),
    NB("UI.SelectionRangeOptionType/NB"),
    NP("UI.SelectionRangeOptionType/NP"),
    GT("UI.SelectionRangeOptionType/GT"),
    LT("UI.SelectionRangeOptionType/LT"),
    CP("UI.SelectionRangeOptionType/CP");

    private final String name;

    private SelectionRangeOptionType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
