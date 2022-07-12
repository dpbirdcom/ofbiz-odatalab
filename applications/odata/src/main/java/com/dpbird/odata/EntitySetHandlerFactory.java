package com.dpbird.odata;

public class EntitySetHandlerFactory {

	public static EntitySetHandler getEntitySetHandler(String entitySetName) {
		return new DefaultEntitySetHandler(entitySetName);
	}
}
