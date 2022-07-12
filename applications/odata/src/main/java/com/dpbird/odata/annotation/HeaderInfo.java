package com.dpbird.odata.annotation;

public class HeaderInfo extends Term {
    private HeaderInfoType headerInfoType;

    public HeaderInfo(String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.termName = "UI.HeaderInfo";
    }

    public HeaderInfoType getHeaderInfoType() {
        return headerInfoType;
    }

    public void setHeaderInfoType(HeaderInfoType headerInfoType) {
        this.headerInfoType = headerInfoType;
    }
}
