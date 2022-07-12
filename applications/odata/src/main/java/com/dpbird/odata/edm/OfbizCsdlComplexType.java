package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlComplexType;

public class OfbizCsdlComplexType extends CsdlComplexType {
	String ofbizClass;

	public OfbizCsdlComplexType(String ofbizClass) {
		super();
		this.ofbizClass = ofbizClass;
	}

	public String getOfbizClass() {
		return ofbizClass;
	}

	public void setOfbizClass(String ofbizClass) {
		this.ofbizClass = ofbizClass;
	}

}
