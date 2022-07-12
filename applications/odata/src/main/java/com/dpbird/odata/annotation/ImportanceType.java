package com.dpbird.odata.annotation;

public enum ImportanceType {
    High("UI.ImportanceType/High"),
    Medium("UI.ImportanceType/Medium"),
    Low("UI.ImportanceType/Low");

    private String name;

    private ImportanceType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
