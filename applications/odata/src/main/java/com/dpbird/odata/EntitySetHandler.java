package com.dpbird.odata;

import java.util.Map;

import org.apache.ofbiz.entity.condition.EntityCondition;

public interface EntitySetHandler {
	public EntityCondition getEntitySetCondition();
	public boolean isCreatable();
	public Map<String, Object> defaultFieldValues();
}
