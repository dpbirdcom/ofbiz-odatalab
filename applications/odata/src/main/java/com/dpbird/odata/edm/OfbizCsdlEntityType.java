package com.dpbird.odata.edm;

import java.math.BigDecimal;
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

public class OfbizCsdlEntityType extends CsdlEntityType implements Cloneable {
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
    private String entitySetName;
    private List<String> keyPropertyNames = null;
    private List<Term> terms;
    private boolean hasRelField = false;
    private List<EntityTypeRelAlias> relAliases = null;
    private String searchOption;
    private boolean groupBy;
    private final boolean autoLabel;
    private final boolean autoDraft;
    private final boolean autoSet;
    private final boolean autoValueList;
    private List<OfbizCsdlAction> actionList;
    private List<OfbizCsdlFunction> functionList;
    private final Map<String, Object> defaultValueProperties = new HashMap<>();
    private final Map<String, Object> autoValueProperties = new HashMap<>();
    private List<String> insertRequireProperties = new ArrayList<>();

    public OfbizCsdlEntityType(String ofbizEntity, String handlerClass, boolean autoProperties, boolean autoEnum,
                               boolean filterByDate, String attrEntityName, String attrNumericEntityName, String attrDateEntityName, boolean hasDerivedEntity,
                               EntityCondition entityCondition, String entityConditionStr, String labelPrefix, String searchOption, boolean groupBy, boolean hasStream,
                               boolean autoLabel, boolean autoDraft, boolean autoValueList, boolean autoSet, String draftEntityName, String entitySetName) {
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
        this.attrEntityName = attrEntityName;
        this.attrNumericEntityName = attrNumericEntityName;
        this.attrDateEntityName = attrDateEntityName;
        this.terms = null;
        this.searchOption = searchOption;
        this.groupBy = groupBy;
        this.autoLabel = autoLabel;
        this.autoDraft = autoDraft;
        this.autoValueList = autoValueList;
        this.autoSet = autoSet;
        this.draftEntityName = draftEntityName;
        this.entitySetName = entitySetName;
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

    public String getDraftEntityName() {
        return draftEntityName;
    }

    public void setDraftEntityName(String draftEntityName) {
        this.draftEntityName = draftEntityName;
    }

    public String getEntitySetName() {
        return entitySetName;
    }

    public void setEntitySetName(String entitySetName) {
        this.entitySetName = entitySetName;
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
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) property;
            Object defaultValue = property.getDefaultValue();
            if (UtilValidate.isNotEmpty(property.getDefaultValue())) {
                if (property.getType().contains("Decimal")) {
                    defaultValue = new BigDecimal((String) defaultValue);
                } else if (property.getType().contains("Edm.Int64")) {
                    defaultValue = Long.valueOf((String) defaultValue);
                }
                defaultValueProperties.put(property.getName(), defaultValue);
            }
            if (UtilValidate.isNotEmpty(ofbizCsdlProperty.getAutoValue())) {
                autoValueProperties.put(property.getName(), ofbizCsdlProperty.getAutoValue());
            }
        }
        return this;
    }

    public Map<String, Object> getDefaultValueProperties() {
        return defaultValueProperties;
    }
    public Map<String, Object> getAutoValueProperties() {
        return autoValueProperties;
    }

    public boolean isAutoLabel() {
        return autoLabel;
    }

    public boolean isAutoDraft() {
        return autoDraft;
    }

    public List<OfbizCsdlAction> getActionList() {
        return actionList;
    }

    public void setActionList(List<OfbizCsdlAction> actionList) {
        this.actionList = actionList;
    }

    public List<OfbizCsdlFunction> getFunctionList() {
        return functionList;
    }

    public void setFunctionList(List<OfbizCsdlFunction> functionList) {
        this.functionList = functionList;
    }

    public boolean isAutoValueList() {
        return autoValueList;
    }

    public List<String> getInsertRequireProperties() {
        return insertRequireProperties;
    }

    public void setInsertRequireProperties(List<String> insertRequireProperties) {
        this.insertRequireProperties = insertRequireProperties;
    }

    public boolean isAutoSet() {
        return autoSet;
    }

    @Override
    public OfbizCsdlEntityType clone() {
        try {
            return (OfbizCsdlEntityType) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }
}
