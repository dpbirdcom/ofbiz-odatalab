package com.dpbird.odata.edm;

import java.util.*;
import java.util.stream.Collectors;

import com.dpbird.odata.OfbizAppEdmProvider;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.annotation.Term;
import com.dpbird.odata.handler.HandlerFactory;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;

import com.dpbird.odata.Util;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;

public class OfbizCsdlEntityType extends CsdlEntityType {
    private String ofbizEntity;
    private String entityConditionStr;
    private EntityCondition entityCondition;
    private String fullQualifiedNameString;
    private String labelPrefix;
    private String handlerClass;
    private List<String> referencedEntitySet;
    //	private List<String> derivedEntities;
    private boolean hasDerivedEntity = false;
    private boolean autoProperties;
    private boolean autoAnnotations;
    private boolean autoNavigations = true;
    private boolean autoEnum = false;
    private boolean filterByDate = false;
    private String attrEntityName;
    private String attrNumericEntityName;
    private String attrDateEntityName;
    private String draftEntityName;
    private List<String> keyPropertyNames = null;
    private List<Term> terms;
    private boolean hasRelField = false;
    private List<EntityTypeRelAlias> relAliases = null;
    private String searchOption;
    private boolean groupBy;
    private Map<String, Object> defaultValueProperties = new HashMap<>();;

    public OfbizCsdlEntityType(String ofbizEntity, String handlerClass, boolean autoProperties, boolean autoEnum,
                               boolean filterByDate, String draftEntityName, String attrEntityName, String attrNumericEntityName, String attrDateEntityName, boolean hasDerivedEntity,
                               EntityCondition entityCondition, String entityConditionStr, String labelPrefix, String searchOption, boolean groupBy, boolean hasStream) {
        super();
        this.ofbizEntity = ofbizEntity;
        this.handlerClass = handlerClass;
        this.entityCondition = entityCondition;
        this.entityConditionStr = entityConditionStr;
        this.fullQualifiedNameString = null;
        this.labelPrefix = labelPrefix;
        this.referencedEntitySet = null;
        this.autoProperties = autoProperties;
        this.autoAnnotations = false;
        this.hasDerivedEntity = hasDerivedEntity;
        this.autoEnum = autoEnum;
        this.filterByDate = filterByDate;
        this.draftEntityName = draftEntityName;
        this.attrEntityName = attrEntityName;
        this.attrNumericEntityName = attrNumericEntityName;
        this.attrDateEntityName = attrDateEntityName;
        this.terms = null;
        this.searchOption = searchOption;
        this.groupBy = groupBy;
        setHasStream(hasStream);
    }

    public List<String> getReferencedEntitySet() {
        return this.referencedEntitySet;
    }

    public String getOfbizEntity() {
        return ofbizEntity;
    }

    public void setOfbizEntity(String ofbizEntity) {
        this.ofbizEntity = ofbizEntity;
    }

    public String getEntityConditionStr() {
        return entityConditionStr;
    }

    public void setEntityConditionStr(String entityConditionStr) {
        this.entityConditionStr = entityConditionStr;
    }

    public EntityCondition getEntityCondition() {
        return entityCondition;
    }

    public void setEntityCondition(EntityCondition entityCondition) {
        this.entityCondition = entityCondition;
    }

    public String getFullQualifiedNameString() {
        if (this.fullQualifiedNameString == null) {
            this.fullQualifiedNameString = Util.getFullQualifiedNameByParamName(this.getName()).getFullQualifiedNameAsString();
        }
        return this.fullQualifiedNameString;
    }

    public String getLabelPrefix() {
        return this.labelPrefix;
    }

    public boolean isAutoAnnotations() {
        return autoAnnotations;
    }

    public boolean isAutoProperties() {
        return autoProperties;
    }

    public void setAutoAnnotations(boolean autoAnnotations) {
        this.autoAnnotations = autoAnnotations;
    }

    public void addReferencedEntitySet(String entitySet) {
        if (this.referencedEntitySet == null) {
            this.referencedEntitySet = new ArrayList<String>();
        }
        this.referencedEntitySet.add(entitySet);
    }

    public String firstReferencedEntitySet() {
        if (UtilValidate.isEmpty(this.referencedEntitySet)) {
            return null;
        }
        return this.referencedEntitySet.get(0);
    }

    public String getHandlerClass() {
        return handlerClass;
    }

