package com.dpbird.odata.edm;

import org.apache.olingo.commons.api.edm.provider.CsdlAction;

public class OfbizCsdlAction extends CsdlAction {
	private String ofbizMethod;
	private String returnName;
	private boolean stickySession = false;
	private boolean entityAction = false; // 比如create entity，update entity，delete entity
	private boolean sideEffects = false;

	public OfbizCsdlAction() {
		super();
	}
	public OfbizCsdlAction(String ofbizMethod, String returnName, boolean stickySession, boolean sideEffects) {
		super();
		this.ofbizMethod = ofbizMethod;
		this.returnName = returnName;
		this.stickySession = stickySession;
		this.sideEffects = sideEffects;
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

	public void setStickySession(boolean stickySession) {
		this.stickySession = stickySession;
	}

	public boolean isStickySession() {
		return stickySession;
	}

	public boolean isEntityAction() {
		return entityAction;
	}

	public void setEntityAction(boolean entityAction) {
		this.entityAction = entityAction;
	}

	public boolean isStickySessionNew() {
		return this.name.endsWith("NewAction");
	}

	public boolean isStickySessionDiscard() {
		return this.name.endsWith("DiscardAction");
	}

	public boolean isStickySessionSave() {
		return this.name.endsWith("SaveAction");
	}

	public boolean isStickySessionEdit() {
		return this.name.endsWith("EditAction");
	}

	public boolean isSideEffects() {
		return sideEffects;
	}

	public void setSideEffects(boolean sideEffects) {
		this.sideEffects = sideEffects;
	}
}
