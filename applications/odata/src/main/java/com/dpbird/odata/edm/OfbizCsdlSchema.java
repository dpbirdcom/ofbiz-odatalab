package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlSchema;

import java.util.List;

public class OfbizCsdlSchema extends CsdlSchema {
    private List<String> mainEntityTypes;

    public OfbizCsdlSchema() {
        super();
    }

    public List<String> getMainEntityTypes() {
        return mainEntityTypes;
    }

    public void setMainEntityTypes(List<String> mainEntityTypes) {
        this.mainEntityTypes = mainEntityTypes;
    }
}
