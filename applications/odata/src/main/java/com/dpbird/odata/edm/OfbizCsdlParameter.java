package com.dpbird.odata.edm;

import com.dpbird.odata.annotation.FieldControlType;
import com.dpbird.odata.annotation.Term;
import org.apache.olingo.commons.api.edm.provider.CsdlParameter;

import java.util.List;

public class OfbizCsdlParameter extends CsdlParameter {
	private String label;
	private FieldControlType fieldControl;
	private List<Term> terms;
	private String hidden;
	private String defaultValue;


	public List<Term> getTerms() {
		return terms;
	}

	public void setTerms(List<Term> terms) {
		this.terms = terms;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public FieldControlType getFieldControl() {
		return fieldControl;
	}

	public void setFieldControl(FieldControlType fieldControl) {
		this.fieldControl = fieldControl;
	}

	public String getHidden() {
		return hidden;
	}

	public void setHidden(String hidden) {
		this.hidden = hidden;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}
}
