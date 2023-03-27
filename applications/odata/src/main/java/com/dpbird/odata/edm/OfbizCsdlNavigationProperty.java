package com.dpbird.odata.edm;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.condition.*;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OfbizCsdlNavigationProperty extends CsdlNavigationProperty {
    private boolean autoBinding = true;
    private String midEntity;
    private String shortcutView;
    private EntityCondition midEntityCondition;
    private Map<String ,Object> simpleMapCondition = new HashMap<>(); // This is just for creation of midEntity
    private boolean filterByDate = false;
    private String createService;
    private String removeService;
    private String relOne;
    private String relTwo;
    private boolean readOnly;
    private EntityTypeRelAlias relAlias;
    private String handler;
    private String handlerNode;

    public boolean isAutoBinding() {
        return autoBinding;
    }

    public void setAutoBinding(boolean autoBinding) {
        this.autoBinding = autoBinding;
    }

    public String getMidEntity() {
        return midEntity;
    }

    public void setMidEntity(String midEntity) {
        this.midEntity = midEntity;
    }

    public EntityCondition getMidEntityCondition() {
        return midEntityCondition;
    }

    public void setMidEntityCondition(EntityCondition midEntityCondition) {
        this.midEntityCondition = midEntityCondition;
    }

    public Map<String, Object> getSimpleMapCondition() {
        return simpleMapCondition;
    }

    public void setSimpleMapCondition(Map<String, Object> simpleMapCondition) {
        this.simpleMapCondition = simpleMapCondition;
    }

    public String getShortcutView() {
        return shortcutView;
    }

    public void setShortcutView(String shortcutView) {
        this.shortcutView = shortcutView;
    }

    public boolean isFilterByDate() {
        return filterByDate;
    }

    public void setFilterByDate(boolean filterByDate) {
        this.filterByDate = filterByDate;
    }

    public String getCreateService() {
        return createService;
    }

    public void setCreateService(String createService) {
        this.createService = createService;
    }

    public String getRemoveService() {
        return removeService;
    }

    public void setRemoveService(String removeService) {
        this.removeService = removeService;
    }

    public String getRelOne() {
        return relOne;
    }

    public void setRelOne(String relOne) {
        this.relOne = relOne;
    }

    public String getRelTwo() {
        return relTwo;
    }

    public void setRelTwo(String relTwo) {
        this.relTwo = relTwo;
    }

    public EntityTypeRelAlias getRelAlias() {
        return relAlias;
    }

    public void setRelAlias(EntityTypeRelAlias relAlias) {
        this.relAlias = relAlias;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public String getHandlerNode() {
        return handlerNode;
    }

    public void setHandlerNode(String handlerNode) {
        this.handlerNode = handlerNode;
    }

}
