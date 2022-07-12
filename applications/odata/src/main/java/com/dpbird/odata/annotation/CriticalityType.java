package com.dpbird.odata.annotation;

public enum CriticalityType {
    VeryNegative("UI.CriticalityType/VeryNegative"),
    Neutral("UI.CriticalityType/Neutral"),
    Negative("UI.CriticalityType/Negative"),
    Critical("UI.CriticalityType/Critical"),
    Positive("UI.CriticalityType/Positive"),
    VeryPositive("UI.CriticalityType/VeryPositive");

    private String name;

    private CriticalityType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
