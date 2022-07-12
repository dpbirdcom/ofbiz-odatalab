package com.dpbird.odata.test;

import java.math.BigDecimal;

public class TestComplexType {

	private String testString;
	private BigDecimal testDecimal;
	private Long testLong;
	
	public TestComplexType(){
		super();
	}
	
	public TestComplexType(String testString, BigDecimal testDecimal, Long testLong) {
		super();
		this.testString = testString;
		this.testDecimal = testDecimal;
		this.testLong = testLong;
	}

	public String getTestString() {
		return testString;
	}
	public void setTestString(String testString) {
		this.testString = testString;
	}
	public BigDecimal getTestDecimal() {
		return testDecimal;
	}
	public void setTestDecimal(BigDecimal testDecimal) {
		this.testDecimal = testDecimal;
	}
	public Long getTestLong() {
		return testLong;
	}
	public void setTestLong(Long testLong) {
		this.testLong = testLong;
	}
}
