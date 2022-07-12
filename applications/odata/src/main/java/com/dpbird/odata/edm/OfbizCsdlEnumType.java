package com.dpbird.odata.edm;

import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.olingo.commons.api.edm.provider.CsdlEnumType;

public class OfbizCsdlEnumType extends CsdlEnumType {
	private String ofbizEntity;
	private EntityCondition entityCondition;

	public OfbizCsdlEnumType(String ofbizEntity, EntityCondition entityCondition) {
		super();
		this.ofbizEntity = ofbizEntity;
		this.entityCondition = entityCondition;
	}

	public String getOfbizEntity() {
		return ofbizEntity;
	}
	public void setOfbizEntity(String ofbizEntity) {
		this.ofbizEntity = ofbizEntity;
	}
	public EntityCondition getEntityCondition() {
		return entityCondition;
	}
	public void setEntityCondition(EntityCondition entityCondition) {
		this.entityCondition = entityCondition;
	}
}
