package com.dpbird.odata.edm;

import com.dpbird.odata.annotation.FieldControlType;
import com.dpbird.odata.annotation.Term;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;

import java.util.List;

public class OfbizCsdlProperty extends CsdlProperty {
    private boolean computed = false;
    private boolean immutable = false;
    private boolean imageUrl = false;
    private boolean isImage = false;
    private String hidden;
    private boolean hiddenFilter = false;
    private boolean multiLineText = false;
    private boolean excludeFromNavigationContext = false;
    private boolean isAttribute = false;
    private boolean isNumericAttribute = false;
    private boolean isDateAttribute = false;
    private FieldControlType fieldControl;
    private String label;
    private String semanticObject;
    private String collectionPath = null;
    private List<Term> terms;
    private String ofbizFieldType;
    private String ofbizFieldName;
    private EntityTypeRelAlias relAlias;
    private String autoValue;
    private String fileNamePath;
    //StickySession保存时的校验
    private boolean only = false;
    private boolean required = false;
    private String unit = null;
    private String unitPath = null;

    public List<Term> getTerms() {
        return terms;
    }

    public void setTerms(List<Term> terms) {
        this.terms = terms;
    }

    public String getCollectionPath() {
        return collectionPath;
    }

    public void setCollectionPath(String collectionPath) {
        this.collectionPath = collectionPath;
    }

    public boolean isComputed() {
        return computed;
    }

    public void setComputed(boolean computed) {
        this.computed = computed;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    public boolean isImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(boolean imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isImage() {
        return isImage;
    }

    public void setIsImage(boolean isImage) {
        this.isImage = isImage;
    }

    public String getHidden() {
        return hidden;
    }

    public void setHidden(String hidden) {
        this.hidden = hidden;
    }

    public boolean isHiddenFilter() {
        return hiddenFilter;
    }

    public void setHiddenFilter(boolean hiddenFilter) {
        this.hiddenFilter = hiddenFilter;
    }

    public boolean isMultiLineText() {
        return multiLineText;
    }

    public void setMultiLineText(boolean multiLineText) {
        this.multiLineText = multiLineText;
    }

    public boolean isExcludeFromNavigationContext() {
        return excludeFromNavigationContext;
    }

    public boolean isAttribute() {
        return isAttribute;
    }

    public void setAttribute(boolean attribute) {
        isAttribute = attribute;
    }

    public boolean isNumericAttribute() {
        return isNumericAttribute;
    }

    public void setNumericAttribute(boolean numericAttribute) {
        isNumericAttribute = numericAttribute;
    }

    public boolean isDateAttribute() {
        return isDateAttribute;
    }

    public void setDateAttribute(boolean dateAttribute) {
        isDateAttribute = dateAttribute;
    }

    public void setExcludeFromNavigationContext(boolean excludeFromNavigationContext) {
        this.excludeFromNavigationContext = excludeFromNavigationContext;
    }

    public FieldControlType getFieldControl() {
        return fieldControl;
    }

    public void setFieldControl(FieldControlType fieldControl) {
        this.fieldControl = fieldControl;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSemanticObject() {
        return semanticObject;
    }

    public void setSemanticObject(String semanticObject) {
        this.semanticObject = semanticObject;
    }

    public String getOfbizFieldType() {
        return ofbizFieldType;
    }

    public void setOfbizFieldType(String ofbizFieldType) {
        this.ofbizFieldType = ofbizFieldType;
    }

//    public List<String> getRelations() {
//        return relations;
//    }
//
//    public void setRelations(List<String> relations) {
//        this.relations = relations;
//    }

    public String getOfbizFieldName() {
        return ofbizFieldName;
    }

    public void setOfbizFieldName(String ofbizFieldName) {
        this.ofbizFieldName = ofbizFieldName;
    }

    public EntityTypeRelAlias getRelAlias() {
        return relAlias;
    }

    public void setRelAlias(EntityTypeRelAlias relAlias) {
        this.relAlias = relAlias;
    }

    public String getAutoValue() {
        return autoValue;
    }

    public void setAutoValue(String autoValue) {
        this.autoValue = autoValue;
    }

    public String getFileNamePath() {
        return fileNamePath;
    }

    public void setFileNamePath(String fileNamePath) {
        this.fileNamePath = fileNamePath;
    }

    public boolean isOnly() {
        return only;
    }

    public void setOnly(boolean only) {
        this.only = only;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getUnitPath() {
        return unitPath;
    }

    public void setUnitPath(String unitPath) {
        this.unitPath = unitPath;
    }

    public boolean equals(Object o) {
        if (!(o instanceof OfbizCsdlProperty)) {
            return false;
        }
        OfbizCsdlProperty other = (OfbizCsdlProperty) o;
        if (!this.getName().equals(other.getName())) {
            return false;
        }
        return true;
    }

}
