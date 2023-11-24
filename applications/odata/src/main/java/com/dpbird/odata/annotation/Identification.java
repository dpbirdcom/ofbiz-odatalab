package com.dpbird.odata.annotation;

import java.util.ArrayList;
import java.util.List;

public class Identification extends Term {
    private List<DataFieldAbstract> dataFields = new ArrayList<>();

    public Identification(String qualifier) {
        super(qualifier);
        this.termName = "UI.Identification";
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

}
