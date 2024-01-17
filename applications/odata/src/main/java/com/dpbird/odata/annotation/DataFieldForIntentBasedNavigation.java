package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class DataFieldForIntentBasedNavigation extends DataFieldAbstract{
    private String semanticObject;
    private String action;
    private Boolean inLine = false;

    private List<Mapping> mappings = new ArrayList<>();

    public String getSemanticObject() {
        return semanticObject;
    }

    public void setSemanticObject(String semanticObject) {
        this.semanticObject = semanticObject;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Boolean getInLine() {
        return inLine;
    }

    public void setInLine(Boolean inLine) {
        this.inLine = inLine;
    }

    public List<Mapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public static class Mapping {
        private String localProperty;
        private String semanticObjectProperty;


        public Mapping(String localProperty, String semanticObjectProperty) {
            this.localProperty = localProperty;
            this.semanticObjectProperty = semanticObjectProperty;
        }

        public String getLocalProperty() {
            return localProperty;
        }

        public void setLocalProperty(String localProperty) {
            this.localProperty = localProperty;
        }

        public String getSemanticObjectProperty() {
            return semanticObjectProperty;
        }

        public void setSemanticObjectProperty(String semanticObjectProperty) {
            this.semanticObjectProperty = semanticObjectProperty;
        }
    }

}
