package com.dpbird.odata;

import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.uri.UriResource;


/**
 * 用来存储UriResource每一段的数据
 */
public class OdataParts {
    private EdmBindingTarget edmBindingTarget;
    private EdmEntityType edmEntityType;
    private UriResource uriResource;
    private Object entityData;

    public OdataParts(EdmBindingTarget edmBindingTarget, EdmEntityType edmEntityType,
                      UriResource uriResource, Object entityData) {
        this.edmBindingTarget = edmBindingTarget;
        this.edmEntityType = edmEntityType;
        this.uriResource = uriResource;
        this.entityData = entityData;
    }

    public EdmBindingTarget getEdmBindingTarget() {
        return edmBindingTarget;
    }

    public void setEdmBindingTarget(EdmBindingTarget edmBindingTarget) {
        this.edmBindingTarget = edmBindingTarget;
    }

    public OdataParts() {
    }

    public EdmEntityType getEdmEntityType() {
        return edmEntityType;
    }

    public void setEdmEntityType(EdmEntityType edmEntityType) {
        this.edmEntityType = edmEntityType;
    }

    public UriResource getUriResource() {
        return uriResource;
    }

    public void setUriResource(UriResource uriResource) {
        this.uriResource = uriResource;
    }

    public Object getEntityData() {
        return entityData;
    }

    public void setEntityData(Object entityData) {
        this.entityData = entityData;
    }

    @Override
    public String toString() {
        return "OdataParts{" +
                "edmBindingTarget=" + edmBindingTarget +
                ", edmEntityType=" + edmEntityType +
                ", uriResource=" + uriResource +
                ", entityData=" + entityData +
                '}';
    }
}
