package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class FieldGroup extends Term{
    private String label;
    private List<DataFieldAbstract> data = new ArrayList<>();

    public FieldGroup(String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.termName = "UI.FieldGroup";
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<DataFieldAbstract> getData() {
        return data;
    }

    public void setData(List<DataFieldAbstract> data) {
        this.data = data;
    }
    public void addData(DataFieldAbstract dataField) {
        data.add(dataField);
    }
}
