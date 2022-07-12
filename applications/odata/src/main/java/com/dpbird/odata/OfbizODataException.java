package com.dpbird.odata;

import org.apache.http.HttpStatus;
import org.apache.olingo.commons.api.ex.ODataException;

public class OfbizODataException extends ODataException {
    private String oDataErrorCode = String.valueOf(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    public OfbizODataException(String msg) {
        super(msg);
    }

    public OfbizODataException(String oDataErrorCode, String msg) {
        super(msg);
        this.oDataErrorCode = oDataErrorCode;
    }

    public String getODataErrorCode() {
        return oDataErrorCode;
    }

    public void setODataErrorCode(String oDataErrorCode) {
        this.oDataErrorCode = oDataErrorCode;
    }

}
