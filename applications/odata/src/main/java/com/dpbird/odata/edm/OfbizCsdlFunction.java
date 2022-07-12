package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlFunction;

public class OfbizCsdlFunction extends CsdlFunction {
	private String ofbizMethod;
	private String returnName;
	private boolean isService = false;

	public OfbizCsdlFunction() {
		super();
	}
	public OfbizCsdlFunction(String ofbizMethod, String returnName, boolean isService) {
		super();
		this.ofbizMethod = ofbizMethod;
		this.returnName = returnName;
		this.isService = isService;
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
	public boolean isService() {
		return isService;
	}
	public void setService(boolean isService) {
		this.isService = isService;
	}

}
