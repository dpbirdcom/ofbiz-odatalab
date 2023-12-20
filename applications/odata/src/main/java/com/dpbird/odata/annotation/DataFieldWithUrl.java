package com.dpbird.odata.annotation;

public class DataFieldWithUrl extends DataFieldAbstract{
    private String value;
    private String url;



    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
