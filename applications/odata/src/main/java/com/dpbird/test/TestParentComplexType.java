package com.dpbird.test;

import java.math.BigDecimal;

public class TestParentComplexType {

	private String testString;
	private BigDecimal testDecimal;
	private Long testLong;
	private TestSubComplexType testSubComplexType;
	
	public TestParentComplexType() {
		super();
	}

	public TestParentComplexType(String testString, BigDecimal testDecimal, Long testLong, TestSubComplexType testSubComplexType) {
		super();
		this.testString = testString;
		this.testDecimal = testDecimal;
		this.testLong = testLong;
		this.testSubComplexType = testSubComplexType;
	}

	public TestSubComplexType getTestSubComplexType() {
		return testSubComplexType;
	}

	public void setTestSubComplexType(TestSubComplexType testSubComplexType) {
		this.testSubComplexType = testSubComplexType;
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
