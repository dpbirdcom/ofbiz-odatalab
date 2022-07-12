package org.apache.ofbiz.service.jms;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class GenericTopicListener extends JmsTopicListener{
    /**
     * Creates a new JmsTopicListener - Should only be called by the JmsListenerFactory.
     *
     * @param delegator
     * @param jndiServer
     * @param jndiName
     * @param topicName
     * @param userName
     * @param password
     */
    public GenericTopicListener(Delegator delegator, String jndiServer, String jndiName, String topicName, String userName, String password) {
        super(delegator, jndiServer, jndiName, topicName, userName, password);
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof MapMessage) {
            super.onMessage(message);
        } else {
            String topicProcessorClassName = UtilProperties.getPropertyValue("service", "jmsTopicProcessor");
            String methodName = "onMessage";
            try {
                Class topicProcessorClass = Class.forName(topicProcessorClassName);
                Method method = topicProcessorClass.getMethod(methodName, LocalDispatcher.class, Message.class);
                method.invoke(topicProcessorClass, dispatcher, message);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            return;
        }
    }
}
