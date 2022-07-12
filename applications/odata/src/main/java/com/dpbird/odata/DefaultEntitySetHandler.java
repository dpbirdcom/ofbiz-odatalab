package com.dpbird.odata;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;

import java.util.Map;

public class DefaultEntitySetHandler implements EntitySetHandler {
    private String entitySetName;


    public DefaultEntitySetHandler(String entitySetName) {
        super();
        this.entitySetName = entitySetName;
    }


    @Override
    public EntityCondition getEntitySetCondition() {
        if (this.entitySetName.equals("WeightUoms")) {
            return EntityCondition.makeCondition("uomTypeId", EntityOperator.EQUALS, "WEIGHT_MEASURE");
        } else if (this.entitySetName.equals("LengthUoms")) {
            return EntityCondition.makeCondition("uomTypeId", EntityOperator.EQUALS, "LENGTH_MEASURE");
        } else if (this.entitySetName.equals("NonVirtualProducts")) {
            return EntityCondition.makeCondition("isVirtual", EntityOperator.EQUALS, "N");
        } else if (this.entitySetName.equals("NonVariantProducts")) {
            return EntityCondition.makeCondition("isVariant", EntityOperator.EQUALS, "N");
        } else if (this.entitySetName.equals("ProductFeatureAndApplColorSet")) {
            return EntityCondition.makeCondition("productFeatureTypeId", EntityOperator.EQUALS, "COLOR");
        } else if (this.entitySetName.equals("ProductFeatureAndApplSizeSet")) {
            return EntityCondition.makeCondition("productFeatureTypeId", EntityOperator.EQUALS, "SIZE");
        } else if (this.entitySetName.equals("ProductFeatureApplSelectableFeatureSet")) {
            return EntityCondition.makeCondition("productFeatureApplTypeId", EntityOperator.EQUALS, "SELECTABLE_FEATURE");
        } else if (this.entitySetName.equals("ProductFeatureApplStandardFeatureSet")) {
            return EntityCondition.makeCondition("productFeatureApplTypeId", EntityOperator.EQUALS, "STANDARD_FEATURE");
        }
        return null;
    }


    @Override
    public boolean isCreatable() {
        return true;
    }


    @Override
    public Map<String, Object> defaultFieldValues() {
        if (this.entitySetName.equals("WeightUoms")) {
            return UtilMisc.toMap("uomTypeId", "WEIGHT_MEASURE");
        } else if (this.entitySetName.equals("LengthUoms")) {
            return UtilMisc.toMap("uomTypeId", "LENGTH_MEASURE");
        } else if (this.entitySetName.equals("NonVirtualProducts")) {
            return UtilMisc.toMap("isVirtual", 0);
        } else if (this.entitySetName.equals("NonVariantProducts")) {
            return UtilMisc.toMap("isVariant", 0);
        } else if (this.entitySetName.equals("ProductFeatureAndApplColorSet")) {
            return UtilMisc.toMap("productFeatureTypeId", "COLOR");
        } else if (this.entitySetName.equals("ProductFeatureAndApplSizeSet")) {
            return UtilMisc.toMap("productFeatureTypeId", "SIZE");
        } else if (this.entitySetName.equals("ProductFeatureApplSelectableFeatureSet")) {
            return UtilMisc.toMap("productFeatureApplTypeId", "SELECTABLE_FEATURE");
        } else if (this.entitySetName.equals("ProductFeatureApplStandardFeatureSet")) {
            return UtilMisc.toMap("productFeatureApplTypeId", "STANDARD_FEATURE");
        }
        return null;
    }

}
