package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlSingleton;

public class OfbizCsdlSingleton extends CsdlSingleton {
    private String handlerMethod;

    public String getHandlerMethod() {
        return handlerMethod;
    }

    public void setHandlerMethod(String handlerMethod) {
        this.handlerMethod = handlerMethod;
    }
}
