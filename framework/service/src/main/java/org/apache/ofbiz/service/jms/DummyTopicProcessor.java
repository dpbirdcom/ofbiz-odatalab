package org.apache.ofbiz.service.jms;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.jms.Message;

public class DummyTopicProcessor {
    public static final String module = DummyTopicProcessor.class.getName();

    public static void onMessage(LocalDispatcher dispatcher, Message message) {
        Debug.logInfo("running into DummyTopicProcessor.onMessage with message: " + message, module);
    }
}
