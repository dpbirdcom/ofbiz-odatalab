package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlFunction;

public class OfbizCsdlFunction extends CsdlFunction {
	private String ofbizMethod;
	private String returnName;

	public OfbizCsdlFunction() {
		super();
	}
	public OfbizCsdlFunction(String ofbizMethod, String returnName) {
		super();
		this.ofbizMethod = ofbizMethod;
		this.returnName = returnName;
	}
	
	public String getOfbizMethod() {
		return ofbizMethod;
	}
	public void setOfbizMethod(String ofbizMethod) {
		this.ofbizMethod = ofbizMethod;
	}
	public String getReturnName() {
		return returnName;
	}
	public void setReturnName(String returnName) {
		this.returnName = returnName;
	}

}
