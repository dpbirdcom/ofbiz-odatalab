package com.dpbird.odata.edm;

import com.dpbird.odata.OdataParts;
import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OdataOfbizEntity extends Entity {
	
	private GenericValue genericValue = null;
	// keyMap是genericValue的pk，理论上和Entity的pk是相同的
	// 但是当genericValue是内存数据库的draft表时，pk是xxxDraftId，和Entity代表的pk是不同的
	// TODO: 如何处理呢？
	private Map<String, Object> keyMap = null;
	private boolean isDraft = false;
	private List<OdataParts> odataParts = new ArrayList<>();
	private String requestEntityTypeFQN;

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
		//在查询expand时查询出来的结果可能是view
		if (UtilValidate.isNotEmpty(genericValue) && genericValue.getModelEntity() instanceof ModelViewEntity) {
			Delegator delegator = genericValue.getDelegator();
			ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
			genericValue = Util.convertToTargetGenericValue(genericValue.getDelegator(), genericValue, modelEntity);
		}
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

	public void addProperty(String propertyName, Object productValue) {
		this.addProperty(new Property(null, propertyName, ValueType.PRIMITIVE, productValue));
	}

	public Object getPropertyValue(String propertyName) {
		return getProperty(propertyName).getValue();
	}

	public List<OdataParts> getOdataParts() {
		return odataParts;
	}

	public void setOdataParts(List<OdataParts> odataParts) {
		this.odataParts = odataParts;
	}

	public void addOdataParts(OdataParts odataParts) {
		if (this.odataParts == null) {
			this.odataParts = new ArrayList<>();
		}
		this.odataParts.add(odataParts);
	}

	public String getRequestEntityTypeFQN() {
		return requestEntityTypeFQN;
	}

	public void setRequestEntityTypeFQN(String requestEntityTypeFQN) {
		this.requestEntityTypeFQN = requestEntityTypeFQN;
	}
}
