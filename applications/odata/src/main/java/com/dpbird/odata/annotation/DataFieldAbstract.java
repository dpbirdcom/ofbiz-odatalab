package com.dpbird.odata.annotation;

public abstract class DataFieldAbstract {
    private String label;
    private CriticalityType criticality; //
    private String criticalityPath;
    private CriticalityRepresentationType criticalityRepresentationType; // WithIcon, WithoutIcon
    private String iconUrl;
    private ImportanceType importance;
    private String hidden = null;
    private LogicalExpression hiddenExpr = null;
    private LogicalExpression criticalityExpr = null;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CriticalityType getCriticality() {
        return criticality;
    }

    public void setCriticality(CriticalityType criticality) {
        this.criticality = criticality;
    }

    public CriticalityRepresentationType getCriticalityRepresentationType() {
        return criticalityRepresentationType;
    }

    public void setCriticalityRepresentationType(CriticalityRepresentationType criticalityRepresentationType) {
        this.criticalityRepresentationType = criticalityRepresentationType;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public ImportanceType getImportance() {
        return importance;
    }

    public void setImportance(ImportanceType importance) {
        this.importance = importance;
    }

    public String getHidden() {
        return hidden;
    }

    public void setHidden(String hidden) {
        this.hidden = hidden;
    }

    public String getCriticalityPath() {
        return criticalityPath;
    }

    public void setCriticalityPath(String criticalityPath) {
        this.criticalityPath = criticalityPath;
    }

    public LogicalExpression getHiddenExpr() {
        return hiddenExpr;
    }

    public void setHiddenExpr(LogicalExpression hiddenExpr) {
        this.hiddenExpr = hiddenExpr;
    }

    public LogicalExpression getCriticalityExpr() {
        return criticalityExpr;
    }

    public void setCriticalityExpr(LogicalExpression criticalityExpr) {
        this.criticalityExpr = criticalityExpr;
    }
}
