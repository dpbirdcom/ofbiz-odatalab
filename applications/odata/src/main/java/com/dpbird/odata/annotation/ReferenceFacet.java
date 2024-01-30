package com.dpbird.odata.annotation;

public class ReferenceFacet {

    private String id;
    private String label;
    private String target;
    private String hidden = null;
    private LogicalExpression hiddenExpr = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getHidden() {
        return hidden;
    }

    public void setHidden(String hidden) {
        this.hidden = hidden;
    }

    public LogicalExpression getHiddenExpr() {
        return hiddenExpr;
    }

    public void setHiddenExpr(LogicalExpression hiddenExpr) {
        this.hiddenExpr = hiddenExpr;
    }
}
