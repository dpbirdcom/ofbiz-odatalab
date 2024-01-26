package com.dpbird.odata.annotation;

import java.util.List;

public class SelectionPresentationVariant extends Term {

    private String text;
    private String visualizations;
    private List<SelectOption> selectOptionList;

    public SelectionPresentationVariant(String qualifier) {
        super(qualifier);
        this.appliesTo = "EntityType";
        this.termName = "UI.SelectionPresentationVariant";
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVisualizations() {
        return visualizations;
    }

    public void setVisualizations(String visualizations) {
        this.visualizations = visualizations;
    }

    public List<SelectOption> getSelectOptionList() {
        return selectOptionList;
    }

    public void setSelectOptionList(List<SelectOption> selectOptionList) {
        this.selectOptionList = selectOptionList;
    }

    public static class SelectOption {
        private String propertyName;
        private List<Range> rangeList;
        public SelectOption(String propertyName) {
            this.propertyName = propertyName;
        }
        public SelectOption() {
        }
        public String getPropertyName() {
            return propertyName;
        }
        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public List<Range> getRangeList() {
            return rangeList;
        }

        public void setRangeList(List<Range> rangeList) {
            this.rangeList = rangeList;
        }
    }

    public static class Range {
        private String low;
        private String high;
        private String option;
        private String sign;

        public String getLow() {
            return low;
        }

        public void setLow(String low) {
            this.low = low;
        }

        public String getHigh() {
            return high;
        }

        public void setHigh(String high) {
            this.high = high;
        }

        public String getOption() {
            return option;
        }

        public void setOption(String option) {
            this.option = option;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }
    }

}
