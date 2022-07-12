package com.dpbird.odata.annotation;

public class DataField extends DataFieldAbstract{
    private Object value;

    public DataField(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
