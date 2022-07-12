package com.dpbird.odata.annotation;

public class DataFieldWithNavigationPath extends DataField{

    private String target;

    public DataFieldWithNavigationPath(Object value) {
        super(value);
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

}
