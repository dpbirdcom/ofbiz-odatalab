package com.dpbird.odata.annotation;

public class HeaderInfoType {
    private String typeName;
    private String typeNamePlural;
    private DataFieldAbstract title;
    private DataFieldAbstract description;
    private String imageUrl;
    private String typeImageUrl;
    private String initials;

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeNamePlural() {
        return typeNamePlural;
    }

    public void setTypeNamePlural(String typeNamePlural) {
        this.typeNamePlural = typeNamePlural;
    }

    public DataFieldAbstract getTitle() {
        return title;
    }

    public void setTitle(DataFieldAbstract title) {
        this.title = title;
    }

    public DataFieldAbstract getDescription() {
        return description;
    }

    public void setDescription(DataFieldAbstract description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getTypeImageUrl() {
        return typeImageUrl;
    }

    public void setTypeImageUrl(String typeImageUrl) {
        this.typeImageUrl = typeImageUrl;
    }

    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }
}