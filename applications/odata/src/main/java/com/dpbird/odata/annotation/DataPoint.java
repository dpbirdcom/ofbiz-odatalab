package com.dpbird.odata.annotation;

/**
 * @author scy
 * @date 2023/11/14
 */
public class DataPoint extends Term {
    private String value;
    private String title;
    private CriticalityType criticality;
    private String criticalityPath;

    public DataPoint(String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.termName = "UI.DataPoint";
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
