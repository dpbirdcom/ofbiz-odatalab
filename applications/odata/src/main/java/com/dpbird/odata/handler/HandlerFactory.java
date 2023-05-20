package com.dpbird.odata.handler;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handler factory
 */
public class HandlerFactory {

    /**
     * 获取一个EntityHandler的实例
     *
     * @return 配置指定的接口实例
     */
    public static EntityHandler getEntityHandler(EdmEntityType edmEntityType, OfbizAppEdmProvider edmProvider, Delegator delegator)
            throws OfbizODataException {
        try {
            List<String> handlerValues = Collections.singletonList(edmEntityType.getName());
            String handlerImpl = getHandlerImpl(edmProvider, handlerValues, delegator);
            if (UtilValidate.isEmpty(handlerImpl)) {
                //Default handler
                return new DefaultEntityHandler();
            }
            Class<?> implClass = Class.forName(handlerImpl);
            if (EntityHandler.class.isAssignableFrom(implClass)) {
                return (EntityHandler) implClass.newInstance();
            }
            throw new OfbizODataException("The wrong instance: " + handlerImpl);
        } catch (ReflectiveOperationException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 获取一个DraftHandler的实例
     *
     * @return 配置指定的接口实例
     */
    public static DraftHandler getDraftHandler(EdmEntityType edmEntityType, OfbizAppEdmProvider edmProvider, Delegator delegator)
            throws OfbizODataException {
        try {
            String resource = edmProvider.getComponentName() + "Edm.properties";
            String handlerKey = edmProvider.getWebapp() + "Draft." + edmEntityType.getName();
            String handlerImpl = EntityUtilProperties.getPropertyValue(resource, handlerKey, delegator);
            if (UtilValidate.isEmpty(handlerImpl)) {
                //Default handler
                return new DefaultDraftHandler();
            }
            Class<?> implClass = Class.forName(handlerImpl);
            if (DraftHandler.class.isAssignableFrom(implClass)) {
                return (DraftHandler) implClass.newInstance();
            }
            throw new OfbizODataException("The wrong instance: " + handlerImpl);
        } catch (ReflectiveOperationException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 获取一个NavigationHandler的实例
     *
     * @param edmEntityType 主实体
     * @param edmNavigationProperty 关联实体
     */
    public static NavigationHandler getNavigationHandler(EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty,
                                                         OfbizAppEdmProvider edmProvider, Delegator delegator) throws OfbizODataException {
        try {
            List<String> handlerValues = Arrays.asList(edmEntityType.getName(), edmNavigationProperty.getName());
            String handlerImpl = getHandlerImpl(edmProvider, handlerValues, delegator);
            if (UtilValidate.isEmpty(handlerImpl)) {
                //Default handler
                return new DefaultNavigationHandler();
            }
            Class<?> implClass = Class.forName(handlerImpl);
            if (NavigationHandler.class.isAssignableFrom(implClass)) {
                return (NavigationHandler) implClass.newInstance();
            }
            throw new OfbizODataException("The wrong instance: " + handlerImpl);
        } catch (ReflectiveOperationException e) {
            throw new OfbizODataException(e.getMessage());
        }
    }

    public static String getHandlerImpl(OfbizAppEdmProvider edmProvider, List<String> values, Delegator delegator) {
        StringBuilder configKey = new StringBuilder(edmProvider.getWebapp());
        for (String value : values) {
            configKey.append(".").append(value);
        }
        String resource = edmProvider.getComponentName() + "Edm.properties";
        return EntityUtilProperties.getPropertyValue(resource, configKey.toString(), delegator);
    }
}
