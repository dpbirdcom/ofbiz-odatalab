package com.dpbird.odata.annotation;

import com.dpbird.odata.OfbizMapOdata;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotation;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author scy
 * @date 2024/1/11
 */
public class TermUtil {

    //Criticality Enum
    private static final List<String> CRITICALITY_TYPES = UtilMisc.toList("VeryNegative", "Neutral", "Negative", "Critical", "Positive", "VeryPositive");

    /**
     * GenericValue转换为LineItem
     */
    public static LineItem getLineItemFromGv(GenericValue lineItemGv, Delegator delegator, Locale locale) {
        String qualifier = lineItemGv.getString("qualifier");
        String criticality = lineItemGv.getString("criticality");
        LineItem lineItem = new LineItem(qualifier);
        if (CRITICALITY_TYPES.contains(criticality)) {
            CriticalityType criticalityType = CriticalityType.valueOf(criticality);
            lineItem.setCriticality(criticalityType);
        } else {
            lineItem.setCriticalityPath(criticality);
        }
        return lineItem;
    }
    /**
     * GenericValue转换为FieldGroup
     */
    public static FieldGroup getFieldGroupFromGv(GenericValue fieldGroupGv, Delegator delegator, Locale locale) {
        String qualifier = fieldGroupGv.getString("qualifier");
        String label = fieldGroupGv.getString("label");
        FieldGroup fieldGroup = new FieldGroup(qualifier);
        fieldGroup.setLabel(getLabel(delegator, label, locale));
        return fieldGroup;
    }

    /**
     * GenericValue转换为ReferenceFacet
     */
    public static ReferenceFacet getReferenceFacetFromGv(GenericValue referenceFacetGv, Delegator delegator, Locale locale) {
        ReferenceFacet referenceFacet = new ReferenceFacet();
        referenceFacet.setId(referenceFacetGv.getString("recordId"));
        referenceFacet.setLabel(getLabel(delegator, referenceFacetGv.getString("label"), locale));
        referenceFacet.setTarget(referenceFacetGv.getString("target"));
        referenceFacet.setHidden(referenceFacetGv.getString("hidden"));
        return referenceFacet;
    }


    /**
     * GenericValue转换为DataField
     */
    public static List<DataField> getDataFieldFromGv(GenericValue dataFieldGv, Delegator delegator, Locale locale) {
        List<DataField> dataFieldList = new ArrayList<>();
        List<String> values = StringUtil.split(dataFieldGv.getString("fieldValues"), ",");
        List<String> labels = StringUtil.split(dataFieldGv.getString("fieldLabels"), ",");
        String importance = dataFieldGv.getString("importance");
        String hidden = dataFieldGv.getString("hidden");
        String fieldCriticality = dataFieldGv.getString("criticality");
        if (UtilValidate.isNotEmpty(values)) {
            for (int i = 0; i < values.size(); i++) {
                DataField dataField = new DataField(values.get(i));
                if (UtilValidate.isNotEmpty(labels)) {
                    if (i < labels.size()) {
                        String dataFieldLabel = labels.get(i);
                        dataField.setLabel(getLabel(delegator, dataFieldLabel, locale));
                    }
                }
                if (UtilValidate.isNotEmpty(importance)) {
                    dataField.setImportance(ImportanceType.valueOf(importance));
                }
                if (UtilValidate.isNotEmpty(hidden)) {
                    dataField.setHidden(hidden);
                }
                if (UtilValidate.isNotEmpty(fieldCriticality)) {
                    if (CRITICALITY_TYPES.contains(fieldCriticality)) {
                        dataField.setCriticality(CriticalityType.valueOf(fieldCriticality));
                    } else {
                        dataField.setCriticalityPath(fieldCriticality);
                    }
                }
                dataFieldList.add(dataField);
            }
        }
        return dataFieldList;
    }


    /**
     * GenericValue转换为DataFieldForAction
     */
    public static DataFieldForAction getDataFieldForActionFromGv(GenericValue dataFieldForActionGv, Delegator delegator, Locale locale) {
        String importance = dataFieldForActionGv.getString("importance");
        Boolean inline = dataFieldForActionGv.getBoolean("inline");
        String fgCriticality = dataFieldForActionGv.getString("criticality");
        String action = dataFieldForActionGv.getString("action");
        String label = getLabel(delegator, dataFieldForActionGv.getString("label"), locale);
        String hidden = dataFieldForActionGv.getString("hidden");
        String invocationGrouping = dataFieldForActionGv.getString("invocationGrouping");
        DataFieldForAction dataFieldForAction = new DataFieldForAction();
        dataFieldForAction.setLabel(label);
        dataFieldForAction.setAction(OfbizMapOdata.NAMESPACE + "." + action);
        if (UtilValidate.isNotEmpty(invocationGrouping)) {
            dataFieldForAction.setInvocationGrouping(OperationGroupingType.valueOf(invocationGrouping));
        }
        if (UtilValidate.isNotEmpty(fgCriticality)) {
            dataFieldForAction.setCriticality(CriticalityType.valueOf(fgCriticality));
        }
        if (UtilValidate.isNotEmpty(inline)) {
            dataFieldForAction.setInline(inline);
        }
        if (UtilValidate.isNotEmpty(hidden)) {
            dataFieldForAction.setHidden(hidden);
        }
        if (UtilValidate.isNotEmpty(importance)) {
            dataFieldForAction.setImportance(ImportanceType.valueOf(importance));
        }
        return dataFieldForAction;
    }


    private static String getLabel(Delegator delegator, String property, Locale locale) {
        try {
            if (UtilValidate.isNotEmpty(property) && property.startsWith("${uiLabelMap.")) {
                //是变量
                property = property.substring(property.indexOf(".") + 1, property.length() - 1);
                GenericValue i18n = EntityQuery.use(delegator).from("Internationalization")
                        .where("lang", locale.getLanguage(), "property", property).queryFirst();
                if (UtilValidate.isNotEmpty(i18n)) {
                    return i18n.getString("value");
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        return property;
    }


}