    public void setHandlerClass(String handlerClass) {
        this.handlerClass = handlerClass;
    }

    public boolean isAutoNavigations() {
        return autoNavigations;
    }

    public void setAutoNavigations(boolean autoNavigations) {
        this.autoNavigations = autoNavigations;
    }

    public boolean isHasDerivedEntity() {
        return hasDerivedEntity;
    }

    public boolean isAutoEnum() {
        return autoEnum;
    }

    public boolean isFilterByDate() {
        return filterByDate;
    }

    public String getAttrEntityName() {
        return attrEntityName;
    }

    public void setAttrEntityName(String attrEntityName) {
        this.attrEntityName = attrEntityName;
    }

    public String getAttrNumericEntityName() {
        return attrNumericEntityName;
    }

    public void setAttrNumericEntityName(String attrNumericEntityName) {
        this.attrNumericEntityName = attrNumericEntityName;
    }

    public String getAttrDateEntityName() {
        return attrDateEntityName;
    }

    public void setAttrDateEntityName(String attrDateEntityName) {
        this.attrDateEntityName = attrDateEntityName;
    }

    public boolean equals(Object o) {
        if (!(o instanceof OfbizCsdlEntityType)) {
            return false;
        }
        OfbizCsdlEntityType other = (OfbizCsdlEntityType) o;
        if (this.getName().equals(other.getName())) {
            return true;
        }
        return false;
    }

    public List<String> getKeyPropertyNames() {
        if (UtilValidate.isNotEmpty(keyPropertyNames)) {
            return keyPropertyNames;
        }
        keyPropertyNames = new ArrayList<>();
        List<CsdlPropertyRef> csdlPropertyRefs = this.getKey();
        for (CsdlPropertyRef csdlPropertyRef : csdlPropertyRefs) {
            keyPropertyNames.add(csdlPropertyRef.getName());
        }
        return keyPropertyNames;
    }

    public OfbizCsdlProperty getBaseTypeProperty(String propertyName, OfbizAppEdmProvider edmProvider) {
        try {
            if (UtilValidate.isEmpty(baseType)) {
                return null;
            }
            OfbizCsdlEntityType baseEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(baseType);
            return (OfbizCsdlProperty) baseEntityType.getProperty(propertyName);
        } catch (OfbizODataException e) {
            Debug.logError(e.getMessage(), OfbizCsdlProperty.class.getName());
            return null;
        }
    }

    public void addTerm(Term term) {
        if (this.terms == null) {
            this.terms = new ArrayList<>();
        }
        this.terms.add(term);
    }

    public List<Term> getTerms() {
        return terms;
    }

    public void setTerms(List<Term> terms) {
        this.terms = terms;
    }

    public String getDraftEntityName() {
        return draftEntityName;
    }

    public void setDraftEntityName(String draftEntityName) {
        this.draftEntityName = draftEntityName;
    }

    public boolean isHasRelField() {
        return hasRelField;
    }

    public void setHasRelField(boolean hasRelField) {
        this.hasRelField = hasRelField;
    }

    public List<EntityTypeRelAlias> getRelAliases() {
        return relAliases;
    }

    public void setRelAliases(List<EntityTypeRelAlias> relAliases) {
        this.relAliases = relAliases;
    }

    public String getSearchOption() {
        return searchOption;
    }

    public void setSearchOption(String searchOption) {
        this.searchOption = searchOption;
    }

    public boolean isGroupBy() {
        return groupBy;
    }

    public void setGroupBy(boolean groupBy) {
        this.groupBy = groupBy;
    }

    public CsdlProperty getStreamProperty() {
        return properties.stream().filter(p -> "Edm.Stream".equals(p.getType())).findFirst().orElse(null);
    }

    public CsdlProperty getPropertyFromField(String ofbizFieldName) {
        for (CsdlProperty property : properties) {
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) property;
            if (ofbizFieldName.equals(ofbizCsdlProperty.getOfbizFieldName())) {
                return property;
            }
        }
        return null;
    }

    @Override
    public CsdlEntityType setProperties(final List<CsdlProperty> properties) {
        this.properties = properties;
        for (CsdlProperty property : properties) {
            if (UtilValidate.isNotEmpty(property.getDefaultValue())) {
                defaultValueProperties.put(property.getName(), property.getDefaultValue());
            }
        }
        return this;
    }

    public Map<String, Object> getDefaultValueProperties() {
        return defaultValueProperties;
    }
}
