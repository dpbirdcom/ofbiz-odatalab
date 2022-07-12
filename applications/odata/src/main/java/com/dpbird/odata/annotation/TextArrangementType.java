package com.dpbird.odata.annotation;

public enum TextArrangementType {
    TextFirst("UI.TextArrangementType/TextFirst"),
    TextLast("UI.TextArrangementType/TextLast"),
    TextSeparate("UI.TextArrangementType/TextSeparate"),
    TextOnly("UI.TextArrangementType/TextOnly");

    private String name;

    private TextArrangementType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
