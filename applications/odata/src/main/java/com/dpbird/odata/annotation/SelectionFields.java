package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class SelectionFields extends Term{
    private List<String> propertyPaths = new ArrayList<>();

    public SelectionFields(List<String> propertyPaths, String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.propertyPaths = propertyPaths;
        this.termName = "UI.SelectionFields";
    }

    public List<String> getPropertyPaths() {
        return propertyPaths;
    }

    public void setPropertyPaths(List<String> propertyPaths) {
        this.propertyPaths = propertyPaths;
    }

}
