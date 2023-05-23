package com.dpbird.odata;

import com.dpbird.odata.annotation.*;
import com.dpbird.odata.edm.*;
import com.google.common.collect.Lists;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityJoinOperator;
import org.apache.ofbiz.entity.datasource.GenericHelperInfo;
import org.apache.ofbiz.entity.jdbc.DatabaseUtil;
import org.apache.ofbiz.entity.model.*;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;
import org.apache.olingo.commons.api.edm.provider.annotation.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class EdmConfigLoader {
    public static final String module = EdmConfigLoader.class.getName();
    private static final String EDM_CONFIG_FILENAME = "globalEdmConfig.xml";
    private static String componentPath = null;

    public EdmConfigLoader(String componentPath) {
        EdmConfigLoader.componentPath = componentPath;
    }


    public static EdmWebConfig loadEdmReference(Delegator delegator, LocalDispatcher dispatcher,
                                                InputStream edmReferenceInputStream, Locale locale)
            throws ODataException {
        try {
            Document configDoc = UtilXml.readXmlDocument(edmReferenceInputStream, false, null);
            EdmWebConfig edmWebConfig = new EdmWebConfig();
            Element rootElement = configDoc.getDocumentElement();
            List<? extends Element> children = UtilXml.childElementList(rootElement);
            for (Element currentElt : children) {
                String tagName = currentElt.getTagName();
                if (tagName.equals("edmx:DataServices")) {
                    List<? extends Element> dataServicesChildren = UtilXml.childElementList(currentElt);
                    for (Element dataServicesChild : dataServicesChildren) {
                        String dataServicesChildTag = dataServicesChild.getTagName();
                        if (dataServicesChildTag.equals("Schema")) {
                            addToEdmWebConfig(delegator, dispatcher, edmWebConfig, dataServicesChild, locale);
                        }
                    }
                }
            } // end for (Element currentElt : children)
            return edmWebConfig;
        } catch (GenericEntityException | SAXException | ParserConfigurationException | IOException | GenericServiceException e) {
            e.printStackTrace();
            throw new ODataException("Load EdmWebConfig Error!");
        }
    }

    public static EdmWebConfig loadAppEdmConfig(Delegator delegator, LocalDispatcher dispatcher, String webapp,
                                                InputStream edmConfigInputStream, Locale locale)
            throws GenericEntityException, GenericServiceException, ParserConfigurationException, IOException, SAXException, OfbizODataException {
        EdmWebConfig edmWebConfig = new EdmWebConfig();
        // load app edmconfig
        Document configDoc = UtilXml.readXmlDocument(edmConfigInputStream, false, null);
        Element rootElement = configDoc.getDocumentElement();
        if (requireGlobal(rootElement)) {
            // load global edmconfig
            URL globalEdmConfigUrl = UtilURL.fromResource(EDM_CONFIG_FILENAME);
            Document globalConfigDoc = UtilXml.readXmlDocument(globalEdmConfigUrl, false);
            Element globalRootElement = globalConfigDoc.getDocumentElement();
            addToEdmWebConfig(delegator, dispatcher, edmWebConfig, globalRootElement, locale);
        }
        for (String importEdm : getImportEdm(rootElement)) {
            URL importUrl = UtilURL.fromResource(importEdm + "EdmConfig.xml");
            Document importDoc = UtilXml.readXmlDocument(importUrl, false);
            if (UtilValidate.isNotEmpty(importDoc)) {
                addToEdmWebConfig(delegator, dispatcher, edmWebConfig, importDoc.getDocumentElement(), locale);
            }
        }
        addToEdmWebConfig(delegator, dispatcher, edmWebConfig, rootElement, locale);
        createDraftTable(edmWebConfig, webapp, delegator);
        return edmWebConfig;
    }

    private static boolean requireGlobal(Element rootElement) {
        boolean importGlobal = false;
        String importGlobalAttr = rootElement.getAttribute("ImportGlobal");
        if (UtilValidate.isNotEmpty(importGlobalAttr)) {
            importGlobal = Boolean.valueOf(importGlobalAttr);
        }
        return importGlobal;
    }

    /**
     * 获取Import
     */
    private static List<String> getImportEdm(Element rootElement) {
        List<String> importList = new ArrayList<>();
        UtilXml.childElementList(rootElement).forEach(ele -> {
            if ("ImportEdm".equals(ele.getTagName())) {
                importList.add(ele.getAttribute("Path"));
            }
        });
        return importList;
    }

    public static void generateAnnotations(Delegator delegator, CsdlSchema csdlSchema, Locale locale) {
        // generate annotations for EntityType
        List<CsdlEntityType> entityTypes = csdlSchema.getEntityTypes();
        List<CsdlEntityType> autoValueListEntities = new ArrayList<>();
        List<CsdlAnnotations> csdlAnnotationsList = csdlSchema.getAnnotationGroups();
        for (CsdlEntityType entityType : entityTypes) {
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) entityType;
            if (ofbizCsdlEntityType.isAutoValueList()) {
                autoValueListEntities.add(ofbizCsdlEntityType);
            }
            CsdlAnnotations csdlAnnotations = generateEntityTypeAnnotations(ofbizCsdlEntityType, (OfbizCsdlSchema) csdlSchema, locale);
            if (csdlAnnotations != null) {
                csdlAnnotationsList.add(csdlAnnotations);
            }
            List<CsdlProperty> properties = entityType.getProperties();
            for (CsdlProperty property : properties) {
                csdlAnnotations = generatePropertyAnnotations(ofbizCsdlEntityType, (OfbizCsdlSchema) csdlSchema,
                        (OfbizCsdlProperty) property, locale);
                if (csdlAnnotations != null) {
                    csdlAnnotationsList.add(csdlAnnotations);
                }
            }
            if (ofbizCsdlEntityType.isAutoDraft()) {
                //StickySessionSupported
                CsdlAnnotations stickySessionAnnotations = generateStickySessionAnnotations(ofbizCsdlEntityType, (OfbizCsdlSchema) csdlSchema, locale);
                csdlAnnotationsList.add(stickySessionAnnotations);
            }
        }

        //generate AutoValueList
        for (CsdlEntityType valueListEntityType : autoValueListEntities) {
            OfbizCsdlEntityType autoValueListEntity = (OfbizCsdlEntityType) valueListEntityType;
            for (CsdlEntityType csdlEntityType : entityTypes) {
                OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) csdlEntityType;
                for (CsdlNavigationProperty navigationProperty : csdlEntityType.getNavigationProperties()) {
                    if (navigationProperty.getType().equals(autoValueListEntity.getFullQualifiedNameString()) && UtilValidate.isNotEmpty(navigationProperty.getReferentialConstraints())) {
                        //生成ValueList
                        csdlAnnotationsList.add(generateAutoValueList(ofbizCsdlEntityType, navigationProperty, csdlSchema, locale));
                    }
                }
            }
        }
    }

    private static CsdlAnnotations generateEntityTypeAnnotations(OfbizCsdlEntityType csdlEntityType,
                                                                 OfbizCsdlSchema csdlSchema, Locale locale) {
        CsdlAnnotations csdlAnnotations = new CsdlAnnotations();
        csdlAnnotations.setTarget(csdlEntityType.getFullQualifiedNameString());
        List<CsdlAnnotation> csdlAnnotationList = new ArrayList<CsdlAnnotation>();
        List<Term> terms = csdlEntityType.getTerms();
        for (Term term : terms) {
            if (term instanceof LineItem) {
                csdlAnnotationList.add(generateLineItem(csdlEntityType, (LineItem) term, locale));
            } else if (term instanceof SemanticKey) {
                csdlAnnotationList.add(generateSemanticKey((SemanticKey) term, locale));
            } else if (term instanceof SelectionFields) {
                csdlAnnotationList.add(generateSelectionFields((SelectionFields) term, locale));
            } else if (term instanceof FieldGroup) {
                csdlAnnotationList.add(generateFieldGroup(csdlEntityType, (FieldGroup) term, locale));
            } else if (term instanceof HeaderInfo) {
                csdlAnnotationList.add(generateHeaderInfo((HeaderInfo) term, locale));
            }
        }
        if (UtilValidate.isEmpty(csdlAnnotationList)) {
            return null;
        }
        csdlAnnotations.setAnnotations(csdlAnnotationList);
        return csdlAnnotations;
    }

    private static CsdlAnnotation generateSemanticKey(SemanticKey semanticKey, Locale locale) {
        CsdlAnnotation csdlAnnotation = createAnnotation(semanticKey.getTermName(), semanticKey.getQualifier());
        csdlAnnotation.setExpression(createCollectionPropertyPath(semanticKey.getPropertyPaths()));
        return csdlAnnotation;
    }

    private static CsdlAnnotation generateSelectionFields(SelectionFields selectionFields, Locale locale) {
        CsdlAnnotation csdlAnnotation = createAnnotation(selectionFields.getTermName(), selectionFields.getQualifier());
        csdlAnnotation.setExpression(createCollectionPropertyPath(selectionFields.getPropertyPaths()));
        return csdlAnnotation;
    }

    private static CsdlCollection createCollectionPropertyPath(List<String> values) {
        CsdlCollection csdlCollection = new CsdlCollection();
        List<CsdlExpression> items = new ArrayList<>();
        for (String value : values) {
            items.add(createExpressionPropertyPath(value));
        }
        csdlCollection.setItems(items);
        return csdlCollection;
    }

    private static CsdlCollection createCollectionDataField(OfbizCsdlEntityType csdlEntityType,
                                                            List<DataFieldAbstract> dataFields,
                                                            boolean withLabel, Locale locale) {
        CsdlCollection csdlCollection = new CsdlCollection();
        List<CsdlExpression> collectionItems = new ArrayList<>();

        for (DataFieldAbstract dataFieldAbstract : dataFields) {
            CsdlRecord csdlRecord = new CsdlRecord();
            List<CsdlPropertyValue> propertyValues = new ArrayList<>();
            // add Value
            if (dataFieldAbstract instanceof DataFieldWithNavigationPath) {
                DataFieldWithNavigationPath dataFieldWithNavigationPath = (DataFieldWithNavigationPath) dataFieldAbstract;
                CsdlPropertyValue propertyValue = createPropertyValueString("Label", dataFieldWithNavigationPath.getLabel());
                propertyValues.add(propertyValue);
                propertyValue = createNavigationPropertyPath("Target", dataFieldWithNavigationPath.getTarget());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueEnum("Criticality", dataFieldWithNavigationPath.getCriticality());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValuePath("Value", (String) dataFieldWithNavigationPath.getValue());
                propertyValues.add(propertyValue);
                String recordType = "UI.DataFieldWithNavigationPath";
                csdlRecord.setType(recordType);
            } else if (dataFieldAbstract instanceof DataField) {
                DataField dataField = (DataField) dataFieldAbstract;
                CsdlPropertyValue propertyValue = createPropertyValuePath("Value", (String) dataField.getValue());
                propertyValues.add(propertyValue);
                String recordType = "UI.DataField";
                csdlRecord.setType(recordType);
                // add Label
//                String label = dataField.getLabel();
//                if (label == null && withLabel) {
//                    CsdlProperty csdlProperty = csdlEntityType.getProperty((String) dataField.getValue());
//                    if (csdlProperty != null) {
//                        label = (String) Util.getUiLabelMap(locale).get(csdlEntityType.getLabelPrefix() + Util.firstUpperCase(csdlProperty.getName()));
//                    } else {
//                        String fieldValue = (String) dataField.getValue();
//                        if (fieldValue.contains("/")) {
//                            //多段式字段
//                            String uiLabelKey = Arrays.stream(fieldValue.split("/")).map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
//                                    .reduce("", String::concat);
//                            label = (String) Util.getUiLabelMap(locale).get(csdlEntityType.getLabelPrefix() + uiLabelKey);
//                        } else {
//                            label = (String) Util.getUiLabelMap(locale).get(csdlEntityType.getLabelPrefix() + Util.firstUpperCase(fieldValue));
//                        }
//                    }
//                }
//                if (label != null) {
//                    propertyValue = createPropertyValueString("Label", label);
//                    propertyValues.add(propertyValue);
//                }
            } else if (dataFieldAbstract instanceof DataFieldForAction) {
                DataFieldForAction dataFieldForAction = (DataFieldForAction) dataFieldAbstract;
                CsdlPropertyValue propertyValue = createPropertyValueString("Label", dataFieldForAction.getLabel());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueString("Action", dataFieldForAction.getAction());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueEnum("InvocationGrouping", dataFieldForAction.getInvocationGrouping());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueEnum("Criticality", dataFieldForAction.getCriticality());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueBool("Inline", dataFieldForAction.isInline());
                propertyValues.add(propertyValue);
                String recordType = "UI.DataFieldForAction";
                csdlRecord.setType(recordType);
            } else if (dataFieldAbstract instanceof DataFieldForAnnotation) {
                DataFieldForAnnotation dataFieldForAnnotation = (DataFieldForAnnotation) dataFieldAbstract;
                CsdlPropertyValue propertyValue = createPropertyValueString("Label", dataFieldForAnnotation.getLabel());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueString("Target", dataFieldForAnnotation.getTarget());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueEnum("InvocationGrouping", dataFieldForAnnotation.getInvocationGrouping());
                propertyValues.add(propertyValue);
                propertyValue = createPropertyValueEnum("Criticality", dataFieldForAnnotation.getCriticality());
                propertyValues.add(propertyValue);
                String recordType = "UI.DataFieldForAnnotation";
                csdlRecord.setType(recordType);
            }
            csdlRecord.setPropertyValues(propertyValues);
            List<CsdlAnnotation> annotationList = new ArrayList<>();
            if (dataFieldAbstract.getImportance() != null) {
                CsdlAnnotation importanceAnnotation = createAnnotationEnum("UI.Importance", dataFieldAbstract.getImportance(), null);
                annotationList.add(importanceAnnotation);
            }
            String hidden = dataFieldAbstract.getHidden();
            if (hidden != null) {
                CsdlAnnotation hiddenAnnotation = "true".equals(hidden) || "false".equals(hidden) ?
                        createAnnotationBool("UI.Hidden", hidden, null) : createAnnotationPath("UI.Hidden", hidden, null);
                annotationList.add(hiddenAnnotation);
            }
            csdlRecord.setAnnotations(annotationList);
            collectionItems.add(csdlRecord);
        }
        csdlCollection.setItems(collectionItems);
        return csdlCollection;
    }

    private static CsdlAnnotation generateText(Text text, Locale locale) {
        CsdlAnnotation textAnnotation = createAnnotationPath(text.getTermName(), text.getPath(), text.getQualifier());
        CsdlAnnotation textArrangementAnnotation = createAnnotationEnum("UI.TextArrangement", text.getTextArrangement(), null);
        textAnnotation.setAnnotations(UtilMisc.toList(textArrangementAnnotation));
        return textAnnotation;
    }

    private static CsdlAnnotation generateValueList(OfbizCsdlEntityType csdlEntityType, OfbizCsdlProperty csdlProperty,
                                                    ValueList valueList, Locale locale) {
        CsdlAnnotation valueListAnnotation = createAnnotation(valueList.getTermName(), valueList.getQualifier());
        CsdlRecord csdlRecord = new CsdlRecord();
        csdlRecord.setType("Common.ValueListType");
        List<CsdlPropertyValue> propertyValues = new ArrayList<>();
        CsdlPropertyValue propertyValue = createPropertyValueString("Label", valueList.getLabel());
        propertyValues.add(propertyValue);
        propertyValue = createPropertyValueString("CollectionPath", valueList.getCollectionPath());
        propertyValues.add(propertyValue);
        // Parameters
        propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty("Parameters");
        CsdlCollection csdlCollection = new CsdlCollection();
        List<CsdlExpression> csdlExpressions = new ArrayList<>();
        List<String> displayOnlyParameters = valueList.getParameterDisplayOnly();
        CsdlRecord parameterRecord;
        for (String displayOnlyParameter : displayOnlyParameters) {
            parameterRecord = new CsdlRecord();
            parameterRecord.setType("Common.ValueListParameterDisplayOnly");
            CsdlPropertyValue parameterPropertyValue = createPropertyValueString("ValueListProperty", displayOnlyParameter);
            parameterRecord.setPropertyValues(UtilMisc.toList(parameterPropertyValue));
            csdlExpressions.add(parameterRecord);
        }
        List<ValueList.Parameter> parameters = valueList.getParameters();
        if (UtilValidate.isNotEmpty(parameters)) {
            //指定了Parameter
            for (ValueList.Parameter parameter : parameters) {
                parameterRecord = new CsdlRecord();
                parameterRecord.setType(parameter.getParameterType());
                CsdlPropertyValue localDataProperty = createPropertyValuePropertyPath("LocalDataProperty", parameter.getLocalDataProperty());
                CsdlPropertyValue valueListProperty = createPropertyValueString("ValueListProperty", parameter.getValueListProperty());
                parameterRecord.setPropertyValues(UtilMisc.toList(localDataProperty, valueListProperty));
                csdlExpressions.add(parameterRecord);
            }
        } else {
            // 从NavigationProperty里面找到ReferentialConstraint来生成ValueListParameterInOut
            List<CsdlNavigationProperty> csdlNavigationProperties = csdlEntityType.getNavigationProperties();
            if (csdlNavigationProperties != null) {
                List<CsdlReferentialConstraint> csdlReferentialConstraints;
                String propertyName = null;
                String referencedPropertyName = null;
                for (CsdlNavigationProperty csdlNavigationProperty : csdlNavigationProperties) {
                    csdlReferentialConstraints = csdlNavigationProperty.getReferentialConstraints();
                    if (UtilValidate.isEmpty(csdlReferentialConstraints) || csdlReferentialConstraints.size() != 1) { // 组合外键想不出怎么用valuelist
                        continue;
                    }
                    CsdlReferentialConstraint csdlReferentialConstraint = csdlReferentialConstraints.get(0);
                    propertyName = csdlReferentialConstraint.getProperty();
                    if (!propertyName.equals(csdlProperty.getName())) {
                        continue;
                    }
                    referencedPropertyName = csdlReferentialConstraint.getReferencedProperty();
                    break;
                }
                if (referencedPropertyName != null && propertyName != null) {
                    parameterRecord = new CsdlRecord();
                    parameterRecord.setType("Common.ValueListParameterInOut");
                    CsdlPropertyValue localDataProperty = createPropertyValuePropertyPath("LocalDataProperty", propertyName);
                    CsdlPropertyValue valueListProperty = createPropertyValueString("ValueListProperty", referencedPropertyName);
                    parameterRecord.setPropertyValues(UtilMisc.toList(localDataProperty, valueListProperty));
                    csdlExpressions.add(parameterRecord);
                }
            }
        }


        csdlCollection.setItems(csdlExpressions);
        propertyValue.setValue(csdlCollection);
        propertyValues.add(propertyValue);
        csdlRecord.setPropertyValues(propertyValues);

        valueListAnnotation.setExpression(csdlRecord);
        return valueListAnnotation;
    }

    private static CsdlAnnotations generateAutoValueList(OfbizCsdlEntityType csdlEntityType, CsdlNavigationProperty csdlNavigationProperty,
                                                        CsdlSchema csdlSchema, Locale locale) {
        List<CsdlReferentialConstraint> referentialConstraints = csdlNavigationProperty.getReferentialConstraints();
        CsdlReferentialConstraint csdlReferentialConstraint = referentialConstraints.get(0);
        String propertyName = csdlReferentialConstraint.getProperty();
        String referencedPropertyName = csdlReferentialConstraint.getReferencedProperty();
        CsdlProperty csdlProperty = csdlEntityType.getProperty(propertyName);

        CsdlAnnotations csdlAnnotations = new CsdlAnnotations();
        String annotationsTarget = csdlEntityType.getFullQualifiedNameString() + "/" + csdlProperty.getName();
        csdlAnnotations.setTarget(annotationsTarget);

        CsdlAnnotation valueListAnnotation = createAnnotation("Common.ValueList", null);
        CsdlRecord csdlRecord = new CsdlRecord();
        csdlRecord.setType("Common.ValueListType");
        CsdlEntityType navCsdlEntityType = csdlSchema.getEntityType(csdlNavigationProperty.getTypeFQN().getName());
        List<CsdlPropertyValue> propertyValues = new ArrayList<>();
        CsdlPropertyValue propertyValue = createPropertyValueString("Label", navCsdlEntityType.getName());
        propertyValues.add(propertyValue);
        propertyValue = createPropertyValueString("CollectionPath", navCsdlEntityType.getName());
        propertyValues.add(propertyValue);
        // Parameters
        propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty("Parameters");
        CsdlCollection csdlCollection = new CsdlCollection();
        List<CsdlExpression> csdlExpressions = new ArrayList<>();

        //DisplayOnly
//        CsdlRecord displayOnlyRecord = new CsdlRecord();
//        displayOnlyRecord.setType("Common.ValueListParameterDisplayOnly");
//        CsdlPropertyValue parameterPropertyValue = createPropertyValueString("ValueListProperty", "referencedPropertyName");
//        displayOnlyRecord.setPropertyValues(UtilMisc.toList(parameterPropertyValue));
//        csdlExpressions.add(displayOnlyRecord);

        CsdlRecord parameterRecord = new CsdlRecord();
        parameterRecord.setType("Common.ValueListParameterInOut");
        CsdlPropertyValue localDataProperty = createPropertyValuePropertyPath("LocalDataProperty", propertyName);
        CsdlPropertyValue valueListProperty = createPropertyValueString("ValueListProperty", referencedPropertyName);
        parameterRecord.setPropertyValues(UtilMisc.toList(localDataProperty, valueListProperty));
        csdlExpressions.add(parameterRecord);
        csdlCollection.setItems(csdlExpressions);
        propertyValue.setValue(csdlCollection);
        propertyValues.add(propertyValue);
        csdlRecord.setPropertyValues(propertyValues);
        valueListAnnotation.setExpression(csdlRecord);

        csdlAnnotations.setAnnotations(UtilMisc.toList(valueListAnnotation));
        return csdlAnnotations;
    }

    private static CsdlAnnotation generateLineItem(OfbizCsdlEntityType csdlEntityType, LineItem lineItem, Locale locale) {
        CsdlAnnotation csdlAnnotation = createAnnotation(lineItem.getTermName(), lineItem.getQualifier());
        CsdlCollection csdlCollection = createCollectionDataField(csdlEntityType, lineItem.getDataFields(), true, locale);
        csdlAnnotation.setExpression(csdlCollection);
        return csdlAnnotation;
    }

    private static CsdlAnnotation generateHeaderInfo(HeaderInfo headerInfo, Locale locale) {
        HeaderInfoType headerInfoType = headerInfo.getHeaderInfoType();
        CsdlAnnotation csdlAnnotation = createAnnotation(headerInfo.getTermName(), headerInfo.getQualifier());
        CsdlRecord csdlRecord = new CsdlRecord();
        csdlRecord.setType("UI.HeaderInfoType");
        List<CsdlPropertyValue> csdlPropertyValues = new ArrayList<>();
        if (headerInfoType.getTypeName() != null) {
            csdlPropertyValues.add(createPropertyValueString("TypeName", headerInfoType.getTypeName()));
        }
        if (headerInfoType.getTypeNamePlural() != null) {
            csdlPropertyValues.add(createPropertyValueString("TypeNamePlural", headerInfoType.getTypeNamePlural()));
        }
        if (headerInfoType.getImageUrl() != null) {
            if (headerInfoType.getImageUrl().startsWith("http")) {
                csdlPropertyValues.add(createPropertyValueString("ImageUrl", headerInfoType.getImageUrl()));
            } else {
                csdlPropertyValues.add(createPropertyValuePath("ImageUrl", headerInfoType.getImageUrl()));
            }
        }
        if (headerInfoType.getTypeImageUrl() != null) {
            csdlPropertyValues.add(createPropertyValueString("TypeImageUrl", headerInfoType.getTypeImageUrl()));
        }
        if (headerInfoType.getInitials() != null) {
            csdlPropertyValues.add(createPropertyValueString("Initials", headerInfoType.getInitials()));
        }
        if (headerInfoType.getTitle() != null) {
            csdlPropertyValues.add(createPropertyValueDataField("Title", (DataField) headerInfoType.getTitle()));
        }
        if (headerInfoType.getDescription() != null) {
            csdlPropertyValues.add(createPropertyValueDataField("Description", (DataField) headerInfoType.getDescription()));
        }
        csdlRecord.setPropertyValues(csdlPropertyValues);
        csdlAnnotation.setExpression(csdlRecord);
        return csdlAnnotation;
    }

    private static CsdlAnnotation generateFieldGroup(OfbizCsdlEntityType csdlEntityType, FieldGroup fieldGroup, Locale locale) {
        CsdlAnnotation csdlAnnotation = createAnnotation(fieldGroup.getTermName(), fieldGroup.getQualifier());
        CsdlRecord csdlRecord = new CsdlRecord();
        csdlRecord.setType("UI.FieldGroupType");
        CsdlPropertyValue csdlPropertyValueData = new CsdlPropertyValue();
        csdlPropertyValueData.setProperty("Data");
        CsdlCollection csdlCollection = createCollectionDataField(csdlEntityType, fieldGroup.getData(), false, locale);
        csdlPropertyValueData.setValue(csdlCollection);
        CsdlPropertyValue csdlPropertyValueLabel = createPropertyValueString("Label", fieldGroup.getLabel());
        csdlRecord.setPropertyValues(UtilMisc.toList(csdlPropertyValueData, csdlPropertyValueLabel));
        csdlAnnotation.setExpression(csdlRecord);
        return csdlAnnotation;
    }

    private static List<OfbizCsdlAction> generateStickySessionAction(OfbizCsdlEntityType csdlEntityType, Locale locale) {
        List<OfbizCsdlAction> actionList = new ArrayList<>();
        CsdlReturnType returnType = new CsdlReturnType();
        returnType.setType(csdlEntityType.getFullQualifiedNameString());

        //new Action
        List<CsdlParameter> parameters = new ArrayList<>();
        CsdlParameter boundParam = new CsdlParameter();
        boundParam.setName(Util.firstLowerCase(csdlEntityType.getName()));
        boundParam.setType(csdlEntityType.getFullQualifiedNameString());
        boundParam.setCollection(true);
        boundParam.setNullable(false);
        parameters.add(boundParam);
        List<String> insertProperties = csdlEntityType.getInsertRequireProperties();
        csdlEntityType.getProperties().forEach(pro -> {
            OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) pro;
            if (csdlProperty.isImmutable() || insertProperties.contains(csdlProperty.getName())) {
                CsdlParameter parameter = new CsdlParameter();
                parameter.setName(csdlProperty.getName());
                parameter.setType(csdlProperty.getTypeAsFQNObject());
                parameter.setCollection(false);
                parameter.setNullable(!csdlProperty.isImmutable());
                //Label
                String labelKey = "${uiLabelMap." + csdlEntityType.getLabelPrefix() + Util.firstUpperCase(csdlProperty.getName()) + "}";
                CsdlAnnotation annotationString = createAnnotationString("Common.Label", parseValue(labelKey, locale), null);
                parameter.setAnnotations(UtilMisc.toList(annotationString));
                parameters.add(parameter);
            }
        });
        String newActionPath = "com.dpbird.odata.services.ProcessorServices.stickySessionNewAction";
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, csdlEntityType.getName() + "NewAction");
        OfbizCsdlAction newAction = createAction(fullQualifiedName, parameters, returnType,
                true, newActionPath, true, true, Util.firstLowerCase(csdlEntityType.getName()));
        actionList.add(newAction);

        //edit Action
        CsdlParameter editParam = new CsdlParameter();
        editParam.setName(Util.firstLowerCase(csdlEntityType.getName()));
        editParam.setType(csdlEntityType.getFullQualifiedNameString());
        editParam.setNullable(false);
        editParam.setCollection(false);
        String editActionPath = "com.dpbird.odata.services.ProcessorServices.stickySessionEditAction";
        fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, csdlEntityType.getName() + "EditAction");
        OfbizCsdlAction editAction =  createAction(fullQualifiedName, UtilMisc.toList(editParam), returnType,
                true, editActionPath, true, true, editParam.getName());
        actionList.add(editAction);

        //save Action
        String saveActionPath = "com.dpbird.odata.services.ProcessorServices.stickySessionSaveAction";
        fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, csdlEntityType.getName() + "SaveAction");
        OfbizCsdlAction saveAction = createAction(fullQualifiedName, UtilMisc.toList(editParam), returnType,
                true, saveActionPath, true, true, editParam.getName());
        actionList.add(saveAction);

        //discard Action
        OfbizCsdlAction discardAction = new OfbizCsdlAction();
        discardAction.setName("DiscardAction").setBound(false);
        discardAction.setOfbizMethod("DiscardAction");
        actionList.add(discardAction);
        return actionList;

    }

    private static CsdlAnnotations generatePropertyAnnotations(OfbizCsdlEntityType csdlEntityType,
                                                               OfbizCsdlSchema csdlSchema,
                                                               OfbizCsdlProperty csdlProperty, Locale locale) {
        CsdlAnnotations csdlAnnotations = new CsdlAnnotations();
        String annotationsTarget = csdlEntityType.getFullQualifiedNameString() + "/" + csdlProperty.getName();
        csdlAnnotations.setTarget(annotationsTarget);
        List<CsdlAnnotation> csdlAnnotationList = new ArrayList<CsdlAnnotation>();
        if (csdlProperty.isComputed()) {
            csdlAnnotationList.add(createAnnotationBool("Core.Computed", "true", null));
        }
        if (csdlProperty.isHidden()) {
            csdlAnnotationList.add(createAnnotationBool("UI.Hidden", "true", null));
        }
        if (csdlProperty.isHiddenFilter()) {
            csdlAnnotationList.add(createAnnotationBool("UI.HiddenFilter", "true", null));
        }
        if (csdlProperty.isImmutable()) {
            csdlAnnotationList.add(createAnnotationBool("Core.Immutable", "true", null));
        }
        if (csdlProperty.isImageUrl()) {
            csdlAnnotationList.add(createAnnotationBool("UI.IsImageURL", "true", null));
        }
        if (csdlProperty.isExcludeFromNavigationContext()) {
            csdlAnnotationList.add(createAnnotationBool("UI.ExcludeFromNavigationContext", "true", null));
        }
        if (UtilValidate.isNotEmpty(csdlProperty.getLabel())) {
            csdlAnnotationList.add(createAnnotationString("Common.Label", csdlProperty.getLabel(), null));
        }
        if (UtilValidate.isNotEmpty(csdlProperty.getSemanticObject())) {
            csdlAnnotationList.add(createAnnotationString("Common.SemanticObject", csdlProperty.getSemanticObject(), null));
        }
        if (csdlProperty.getFieldControl() != null) {
            csdlAnnotationList.add(createAnnotationEnum("Common.FieldControl", csdlProperty.getFieldControl(), null));
        }
        List<Term> terms = csdlProperty.getTerms();
        if (terms != null) {
            for (Term term : terms) {
                if (term instanceof Text) {
                    csdlAnnotationList.add(generateText((Text) term, locale));
                } else if (term instanceof ValueList) {
                    csdlAnnotationList.add(generateValueList(csdlEntityType, csdlProperty, (ValueList) term, locale));
                    csdlAnnotationList.add(createAnnotationBool("Common.ValueListWithFixedValues",
                            Boolean.toString(((ValueList) term).isWithFixedValues()), null));
                }
            }
        }
        if (UtilValidate.isEmpty(csdlAnnotationList)) {
            return null;
        }
        csdlAnnotations.setAnnotations(csdlAnnotationList);
        return csdlAnnotations;
    }


    private static CsdlAnnotations generateStickySessionAnnotations(OfbizCsdlEntityType csdlEntityType,
                                                               OfbizCsdlSchema csdlSchema, Locale locale) {
        CsdlAnnotations csdlAnnotations = new CsdlAnnotations();
        String entityTypeFQN = csdlEntityType.getFullQualifiedNameString();
        String annotationsTarget = csdlSchema.getNamespace() + "." + csdlSchema.getEntityContainer().getName() + "/" + csdlEntityType.getName();
        csdlAnnotations.setTarget(annotationsTarget);
        List<CsdlAnnotation> csdlAnnotationList = new ArrayList<CsdlAnnotation>();
        CsdlAnnotation annotation = createAnnotation("Session.StickySessionSupported", null);
        CsdlRecord record = new CsdlRecord();
        CsdlPropertyValue newAction = createPropertyValueString("NewAction", entityTypeFQN + "NewAction");
        CsdlPropertyValue editAction = createPropertyValueString("EditAction", entityTypeFQN + "EditAction");
        CsdlPropertyValue saveAction = createPropertyValueString("SaveAction", entityTypeFQN + "SaveAction");
        CsdlPropertyValue discardAction = createPropertyValueString("DiscardAction", "DiscardAction");
        record.setPropertyValues(UtilMisc.toList(newAction, editAction, saveAction, discardAction));
        List<CsdlExpression> csdlExpressions = new ArrayList<>();
        csdlExpressions.add(record);
        CsdlCollection csdlCollection = new CsdlCollection();
        csdlCollection.setItems(csdlExpressions);
        CsdlPropertyValue csdlPropertyValue = new CsdlPropertyValue();
        csdlPropertyValue.setValue(csdlCollection);

        annotation.setExpression(record);
        annotation.setTerm("Session.StickySessionSupported");
        csdlAnnotations.setAnnotations(UtilMisc.toList(annotation));

        return csdlAnnotations;
    }

    private static void addToEdmWebConfig(Delegator delegator, LocalDispatcher dispatcher, EdmWebConfig edmWebConfig,
                                          Element rootElement, Locale locale)
            throws GenericEntityException, GenericServiceException, OfbizODataException {
        List<String> mainEntityTypeNames = null;
        String mainEntityType = rootElement.getAttribute("MainEntityType");
        if (UtilValidate.isNotEmpty(mainEntityType)) {
            mainEntityTypeNames = StringUtil.split(mainEntityType, ",");
            edmWebConfig.setMainEntityTypes(mainEntityTypeNames);
        }
        List<? extends Element> viewEntityElements = UtilXml.childElementList(rootElement, "view-entity");
        for (Element viewEntityElement : viewEntityElements) {
            loadViewEntityFromElement(viewEntityElement, delegator);
        }
        List<? extends Element> children = UtilXml.childElementList(rootElement);
        for (Element currentElt : children) {
            String tagName = currentElt.getTagName();
            if (tagName.equals("ComplexType")) {
                OfbizCsdlComplexType csdlComplexType = loadComplexTypeFromElement(dispatcher, currentElt, locale);
                edmWebConfig.addComplexType(csdlComplexType);
            } else if (tagName.equals("EnumType")) {
                OfbizCsdlEnumType csdlEnumType = loadEnumTypeFromElement(delegator, currentElt, locale);
                edmWebConfig.addEnumType(csdlEnumType);
            } else if (tagName.equals("EntityType")) {
                OfbizCsdlEntityType csdlEntityType = loadEntityTypeFromElement(delegator, dispatcher, currentElt, locale);
                //Default EntitySet
                edmWebConfig.addEntityType(csdlEntityType);
                if (csdlEntityType.isAutoSet()) {
                    OfbizCsdlEntitySet csdlEntitySet = new OfbizCsdlEntitySet(null);
                    csdlEntitySet.setName(csdlEntityType.getName());
                    csdlEntitySet.setType(new FullQualifiedName(csdlEntityType.getFullQualifiedNameString()));
                    csdlEntityType.addReferencedEntitySet(csdlEntitySet.getName());
                    edmWebConfig.addEntitySet(csdlEntitySet);
                }
                //Default StickySessionAction TODO: remove isAutoSet
                if (csdlEntityType.isAutoDraft() && csdlEntityType.isAutoSet()) {
                    List<OfbizCsdlAction> actionList = generateStickySessionAction(csdlEntityType, locale);
                    actionList.forEach(edmWebConfig::addAction);
                    //DiscardActionImport
                    CsdlActionImport csdlActionImport = new CsdlActionImport();
                    FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, "DiscardAction");
                    csdlActionImport.setName("DiscardAction").setAction(fullQualifiedName);
                    edmWebConfig.addActionImport(csdlActionImport);
                }
                csdlEntityType.getActionList().forEach(edmWebConfig::addAction);
                csdlEntityType.getFunctionList().forEach(edmWebConfig::addFunction);
            } else if (tagName.equals("Action")) {
                OfbizCsdlAction csdlAction = loadActionFromElement(currentElt);
                edmWebConfig.addAction(csdlAction);
            } else if (tagName.equals("Function")) {
                OfbizCsdlFunction csdlFunction = loadFunctionFromElement(currentElt);
                edmWebConfig.addFunction(csdlFunction);
            } else if (tagName.equals("EntityContainer")) {
                List<? extends Element> containerChildren = UtilXml.childElementList(currentElt);
                for (Element inContainerElement : containerChildren) {
                    String inContainerTagName = inContainerElement.getTagName();
                    if (inContainerTagName.equals("EntitySet")) {
                        OfbizCsdlEntitySet csdlEntitySet = loadEntitySetFromElement(inContainerElement, locale);
                        edmWebConfig.addEntitySet(csdlEntitySet);
                        OfbizCsdlEntityType ofbizCsdlEntityType = edmWebConfig.getEntityTypeMap().get(csdlEntitySet.getTypeFQN().getName());
                        if (ofbizCsdlEntityType != null) {
                            ofbizCsdlEntityType.addReferencedEntitySet(csdlEntitySet.getName());
                        }
                    } else if (inContainerTagName.equals("FunctionImport")) {
                        CsdlFunctionImport csdlFunctionImport = loadFunctionImportFromElement(inContainerElement);
                        edmWebConfig.addFunctionImport(csdlFunctionImport);
                    } else if (inContainerTagName.equals("ActionImport")) {
                        CsdlActionImport csdlActionImport = loadActionImportFromElement(inContainerElement);
                        edmWebConfig.addActionImport(csdlActionImport);
                    } else if (inContainerTagName.equals("Singleton")) {
                        OfbizCsdlSingleton csdlSingleton = loadSingletonFromElement(inContainerElement);
                        edmWebConfig.addSingleton(csdlSingleton);
                    }
                }
            } else if (tagName.equals("Annotations")) {
                CsdlAnnotations csdlAnnotations = loadAnnotationsFromElement(currentElt, locale);
                edmWebConfig.addAnnotations(csdlAnnotations);
            } else if (tagName.equals("Term")) {
                CsdlTerm csdlTerm = loadTermFromElement(currentElt, locale);
                edmWebConfig.addTerm(csdlTerm);
            }
        }
        generateNavigationBindings(edmWebConfig);
    }

    private static void generateNavigationBindings(EdmWebConfig edmWebConfig) throws OfbizODataException {
        Collection<OfbizCsdlEntityType> entityTypes = edmWebConfig.getEntityTypes();
        for (OfbizCsdlEntityType entityType : entityTypes) {
            List<CsdlNavigationProperty> navigationProperties = entityType.getNavigationProperties();
            for (CsdlNavigationProperty navigationProperty : navigationProperties) {
                OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty = (OfbizCsdlNavigationProperty) navigationProperty;
                if (ofbizCsdlNavigationProperty.isAutoBinding()) {
                    generateNavigationBinding(entityType, ofbizCsdlNavigationProperty, edmWebConfig);
                }
            }
        }
    }

    private static void generateNavigationBinding(OfbizCsdlEntityType csdlEntityType,
                                                  OfbizCsdlNavigationProperty ofbizCsdlNavigationProperty,
                                                  EdmWebConfig edmWebConfig) throws OfbizODataException {
        String navigationEntityTypeName = ofbizCsdlNavigationProperty.getTypeFQN().getName();
        OfbizCsdlEntityType navigationEntityType = edmWebConfig.getEntityType(navigationEntityTypeName);
        if (navigationEntityType == null) {
            throw new OfbizODataException("Can't find EntityType for EntityType:" + csdlEntityType.getName() + ", NavigationProperty:" + ofbizCsdlNavigationProperty.getName());
        }
        List<String> referencedEntitySetNames = navigationEntityType.getReferencedEntitySet();
        if (UtilValidate.isEmpty(referencedEntitySetNames) || UtilValidate.isEmpty(csdlEntityType.getReferencedEntitySet())) {
            return;
        }
        String referencedEntitySetName = referencedEntitySetNames.get(0); // 就取第一个作为autoBindingd的target
//        String entitySetName = csdlEntityType.firstReferencedEntitySet();
        List<String> entitySetNames = csdlEntityType.getReferencedEntitySet();
        for (String entitySetName : entitySetNames) {
            CsdlEntitySet csdlEntitySet = edmWebConfig.getEntitySet(entitySetName);
            List<CsdlNavigationPropertyBinding> csdlNavigationPropertyBindings = csdlEntitySet.getNavigationPropertyBindings();
            if (UtilValidate.isEmpty(csdlNavigationPropertyBindings)) {
                csdlNavigationPropertyBindings = new ArrayList<>();
            }
            boolean isBindingExist = false;
            for (CsdlNavigationPropertyBinding csdlNavigationPropertyBinding : csdlNavigationPropertyBindings) {
                if (csdlNavigationPropertyBinding.getPath().equals(ofbizCsdlNavigationProperty.getName())) { // 已经存在binding了，就不需要了
//                    return;
                    isBindingExist = true;
                    break;
                }
            }
            if (isBindingExist) {
                continue;
            }
            // 不存在，就要generate一个
            CsdlNavigationPropertyBinding csdlNavigationPropertyBinding = new CsdlNavigationPropertyBinding();
            csdlNavigationPropertyBinding.setPath(ofbizCsdlNavigationProperty.getName());
            csdlNavigationPropertyBinding.setTarget(referencedEntitySetName);
            csdlNavigationPropertyBindings.add(csdlNavigationPropertyBinding);
            csdlEntitySet.setNavigationPropertyBindings(csdlNavigationPropertyBindings);
        }
    }

    private static OfbizCsdlEntityType loadEntityTypeFromElement(Delegator delegator, LocalDispatcher dispatcher,
                                                                 Element entityTypeElement, Locale locale)
            throws GenericServiceException {
        String name = entityTypeElement.getAttribute("Name");
        String ofbizEntity = name;
        String attrEntityName = null;
        String attrNumericEntityName = null;
        String attrDateEntityName = null;
        String handlerClass = null;
        String entityConditionStr = null;
        String searchOption = null;
        String draftEntityName = null;
        EntityCondition entityCondition = null;
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("OfbizEntity"))) {
            ofbizEntity = entityTypeElement.getAttribute("OfbizEntity");
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("AttrEntityName"))) {
            attrEntityName = entityTypeElement.getAttribute("AttrEntityName");
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("AttrNumericEntityName"))) {
            attrNumericEntityName = entityTypeElement.getAttribute("AttrNumericEntityName");
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("AttrDateEntityName"))) {
            attrDateEntityName = entityTypeElement.getAttribute("AttrDateEntityName");
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("DraftEntityName"))) {
            draftEntityName = entityTypeElement.getAttribute("DraftEntityName");
        }
        ModelEntity modelEntity = null;
        try {
            modelEntity = delegator.getModelReader().getModelEntity(ofbizEntity);
        } catch (GenericEntityException e) {
            Debug.logWarning(e.getMessage(), module);
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("Handler"))) {
            handlerClass = entityTypeElement.getAttribute("Handler");
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("EntityCondition"))) {
            entityConditionStr = entityTypeElement.getAttribute("EntityCondition");
            entityCondition = parseEntityCondition(entityConditionStr, null);
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("SearchOption"))) {
            searchOption = entityTypeElement.getAttribute("SearchOption");
        }
        List<CsdlProperty> csdlProperties = new ArrayList<>();
        List<CsdlNavigationProperty> csdlNavigationProperties = null;
        List<CsdlPropertyRef> csdlPropertyRefs = null;
        boolean filterByDate = false;
        String filterByDateAttr = entityTypeElement.getAttribute("FilterByDate");
        if ("true".equals(filterByDateAttr)) {
            filterByDate = true;
        }
        String labelPrefix = name;
        String labelPrefixAttr = entityTypeElement.getAttribute("LabelPrefix");
        if (UtilValidate.isNotEmpty(labelPrefixAttr)) {
            labelPrefix = labelPrefixAttr;
        }
        String baseType = entityTypeElement.getAttribute("BaseType");
        boolean hasDerivedEntity = false;
        String hasDerivedEntityAttr = entityTypeElement.getAttribute("hasDerivedEntity");
        if ("true".equals(hasDerivedEntityAttr)) {
            hasDerivedEntity = true;
        }
        boolean openType = false;
        String openTypeString = entityTypeElement.getAttribute("OpenType");
        if ("true".equals(openTypeString)) {
            openType = true;
        }
        boolean isAbstract = false;
        String isAbstractAttr = entityTypeElement.getAttribute("Abstract");
        if ("true".equals(isAbstractAttr)) {
            isAbstract = true;
        }
        boolean groupBy = true;
        String isGroupBy = entityTypeElement.getAttribute("GroupBy");
        if ("false".equals(isGroupBy)) {
            groupBy = false;
        }
        boolean hasStream = false;
        String isStream = entityTypeElement.getAttribute("HasStream");
        if ("true".equals(isStream)) {
            hasStream = true;
        }
        boolean autoProperties = false; // 缺省从ofbiz的entity定义中获取全部字段
        String autoPropertiesAttr = entityTypeElement.getAttribute("AutoProperties");
        if ("true".equals(autoPropertiesAttr)) {
            autoProperties = true;
        }
        //是否自动生成所有Property的Label
        boolean autoLabel = true;
        if ("false".equals(entityTypeElement.getAttribute("AutoLabel"))) {
            autoLabel = false;
        }
        //是否自动生成StickySessionAction
        boolean autoDraft = false;
        if ("true".equals(entityTypeElement.getAttribute("AutoDraft"))) {
            autoDraft = true;
        }
        boolean autoValueList = false;
        if ("true".equals(entityTypeElement.getAttribute("AutoValueList"))) {
            autoValueList = true;
        }
        boolean autoSet = false;
        if ("true".equals(entityTypeElement.getAttribute("AutoSet"))) {
            autoSet = true;
        }
        if (UtilValidate.isNotEmpty(entityTypeElement.getAttribute("Properties"))) {
            List<OfbizCsdlProperty> propertyList = generatePropertiesFromAttribute(dispatcher, modelEntity, null, entityTypeElement.getAttribute("Properties"), locale, labelPrefix, autoLabel);
            csdlProperties.addAll(propertyList);
        }
        List<String> insertRequireProperties = new ArrayList<>();
        String insertRequireAttr = entityTypeElement.getAttribute("InsertRequireProperties");
        if (UtilValidate.isNotEmpty(insertRequireAttr)) {
            insertRequireProperties.addAll(Arrays.asList(insertRequireAttr.split(",")));
        }
        List<String> excludeProperties = new ArrayList<>();
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, name);
        List<? extends Element> entityTypeChildren = UtilXml.childElementList(entityTypeElement);
        List<CsdlAnnotation> csdlAnnotationList = new ArrayList<>();
        List<Term> terms = new ArrayList<>();
        List<OfbizCsdlAction> actionList = new ArrayList<>();
        List<OfbizCsdlFunction> functionList = new ArrayList<>();
        List<EntityTypeRelAlias> relAliases = new ArrayList<>();
        boolean hasRelField = false;
        for (Element inEntityElement : entityTypeChildren) {
            String inEntityTagName = inEntityElement.getTagName();
            if (inEntityTagName.equals("RelAlias")) {
                EntityTypeRelAlias entityTypeRelAlias = loadRelAliasFromElement(dispatcher, modelEntity, inEntityElement, locale);
                relAliases.add(entityTypeRelAlias);
                if (UtilValidate.isNotEmpty(inEntityElement.getAttribute("Properties"))) {
                    List<OfbizCsdlProperty> propertyList = generatePropertiesFromAttribute(dispatcher, modelEntity, entityTypeRelAlias,
                            inEntityElement.getAttribute("Properties"), locale, labelPrefix, autoLabel);
                    csdlProperties.addAll(propertyList);
                }
                hasRelField = true;
            } else if (inEntityTagName.equals("Property")) {
                OfbizCsdlProperty csdlProperty = loadPropertyFromElement(dispatcher, modelEntity, relAliases, inEntityElement, locale, labelPrefix, autoLabel);
                csdlProperties.add(csdlProperty);
                if (UtilValidate.isNotEmpty(csdlProperty.getRelAlias())) {
                    hasRelField = true;
                }
            } else if (inEntityTagName.equals("NavigationProperty")) {
                CsdlNavigationProperty csdlNavigationProperty = loadNavigationFromElement(delegator, modelEntity, inEntityElement);
                if (csdlNavigationProperties == null) {
                    csdlNavigationProperties = new ArrayList<>();
                }
                csdlNavigationProperties.add(csdlNavigationProperty);
            } else if (inEntityTagName.equals("Exclude")) {
                String excludeName = inEntityElement.getAttribute("Name");
                excludeProperties.add(excludeName);
            } else if (inEntityTagName.equals("Key")) {
                csdlPropertyRefs = loadKeyFromElement(inEntityElement, locale);
            }

            // all the Terms
            if (inEntityTagName.equals("LineItem")) {
                terms.add(loadLineItemFromElement(inEntityElement, locale));
            } else if (inEntityTagName.equals("SemanticKey")) {
                terms.add(loadSemanticKeyFromElement(inEntityElement));
            } else if (inEntityTagName.equals("SelectionFields")) {
                terms.add(loadSelectionFieldsFromElement(inEntityElement));
            } else if (inEntityTagName.equals("FieldGroup")) {
                terms.add(loadFieldGroupFromElement(inEntityElement, locale));
            } else if (inEntityTagName.equals("HeaderInfo")) {
                terms.add(loadHeaderInfoFromElement(inEntityElement, locale));
            }
            // manually added Annotation
            if (inEntityTagName.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(inEntityElement, locale);
                csdlAnnotationList.add(csdlAnnotation);
            }
            // manually added Annotation
            if (inEntityTagName.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(inEntityElement, locale);
                csdlAnnotationList.add(csdlAnnotation);
            }
            // Action
            if (inEntityTagName.equals("Action")) {
                actionList.add(loadActionFromElement(inEntityElement));
            }
            // Function
            if (inEntityTagName.equals("Function")) {
                functionList.add(loadFunctionFromElement(inEntityElement));
            }
        }
        OfbizCsdlEntityType csdlEntityType = createEntityType(delegator, dispatcher, fullQualifiedName, ofbizEntity,
                attrEntityName, attrNumericEntityName, attrDateEntityName, handlerClass, autoProperties,
                csdlProperties, csdlNavigationProperties, csdlPropertyRefs, filterByDate, baseType, hasDerivedEntity,
                excludeProperties, entityCondition, entityConditionStr, labelPrefix, locale, searchOption, groupBy, hasStream,
                autoLabel, autoDraft, autoValueList, autoSet, draftEntityName);
        csdlEntityType.setAbstract(isAbstract);
        csdlEntityType.setAnnotations(csdlAnnotationList);
        csdlEntityType.setTerms(terms);
        csdlEntityType.setHasRelField(hasRelField);
        csdlEntityType.setRelAliases(relAliases);
        csdlEntityType.setOpenType(openType);
        csdlEntityType.setActionList(actionList);
        csdlEntityType.setFunctionList(functionList);
        csdlEntityType.setInsertRequireProperties(insertRequireProperties);
        return csdlEntityType;
    }

    private static Term loadLineItemFromElement(Element lineItemElement, Locale locale) {
        List<? extends Element> lineItemChildren = UtilXml.childElementList(lineItemElement);
        if (UtilValidate.isEmpty(lineItemChildren)) {
            return null;
        }
        String qualifier = lineItemElement.getAttribute("Qualifier");
        LineItem lineItem = new LineItem(qualifier);
        for (Element lineItemChild : lineItemChildren) {
            String lineItemChildTag = lineItemChild.getTagName();
            if (lineItemChildTag.equals("DataField")) {
                String values = lineItemChild.getAttribute("Values");
                List<String> propertyNames = StringUtil.split(values, ",");
                String importance = lineItemChild.getAttribute("Importance");
                for (String propertyName : propertyNames) {
                    DataField dataField = new DataField(propertyName);
                    if (UtilValidate.isNotEmpty(importance)) {
                        dataField.setImportance(ImportanceType.valueOf(importance));
                    }
                    lineItem.addDataField(dataField);
                }
            } else if (lineItemChildTag.equals("DataFieldForAction")) {
                String label = loadAttributeValue(lineItemChild, "Label", locale);
                String action = lineItemChild.getAttribute("Action");
                String invocationGrouping = lineItemChild.getAttribute("InvocationGrouping");
                String criticality = lineItemChild.getAttribute("Criticality");
                String inline = lineItemChild.getAttribute("Inline");
                String hidden = lineItemChild.getAttribute("Hidden");
                String importance = lineItemChild.getAttribute("Importance");
                DataFieldForAction dataFieldForAction = new DataFieldForAction();
                dataFieldForAction.setLabel(label);
                dataFieldForAction.setAction(OfbizMapOdata.NAMESPACE + "." + action);
                if (UtilValidate.isNotEmpty(invocationGrouping)) {
                    dataFieldForAction.setInvocationGrouping(OperationGroupingType.valueOf(invocationGrouping));
                }
                if (UtilValidate.isNotEmpty(criticality)) {
                    dataFieldForAction.setCriticality(CriticalityType.valueOf(criticality));
                }
                if (UtilValidate.isNotEmpty(inline)) {
                    dataFieldForAction.setInline(Boolean.valueOf(inline));
                }
                if (UtilValidate.isNotEmpty(hidden)) {
                    dataFieldForAction.setHidden(hidden);
                }
                if (UtilValidate.isNotEmpty(importance)) {
                    dataFieldForAction.setImportance(ImportanceType.valueOf(importance));
                }
                lineItem.addDataField(dataFieldForAction);
            } else if (lineItemChildTag.equals("DataFieldForAnnotation")) {
                String label = loadAttributeValue(lineItemChild, "Label", locale);
                String target = lineItemChild.getAttribute("Target");
                String invocationGrouping = lineItemChild.getAttribute("InvocationGrouping");
                String criticality = lineItemChild.getAttribute("Criticality");
                String importance = lineItemChild.getAttribute("Importance");
                DataFieldForAnnotation dataFieldForAnnotation = new DataFieldForAnnotation();
                dataFieldForAnnotation.setLabel(label);
                dataFieldForAnnotation.setTarget(target);
                if (UtilValidate.isNotEmpty(invocationGrouping)) {
                    dataFieldForAnnotation.setInvocationGrouping(OperationGroupingType.valueOf(invocationGrouping));
                }
                if (UtilValidate.isNotEmpty(criticality)) {
                    dataFieldForAnnotation.setCriticality(CriticalityType.valueOf(criticality));
                }
                if (UtilValidate.isNotEmpty(importance)) {
                    dataFieldForAnnotation.setImportance(ImportanceType.valueOf(importance));
                }
                lineItem.addDataField(dataFieldForAnnotation);
            }
        }

        return lineItem;
    }

    private static Term loadHeaderInfoFromElement(Element headerInfoElement, Locale locale) {
        HeaderInfoType headerInfoType = new HeaderInfoType();
        String qualifier = loadAttributeValue(headerInfoElement, "Qualifier", locale);
        HeaderInfo headerInfo = new HeaderInfo(qualifier);
        String typeName = loadAttributeValue(headerInfoElement, "TypeName", locale);
        headerInfoType.setTypeName(typeName);
        String typeNamePlural = loadAttributeValue(headerInfoElement, "TypeNamePlural", locale);
        headerInfoType.setTypeNamePlural(typeNamePlural);
        String imageUrl = loadAttributeValue(headerInfoElement, "ImageUrl", locale);
        headerInfoType.setImageUrl(imageUrl);
        String typeImageUrl = loadAttributeValue(headerInfoElement, "TypeImageUrl", locale);
        headerInfoType.setTypeImageUrl(typeImageUrl);
        String initials = loadAttributeValue(headerInfoElement, "Initials", locale);
        headerInfoType.setInitials(initials);
        List<? extends Element> headerInfoChildren = UtilXml.childElementList(headerInfoElement);
        if (UtilValidate.isEmpty(headerInfoChildren)) {
            headerInfo.setHeaderInfoType(headerInfoType);
            return headerInfo;
        }
        for (Element headerInfoChild : headerInfoChildren) {
            String headerInfoChildTag = headerInfoChild.getTagName();
            if (headerInfoChildTag.equals("Title")) {
                String path = loadAttributeValue(headerInfoChild, "Path", locale);
                DataField dataField = new DataField(path);
                headerInfoType.setTitle(dataField);
            } else if (headerInfoChildTag.equals("Description")) {
                String path = loadAttributeValue(headerInfoChild, "Path", locale);
                DataField dataField = new DataField(path);
                headerInfoType.setDescription(dataField);
            }
        }
        headerInfo.setHeaderInfoType(headerInfoType);
        return headerInfo;
    }

    private static Term loadFieldGroupFromElement(Element fieldGroupElement, Locale locale) {
        String qualifier = fieldGroupElement.getAttribute("Qualifier");
        if (UtilValidate.isEmpty(qualifier)) {
            qualifier = null;
        }
        FieldGroup fieldGroup = new FieldGroup(qualifier);
        String label = fieldGroupElement.getAttribute("Label");
        if (UtilValidate.isNotEmpty(label)) {
            fieldGroup.setLabel(parseValue(label, locale));
        }
        List<? extends Element> fieldGroupChildren = UtilXml.childElementList(fieldGroupElement);
        if (UtilValidate.isEmpty(fieldGroupChildren)) {
            return null;
        }
        for (Element fieldGroupChild : fieldGroupChildren) {
            String fieldGroupChildTag = fieldGroupChild.getTagName();
            if (fieldGroupChildTag.equals("DataField")) {
                String values = fieldGroupChild.getAttribute("Values");
                List<String> propertyNames = StringUtil.split(values, ",");
                String labelsAttr = fieldGroupChild.getAttribute("Labels");
                List<String> labels = StringUtil.split(labelsAttr, ",");
                for (int i = 0; i < propertyNames.size(); i++) {
                    String propertyName = propertyNames.get(i);
                    DataField dataField = new DataField(propertyName);
                    if (UtilValidate.isNotEmpty(labels)) {
                        if (i < labels.size()) {
                            String dataFieldLabel = labels.get(i);
                            dataField.setLabel(parseValue(dataFieldLabel, locale));
                        }
                    }
                    fieldGroup.addData(dataField);
                }
            }/* else if (fieldGroupChildTag.equals("DataFieldForAction")) {
                String label = fieldGroupChild.getAttribute("Label");
                String action = fieldGroupChild.getAttribute("Action");
                String invocationGrouping = fieldGroupChild.getAttribute("InvocationGrouping");
                String criticality = fieldGroupChild.getAttribute("Criticality");
                String inline = fieldGroupChild.getAttribute("Inline");
                String importance = fieldGroupChild.getAttribute("Importance");
                DataFieldForAction dataFieldForAction = new DataFieldForAction();
                dataFieldForAction.setLabel(label);
                dataFieldForAction.setAction(OfbizMapOdata.NAMESPACE + "." + action);
                dataFieldForAction.setInvocationGrouping(OperationGroupingType.valueOf(invocationGrouping));
                dataFieldForAction.setCriticality(CriticalityType.valueOf(criticality));
                dataFieldForAction.setInline(Boolean.valueOf(inline));
                dataFieldForAction.setImportance(ImportanceType.valueOf(importance));
                fieldGroup.addData(dataFieldForAction);
            }*/
        }

        return fieldGroup;
    }

    private static CsdlNavigationProperty loadNavigationFromElement(Delegator delegator, ModelEntity modelEntity, Element navigationPropertyElement) {
        String name = navigationPropertyElement.getAttribute("Name");
        String type = navigationPropertyElement.getAttribute("Type");
        String autoBindingAttr = navigationPropertyElement.getAttribute("AutoBinding");
        String stickyReadOnlyAttr = navigationPropertyElement.getAttribute("ReadOnly");
        String handlerNode = navigationPropertyElement.getAttribute("HandlerNode");
        String preCreateAttr = navigationPropertyElement.getAttribute("PreCreate");
        boolean autoBinding = true;
        if (UtilValidate.isNotEmpty(autoBindingAttr)) {
            autoBinding = Boolean.valueOf(autoBindingAttr);
        }
        boolean stickyReadOnly = false;
        if (UtilValidate.isNotEmpty(stickyReadOnlyAttr)) {
            stickyReadOnly = Boolean.valueOf(stickyReadOnlyAttr);
        }
        boolean preCreate = false;
        if (UtilValidate.isNotEmpty(preCreateAttr)) {
            preCreate = Boolean.valueOf(preCreateAttr);
        }
        boolean filterByDate = false;
        String filterByDateAttr = navigationPropertyElement.getAttribute("FilterByDate");
        if (UtilValidate.isNotEmpty(filterByDateAttr)) {
            filterByDate = Boolean.valueOf(filterByDateAttr);
        }
        String containsTargetStr = navigationPropertyElement.getAttribute("ContainsTarget");
        boolean containsTarget = containsTargetStr != null && containsTargetStr.equals("true");
        String midEntity = navigationPropertyElement.getAttribute("MidEntity");
        String midEntityCondition = navigationPropertyElement.getAttribute("MidEntityCondition");
        String shortcutView = navigationPropertyElement.getAttribute("ShortcutView");
        String relOne = navigationPropertyElement.getAttribute("RelOne");
        String relTwo = navigationPropertyElement.getAttribute("RelTwo");
        String createService = navigationPropertyElement.getAttribute("CreateService");
        String removeService = navigationPropertyElement.getAttribute("RemoveService");
        String relationsAttr = navigationPropertyElement.getAttribute("Relations");
        String handlerAttr = navigationPropertyElement.getAttribute("Handler");
        String partner = navigationPropertyElement.getAttribute("Partner");
        EntityTypeRelAlias relAlias = loadRelAliasFromAttribute(delegator, modelEntity, name, relationsAttr);
        //处理完RelAlias再处理
        ModelRelation modelRelation = null;
//        if (UtilValidate.isEmpty(UtilXml.childElementList(navigationPropertyElement))) {
//            modelRelation = modelEntity.getRelation(name);
//        } else {
//            //处理ReferentialConstraint 根据ReferentialConstraint在数据库生成一个Relation
//            modelRelation = processReferentialConstraint(delegator, modelEntity, navigationPropertyElement);
//        }
        //获取modelRelation
        if (UtilValidate.isNotEmpty(relAlias) && relAlias.getRelations().size() == 1) {
            modelRelation = relAlias.getRelationsEntity().get(relAlias.getRelations().get(0));
        }
        String relationType = null;
        if (modelRelation != null) {
            relationType = modelRelation.getType();
        }
        if (UtilValidate.isEmpty(type) && modelRelation != null) {
            type = modelRelation.getRelEntityName();
        }
        String isCollectionStr = navigationPropertyElement.getAttribute("IsCollection");
        boolean isCollection = false;
        if (UtilValidate.isEmpty(isCollectionStr)) {
            if ("many".equals(relationType)) {
                isCollection = true;
            }
        } else {
            if ("true".equals(isCollectionStr)) {
                isCollection = true;
            }
        }

        OfbizCsdlNavigationProperty navigationProperty = new OfbizCsdlNavigationProperty();
        navigationProperty.setName(name);
        navigationProperty.setAutoBinding(autoBinding);
        navigationProperty.setFilterByDate(filterByDate);
        navigationProperty.setType(new FullQualifiedName(OfbizMapOdata.NAMESPACE, type));
        navigationProperty.setCollection(isCollection);
        navigationProperty.setContainsTarget(containsTarget);
        navigationProperty.setRelAlias(relAlias);
        navigationProperty.setReadOnly(stickyReadOnly);
        navigationProperty.setPreCreate(preCreate);
        navigationProperty.setHandlerNode(handlerNode);
        if (UtilValidate.isNotEmpty(midEntity)) {
            navigationProperty.setMidEntity(midEntity);
        }
        if (UtilValidate.isNotEmpty(midEntityCondition)) {
            navigationProperty.setMidEntityCondition(parseEntityCondition(midEntityCondition, navigationProperty.getSimpleMapCondition()));
        }
        if (UtilValidate.isNotEmpty(shortcutView)) {
            navigationProperty.setShortcutView(shortcutView);
        }
        if (UtilValidate.isNotEmpty(relOne)) {
            navigationProperty.setRelOne(relOne);
        }
        if (UtilValidate.isNotEmpty(relTwo)) {
            navigationProperty.setRelTwo(relTwo);
        }
        if (UtilValidate.isNotEmpty(createService)) {
            navigationProperty.setCreateService(createService);
        }
        if (UtilValidate.isNotEmpty(removeService)) {
            navigationProperty.setRemoveService(removeService);
        }
        if (UtilValidate.isNotEmpty(handlerAttr)) {
            navigationProperty.setHandler(handlerAttr);
        }
        if (UtilValidate.isNotEmpty(partner)) {
            navigationProperty.setPartner(partner);
        }
        List<? extends Element> navigationChildren = UtilXml.childElementList(navigationPropertyElement);
        if (UtilValidate.isNotEmpty(navigationChildren)) {
            List<CsdlReferentialConstraint> csdlReferentialConstraints = new ArrayList<>();
            for (Element inNavigationElement : navigationChildren) {
                String inNavigationTagName = inNavigationElement.getTagName();
                if ("ReferentialConstraint".equals(inNavigationTagName)) {
                    CsdlReferentialConstraint csdlReferentialConstraint = new CsdlReferentialConstraint();
                    String referentialConstraintProperty = inNavigationElement.getAttribute("Property");
                    String referentialConstraintReferencedProperty = inNavigationElement.getAttribute("ReferencedProperty");
                    csdlReferentialConstraint.setProperty(referentialConstraintProperty);
                    csdlReferentialConstraint.setReferencedProperty(referentialConstraintReferencedProperty);
                    csdlReferentialConstraints.add(csdlReferentialConstraint);
                }
            }
            navigationProperty.setReferentialConstraints(csdlReferentialConstraints);
        } else {
            if ("one".equals(relationType) || "one-nofk".equals(relationType)) {
                List<CsdlReferentialConstraint> csdlReferentialConstraints = new ArrayList<>();
                List<ModelKeyMap> modelKeyMaps = modelRelation.getKeyMaps();
                for (ModelKeyMap modelKeyMap : modelKeyMaps) {
                    CsdlReferentialConstraint csdlReferentialConstraint = new CsdlReferentialConstraint();
                    String referentialConstraintProperty = modelKeyMap.getFieldName();
                    String referentialConstraintReferencedProperty = modelKeyMap.getRelFieldName();
                    csdlReferentialConstraint.setProperty(referentialConstraintProperty);
                    csdlReferentialConstraint.setReferencedProperty(referentialConstraintReferencedProperty);
                    csdlReferentialConstraints.add(csdlReferentialConstraint);
                }
                navigationProperty.setReferentialConstraints(csdlReferentialConstraints);
            }
        }
        return navigationProperty;
    }

    public static ModelRelation processReferentialConstraint(Delegator delegator, ModelEntity modelEntity,
                                                             Element navigationPropertyElement) {
        //TODO：如果navigation不定义type或者isCollection
        //暂时拿type当作entityName使用
        String type = navigationPropertyElement.getAttribute("Type");
        String isCollectionStr = navigationPropertyElement.getAttribute("IsCollection");
        ModelEntity navModelEntity = delegator.getModelEntity(type);
        if (navModelEntity == null) {
            Debug.logInfo("Navigation type is empty.", module);
            return null;
        }
        //创建modelRelation
        List<ModelKeyMap> modelKeyMapList = new ArrayList<>();
        String relType = "true".equals(isCollectionStr) ? "many" : "one-nofk";
        List<? extends Element> referentialConstraintEle = UtilXml.childElementList(navigationPropertyElement, "ReferentialConstraint");
        for (Element currReferentEle : referentialConstraintEle) {
            String constraintProperty = currReferentEle.getAttribute("Property");
            String constraintReferencedProperty = currReferentEle.getAttribute("ReferencedProperty");
            modelKeyMapList.add(new ModelKeyMap(constraintProperty, constraintReferencedProperty));
        }
        ModelRelation newRelation = ModelRelation.create(modelEntity, null, relType, null, navModelEntity.getEntityName(), null, modelKeyMapList, false);
        //已经存在的relation，先移除再新增
        if (modelEntity.getRelation(newRelation.getCombinedName()) != null) {
            for (int i = 0; i < modelEntity.getRelationsSize(); i++) {
                ModelRelation relation = modelEntity.getRelation(i);
                if (relation.getCombinedName().equals(newRelation.getCombinedName())) {
                    modelEntity.removeRelation(i);
                }
            }
        }
        modelEntity.addRelation(newRelation);
        return newRelation;
    }

    public static OfbizCsdlEnumType loadEnumTypeFromElement(Delegator delegator, Element enumTypeElement, Locale locale)
            throws GenericEntityException {
        String name = enumTypeElement.getAttribute("Name");
        String ofbizEntity = null;
        String entityConditionStr;
        EntityCondition entityCondition = null;
        if (UtilValidate.isNotEmpty(enumTypeElement.getAttribute("OfbizEntity"))) {
            ofbizEntity = enumTypeElement.getAttribute("OfbizEntity");
        }
        if (UtilValidate.isNotEmpty(enumTypeElement.getAttribute("EntityCondition"))) {
            entityConditionStr = enumTypeElement.getAttribute("EntityCondition");
            entityCondition = parseEntityCondition(entityConditionStr, null);
        }
        List<CsdlEnumMember> csdlEnumMembers;
        if (ofbizEntity != null) {
            csdlEnumMembers = getEnumMemberFromOfbizEntity(delegator, ofbizEntity, entityCondition, locale);
        } else {
            List<? extends Element> enumTypeChildren = UtilXml.childElementList(enumTypeElement);
            csdlEnumMembers = new ArrayList<CsdlEnumMember>();
            for (Element inEnumElement : enumTypeChildren) {
                String inEnumTagName = inEnumElement.getTagName();
                if (inEnumTagName.equals("Member")) {
                    CsdlEnumMember csdlEnumMember = loadEnumMemberFromElement(inEnumElement);
                    csdlEnumMembers.add(csdlEnumMember);
                }
            }
        }
        OfbizCsdlEnumType csdlEnumType = new OfbizCsdlEnumType(ofbizEntity, entityCondition);
        csdlEnumType.setName(name);
        csdlEnumType.setMembers(csdlEnumMembers);
        return csdlEnumType;
    }

    private static CsdlEnumMember loadEnumMemberFromElement(Element enumMemberElement) {
        CsdlEnumMember csdlEnumMember = new CsdlEnumMember();
        String enumMemberName = enumMemberElement.getAttribute("Name");
        String enumMemberValue = enumMemberElement.getAttribute("Value");
        csdlEnumMember.setName(enumMemberName);
        csdlEnumMember.setValue(enumMemberValue);
        return csdlEnumMember;
    }

    private static List<CsdlEnumMember> getEnumMemberFromOfbizEntity(Delegator delegator, String ofbizEntity,
                                                                     EntityCondition entityCondition, Locale locale)
            throws GenericEntityException {
        List<CsdlEnumMember> enumMembers = new ArrayList<CsdlEnumMember>();
        ModelEntity modelEntity = delegator.getModelEntity(ofbizEntity);
        String pkFieldName = modelEntity.getFirstPkFieldName();
        List<GenericValue> genericValues = delegator.findList(ofbizEntity, entityCondition, null, UtilMisc.toList(pkFieldName), null, true);
        int index = 0;
        for (GenericValue genericValue : genericValues) {
            index++;
            // 不是每个实体都能作为enumeration，必须要有sequenceId字段，目前只有Enumeration表能满足
            String pkFieldValue = genericValue.getString(pkFieldName);
            String memberValue;
            if (modelEntity.getAllFieldNames().contains("sequenceId")) {
                String sequenceId = genericValue.getString("sequenceId");
                if (sequenceId == null) {
                    Debug.logError("muse have sequenceId for " + modelEntity.getEntityName(), module);
                }
                memberValue = Integer.toString(Integer.parseInt(sequenceId));
            } else {
                memberValue = Integer.toString(index);
            }
            CsdlEnumMember csdlEnumMember = new CsdlEnumMember();
            // csdlEnumMember.setName(description);
            csdlEnumMember.setName(pkFieldValue);
            // setValue主要将01、02等转换成1、2
            csdlEnumMember.setValue(memberValue);

            String description = genericValue.getString("description");
            Object uiLabelValue = Util.getUiLabelMap(locale).get(ofbizEntity + ".description" + "." + pkFieldValue);
            if (!uiLabelValue.equals(ofbizEntity + ".description." + pkFieldValue)) {
                description = (String) uiLabelValue;
            }
            CsdlAnnotation csdlAnnotation = createAnnotationString("Core.Description", description, null);
            csdlEnumMember.setAnnotations(UtilMisc.toList(csdlAnnotation));
            enumMembers.add(csdlEnumMember);
        }
        return enumMembers;
    }

    private static OfbizCsdlComplexType loadComplexTypeFromElement(LocalDispatcher dispatcher,
                                                                   Element complexTypeElement, Locale locale) {
        String name = complexTypeElement.getAttribute("Name");
        String ofbizClass = name;
        if (UtilValidate.isNotEmpty(complexTypeElement.getAttribute("OfbizClass"))) {
            ofbizClass = complexTypeElement.getAttribute("OfbizClass");
        }
        List<CsdlProperty> csdlProperties = null;
        List<? extends Element> complexTypeChildren = UtilXml.childElementList(complexTypeElement);
        for (Element inComplexElement : complexTypeChildren) {
            String inComplexTagName = inComplexElement.getTagName();
            if (inComplexTagName.equals("Property")) {
                CsdlProperty csdlProperty = loadPropertyFromElement(dispatcher, null, null, inComplexElement, locale, name, false);
                if (csdlProperties == null) {
                    csdlProperties = new ArrayList<>();
                }
                csdlProperties.add(csdlProperty);
            }
        }
        OfbizCsdlComplexType csdlComplexType = new OfbizCsdlComplexType(ofbizClass);
        csdlComplexType.setName(name);
        csdlComplexType.setProperties(csdlProperties);
        return csdlComplexType;
    }

    private static EntityTypeRelAlias loadRelAliasFromElement(LocalDispatcher dispatcher, ModelEntity modelEntity,
                                                              Element relAliasElement, Locale locale) {
        String name = relAliasElement.getAttribute("Name");
        String relationsAttr = relAliasElement.getAttribute("Relations");
        return loadRelAliasFromAttribute(dispatcher.getDelegator(), modelEntity, name, relationsAttr);
    }

    public static EntityTypeRelAlias loadRelAliasFromAttribute(Delegator delegator, ModelEntity modelEntity, String name, String relationsAttr) {
        List<String> relations;
        if (UtilValidate.isNotEmpty(relationsAttr)) {
            relations = StringUtil.split(relationsAttr, "/");
        } else {
            relations = UtilMisc.toList(name);
        }
        List<String> relationNames = new ArrayList<>();
        Map<String, EntityCondition> relationsCondition = new HashMap<>();
        Map<String, Map<String, Object>> relationsFieldMap = new HashMap<>();
        Map<String, ModelRelation> relationsEntity = new HashMap<>();
        ModelEntity relModelEntity = modelEntity;
        for (String relation : relations) {
            String relationName = relation;
            if (relation.contains("(")) { // 这个relation是有条件的
                relationName = relation.substring(0, relation.indexOf('('));
//                String relationConditionStr = relation.substring(relation.indexOf('(') + 1, relation.indexOf(')'));
                String relationConditionStr = relation.substring(relation.indexOf('(') + 1, relation.length() - 1);
                Map<String, Object> relationFieldMap = new HashMap<>();
                EntityCondition relationCondition = parseEntityCondition(relationConditionStr, relationFieldMap);
                relationsCondition.put(relationName, relationCondition);
                relationsFieldMap.put(relationName, relationFieldMap);
            }
            ModelRelation modelRelation = relModelEntity.getRelation(relationName);
            if (modelRelation == null) { // relation不存在于entity之间
                return null;
            }
            String relEntityName = modelRelation.getRelEntityName();
            relationsEntity.put(relationName, modelRelation);
            relModelEntity = delegator.getModelEntity(relEntityName);
            relationNames.add(relationName);
        }
        EntityTypeRelAlias entityTypeRelAlias = new EntityTypeRelAlias();
        entityTypeRelAlias.setName(name);
        entityTypeRelAlias.setRelations(relationNames);
        entityTypeRelAlias.setRalationsCondition(relationsCondition);
        entityTypeRelAlias.setRelationsFieldMap(relationsFieldMap);
        entityTypeRelAlias.setRelationsEntity(relationsEntity);
        return entityTypeRelAlias;
    }

    private static List<OfbizCsdlProperty> generatePropertiesFromAttribute(LocalDispatcher dispatcher, ModelEntity modelEntity, EntityTypeRelAlias relAlias,
                                                                           String propertiesAttribute, Locale locale, String labelPrefix, boolean autoLabel) {
        List<OfbizCsdlProperty> propertyList = new ArrayList<>();
        for (String property : propertiesAttribute.split(",")) {
            OfbizCsdlProperty csdlProperty;
            if (UtilValidate.isEmpty(relAlias)) {
                csdlProperty = generatePropertyFromField(dispatcher.getDelegator(), dispatcher, modelEntity.getField(property), false);
            } else {
                csdlProperty = generatePropertyFromRelAlias(dispatcher.getDelegator(), dispatcher, modelEntity, relAlias, property, false);
                csdlProperty.setNullable(true);
            }
            if (autoLabel) {
                String labelKey = "${uiLabelMap." + labelPrefix + Util.firstUpperCase(csdlProperty.getName()) + "}";
                csdlProperty.setLabel(parseValue(labelKey, locale));
            }
            propertyList.add(csdlProperty);
        }
        return propertyList;
    }

    private static OfbizCsdlProperty loadPropertyFromElement(LocalDispatcher dispatcher, ModelEntity modelEntity,
                                                             List<EntityTypeRelAlias> relAliases,
                                                             Element propertyElement, Locale locale, String labelPrefix, boolean autoLabel) {
        String name = propertyElement.getAttribute("Name");
        OfbizCsdlProperty property = null;
        if (modelEntity != null) {
            String relAliasName = propertyElement.getAttribute("RelAlias");
            EntityTypeRelAlias entityTypeRelAlias = null;
            for (EntityTypeRelAlias relAlias : relAliases) {
                if (relAlias.getName().equals(relAliasName)) {
                    entityTypeRelAlias = relAlias;
                }
            }
            String field = propertyElement.getAttribute("Field");
            if (UtilValidate.isNotEmpty(field)) {
                if (UtilValidate.isNotEmpty(entityTypeRelAlias)) {
                    property = generatePropertyFromRelAlias(dispatcher.getDelegator(), dispatcher, modelEntity, entityTypeRelAlias, field, false);
                } else {
                    property = generatePropertyFromField(dispatcher.getDelegator(), dispatcher, modelEntity.getField(field), false);
                }
            } else {
                property = generatePropertyFromField(dispatcher.getDelegator(), dispatcher, modelEntity.getField(name), false);
            }
        }
        if (property == null) {
            property = new OfbizCsdlProperty();
        }
        String scale = propertyElement.getAttribute("Scale");
        String precision = propertyElement.getAttribute("Precision");
        String nullable = propertyElement.getAttribute("Nullable");
        String isCollection = propertyElement.getAttribute("IsCollection");
        String maxLength = propertyElement.getAttribute("MaxLength");
        String defaultValue = propertyElement.getAttribute("DefaultValue");
        String autoValue = propertyElement.getAttribute("AutoValue");
        // attribute for annotation
        String label = propertyElement.getAttribute("Label");
        if (UtilValidate.isEmpty(label) && autoLabel) {
            String labelKey = "${uiLabelMap." + labelPrefix + Util.firstUpperCase(name) + "}";
            label = parseValue(labelKey, locale);
        }
        String semanticObjectAttr = propertyElement.getAttribute("SemanticObject");
        String semanticObject;
        semanticObject = parseValue(semanticObjectAttr, locale);

        String fieldControl = propertyElement.getAttribute("FieldControl");
        String hidden = propertyElement.getAttribute("Hidden");
        String hiddenFilter = propertyElement.getAttribute("HiddenFilter");
        String computed = propertyElement.getAttribute("Computed");
        String immutable = propertyElement.getAttribute("Immutable");
        String isImageURL = propertyElement.getAttribute("IsImageURL");
        String excludeFromNavigationContext = propertyElement.getAttribute("ExcludeFromNavigationContext");
        String collectionPath = propertyElement.getAttribute("CollectionPath");
        String isAttribute = propertyElement.getAttribute("IsAttribute");
        String isNumericAttribute = propertyElement.getAttribute("IsNumericAttribute");
        String isDateAttribute = propertyElement.getAttribute("IsDateAttribute");
        String mimeType = propertyElement.getAttribute("MimeType");
        FullQualifiedName fullQualifiedName;
        String type = propertyElement.getAttribute("Type");
        if (UtilValidate.isEmpty(type) && property.getOfbizFieldType() != null) {
            type = property.getOfbizFieldType();
        }
        if (UtilValidate.isEmpty(type)) {
            Debug.logInfo("Can't get type for Property: " + name, module);
            return null;
        }
        EdmPrimitiveTypeKind paramEdmType = OfbizMapOdata.FIELDMAP.get(type);
        if (paramEdmType == null) {
            paramEdmType = OfbizMapOdata.PARAM_TYPE_MAP.get(type);
        }
        if (paramEdmType != null) {
            fullQualifiedName = paramEdmType.getFullQualifiedName();
        } else {
            fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, type);
        }
        property.setType(fullQualifiedName);
        property.setName(name);

        if (UtilValidate.isNotEmpty(scale) && !"variable".equals(scale)) {
            property.setScale(Integer.valueOf(scale));
        }
        if (UtilValidate.isNotEmpty(precision)) {
            property.setPrecision(Integer.valueOf(precision));
        }

        if (UtilValidate.isNotEmpty(nullable)) {
            property.setNullable(!"false".equals(nullable));
        } else {
            boolean isNullable = isPropertyNullable(dispatcher, modelEntity, name);
            property.setNullable(isNullable);
        }
        property.setCollection("true".equals(isCollection));
        if (UtilValidate.isNotEmpty(maxLength)) {
            property.setMaxLength(Integer.valueOf(maxLength));
        }
        if (UtilValidate.isNotEmpty(collectionPath)) {
            property.setCollectionPath(collectionPath);
        }
        if (UtilValidate.isNotEmpty(label)) {
            property.setLabel(label);
        }
        if (UtilValidate.isNotEmpty(semanticObject)) {
            property.setSemanticObject(semanticObject);
        }
        if (UtilValidate.isNotEmpty(fieldControl)) {
            property.setFieldControl(FieldControlType.valueOf(fieldControl));
        }
        if (UtilValidate.isNotEmpty(hidden)) {
            property.setHidden(Boolean.valueOf(hidden));
        }
        if (UtilValidate.isNotEmpty(hiddenFilter)) {
            property.setHiddenFilter(Boolean.valueOf(hiddenFilter));
        }
        if (UtilValidate.isNotEmpty(computed)) {
            property.setComputed(Boolean.valueOf(computed));
        }
        if (UtilValidate.isNotEmpty(immutable)) {
            property.setImmutable(Boolean.valueOf(immutable));
        }
        if (UtilValidate.isNotEmpty(isImageURL)) {
            property.setImageUrl(Boolean.valueOf(isImageURL));
        }
        if (UtilValidate.isNotEmpty(excludeFromNavigationContext)) {
            property.setExcludeFromNavigationContext(Boolean.valueOf(excludeFromNavigationContext));
        }
        if (UtilValidate.isNotEmpty(isAttribute)) {
            property.setAttribute(Boolean.valueOf(isAttribute));
        }
        if (UtilValidate.isNotEmpty(isNumericAttribute)) {
            property.setNumericAttribute(Boolean.valueOf(isNumericAttribute));
        }
        if (UtilValidate.isNotEmpty(isDateAttribute)) {
            property.setDateAttribute(Boolean.valueOf(isDateAttribute));
        }
        if (UtilValidate.isNotEmpty(mimeType)) {
            property.setMimeType(mimeType);
        }
        if (UtilValidate.isNotEmpty(defaultValue)) {
            property.setDefaultValue(defaultValue);
        }
        if (UtilValidate.isNotEmpty(autoValue)) {
            property.setAutoValue(autoValue);
        }
        List<? extends Element> propertyChildren = UtilXml.childElementList(propertyElement);
        List<CsdlAnnotation> annotations = new ArrayList<>();
        List<Term> terms = new ArrayList<>();
        for (Element propertyChild : propertyChildren) {
            String propertyChildTag = propertyChild.getTagName();
            if (propertyChildTag.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(propertyChild, locale);
                annotations.add(csdlAnnotation);
            } else if (propertyChildTag.equals("Text")) {
                terms.add(loadTextFromElement(propertyChild));
            } else if (propertyChildTag.equals("ValueList")) {
                terms.add(loadValueListFromElement(property, modelEntity, propertyChild, locale));
            }
        }
        if (UtilValidate.isNotEmpty(annotations)) {
            property.setAnnotations(annotations);
        }
        property.setTerms(terms);
        return property;
    }

    private static List<CsdlPropertyRef> loadKeyFromElement(Element keyElement, Locale locale) {
        List<CsdlPropertyRef> keyPropertyRefs = new ArrayList<>();
        List<? extends Element> keyChildren = UtilXml.childElementList(keyElement);
        if (UtilValidate.isEmpty(keyChildren)) {
            return keyPropertyRefs;
        }
        for (Element keyChild : keyChildren) {
            String keyChildTag = keyChild.getTagName();
            if (keyChildTag.equals("PropertyRef")) {
                String propertyRefName = keyChild.getAttribute("Name");
                CsdlPropertyRef csdlPropertyRef = new CsdlPropertyRef();
                csdlPropertyRef.setName(propertyRefName);
                keyPropertyRefs.add(csdlPropertyRef);
            }
        }
        return keyPropertyRefs;
    }

    private static Term loadValueListFromElement(OfbizCsdlProperty property, ModelEntity modelEntity, Element valueListElement, Locale locale) {
        String collectionPath = valueListElement.getAttribute("CollectionPath");
        String label = loadAttributeValue(valueListElement, "Label", locale);
        if (UtilValidate.isEmpty(label)) {
            if (UtilValidate.isNotEmpty(property.getLabel())) {
                label = property.getLabel();
            } else if (modelEntity != null) {
                String labelAttr = "${uiLabelMap." + modelEntity.getEntityName() + Util.firstUpperCase(property.getName()) + "}";
                label = parseValue(labelAttr, locale);
            }
        }
        String qualifier = valueListElement.getAttribute("Qualifier");
        if (UtilValidate.isEmpty(qualifier)) {
            qualifier = null;
        }
        ValueList valueList = new ValueList(label, collectionPath, qualifier);
        String parameterDisplayOnly = valueListElement.getAttribute("ParameterDisplayOnly");
        if (UtilValidate.isNotEmpty(parameterDisplayOnly)) {
            List<String> displayOnlyList = StringUtil.split(parameterDisplayOnly, ",");
            valueList.setParameterDisplayOnly(displayOnlyList);
        }
        //ParameterInOut
        List<? extends Element> childElementList = UtilXml.childElementList(valueListElement);
        if (UtilValidate.isNotEmpty(childElementList)) {
            List<ValueList.Parameter> parameterList = new ArrayList<>();
            for (Element element : childElementList) {
                String tagName = element.getTagName();
                String valueParameterType = tagName;
                if ("ParameterIn".equals(tagName)) {
                    valueParameterType = "Common.ValueListParameterIn";
                }
                if ("ParameterOut".equals(tagName)) {
                    valueParameterType = "Common.ValueListParameterOut";
                }
                if ("ParameterInOut".equals(tagName)) {
                    valueParameterType = "Common.ValueListParameterInOut";
                }
                ValueList.Parameter parameter = new ValueList.Parameter(valueParameterType, element.getAttribute("ValueListProperty"), element.getAttribute("LocalDataProperty"));
                parameterList.add(parameter);
            }
            valueList.setParameters(parameterList);
        }

        // WithFixedValues
        String withFixedValues = valueListElement.getAttribute("WithFixedValues");
        if (UtilValidate.isNotEmpty(withFixedValues)) {
            valueList.setWithFixedValues(Boolean.valueOf(withFixedValues));
        }

        return valueList;
    }

    private static Term loadSelectionFieldsFromElement(Element SelectionFieldsElement) {
        String propertyPathAttr = SelectionFieldsElement.getAttribute("PropertyPath");
        String qualifier = SelectionFieldsElement.getAttribute("Qualifier");
        List<String> propertyPaths = StringUtil.split(propertyPathAttr, ",");
        return new SelectionFields(propertyPaths, qualifier);
    }

    private static Term loadSemanticKeyFromElement(Element semanticKeyElement) {
        String propertyPathAttr = semanticKeyElement.getAttribute("PropertyPath");
        String qualifier = semanticKeyElement.getAttribute("Qualifier");
        List<String> propertyPaths = StringUtil.split(propertyPathAttr, ",");
        return new SemanticKey(propertyPaths, qualifier);
    }

    private static Term loadTextFromElement(Element textElement) {
        String path = textElement.getAttribute("Path");
        String textArrangement = textElement.getAttribute("TextArrangement");
        String qualifier = textElement.getAttribute("Qualifier");
        return new Text(path, textArrangement, qualifier);
    }

    private static OfbizCsdlFunction loadFunctionFromElement(Element functionElement) {
        String name = functionElement.getAttribute("Name");
        boolean isBound = UtilValidate.isNotEmpty(functionElement.getAttribute("IsBound")) && "true".equals(functionElement.getAttribute("IsBound"));
        String ofbizService = name;
        if (UtilValidate.isNotEmpty(functionElement.getAttribute("OfbizService"))) {
            ofbizService = functionElement.getAttribute("OfbizService");
        }
        boolean isComposable = false;
        if (UtilValidate.isNotEmpty(functionElement.getAttribute("IsComposable"))) {
            isComposable = "true".equals(functionElement.getAttribute("IsComposable"));
        }
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, name);
        List<? extends Element> functionChildren = UtilXml.childElementList(functionElement);
        List<CsdlParameter> parameters = new ArrayList<>();
        CsdlReturnType csdlReturnType = null;
        for (Element inFunctionElement : functionChildren) {
            String inFunctionTagName = inFunctionElement.getTagName();
            if (inFunctionTagName.equals("Parameter")) { // <Parameter>
                CsdlParameter parameter = loadParameterFromElement(inFunctionElement);
                parameters.add(parameter);
            } // </Parameter>
            if (inFunctionTagName.equals("ReturnType")) {
                csdlReturnType = loadReturnTypeFromElement(inFunctionElement);
            }
        }
        OfbizCsdlFunction ofbizCsdlFunction;
        ofbizCsdlFunction = createFunction(fullQualifiedName, parameters, csdlReturnType, isBound);
        ofbizCsdlFunction.setOfbizMethod(ofbizService);
        ofbizCsdlFunction.setComposable(isComposable);
        return ofbizCsdlFunction;
    }

    private static OfbizCsdlFunction createFunction(final FullQualifiedName functionName,
                                                    List<CsdlParameter> parameters,
                                                    CsdlReturnType csdlReturnType, boolean isBound) {
        final OfbizCsdlFunction csdlFunction = new OfbizCsdlFunction();
        try {
            // 处理IN参数
            List<CsdlParameter> csdlParameters;
            csdlParameters = parameters;

            // Action & Function Use FullQualifiedName ...
            csdlFunction.setName(functionName.getName()).setParameters(csdlParameters).setBound(isBound);
            if (csdlReturnType != null) {
                csdlFunction.setReturnType(csdlReturnType);
            }
        } catch (Exception e) { // 即使出错了，生活还要继续，打印错误，跳过，然后继续下一个
            e.printStackTrace();
            return null;
        }
        return csdlFunction;
    }

    private static OfbizCsdlAction loadActionFromElement(Element actionElement) {
        String name = actionElement.getAttribute("Name");
        String ofbizService = name;
        if (UtilValidate.isNotEmpty(actionElement.getAttribute("OfbizService"))) {
            ofbizService = actionElement.getAttribute("OfbizService");
        }
        boolean isBound = UtilValidate.isNotEmpty(actionElement.getAttribute("IsBound")) && "true".equals(actionElement.getAttribute("IsBound"));
        boolean stickySession = UtilValidate.isNotEmpty(actionElement.getAttribute("StickySession")) && "true".equals(actionElement.getAttribute("StickySession"));
        boolean isEntityAction = UtilValidate.isNotEmpty(actionElement.getAttribute("IsEntityAction")) && "true".equals(actionElement.getAttribute("IsEntityAction"));
        String entitySetPath = "".equals(actionElement.getAttribute("EntitySetPath")) ? null : actionElement.getAttribute("EntitySetPath");
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, name);
        List<? extends Element> actionChildren = UtilXml.childElementList(actionElement);
        List<CsdlParameter> parameters = new ArrayList<CsdlParameter>();
        CsdlReturnType csdlReturnType = null;
        for (Element inActionElement : actionChildren) {
            String inActionTagName = inActionElement.getTagName();
            if (inActionTagName.equals("Parameter")) { // <Parameter>
                CsdlParameter parameter = loadParameterFromElement(inActionElement);
                parameters.add(parameter);
            } // </Parameter>
            if (inActionTagName.equals("ReturnType")) {
                csdlReturnType = loadReturnTypeFromElement(inActionElement);
            }
        }
        return createAction(fullQualifiedName, parameters, csdlReturnType,
                isBound, ofbizService, isEntityAction, stickySession, entitySetPath);
    }

    private static CsdlParameter loadParameterFromElement(Element parameterElement) {
        String name = parameterElement.getAttribute("Name");
        String type = parameterElement.getAttribute("Type");
        String precision = parameterElement.getAttribute("Precision");
        String nullable = parameterElement.getAttribute("Nullable");
        String isCollection = parameterElement.getAttribute("IsCollection");
        FullQualifiedName paramFullQualifiedName;
        EdmPrimitiveTypeKind paramEdmType = OfbizMapOdata.PARAM_TYPE_MAP.get(type);
        if (paramEdmType != null) {
            paramFullQualifiedName = paramEdmType.getFullQualifiedName();
        } else {
            paramFullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, type);
        }
        CsdlParameter parameter = new CsdlParameter();
        parameter.setType(paramFullQualifiedName);
        parameter.setName(name);
        if (UtilValidate.isNotEmpty(precision)) {
            parameter.setPrecision(Integer.valueOf(precision));
        }
        parameter.setNullable(!"false".equals(nullable));
        parameter.setCollection("true".equals(isCollection));
        return parameter;
    }

    private static CsdlReturnType loadReturnTypeFromElement(Element returnTypeElement) {
        String type = returnTypeElement.getAttribute("Type");
        String precision = returnTypeElement.getAttribute("Precision");
        String scale = returnTypeElement.getAttribute("Scale");
        String nullable = returnTypeElement.getAttribute("Nullable");
        String isCollection = returnTypeElement.getAttribute("IsCollection");
        FullQualifiedName returnFullQualifiedName;
        EdmPrimitiveTypeKind returnEdmType = OfbizMapOdata.PARAM_TYPE_MAP.get(type);
        if (returnEdmType != null) {
            returnFullQualifiedName = returnEdmType.getFullQualifiedName();
        } else {
            returnFullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, type);
        }
        CsdlReturnType returnType = new CsdlReturnType();
        returnType.setType(returnFullQualifiedName);
        if (UtilValidate.isNotEmpty(precision)) {
            returnType.setPrecision(Integer.valueOf(precision));
        }
        if (UtilValidate.isNotEmpty(scale)) {
            returnType.setScale(Integer.valueOf(scale));
        }
        returnType.setNullable(!"false".equals(nullable));
        returnType.setCollection("true".equals(isCollection));
        return returnType;
    }

    private static OfbizCsdlAction createAction(final FullQualifiedName actionName,
                                                List<CsdlParameter> parameters, CsdlReturnType csdlReturnType, boolean isBound,
                                                String ofbizMethod, boolean isEntityAction,
                                                boolean stickySession, String entitySetPath) {
        final OfbizCsdlAction csdlAction = new OfbizCsdlAction();
        try {
            // 处理IN参数
            List<CsdlParameter> csdlParameters;
            csdlParameters = parameters;

            // Action & Function Use FullQualifiedName ...
            csdlAction.setName(actionName.getName()).setParameters(csdlParameters).setBound(isBound);
            if (csdlReturnType != null) {
                csdlAction.setReturnType(csdlReturnType);
            }
            csdlAction.setOfbizMethod(ofbizMethod);
            csdlAction.setStickySession(stickySession);
            csdlAction.setEntityAction(isEntityAction);
            csdlAction.setEntitySetPath(entitySetPath);
        } catch (Exception e) { // 即使出错了，生活还要继续，打印错误，跳过，然后继续下一个
            e.printStackTrace();
            return null;
        }
        return csdlAction;
    }

    private static OfbizCsdlEntitySet loadEntitySetFromElement(Element entitySetElement, Locale locale) {
        String name = entitySetElement.getAttribute("Name");
        String entityType = entitySetElement.getAttribute("EntityType");
        EntityCondition entityCondition = null;
        Map<String, Object> conditionMap = null;
        String entityConditionStr = null;
        if (UtilValidate.isNotEmpty(entitySetElement.getAttribute("EntityCondition"))) {
            entityConditionStr = entitySetElement.getAttribute("EntityCondition");
            conditionMap = new HashMap<>();
            entityCondition = parseEntityCondition(entityConditionStr, conditionMap);
        }
        String handler = null;
        if (UtilValidate.isNotEmpty(entitySetElement.getAttribute("Handler"))) {
            handler = entitySetElement.getAttribute("Handler");
        }

        List<? extends Element> entitySetChildren = UtilXml.childElementList(entitySetElement);
        List<CsdlNavigationPropertyBinding> navigationPropertyBindings = new ArrayList<CsdlNavigationPropertyBinding>();
        List<CsdlAnnotation> csdlAnnotations = new ArrayList<CsdlAnnotation>();
        for (Element inEntitySetElement : entitySetChildren) {
            String inEntitySetTagName = inEntitySetElement.getTagName();
            if (inEntitySetTagName.equals("NavigationPropertyBinding")) { // <NavigationPropertyBinding>
                CsdlNavigationPropertyBinding navigationPropertyBinding = loadNavigationPropertyBindingFromElement(inEntitySetElement);
                navigationPropertyBindings.add(navigationPropertyBinding);
            } // </NavigationPropertyBinding>
            if (inEntitySetTagName.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(inEntitySetElement, locale);
                csdlAnnotations.add(csdlAnnotation);
            }
        }
        OfbizCsdlEntitySet csdlEntitySet = new OfbizCsdlEntitySet(entityConditionStr);
        csdlEntitySet.setHandler(handler);
        csdlEntitySet.setName(name);
        csdlEntitySet.setType(new FullQualifiedName(OfbizMapOdata.NAMESPACE, entityType));
        csdlEntitySet.setNavigationPropertyBindings(navigationPropertyBindings);
        csdlEntitySet.setAnnotations(csdlAnnotations);
        return csdlEntitySet;
    }

    private static CsdlNavigationPropertyBinding loadNavigationPropertyBindingFromElement(Element navigationPropertyBindingElement) {
        String path = navigationPropertyBindingElement.getAttribute("Path");
        String target = navigationPropertyBindingElement.getAttribute("Target");
        CsdlNavigationPropertyBinding navigationPropertyBinding = new CsdlNavigationPropertyBinding();
        navigationPropertyBinding.setPath(path);
        navigationPropertyBinding.setTarget(target);
        return navigationPropertyBinding;
    }

    private static CsdlFunctionImport loadFunctionImportFromElement(Element functionImportElement) {
        String name = functionImportElement.getAttribute("Name");
        String function = functionImportElement.getAttribute("Function");
        CsdlFunctionImport csdlFunctionImport = new CsdlFunctionImport();
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, function);
        csdlFunctionImport.setName(name).setFunction(fullQualifiedName)
                .setIncludeInServiceDocument(true);
        return csdlFunctionImport;
    }

    private static CsdlActionImport loadActionImportFromElement(Element actionImportElement) {
        String name = actionImportElement.getAttribute("Name");
        String action = actionImportElement.getAttribute("Action");
        CsdlActionImport csdlActionImport = new CsdlActionImport();
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, action);
        csdlActionImport.setName(name).setAction(fullQualifiedName);
        return csdlActionImport;
    }

    private static OfbizCsdlSingleton loadSingletonFromElement(Element singletonElement) {
        String name = singletonElement.getAttribute("Name");
        String singletonType = singletonElement.getAttribute("Type");
        String handlerMethod = singletonElement.getAttribute("Handler");
        List<? extends Element> singletonChildren = UtilXml.childElementList(singletonElement);
        List<CsdlNavigationPropertyBinding> navigationPropertyBindings = new ArrayList<CsdlNavigationPropertyBinding>();
        for (Element inSingletonElement : singletonChildren) {
            String inSingletonTagName = inSingletonElement.getTagName();
            if (inSingletonTagName.equals("NavigationPropertyBinding")) { // <NavigationPropertyBinding>
                CsdlNavigationPropertyBinding navigationPropertyBinding = loadNavigationPropertyBindingFromElement(inSingletonElement);
                navigationPropertyBindings.add(navigationPropertyBinding);
            } // </NavigationPropertyBinding>
        }
        OfbizCsdlSingleton csdlSingleton = new OfbizCsdlSingleton();
        FullQualifiedName fullQualifiedName = new FullQualifiedName(OfbizMapOdata.NAMESPACE, singletonType);
        csdlSingleton.setName(name).setType(fullQualifiedName);
        csdlSingleton.setHandlerMethod(handlerMethod);
        csdlSingleton.setNavigationPropertyBindings(navigationPropertyBindings);
        return csdlSingleton;
    }

    private static CsdlAnnotations loadAnnotationsFromElement(Element annotationsElement, Locale locale) {
        String target = annotationsElement.getAttribute("Target");
        String qualifier = annotationsElement.getAttribute("Qualifier");
        CsdlAnnotations csdlAnnotations = new CsdlAnnotations();
        if (UtilValidate.isNotEmpty(target)) {
            csdlAnnotations.setTarget(target);
        }
        if (UtilValidate.isNotEmpty(qualifier)) {
            csdlAnnotations.setQualifier(qualifier);
        }
        List<? extends Element> annotationsChildren = UtilXml.childElementList(annotationsElement);
        for (Element annotationsChild : annotationsChildren) {
            String annotationsChildTag = annotationsChild.getTagName();
            if (annotationsChildTag.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(annotationsChild, locale);
                csdlAnnotations.getAnnotations().add(csdlAnnotation);
            }
        }
        return csdlAnnotations;
    }

    private static String loadAttributeValue(Element element, String attribute, Locale locale) {
        String attrValue = element.getAttribute(attribute);
        String parsedValue = parseValue(attrValue, locale);
        if (UtilValidate.isNotEmpty(parsedValue)) {
            return parsedValue;
        }
        if (UtilValidate.isNotEmpty(attrValue)) {
            return attrValue;
        }
        return null;
    }

    private static String parseValue(String value, Locale locale) {
        FlexibleStringExpander fse = FlexibleStringExpander.getInstance(value, false);
        Map<String, Object> uiLabelMap = Util.getUiLabelMap(locale);
        Map<String, Object> context = UtilMisc.toMap("uiLabelMap", uiLabelMap);
        return fse.expandString(context);
    }

    private static OfbizCsdlProperty generatePropertyFromRelAlias(Delegator delegator, LocalDispatcher dispatcher,
                                                                  ModelEntity modelEntity, EntityTypeRelAlias relAlias,
                                                                  String field, boolean autoEnum) {
        List<String> relations = relAlias.getRelations();
        ModelEntity relModelEntity = modelEntity;
        ModelRelation modelRelation;
        ModelField modelField;
        Iterator<String> it = relations.iterator();
        while (it.hasNext()) {
            String relation = it.next();
            modelRelation = relModelEntity.getRelation(relation);
            relModelEntity = delegator.getModelEntity(modelRelation.getRelEntityName());
        }
        modelField = relModelEntity.getField(field);
        OfbizCsdlProperty ofbizCsdlProperty = generatePropertyFromField(delegator, dispatcher, modelField, autoEnum);
        ofbizCsdlProperty.setRelAlias(relAlias);
        return ofbizCsdlProperty;
    }

    private static OfbizCsdlProperty generatePropertyFromField(Delegator delegator, LocalDispatcher dispatcher,
                                                               ModelField field, boolean autoEnum) {
        if (field == null) {
            return null;
        }
        ModelEntity modelEntity = field.getModelEntity();
        Map<String, String> enumMap = null;
        if (autoEnum) {
            enumMap = Util.getEntityAutoEnum(modelEntity);
        }
        String fieldType = field.getType();
        String fieldName = field.getName();

        OfbizCsdlProperty csdlProperty = new OfbizCsdlProperty();
        csdlProperty.setName(fieldName);
        if (autoEnum && "indicator".equals(fieldType)) {
            if ("gender".equals(fieldName)) {
                csdlProperty.setType(OfbizMapOdata.NAMESPACE + ".Gender");
            } else if ("maritalStatus".equals(fieldName)) {
                csdlProperty.setType(OfbizMapOdata.NAMESPACE + ".MaritalStatus");
            } else if ("priority".equals(fieldName)) {
                csdlProperty.setType(OfbizMapOdata.NAMESPACE + ".Priority");
            } else {
                csdlProperty.setType("Edm.Boolean");
            }
        } else if (enumMap != null && enumMap.containsKey(fieldName)) { // 先只处理Product的enumeration
            csdlProperty.setType(OfbizMapOdata.NAMESPACE + "." + enumMap.get(fieldName));
        } else {
            csdlProperty.setType(OfbizMapOdata.FIELDMAP.get(fieldType).getFullQualifiedName());
        }

        if (fieldType.equals("date-time")) {
            csdlProperty.setPrecision(3);
        } else if (csdlProperty.getType().equals("Edm.String")) {
            ModelFieldType modelFieldType;
            try {
                modelFieldType = delegator.getEntityFieldType(modelEntity, fieldType);
            } catch (GenericEntityException e) {
                e.printStackTrace();
                return null;
            }
            int maxLength = modelFieldType.stringLength();
            csdlProperty.setMaxLength(maxLength);
        } else if (csdlProperty.getType().equals("Edm.Decimal")) {
            setPropertyPrecision(delegator, csdlProperty, modelEntity, fieldType);
        }
        boolean nullable = isPropertyNullable(dispatcher, modelEntity, fieldName);
        csdlProperty.setNullable(nullable);
        csdlProperty.setOfbizFieldType(fieldType);
        csdlProperty.setOfbizFieldName(fieldName);
        return csdlProperty;
    }

    private static boolean isPropertyNullable(LocalDispatcher dispatcher, ModelEntity modelEntity,
                                              String propertyName) {
        if (modelEntity == null) {
            return true;
        }
        ModelField modelField = modelEntity.getField(propertyName);
        return modelField == null || !modelField.getIsNotNull();
    }

    private static void setPropertyPrecision(Delegator delegator, CsdlProperty csdlProperty,
                                             ModelEntity modelEntity, String fieldType) {
        ModelFieldType modelFieldType;
        try {
            modelFieldType = delegator.getEntityFieldType(modelEntity, fieldType);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            return;
        }
        String sqlType = modelFieldType.getSqlType();
        String precisionStr = sqlType.substring(sqlType.indexOf('(') + 1, sqlType.indexOf(','));
        Integer precision = Integer.valueOf(precisionStr);
        String scaleStr = sqlType.substring(sqlType.indexOf(',') + 1, sqlType.indexOf(')'));
        Integer scale = Integer.valueOf(scaleStr);
        csdlProperty.setPrecision(precision);
        csdlProperty.setScale(scale);
    }

    private static CsdlTerm loadTermFromElement(Element termElement, Locale locale) {
        CsdlTerm term = new CsdlTerm();
        String name = termElement.getAttribute("Name");
        String type = termElement.getAttribute("Type");
        String termAppliesTo = termElement.getAttribute("AppliesTo");
        term.setName(name);
        if (UtilValidate.isNotEmpty(type)) {
            term.setType(type);
        }
        if (UtilValidate.isNotEmpty(termAppliesTo)) {
            term.setAppliesTo(UtilMisc.toList(termAppliesTo));
        }
        List<CsdlAnnotation> annotations = new ArrayList<CsdlAnnotation>();
        List<? extends Element> annotationElements = UtilXml.childElementList(termElement);
        for (Element annotationElt : annotationElements) {
            CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(annotationElt, locale);
            annotations.add(csdlAnnotation);
        }
        term.setAnnotations(annotations);
        return term;
    }

    private static CsdlAnnotation loadAnnotationFromElement(Element annotationElement, Locale locale) {
        CsdlAnnotation csdlAnnotation = createAnnotation(annotationElement.getAttribute("Term"), annotationElement.getAttribute("Qualifier"));
        String qualifier = annotationElement.getAttribute("Qualifier");
        if (UtilValidate.isNotEmpty(qualifier)) {
            csdlAnnotation.setQualifier(qualifier);
        }
        String annotationString = annotationElement.getAttribute("String");
        if (UtilValidate.isNotEmpty(annotationString)) {
            csdlAnnotation.setExpression(createExpressionString(annotationString));
        }
        String annotationBool = annotationElement.getAttribute("Bool");
        if (UtilValidate.isNotEmpty(annotationBool)) {
            csdlAnnotation.setExpression(createExpressionBool(Boolean.valueOf(annotationBool)));
        }
        String annotationEnumMember = annotationElement.getAttribute("EnumMember");
        if (UtilValidate.isNotEmpty(annotationEnumMember)) {
            csdlAnnotation.setExpression(createExpressionEnumMember(annotationEnumMember));
        }
        List<? extends Element> annotationChildren = UtilXml.childElementList(annotationElement);
        for (Element annotationChild : annotationChildren) {
            String annotationChildTagName = annotationChild.getTagName();
            if (annotationChildTagName.equals("Collection")) {
                CsdlCollection csdlCollection = loadCollectionFromElement(annotationChild, locale);
                csdlAnnotation.setExpression(csdlCollection);
            } // end if Collection
            if (annotationChildTagName.equals("Record")) {
                CsdlRecord csdlRecord = loadRecordFromElement(annotationChild, locale);
                csdlAnnotation.setExpression(csdlRecord);
            }
        }
        // csdlAnnotation.setExpression(csdlCollection);
        return csdlAnnotation;
    }

    private static CsdlCollection loadCollectionFromElement(Element collectionElement, Locale locale) {
        CsdlCollection csdlCollection = new CsdlCollection();
        List<? extends Element> collectionChildren = UtilXml.childElementList(collectionElement);
        List<CsdlExpression> collectionItems = new ArrayList<CsdlExpression>();
        for (Element collectionChild : collectionChildren) {
            String collectionChildTagName = collectionChild.getTagName();
            if (collectionChildTagName.equals("Record")) {
                CsdlRecord csdlRecord = loadRecordFromElement(collectionChild, locale);
                collectionItems.add(csdlRecord);
            } // end if Record
            if (collectionChildTagName.equals("PropertyPath")) {
                CsdlPropertyPath csdlPropertyPath = new CsdlPropertyPath();
                csdlPropertyPath.setValue(collectionChild.getTextContent());
                collectionItems.add(csdlPropertyPath);
            } // end if PropertyPath
            if (collectionChildTagName.equals("String")) {
                collectionItems.add(createExpressionString(collectionChild.getTextContent()));
            }
            if (collectionChildTagName.equals("AnnotationPath")) {
                collectionItems.add(createExpressionAnnotationPath(collectionChild.getTextContent()));
            }
        } // end for collectionChildren
        csdlCollection.setItems(collectionItems);
        return csdlCollection;
    }

    private static CsdlRecord loadRecordFromElement(Element recordElement, Locale locale) {
        String recordType = recordElement.getAttribute("Type");
        CsdlRecord csdlRecord = new CsdlRecord();
        if (UtilValidate.isNotEmpty(recordType)) {
            csdlRecord.setType(recordType);
        }
        List<? extends Element> recordChildren = UtilXml.childElementList(recordElement);
        List<CsdlPropertyValue> propertyValues = new ArrayList<CsdlPropertyValue>();
        List<CsdlAnnotation> csdlAnnotations = new ArrayList<>();
        for (Element recordChild : recordChildren) {
            String recordChildTagName = recordChild.getTagName();
            if (recordChildTagName.equals("PropertyValue")) {
                CsdlPropertyValue propertyValue = loadPropertyValueFromElement(recordChild, locale);
                propertyValues.add(propertyValue);
            }
            if (recordChildTagName.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(recordChild, locale);
                csdlAnnotations.add(csdlAnnotation);
            }
        }
        csdlRecord.setPropertyValues(propertyValues);
        if (UtilValidate.isNotEmpty(csdlAnnotations)) {
            csdlRecord.setAnnotations(csdlAnnotations);
        }
        return csdlRecord;
    }

    private static CsdlPropertyValue loadPropertyValueFromElement(Element propertyValueElt, Locale locale) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        String property = propertyValueElt.getAttribute("Property");
        propertyValue.setProperty(property);
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("Path"))) {
            CsdlPath csdlPath = new CsdlPath();
            csdlPath.setValue(propertyValueElt.getAttribute("Path"));
            propertyValue.setValue(csdlPath);
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("NavigationPropertyPath"))) {
            CsdlNavigationPropertyPath csdlPath = new CsdlNavigationPropertyPath();
            csdlPath.setValue(propertyValueElt.getAttribute("NavigationPropertyPath"));
            propertyValue.setValue(csdlPath);
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("String"))) {
            propertyValue = createPropertyValueString(property, loadAttributeValue(propertyValueElt, "String", locale));
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("Bool"))) {
            propertyValue.setValue(createExpressionBool(Boolean.valueOf(propertyValueElt.getAttribute("Bool"))));
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("AnnotationPath"))) {
            propertyValue = createPropertyValueAnnotationPath(property, loadAttributeValue(propertyValueElt, "AnnotationPath", locale));
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("PropertyPath"))) {
            propertyValue = createPropertyValuePropertyPath(property, loadAttributeValue(propertyValueElt, "PropertyPath", locale));
        }
        if (UtilValidate.isNotEmpty(propertyValueElt.getAttribute("EnumMember"))) {
            propertyValue = createPropertyValueEnumMember(property, loadAttributeValue(propertyValueElt, "EnumMember", locale));
        }
        List<? extends Element> propertyValueChildren = UtilXml.childElementList(propertyValueElt);
        for (Element propertyValueChild : propertyValueChildren) {
            String propertyValueChildTag = propertyValueChild.getTagName();
            if (propertyValueChildTag.equals("Collection")) {
                CsdlCollection csdlCollection = loadCollectionFromElement(propertyValueChild, locale);
                propertyValue.setValue(csdlCollection);
            }
            if (propertyValueChildTag.equals("Record")) {
                CsdlRecord csdlRecord = loadRecordFromElement(propertyValueChild, locale);
                propertyValue.setValue(csdlRecord);
            }
            if (propertyValueChildTag.equals("Annotation")) {
                CsdlAnnotation csdlAnnotation = loadAnnotationFromElement(propertyValueChild, locale);
                propertyValue.setAnnotations(UtilMisc.toList(csdlAnnotation));
            }
        }
        return propertyValue;
    }

    private static EntityCondition parseEntityCondition(String conditionStr, Map<String, Object> conditionMap) {
        boolean isAnd = false;
        boolean isAllEqual = true;
        String[] expressions;
        if (conditionStr.contains(" or ")) {
            expressions = conditionStr.split(" or ");
        } else {
            isAnd = true;
            expressions = conditionStr.split(" and ");
        }
        EntityCondition resultCondition = null;
        List<EntityCondition> entityConditionList = new ArrayList<>();
        for (String expression : expressions) {
            expression = expression.trim();
            EntityCondition entityCondition = null;
            if (expression.contains("=")) {
                String[] keyValue = expression.split("=");
                if (UtilValidate.isNotEmpty(keyValue)) {
                    String value = "null".equals(keyValue[1]) ? null : keyValue[1];
                    entityCondition = EntityCondition.makeCondition(keyValue[0], value);
                }
                if (conditionMap != null) {
                    conditionMap.put(keyValue[0], keyValue[1]);
                }
            } else if (expression.contains(" not in ")) {
                String[] keyValue = expression.split(" not in ");
                String key = keyValue[0].trim();
                String valueStr = keyValue[1].trim();
                valueStr = valueStr.substring(1, valueStr.length() - 1);
                List<String> values = StringUtil.split(valueStr, ",");
                entityCondition = EntityCondition.makeCondition(key, EntityJoinOperator.NOT_IN, values);
                isAllEqual = false;
            } else if (expression.contains(" in ")) {
                String[] keyValue = expression.split(" in ");
                String key = keyValue[0].trim();
                String valueStr = keyValue[1].trim();
                valueStr = valueStr.substring(1, valueStr.length() - 1);
                List<String> values = StringUtil.split(valueStr, ",");
                entityCondition = EntityCondition.makeCondition(key, EntityJoinOperator.IN, values);
                isAllEqual = false;
            }
            if (entityCondition != null) {
                entityConditionList.add(entityCondition);
            }
            if (!isAnd && !isAllEqual && conditionMap != null) {
                conditionMap.clear();
            }
        }
        if (isAnd) {
            return new EntityConditionList<>(entityConditionList, EntityJoinOperator.AND);
        } else {
            return new EntityConditionList<>(entityConditionList, EntityJoinOperator.OR);
        }
    }

    private static OfbizCsdlEntityType createEntityType(Delegator delegator, LocalDispatcher dispatcher,
                                                        FullQualifiedName entityTypeFqn, String ofbizEntity,
                                                        String attrEntityName, String attrNumericEntityName, String attrDateEntityName,
                                                        String handlerClass, boolean autoProperties,
                                                        List<CsdlProperty> csdlProperties,
                                                        List<CsdlNavigationProperty> csdlNavigationProperties,
                                                        List<CsdlPropertyRef> csdlPropertyRefs, boolean filterByDate,
                                                        String baseType, boolean hadDerivedEntity, List<String> excludeProperties,
                                                        EntityCondition entityCondition, String entityConditionStr, String labelPrefix, Locale locale, String searchOption,
                                                        boolean groupBy, boolean hasStream, boolean autoLabel, boolean autoDraft, boolean autoValueList, boolean autoSet, String draftEntityName) {
        String entityName = entityTypeFqn.getName(); // Such as Invoice
        List<CsdlPropertyRef> propertyRefs = csdlPropertyRefs;
        ModelEntity modelEntity = null;
        if (ofbizEntity != null) {
            try {
                modelEntity = delegator.getModelReader().getModelEntity(ofbizEntity);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
            }
        }
        if (autoProperties && modelEntity != null) { // 需要从ofbiz的entity定义里面获取所有Property
            Iterator<ModelField> fieldIterator = modelEntity.getFieldsIterator();
            // 获取所有的外键字段，以及关联到Enumeration表的字段
//            Set<String> fkFieldNames = Util.getEntityFk(modelEntity);
            List<String> automacticFieldNames = modelEntity.getAutomaticFieldNames(); // lastUpdatedStamp, lastUpdatedTxStamp, createdStamp, createdTxStamp
            while (fieldIterator.hasNext()) {
                ModelField field = fieldIterator.next();
                String fieldName = field.getName();
                if (automacticFieldNames.contains(fieldName)) {
                    continue;
                }
                if (excludeProperties != null && excludeProperties.contains(fieldName)) {
                    continue;
                }
                /**** fk先暂时加回来，牵扯面太广 **********************
                 //中间表的主键同时也有外键约束，这种情况应该保留主键
                 if (!pkFieldNames.contains(fieldName) && fkFieldNames.contains(fieldName)) {
                 continue;
                 }
                 **************************************************/
                OfbizCsdlProperty csdlProperty = generatePropertyFromField(delegator, dispatcher, field, false);
                if (csdlProperties != null) {
                    if (csdlProperties.contains(csdlProperty)) {
                        //已经xml定义了，就不要自动生成了
                        continue;
                    }
                    if (autoLabel) {
                        String label = (String) Util.getUiLabelMap(locale).get(entityName + Util.firstUpperCase(csdlProperty.getName()));
                        csdlProperty.setLabel(label);
                    }
                    csdlProperties.add(csdlProperty);
                }
            }
        }
        if (UtilValidate.isEmpty(propertyRefs) && UtilValidate.isNotEmpty(modelEntity)) { // EntityType的Key还没有定义
            // 先添加主键，所有odata的EntityType必须映射到一个ofbiz对象作为主对象，所以，总是可以从ofbiz主对象中获取主键字段
//            Iterator<ModelField> pksIterator = modelEntity.getPksIterator();
            List<String> ofbizAllPk = modelEntity.getPkFieldNames();
            propertyRefs = new ArrayList<>();
            for (CsdlProperty csdlProperty : csdlProperties) {
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlProperty;
                if (ofbizCsdlProperty.getRelAlias() != null) {
                    continue;
                }
                if (ofbizAllPk.contains(ofbizCsdlProperty.getOfbizFieldName())) {
                    CsdlPropertyRef propertyRef = new CsdlPropertyRef();
                    propertyRef.setName(ofbizCsdlProperty.getName());
                    propertyRefs.add(propertyRef);
                }
            }
//            while (pksIterator.hasNext()) {
//                ModelField field = pksIterator.next();
//                String fieldName = field.getName();
//                pkFieldNames.add(fieldName);
//                CsdlPropertyRef propertyRef = new CsdlPropertyRef();
//                propertyRef.setName(fieldName);
//                propertyRefs.add(propertyRef);
//            }
        }
        OfbizCsdlEntityType entityType = new OfbizCsdlEntityType(ofbizEntity, handlerClass, false,
                false, filterByDate, attrEntityName, attrNumericEntityName, attrDateEntityName,
                hadDerivedEntity, entityCondition, entityConditionStr, labelPrefix, searchOption, groupBy, hasStream, autoLabel, autoDraft, autoValueList, autoSet, draftEntityName);
        if (UtilValidate.isNotEmpty(baseType)) {
            //有BaseType, Property里就不应该再有pk
            List<String> propertyRefNames = propertyRefs.stream().map(CsdlPropertyRef::getName).collect(Collectors.toList());
            csdlProperties.removeIf(cp -> propertyRefNames.contains(cp.getName()));
            if (baseType.indexOf('.') == -1) {
                entityType.setBaseType(new FullQualifiedName(OfbizMapOdata.NAMESPACE, baseType));
            } else {
                entityType.setBaseType(baseType);
            }
        }
        entityType.setName(entityName);
        entityType.setProperties(csdlProperties);
        if (csdlNavigationProperties != null) {
            entityType.setNavigationProperties(csdlNavigationProperties);
        }
        if (propertyRefs != null) {
            entityType.setKey(propertyRefs);
        }
        return entityType;
    }

    private static CsdlAnnotation createAnnotationString(String term, String stringValue, String qualifier) {
        CsdlAnnotation csdlAnnotation = createAnnotation(term, qualifier);
        CsdlExpression csdlExpression = createExpressionString(stringValue);
        csdlAnnotation.setExpression(csdlExpression);
        return csdlAnnotation;
    }

    private static CsdlAnnotation createAnnotationBool(String term, String stringValue, String qualifier) {
        CsdlAnnotation csdlAnnotation = createAnnotation(term, qualifier);
        CsdlExpression csdlExpression = createExpressionBool(Boolean.valueOf(stringValue));
        csdlAnnotation.setExpression(csdlExpression);
        return csdlAnnotation;
    }

    private static CsdlAnnotation createAnnotationEnum(String term, Enum value, String qualifier) {
        CsdlAnnotation csdlAnnotation = createAnnotation(term, qualifier);
        CsdlExpression csdlExpression = createExpressionEnum(value);
        csdlAnnotation.setExpression(csdlExpression);
        return csdlAnnotation;
    }

    private static CsdlAnnotation createAnnotationEnumMember(String term, String value, String qualifier) {
        CsdlAnnotation csdlAnnotation = createAnnotation(term, qualifier);
        CsdlExpression csdlExpression = createExpressionEnumMember(value);
        csdlAnnotation.setExpression(csdlExpression);
        return csdlAnnotation;
    }

    private static CsdlAnnotation createAnnotationPath(String term, String path, String qualifier) {
        CsdlAnnotation csdlAnnotation = createAnnotation(term, qualifier);
        CsdlExpression csdlExpression = createExpressionPath(path);
        csdlAnnotation.setExpression(csdlExpression);
        return csdlAnnotation;
    }

    private static CsdlAnnotation createAnnotation(String term, String qualifier) {
        CsdlAnnotation csdlAnnotation = new CsdlAnnotation();
        csdlAnnotation.setTerm(term);
        if (UtilValidate.isNotEmpty(qualifier)) {
            csdlAnnotation.setQualifier(qualifier);
        }
        return csdlAnnotation;
    }

    private static CsdlExpression createExpressionNavigationPropertyPath(String propertyPath) {
        CsdlNavigationPropertyPath csdlNavigationPropertyPath = new CsdlNavigationPropertyPath();
        csdlNavigationPropertyPath.setValue(propertyPath);
        return csdlNavigationPropertyPath;
    }

    private static CsdlPropertyValue createNavigationPropertyPath(String property, String propertyPath) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionNavigationPropertyPath(propertyPath));
        return propertyValue;
    }

    private static CsdlExpression createExpressionPropertyPath(String propertyPath) {
        CsdlPropertyPath csdlPropertyPath = new CsdlPropertyPath();
        csdlPropertyPath.setValue(propertyPath);
        return csdlPropertyPath;
    }

    private static CsdlExpression createExpressionAnnotationPath(String annotationPath) {
        CsdlAnnotationPath csdlAnnotationPath = new CsdlAnnotationPath();
        csdlAnnotationPath.setValue(annotationPath);
        return csdlAnnotationPath;
    }

    private static CsdlExpression createExpressionString(String value) {
        CsdlConstantExpression constantExpression = new CsdlConstantExpression(CsdlConstantExpression.ConstantExpressionType.String);
        constantExpression.setValue(value);
        return constantExpression;
    }

    private static CsdlExpression createExpressionBool(boolean value) {
        CsdlConstantExpression constantExpression = new CsdlConstantExpression(CsdlConstantExpression.ConstantExpressionType.Bool);
        constantExpression.setValue(Boolean.toString(value));
        return constantExpression;
    }

    private static CsdlExpression createExpressionEnum(Enum value) {
        CsdlConstantExpression constantExpression = new CsdlConstantExpression(CsdlConstantExpression.ConstantExpressionType.EnumMember);
        constantExpression.setValue(value.toString());
        return constantExpression;
    }

    private static CsdlExpression createExpressionEnumMember(String value) {
        CsdlConstantExpression constantExpression = new CsdlConstantExpression(CsdlConstantExpression.ConstantExpressionType.EnumMember);
        constantExpression.setValue(value);
        return constantExpression;
    }

    private static CsdlExpression createExpressionPath(String path) {
        CsdlPath csdlPath = new CsdlPath();
        csdlPath.setValue(path);
        return csdlPath;
    }

    private static CsdlPropertyValue createPropertyValueString(String property, String value) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionString(value));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValueAnnotationPath(String property, String annotationPath) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionAnnotationPath(annotationPath));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValuePropertyPath(String property, String propertyPath) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionPropertyPath(propertyPath));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValuePath(String property, String path) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionPath(path));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValueDataField(String property, DataField dataField) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        CsdlRecord csdlRecord = new CsdlRecord();
        csdlRecord.setType("UI.DataField");
        CsdlPropertyValue recordValue = createPropertyValuePath("Value", (String) dataField.getValue());
        csdlRecord.setPropertyValues(UtilMisc.toList(recordValue));
        propertyValue.setValue(csdlRecord);
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValueBool(String property, boolean value) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionBool(value));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValueEnum(String property, Enum value) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionEnum(value));
        return propertyValue;
    }

    private static CsdlPropertyValue createPropertyValueEnumMember(String property, String value) {
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);
        propertyValue.setValue(createExpressionEnumMember(value));
        return propertyValue;
    }

    public synchronized static void createDraftTable(EdmWebConfig edmWebConfig, String webapp, Delegator delegator) throws GenericEntityException {
        ModelReader modelReader = delegator.getModelReader();
        Map<String, ModelEntity> entityCache = modelReader.getEntityCache();
        for (OfbizCsdlEntityType entityType : edmWebConfig.getEntityTypes()) {
            String draftEntityName = entityType.getDraftEntityName();
            if (UtilValidate.isEmpty(draftEntityName) || UtilValidate.isNotEmpty(entityCache.get(draftEntityName))) {
                continue;
            }
//            String draftEntityName = webapp + entityType.getName() + "Draft";
            //没有定义Draft或者已经创建过了 跳过
//            if (!entityType.isAutoDraft()) {
//                continue;
//            }
//            if (UtilValidate.isNotEmpty(entityCache.get(draftEntityName))) {
//                entityType.setDraftEntityName(draftEntityName);
//                continue;
//            }
            //根据这个EntityType创建一个内存数据库表
            ModelEntity modelEntity = new ModelEntity();
            modelEntity.setEntityName(draftEntityName);
            modelEntity.setTableName(draftEntityName);
            modelEntity.setPackageName("com.dpbird.draft");
            modelEntity.setNoAutoStamp(true);
            //Draft固定字段
            modelEntity.addField(ModelField.create(modelEntity, "draftUUID", "id", true));
            modelEntity.addField(ModelField.create(modelEntity, "isActiveEntity", "id", false));
            modelEntity.addField(ModelField.create(modelEntity, "hasActiveEntity", "id", false));
            modelEntity.addField(ModelField.create(modelEntity, "hasDraftEntity", "id", false));
            //EntityType定义的字段
            List<CsdlProperty> properties = entityType.getProperties();
            ModelEntity currModelEntity = delegator.getModelEntity(entityType.getOfbizEntity());
            for (CsdlProperty property : properties) {
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) property;
                //忽略Complex字段
                if (!modelEntity.isField(property.getName()) && !property.getType().contains(OfbizMapOdata.NAMESPACE)) {
                    String ofbizPropertyType = getPropertyOfbizType(entityType, ofbizCsdlProperty, delegator);
                    modelEntity.addField(ModelField.create(modelEntity, property.getName(), ofbizPropertyType, false));
                }
            }
            //如果有Derived 把Derived的字段和relation也添加进去以支持编辑
            if (entityType.isHasDerivedEntity()) {
                addDerivedModelElement(entityType, edmWebConfig, delegator, modelEntity);
            }
            //Draft固定的relation
            ModelRelation draftAdminRelation = ModelRelation.create(modelEntity, null, "one-nofk", null, "DraftAdministrativeData", null, ModelKeyMap.makeKeyMapList("draftUUID"), false);
            modelEntity.addRelation(draftAdminRelation);
            //把navigation转为relation
            for (CsdlNavigationProperty csdlNavigationProperty : entityType.getNavigationProperties()) {
                OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty) csdlNavigationProperty;
                EntityTypeRelAlias relAlias = navigationProperty.getRelAlias();
                //所有带有Handler的Navigation都跳过
                if (UtilValidate.isNotEmpty(navigationProperty.getHandler())) {
                    continue;
                }
                //relAlias为空
                if (UtilValidate.isEmpty(relAlias)) {
                    Debug.logWarning("NavigationProperty (" + navigationProperty.getName() + ") relAlias is empty.", module);
                    continue;
                }
                //拿第一段去添加relation，没有定义外键就不添加
                ModelRelation relation = currModelEntity.getRelation(relAlias.getRelations().get(0));
                boolean hasRelationKey = true;
                for (ModelKeyMap keyMap : relation.getKeyMaps()) {
                    if (!modelEntity.isField(keyMap.getFieldName())) {
                        hasRelationKey = false;
                    }
                }
                //按照原生的表设定relation的Type
                String relType = relation.getType();
                ModelRelation modelRelation = ModelRelation.create(modelEntity, null, relType, relation.getTitle(), relation.getRelEntityName(), null, relation.getKeyMaps(), false);
                if (hasRelationKey && modelEntity.getRelation(modelRelation.getCombinedName()) == null) {
                    modelEntity.addRelation(modelRelation);
                }
            }
            entityType.setDraftEntityName(draftEntityName);
            GenericHelperInfo helperInfo = delegator.getGroupHelperInfo("org.apache.ofbiz.memory");
            DatabaseUtil databaseUtil = new DatabaseUtil(helperInfo);
            //创建
            databaseUtil.createTable(modelEntity, UtilMisc.toMap(modelEntity.getEntityName(), modelEntity), false);
            Debug.logInfo("========= Created a draft table: " + modelEntity.getEntityName(), module);

            //创建之后需要加到entity缓存列表和group缓存列表中，否则实体引擎会查不到这个实体，ofbiz提供的一些find方法也查不到
            entityCache.put(modelEntity.getEntityName(), modelEntity);
            Map<String, String> groupCache = delegator.getModelGroupReader().getGroupCache(delegator.getDelegatorBaseName());
            groupCache.put(modelEntity.getEntityName(), "org.apache.ofbiz.memory");
        }
    }

    private static void addDerivedModelElement(OfbizCsdlEntityType baseCsdlEntityType, EdmWebConfig edmWebConfig, Delegator delegator, ModelEntity baseDraftModelEntity) {
        for (OfbizCsdlEntityType csdlEntityType : edmWebConfig.getEntityTypes()) {
            if (csdlEntityType.getBaseTypeFQN() != null && baseCsdlEntityType.getName().equals(csdlEntityType.getBaseTypeFQN().getName())) {
                for (CsdlProperty property : csdlEntityType.getProperties()) {
                    OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) property;
                    //忽略Complex字段
                    if (!baseDraftModelEntity.isField(property.getName()) && !property.getType().contains(OfbizMapOdata.NAMESPACE)) {
                        String ofbizPropertyType = getPropertyOfbizType(csdlEntityType, ofbizCsdlProperty, delegator);
                        baseDraftModelEntity.addField(ModelField.create(baseDraftModelEntity, property.getName(), ofbizPropertyType, false));
                    }
                }
                //TODO: add relation.
            }
        }
    }

    private static void loadViewEntityFromElement(Element element, Delegator delegator) throws GenericEntityException {
        ModelReader modelReader = delegator.getModelReader();
        DynamicViewEntity dynamicViewEntity = new DynamicViewEntity();
        dynamicViewEntity.setEntityName(element.getAttribute("entity-name"));
        dynamicViewEntity.setPackageName(element.getAttribute("package-name"));
        dynamicViewEntity.setTitle(element.getAttribute("title"));
        boolean neverCache = "true".equals(element.getAttribute("never-cache"));
        for (Element viewEntityChild : UtilXml.childElementList(element)) {
            if ("member-entity".equals(viewEntityChild.getTagName())) {
                dynamicViewEntity.addMemberEntity(viewEntityChild.getAttribute("entity-alias"), viewEntityChild.getAttribute("entity-name"));
            } else if ("alias-all".equals(viewEntityChild.getTagName())) {
                ModelViewEntity.ModelAliasAll aliasAll = new ModelViewEntity.ModelAliasAll(viewEntityChild);
                dynamicViewEntity.addAliasAll(aliasAll.getEntityAlias(), aliasAll.getPrefix(), Lists.newArrayList(aliasAll.iterator()), aliasAll.getGroupBy());
            } else if ("alias".equals(viewEntityChild.getTagName())) {
                ModelViewEntity.ModelAlias modelAlias = new ModelViewEntity.ModelAlias(viewEntityChild);
                dynamicViewEntity.addAlias(modelAlias.getEntityAlias(), modelAlias.getName(), modelAlias.getField(), modelAlias.getColAlias(), modelAlias.getIsPk(), modelAlias.getGroupBy(), modelAlias.getFunction());
            }
        }
        ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
        modelViewEntity.setNeverCache(neverCache);
        //添加 relation
        List<? extends Element> relationElements = UtilXml.childElementList(element, "relation");
        for (Element relationElement : relationElements) {
            ModelRelation modelRelation = ModelRelation.create(modelViewEntity, relationElement, false);
            modelViewEntity.addRelation(modelRelation);
        }
        //添加 viewLink
        List<? extends Element> viewLinkElements = UtilXml.childElementList(element, "view-link");
        for (Element viewLinkElement : viewLinkElements) {
            ModelViewEntity.ModelViewLink viewLink = new ModelViewEntity.ModelViewLink(modelViewEntity, viewLinkElement);
            modelViewEntity.addViewLink(viewLink);
        }
        Debug.logInfo("========= Create a view Entity: " + modelViewEntity.getEntityName(), module);
        Map<String, ModelEntity> entityCache = modelReader.getEntityCache();
        entityCache.put(modelViewEntity.getEntityName(), modelViewEntity);
    }

    //获取这个字段的数据库类型
    private static String getPropertyOfbizType(OfbizCsdlEntityType entityType, OfbizCsdlProperty property, Delegator delegator) {
        ModelEntity modelEntity = delegator.getModelEntity(entityType.getOfbizEntity());
        if (modelEntity.isField(property.getName()) || UtilValidate.isNotEmpty(property.getRelAlias())) {
            //原生字段返回原生ofbiz类型
            return property.getOfbizFieldType();
        } else if (property.isAttribute() || property.isNumericAttribute() || property.isDateAttribute()) {
            if (property.isNumericAttribute() && "Edm.Int64".equals(property.getType())) {
                return OfbizMapOdata.EDM_TYPE_MAP.get(property.getType());
            }
            //Attr字段 取attrValue的类型
            String attrEntityName = property.isAttribute() ? entityType.getAttrEntityName() : property.isNumericAttribute() ?
                    entityType.getAttrNumericEntityName() : entityType.getAttrDateEntityName();
            return delegator.getModelEntity(attrEntityName).getField("attrValue").getType();
        } else {
            //语义化字段
            return OfbizMapOdata.EDM_TYPE_MAP.get(property.getType());
        }
    }
}
