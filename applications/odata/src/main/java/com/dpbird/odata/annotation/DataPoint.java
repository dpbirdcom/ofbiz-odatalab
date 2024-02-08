package com.dpbird.odata.annotation;

import java.math.BigDecimal;

/**
 * @author scy
 * @date 2023/11/14
 */
public class DataPoint extends Term {
    private String value;
    private String title;
    private CriticalityType criticality;
    private CriticalityRepresentationType criticalityRepresentationType; // WithIcon, WithoutIcon
    private VisualizationType visualization;
    private String criticalityPath;
    private String targetValue;

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

    public String getTargetValue() { return targetValue; }

    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }

    public VisualizationType getVisualization() { return visualization; }

    public void setVisualization(VisualizationType visualization) { this.visualization = visualization; }

    public CriticalityRepresentationType getCriticalityRepresentationType() {
        return criticalityRepresentationType;
    }

    public void setCriticalityRepresentationType(CriticalityRepresentationType criticalityRepresentationType) {
        this.criticalityRepresentationType = criticalityRepresentationType;
    }
}
