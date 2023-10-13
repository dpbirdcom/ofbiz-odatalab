package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class LineItem extends Term {
    private List<DataFieldAbstract> dataFields = new ArrayList<>();
    private CriticalityType criticality;
    private String criticalityPath;

    public LineItem(String qualifier) {
        super(qualifier);
        this.termName = "UI.LineItem";
        this.appliesTo = "EntityType";
    }

    public List<DataFieldAbstract> getDataFields() {
        return dataFields;
    }

    public void setDataFields(List<DataFieldAbstract> dataFields) {
        this.dataFields = dataFields;
    }

    public void addDataField(DataFieldAbstract dataField) {
        dataFields.add(dataField);
    }

    public CriticalityType getCriticality() {
        return criticality;
    }

    public void setCriticality(CriticalityType criticality) {
        this.criticality = criticality;
    }

    public String getCriticalityPath() {
        return criticalityPath;
    }

    public void setCriticalityPath(String criticalityPath) {
        this.criticalityPath = criticalityPath;
    }
}
