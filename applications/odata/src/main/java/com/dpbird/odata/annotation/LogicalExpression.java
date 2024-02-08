package com.dpbird.odata.annotation;

import org.apache.olingo.commons.api.edm.provider.annotation.CsdlLogicalOrComparisonExpression.LogicalOrComparisonExpressionType;

import java.util.List;

/**
 * @author scy
 * @date 2024/1/29
 */
public class LogicalExpression {

    private List<Expression> expressionList;
    private Values thenValue;
    private Values elseValue;
    private LogicalOrComparisonExpressionType combine;

    public LogicalOrComparisonExpressionType getCombine() {
        return combine;
    }

    public void setCombine(LogicalOrComparisonExpressionType combine) {
        this.combine = combine;
    }

    public List<Expression> getExpressionList() {
        return expressionList;
    }

    public void setExpressionList(List<Expression> expressionList) {
        this.expressionList = expressionList;
    }

    public Values getThenValue() {
        return thenValue;
    }

    public void setThenValue(Values thenValue) {
        this.thenValue = thenValue;
    }

    public Values getElseValue() {
        return elseValue;
    }

    public void setElseValue(Values elseValue) {
        this.elseValue = elseValue;
    }

    public static class Expression {
        private String path;
        private LogicalOrComparisonExpressionType operator;
        private Values value;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public LogicalOrComparisonExpressionType getOperator() {
            return operator;
        }

        public void setOperator(LogicalOrComparisonExpressionType operator) {
            this.operator = operator;
        }

        public Values getValue() {
            return value;
        }

        public void setValue(Values value) {
            this.value = value;
        }
    }


    public static class Values {
        private String stringValue;
        private String booleanValue;
        private String intValue;

        public String getStringValue() {
            return stringValue;
        }

        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }

        public String getBooleanValue() {
            return booleanValue;
        }

        public void setBooleanValue(String booleanValue) {
            this.booleanValue = booleanValue;
        }

        public String getIntValue() {
            return intValue;
        }

        public void setIntValue(String intValue) {
            this.intValue = intValue;
        }
    }



}
