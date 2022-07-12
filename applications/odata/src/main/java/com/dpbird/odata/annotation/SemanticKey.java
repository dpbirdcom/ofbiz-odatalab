package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class SemanticKey extends Term{
    private List<String> propertyPaths = new ArrayList<>();

    public SemanticKey(List<String> propertyPaths, String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.propertyPaths = propertyPaths;
        this.termName = "Common.SemanticKey";
    }

    public List<String> getPropertyPaths() {
        return propertyPaths;
    }

    public void setPropertyPaths(List<String> propertyPaths) {
        this.propertyPaths = propertyPaths;
    }
}
