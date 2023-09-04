package com.dpbird.odata.services;

import org.apache.ofbiz.base.util.GeneralException;

/**
 * 全局通用异常
 *
 * @author scy
 * @date 2023/9/4
 */
public class OfbizServiceException extends GeneralException {

    public OfbizServiceException(String msg) {
        super(msg);
    }

}
