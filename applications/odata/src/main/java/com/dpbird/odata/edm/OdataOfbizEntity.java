package com.dpbird.odata.edm;

import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.Entity;

import java.util.Map;

public class OdataOfbizEntity extends Entity {
	
	private GenericValue genericValue = null;
	// keyMap是genericValue的pk，理论上和Entity的pk是相同的
	// 但是当genericValue是内存数据库的draft表时，pk是xxxDraftId，和Entity代表的pk是不同的
	// TODO: 如何处理呢？
	private Map<String, Object> keyMap = null;
	private boolean isDraft = false;

	public Map<String, Object> getKeyMap() {
		return keyMap;
	}

	public void setKeyMap(Map<String, Object> keyMap) {
		this.keyMap = keyMap;
	}

	public OdataOfbizEntity() {
		super();
	}

	public OdataOfbizEntity(OfbizCsdlEntityType csdlEntityType, GenericValue genericValue) {
		super();
		this.genericValue = genericValue;
		keyMap = genericValue.getPrimaryKey();
		String draftEntityName = csdlEntityType.getDraftEntityName();
		if (genericValue.getEntityName().equals(draftEntityName)) {
			isDraft = true;
		}
	}

	public GenericValue getGenericValue() {
		return genericValue;
	}

	public boolean isDraft() {
		return isDraft;
	}
}
