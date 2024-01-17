package com.dpbird.odata.annotation;

public enum VisualizationType {
    Rating("UI.VisualizationType/Rating"),
    Progress("UI.VisualizationType/Progress"),
    Number("UI.VisualizationType/Number");

    private String name;

    private VisualizationType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
