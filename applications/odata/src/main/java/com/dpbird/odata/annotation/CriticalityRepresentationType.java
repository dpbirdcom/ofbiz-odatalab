package com.dpbird.odata.annotation;

public enum CriticalityRepresentationType {
    WithIcon("UI.CriticalityRepresentationType/WithIcon"),
    WithoutIcon("UI.CriticalityRepresentationType/WithoutIcon"),;

    private final String name;

    private CriticalityRepresentationType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
