package com.dpbird.odata;

import com.dpbird.odata.edm.*;
import com.dpbird.odata.handler.HandlerFactory;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityComparisonOperator;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityJoinOperator;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.olingo.commons.api.Constants;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.apply.Aggregate;
import org.apache.olingo.server.api.uri.queryoption.apply.AggregateExpression;
import org.apache.olingo.server.api.uri.queryoption.apply.GroupBy;
import org.apache.olingo.server.api.uri.queryoption.apply.GroupByItem;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.apache.olingo.server.core.uri.queryoption.ExpandItemImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.LevelsOptionImpl;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

import static com.dpbird.odata.OdataExpressionVisitor.AGGREGATE_MAP;

public class OfbizOdataProcessor {

    public static final String module = OfbizOdataProcessor.class.getName();
    public static final int MAX_ROWS = 10000;
    public static final int EXTRA_QUERY_MAX_RAW = 1000;
    protected Delegator delegator;
    protected LocalDispatcher dispatcher;
    protected GenericValue userLogin;
    protected Locale locale;
    protected EntityCondition entityCondition = null;
    //entityCondition是全部的条件，entitySearchCondition仅仅是search的条件
    protected EntityCondition entitySearchCondition = null;
    protected OData oData;
    protected ServiceMetadata serviceMetadata;
    protected OfbizAppEdmProvider edmProvider;
    protected HttpServletRequest httpServletRequest;
    protected ModelEntity modelEntity = null; // targetModelEntity
    protected DynamicViewHolder dynamicViewHolder = null;
    protected String entityName = null; // targetEntityName
    protected EdmEntityType edmEntityType; // targetEdmEntityType
    protected Set<String> fieldsToSelect = null;
    protected Set<String> applySelect = null;
    protected int skipValue = 0;
    protected int topValue = MAX_ROWS;
    protected List<String> orderBy;
    protected String sapContextId;
    protected boolean filterByDate = false;
    protected Map<String, Object> odataContext;
    protected Map<String, QueryOption> queryOptions;
    protected Map<String, Object> edmParams;


    public OfbizOdataProcessor(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        this.odataContext = odataContext;
        this.queryOptions = queryOptions;
        this.edmParams = edmParams;
        this.delegator = (Delegator) odataContext.get("delegator");
        this.dispatcher = (LocalDispatcher) odataContext.get("dispatcher");
        this.userLogin = (GenericValue) odataContext.get("userLogin");
        this.locale = (Locale) odataContext.get("locale");
        this.httpServletRequest = (HttpServletRequest) odataContext.get("httpServletRequest");
        this.edmProvider = (OfbizAppEdmProvider) odataContext.get("edmProvider");
        this.serviceMetadata = (ServiceMetadata) odataContext.get("serviceMetadata");
        this.sapContextId = (String) odataContext.get("sapContextId");
        if (UtilValidate.isNotEmpty(edmParams) && edmParams.get("entityName") != null) {
            this.entityName = (String) edmParams.get("entityName");
        }
        try {
            retrieveModelEntity();
            retrieveFindOption();
            if (this.modelEntity != null) {
                retrieveEntityCondition();
                retrieveFieldsToSelect();
                retrieveOrderBy();
                retrieveApply();
            }
        } catch (ODataException e) {
            e.printStackTrace();
        }
    }

    public Map<String, Object> getOdataContext() {
        return UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                "edmProvider", edmProvider, "userLogin", userLogin, "locale", locale, "httpServletRequest", httpServletRequest);
    }

    protected void retrieveEntityCondition() throws ODataException {
        //处理filter和search查询
        entityCondition = parseFilterOption();
        EntityCondition searchCondition = processSearchOption();
        if (searchCondition != null) {
            entityCondition = Util.appendCondition(entityCondition, searchCondition);
        }
        EntityCondition entitySetCondition = null;
        //检查是否是多段式的apply查询 如果是就不添加主对象的EntitySetCondition
        List<UriResource> uriResourceParts = (List<UriResource>) odataContext.get("uriResourceParts");
        boolean isMultistageApply = Util.isMultistageApply(uriResourceParts, queryOptions);
        if (UtilValidate.isNotEmpty(edmParams) && edmParams.get("edmBindingTarget") != null && !isMultistageApply) {
            EdmBindingTarget edmBindingTarget = (EdmBindingTarget) edmParams.get("edmBindingTarget");
            if (edmBindingTarget instanceof EdmEntitySet) { // 只有entitySet时才会有entitySetCondition
                OfbizCsdlEntitySet csdlEntitySet = (OfbizCsdlEntitySet) this.edmProvider.getEntityContainer()
                        .getEntitySet(((EdmEntitySet) edmParams.get("edmBindingTarget")).getName());
                String entitySetConditionStr = csdlEntitySet.getConditionStr();
                if (UtilValidate.isNotEmpty(entitySetConditionStr) && entitySetConditionStr.contains("/")) {
                    //entitySet的Condition是多段式的, 要使用dynamicView
                    if (dynamicViewHolder == null) {
                        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(csdlEntitySet.getTypeFQN());
                        dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                    }
                    List<EntityCondition> entitySetConditionList = new ArrayList<>();
                    String[] split;
                    EntityJoinOperator conditionsOperator;
                    if (entitySetConditionStr.contains(" or ")) {
                        split = entitySetConditionStr.split(" or ");
                        conditionsOperator = EntityOperator.OR;
                    } else {
                        split = entitySetConditionStr.split(" and ");
                        conditionsOperator = EntityOperator.AND;
                    }
                    for (int i = 0; i < split.length; i++) {
                        String relString = split[i];
                        if (relString.contains("/")) {
                            String r = relString.substring(0, relString.lastIndexOf("/"));
                            EntityTypeRelAlias entityTypeRelAlias = EdmConfigLoader.loadRelAliasFromAttribute(dispatcher.getDelegator(), modelEntity, null, r);
                            String lastRelEntityName = dynamicViewHolder.addRelAlias(null, entityTypeRelAlias);
                            String fieldCondition = relString.substring(relString.lastIndexOf("/") + 1);
                            String[] condition;
                            if (fieldCondition.contains("!=")) {
                                condition = fieldCondition.split("!=");
                                String conValue = Util.parseVariable(condition[1].trim(), userLogin);
                                String filterProperty = dynamicViewHolder.addFilterProperty(lastRelEntityName, condition[0].trim());
                                entitySetConditionList.add(EntityCondition.makeCondition(filterProperty, EntityOperator.NOT_EQUAL, conValue));
                            } else if (fieldCondition.contains("=")) {
                                condition = fieldCondition.split("=");
                                String conValue = Util.parseVariable(condition[1].trim(), userLogin);
                                String filterProperty = dynamicViewHolder.addFilterProperty(lastRelEntityName, condition[0].trim());
                                entitySetConditionList.add(EntityCondition.makeCondition(filterProperty, EntityOperator.EQUALS, conValue));
                            } else if (fieldCondition.contains(" in ")) {
                                EntityComparisonOperator<?, ?> operator;
                                //是not in
                                if (fieldCondition.contains(" not in ")) {
                                    condition = fieldCondition.split(" not in ");
                                    operator = EntityOperator.NOT_IN;
                                } else {
                                    condition = fieldCondition.split(" in ");
                                    operator = EntityOperator.IN;
                                }
                                String valueStr = condition[1].trim();
                                valueStr = valueStr.substring(1, valueStr.length() - 1);
                                List<String> values = StringUtil.split(valueStr, ",");
                                String filterProperty = dynamicViewHolder.addFilterProperty(lastRelEntityName, condition[0]);
                                entitySetConditionList.add(EntityCondition.makeCondition(filterProperty, operator, values));
                            }
                        } else {
                            entitySetConditionList.add(Util.parseEntityCondition(relString, userLogin));
                        }
                    }
                    if (entitySetConditionList.size() > 0) {
                        entitySetCondition = EntityCondition.makeCondition(entitySetConditionList, conditionsOperator);
                    }
                } else {
                    entitySetCondition = Util.parseEntityCondition(csdlEntitySet.getConditionStr(), userLogin);
                }
            }
        }
        if (entitySetCondition != null) {
            if (entityCondition == null) {
                entityCondition = entitySetCondition;
            } else {
                entityCondition = EntityCondition.makeCondition(entityCondition, EntityOperator.AND, entitySetCondition);
            }
        }
        if (this.edmEntityType != null) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            this.filterByDate = csdlEntityType.isFilterByDate();
            if (csdlEntityType.getEntityCondition() != null) {
                if (entityCondition == null) {
                    entityCondition = csdlEntityType.getEntityCondition();
                } else {
                    entityCondition = EntityCondition.makeCondition(entityCondition, EntityOperator.AND, csdlEntityType.getEntityCondition());
                }
            }
            if (UtilValidate.isNotEmpty(sapContextId) && UtilValidate.isNotEmpty(csdlEntityType.getDraftEntityName())) {
                List<EntityCondition> exprs = new ArrayList<EntityCondition>();
                exprs.add(EntityCondition.makeCondition(UtilMisc.toMap("isActiveEntity", "Y")));
                exprs.add(EntityCondition.makeCondition(UtilMisc.toMap("hasDraftEntity", "Y")));
                EntityCondition draftCondition = EntityCondition.makeCondition(exprs, EntityOperator.OR);
                if (entityCondition == null) {
                    entityCondition = draftCondition;
                } else {
                    entityCondition = EntityCondition.makeCondition(entityCondition, EntityOperator.AND, draftCondition);
                }
            }
        }
    }

    protected EntityCondition parseFilterOption() throws OfbizODataException {
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("filterOption") != null) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            if (edmParams.get("edmNavigationProperty") != null) {
                EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) edmParams.get("edmNavigationProperty");
                csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
            }
            Expression filterExpression = ((FilterOption) queryOptions.get("filterOption")).getExpression();
            OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
            EntityCondition entityCondition;
            try {
                entityCondition = (EntityCondition) filterExpression.accept(expressionVisitor);
            } catch (ExpressionVisitException | ODataApplicationException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
            dynamicViewHolder = expressionVisitor.getDynamicViewHolder();
            // 对于Product/SelectableFeature，生成的DynamicView中隐含带有productFeatureApplTypeId=SELECTABLE_FEATURE这个条件。
            // 这个条件无法在expressionVisitor中体现，但是在过程中，产生在了MemberEntityField.extraCondition中
            if (dynamicViewHolder != null && dynamicViewHolder.getEntityCondition() != null) {
                if (entityCondition != null) {
                    entityCondition = EntityCondition.makeCondition(entityCondition, EntityOperator.AND, dynamicViewHolder.getEntityCondition());
                } else {
                    entityCondition = dynamicViewHolder.getEntityCondition();
                }
            }
            return entityCondition;
        }
        return null;
    }

    protected EntityCondition getFilterOption(Map<String, QueryOption> queryOptions) throws OfbizODataException {
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("filterOption") != null) {
            OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            Expression filterExpression = ((FilterOption) queryOptions.get("filterOption")).getExpression();
            OdataExpressionVisitor expressionVisitor = new OdataExpressionVisitor(csdlEntityType, delegator, dispatcher, userLogin, edmProvider);
            EntityCondition entityCondition;
            try {
                entityCondition = (EntityCondition) filterExpression.accept(expressionVisitor);
            } catch (ExpressionVisitException | ODataApplicationException e) {
                e.printStackTrace();
                throw new OfbizODataException(e.getMessage());
            }
            DynamicViewHolder dynamicViewHolder = expressionVisitor.getDynamicViewHolder();
            // 对于Product/SelectableFeature，生成的DynamicView中隐含带有productFeatureApplTypeId=SELECTABLE_FEATURE这个条件。
            // 这个条件无法在expressionVisitor中体现，但是在过程中，产生在了MemberEntityField.extraCondition中
            if (dynamicViewHolder != null && dynamicViewHolder.getEntityCondition() != null) {
                if (entityCondition != null) {
                    entityCondition = EntityCondition.makeCondition(entityCondition, EntityOperator.AND, dynamicViewHolder.getEntityCondition());
                } else {
                    entityCondition = dynamicViewHolder.getEntityCondition();
                }
            }
            return entityCondition;
        }
        return null;
    }

    private EntityCondition processSearchOption() throws OfbizODataException {
        if (UtilValidate.isEmpty(queryOptions) || UtilValidate.isEmpty(queryOptions.get("searchOption"))) {
            return null;
        }
        List<String> searchProperties = new ArrayList<>();
        SearchOption search = (SearchOption) queryOptions.get("searchOption");
        Debug.log("========= Search text = " + search.getText(), module);
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        if (edmParams.get("edmNavigationProperty") != null) {
            EdmNavigationProperty navigationProperty = (EdmNavigationProperty) edmParams.get("edmNavigationProperty");
            csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navigationProperty.getType().getFullQualifiedName());
        }
        if (csdlEntityType.getSearchOption() == null) {
            //没有指定SearchOption
            return null;
        }
        List<String> searchList = parseSearchExpr(csdlEntityType.getSearchOption());
        for (String option : searchList) {
            if (option.contains("/")) {
                //是关联子对象的search
                if (dynamicViewHolder == null) {
                    dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                }
                String aliasEntityString = option.substring(0, option.lastIndexOf("/"));
                EntityTypeRelAlias entityTypeRelAlias = EdmConfigLoader.loadRelAliasFromAttribute(dispatcher.getDelegator(), modelEntity, null, aliasEntityString);
                String lastRelEntityName = dynamicViewHolder.addRelAlias(null, entityTypeRelAlias);

                String aliasPropertyName = option.substring(option.lastIndexOf("/") + 1);
                aliasPropertyName = aliasPropertyName.replace("[", "").replace("]", "");
                ArrayList<String> navEntityProperties = new ArrayList<>(Arrays.asList(aliasPropertyName.split(",")));
                for (String navEntityProperty : navEntityProperties) {
                    String filterProperty = dynamicViewHolder.addFilterProperty(lastRelEntityName, navEntityProperty);
                    searchProperties.add(filterProperty);
                }
            } else {
                OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(option);
                if (csdlProperty.isAttribute() || csdlProperty.getRelAlias() != null) {
                    //如果是Attribute字段或者RelAlias字段 需要使用dynamicViewHolder
                    if (dynamicViewHolder == null) {
                        dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                    }
                    if (csdlProperty.isAttribute()) {
                        //Attribute字段 search
                        dynamicViewHolder.addPropertyAttr(csdlEntityType, csdlProperty.getName());
                        searchProperties.add(option);
                    } else {
                        //relAlias字段 search
                        String lastRelEntityName = dynamicViewHolder.addRelAlias(null, csdlProperty.getRelAlias());
                        String filterProperty = dynamicViewHolder.addFilterProperty(lastRelEntityName, csdlProperty.getOfbizFieldName());
                        searchProperties.add(filterProperty);
                    }
                } else {
                    //普通字段search
                    ModelEntity currModelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
                    if (!currModelEntity.areFields(UtilMisc.toList(option))) {
                        continue;
                    }
                    searchProperties.add(option);
                }
            }
        }
        List<EntityCondition> searchLikeCondition = new ArrayList<>();
        for (String searchText : Util.getSearchOptionWords(search)) {
            List<EntityCondition> multiLikeCondition = new ArrayList<>();
            searchProperties.forEach(field -> multiLikeCondition.add(EntityCondition.makeCondition(field, EntityOperator.LIKE, "%" + searchText + "%")));
            searchLikeCondition.add(EntityCondition.makeCondition(multiLikeCondition, EntityOperator.OR));
        }
        entitySearchCondition = EntityCondition.makeCondition(searchLikeCondition, EntityOperator.AND);
        return entitySearchCondition;
    }

    //解析SearchOption字符串
    public static List<String> parseSearchExpr(String searchExpr) {
        List<String> subList = new ArrayList<>();
        while (searchExpr.length() > 0) {
            if (!searchExpr.contains(",")) {
                subList.add(searchExpr);
                break;
            }
            int indexOf = searchExpr.indexOf(",");
            String substring = searchExpr.substring(0, indexOf);
            if (substring.contains("[")) {
                int arr = searchExpr.indexOf("]") + 1;
                String arrStr = searchExpr.substring(0, arr);
                subList.add(arrStr);
                if (arrStr.equals(searchExpr)) {
                    break;
                }
                searchExpr = searchExpr.substring(arr + 1);
            } else {
                subList.add(substring);
                searchExpr = searchExpr.substring(indexOf + 1);
            }
        }
        return subList;
    }

    protected void retrieveFindOption() {
        topValue = getTopOption(queryOptions);
        skipValue = getSkipOption(queryOptions);
    }

    protected int getTopOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("topOption") != null
                && ((TopOption) queryOptions.get("topOption")).getValue() > 0) {
            return ((TopOption) queryOptions.get("topOption")).getValue();
        }
        return MAX_ROWS;
    }

    protected int getSkipOption(Map<String, QueryOption> queryOptions) {
        if (UtilValidate.isNotEmpty(queryOptions)
                && queryOptions.get("skipOption") != null
                && ((SkipOption) queryOptions.get("skipOption")).getValue() > 0) {
            return ((SkipOption) queryOptions.get("skipOption")).getValue();
        }
        return 0;
    }

    protected void retrieveFieldsToSelect() {
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("selectOption") != null) {
            SelectOption selectOption = (SelectOption) queryOptions.get("selectOption");
            fieldsToSelect = new HashSet<>(Util.getSelectOptionFields(selectOption));
            if (this.modelEntity != null) {
                fieldsToSelect.addAll(modelEntity.getPkFieldNames());
            }
        }
    }

    protected void retrieveApply() throws OfbizODataException {
        if (UtilValidate.isNotEmpty(queryOptions) && UtilValidate.isNotEmpty(queryOptions.get("applyOption"))) {
            retrieveGroupBy();
            retrieveAggregate();
        }
    }

    //groupBy 使用dynamicView实现group查询
    protected void retrieveGroupBy() throws OfbizODataException {
        ApplyOption applyOption = (ApplyOption) queryOptions.get("applyOption");
        if (UtilValidate.isNotEmpty(applyOption) && Util.isGroupBy(applyOption)) {
            EdmStructuredType edmStructuredType = applyOption.getEdmStructuredType();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmStructuredType.getFullQualifiedName());
            ModelEntity currModelEntity = delegator.getModelEntity(ofbizCsdlEntityType.getOfbizEntity());
            if (dynamicViewHolder == null) {
                dynamicViewHolder = new DynamicViewHolder(ofbizCsdlEntityType, edmProvider, delegator, dispatcher, userLogin);
            }

            DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
            for (ApplyItem applyItem : applyOption.getApplyItems()) {
                GroupBy groupBy = (GroupBy) applyItem;
                List<GroupByItem> groupByItems = groupBy.getGroupByItems();
                for (GroupByItem groupByItem : groupByItems) {
                    List<UriResource> path = groupByItem.getPath();
                    if (path.size() == 1) {
                        String segmentValue = path.get(0).getSegmentValue();
                        //要把这个字段加到selectField里 否则ofbiz不会进行groupBy处理
                        if (UtilValidate.isEmpty(applySelect)) {
                            applySelect = new HashSet<>();
                        }
                        applySelect.add(segmentValue);
                        dynamicViewEntity.addAlias(ofbizCsdlEntityType.getOfbizEntity(), segmentValue, segmentValue, null, false, true, null);
                    } else if (path.size() == 2) {
                        //子对象字段
                        //add MemberEntity
                        UriResourceNavigation resourceNavigation = (UriResourceNavigation) path.get(0);
                        EdmNavigationProperty edmNavigationProperty = resourceNavigation.getProperty();
                        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmNavigationProperty.getType().getFullQualifiedName());
                        String navigationName = edmNavigationProperty.getName();
                        String navigationEntityName = navCsdlEntityType.getOfbizEntity();
                        if (!dynamicViewHolder.hasMemberEntity(navigationName, navigationEntityName)) {
                            dynamicViewEntity.addMemberEntity(navigationName, navigationEntityName);
                        }
                        //add Alias
                        UriResourcePrimitiveProperty resourcePrimitiveProperty = (UriResourcePrimitiveProperty) path.get(1);
                        String segmentValue = resourcePrimitiveProperty.getSegmentValue();
                        dynamicViewEntity.addAlias(navigationName, navigationName + segmentValue, segmentValue, null, false, true, null);
                        //要把这个字段加到selectField里 否则ofbiz不会进行groupBy处理
                        if (UtilValidate.isEmpty(applySelect)) {
                            applySelect = new HashSet<>();
                        }
                        applySelect.add(navigationName + segmentValue);
                        //add ViewLink
                        if (!dynamicViewHolder.hasViewLink(ofbizCsdlEntityType.getOfbizEntity(), navigationName)) {
                            ModelRelation relation = currModelEntity.getRelation(resourceNavigation.getSegmentValue());
                            dynamicViewEntity.addViewLink(ofbizCsdlEntityType.getOfbizEntity(), navigationName, false, relation.getKeyMaps());
                        }
                    }
                }
            }
        }
    }


    protected void retrieveAggregate() throws OfbizODataException {
        ApplyOption applyOption = (ApplyOption) queryOptions.get("applyOption");
        if (UtilValidate.isNotEmpty(applyOption) && Util.isAggregate(applyOption)) {
            EdmStructuredType edmStructuredType = applyOption.getEdmStructuredType();
            OfbizCsdlEntityType ofbizCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmStructuredType.getFullQualifiedName());
            ModelEntity modelEntity = delegator.getModelEntity(ofbizCsdlEntityType.getOfbizEntity());
            if (dynamicViewHolder == null) {
                dynamicViewHolder = new DynamicViewHolder(ofbizCsdlEntityType, edmProvider, delegator, dispatcher, userLogin);
            }
            DynamicViewEntity dynamicViewEntity = dynamicViewHolder.getDynamicViewEntity();
            Aggregate applyAggregate = Util.getApplyAggregate(applyOption);
            for (AggregateExpression aggregateExpression : applyAggregate.getExpressions()) {
                //聚合函数类型
                AggregateExpression.StandardMethod standardMethod = aggregateExpression.getStandardMethod();
                //返回字段别名
                String expressionAlias = aggregateExpression.getAlias();
                if (UtilValidate.isNotEmpty(standardMethod)) {
                    //expression 字段名称或者子对象名称
                    String expression = aggregateExpression.getExpression().toString();
                    expression = expression.substring(1, expression.length() - 1);
                    if (standardMethod.equals(AggregateExpression.StandardMethod.COUNT_DISTINCT)) {
                        List<String> relationKeyList = Util.getRelationKey(modelEntity, expression);
                        if (relationKeyList.size() > 1) {
                            throw new OfbizODataException("Multiple field association is not supported.");
                        }
                        dynamicViewEntity.addAlias(ofbizCsdlEntityType.getName(), expressionAlias, relationKeyList.get(0), null, false, null, AGGREGATE_MAP.get(standardMethod));
                    } else {
                        dynamicViewEntity.addAlias(ofbizCsdlEntityType.getName(), expressionAlias, expression, null, false, null, AGGREGATE_MAP.get(standardMethod));
                    }
                } else if (Util.isAggregateCount(aggregateExpression)) {
                    //这里处理aggregate的$count，使用统计主键数量的方式实现，多主键暂不支持
                    List<String> pkFieldNames = modelEntity.getPkFieldNames();
                    if (pkFieldNames.size() > 1) {
                        throw new OfbizODataException("Count queries with multiple primary keys are not supported.");
                    }
                    dynamicViewEntity.addAlias(ofbizCsdlEntityType.getName(), expressionAlias, modelEntity.getFirstPkFieldName(), null, false, null, "count");
                }
                if (applySelect == null) {
                    applySelect = new HashSet<>();
                }
                applySelect.add(expressionAlias);
            }

        }
    }

    protected void retrieveOrderBy() throws OfbizODataException {
        if (entityName == null) {
            return;
        }
        OfbizCsdlEntityType csdlEntityType = null;
        if (UtilValidate.isNotEmpty(queryOptions) && queryOptions.get("orderByOption") != null) {
            OrderByOption orderByOption = (OrderByOption) queryOptions.get("orderByOption");
            if (queryOptions.get("applyOption") != null) {
                //apply的orderby不需要处理
                orderBy = Util.retrieveSimpleOrderByOption(orderByOption);
                return;
            }
            if (orderBy == null) {
                orderBy = new ArrayList<>();
            }
            csdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
            //是个多级的查询
            if (edmParams.get("edmNavigationProperty") != null) {
                EdmNavigationProperty navigationProperty = (EdmNavigationProperty) edmParams.get("edmNavigationProperty");
                csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navigationProperty.getType().getFullQualifiedName());
            }
            boolean isCount = orderByOption.getText() != null && orderByOption.getText().contains("/$count");
            if (Util.isExtraOrderby(orderByOption, csdlEntityType, delegator) || isCount) {
                //ofbiz不支持的orderby 在查询出结果后自定义处理
                return;
            }
            //有BaseType的实体 需要使用dynamicView
            if (UtilValidate.isNotEmpty(csdlEntityType.getBaseType())) {
                if (dynamicViewHolder == null) {
                    dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                }
            }
            List<OrderByItem> orderItemList = ((OrderByOption) queryOptions.get("orderByOption")).getOrders();
            for (OrderByItem orderByItem : orderItemList) {
                Expression expression = orderByItem.getExpression();
                UriInfoResource resourcePath = ((Member) expression).getResourcePath();
                List<UriResource> uriResourceParts = resourcePath.getUriResourceParts();
                //单个字段
                if (uriResourceParts.size() == 1 && uriResourceParts.get(0) instanceof UriResourcePrimitiveProperty) {
                    UriResource uriResource = uriResourceParts.get(0);
                    EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
                    final String sortPropertyName = edmProperty.getName();
                    OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(sortPropertyName);
                    String propertyAlias = sortPropertyName;
                    if (UtilValidate.isEmpty(csdlProperty) && UtilValidate.isNotEmpty(csdlEntityType.getBaseType())) {
                        //或许是BaseType字段
                        csdlProperty = csdlEntityType.getBaseTypeProperty(sortPropertyName, edmProvider);
                    }
                    if (csdlProperty.getRelAlias() != null || csdlProperty.isAttribute() || csdlProperty.isNumericAttribute() || csdlProperty.isDateAttribute()) {
                        //RelAlias或者Attribute字段，需要用DynamicView
                        if (dynamicViewHolder == null) {
                            dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                        }
                        String orderByProperty = dynamicViewHolder.addOrderByProperty(null, propertyAlias);
                        orderByProperty = orderByProperty + " NULLS LAST";
                        orderBy.add(orderByItem.isDescending() ? "-" + orderByProperty : orderByProperty);
                    } else {
                        //普通字段
                        propertyAlias = csdlProperty.getOfbizFieldName() + " NULLS LAST";
                        orderBy.add(orderByItem.isDescending() ? "-" + propertyAlias : propertyAlias);
                    }
                } else {
                    //多段式orderBy
                    if (dynamicViewHolder == null) {
                        dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
                    }
                    List<String> resourceParts = new ArrayList<>();
                    for (int i = 0; i < uriResourceParts.size() - 1; i++) {
                        resourceParts.add(uriResourceParts.get(i).getSegmentValue());
                    }
                    //添加到dynamicView之后 得到多段式的最后一个entity
                    String lastEntityAlias = dynamicViewHolder.addMultiParts(resourceParts, null);
                    UriResource uriResource = resourcePath.getUriResourceParts().get(uriResourceParts.size() - 1);
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResourceParts.get(uriResourceParts.size() - 2);
                    OfbizCsdlEntityType lastCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(uriResourceNavigation.getProperty().getType().getFullQualifiedName());
                    OfbizCsdlProperty property = (OfbizCsdlProperty) lastCsdlEntityType.getProperty(uriResource.getSegmentValue());
                    if (property.isAttribute() || property.isNumericAttribute() || property.isDateAttribute() || property.getRelAlias() != null) {
                        OfbizCsdlEntityType newCsdlEntity = new OfbizCsdlEntityType(lastCsdlEntityType.getOfbizEntity(), lastCsdlEntityType.getHandlerClass(), lastCsdlEntityType.isAutoProperties(),
                                false, lastCsdlEntityType.isFilterByDate(), lastCsdlEntityType.getDraftEntityName(), lastCsdlEntityType.getAttrEntityName(),
                                lastCsdlEntityType.getAttrNumericEntityName(), lastCsdlEntityType.getAttrDateEntityName(), false, lastCsdlEntityType.getEntityCondition(),
                                lastCsdlEntityType.getEntityConditionStr(), lastCsdlEntityType.getLabelPrefix(), null, lastCsdlEntityType.isGroupBy(), lastCsdlEntityType.hasStream());
                        newCsdlEntity.setName(lastEntityAlias);
                        newCsdlEntity.setProperties(lastCsdlEntityType.getProperties());
                        //多段式的Attribute字段排序
                        if (property.isAttribute() || property.isNumericAttribute() || property.isDateAttribute()) {
                            dynamicViewHolder.addPropertyAttr(newCsdlEntity, property.getName());
                            String orderByPropertyName = property.getName() + " NULLS LAST";
                            orderBy.add(orderByItem.isDescending() ? "-" + orderByPropertyName : orderByPropertyName);
                            return;
                        }
                        //多段式的RelAlias字段排序
                        if (property.getRelAlias() != null) {
                            EntityTypeRelAlias relAlias = property.getRelAlias();
                            dynamicViewHolder.addRelAlias(newCsdlEntity, relAlias);
                            List<String> relations = relAlias.getRelations();
                            String relPropertyName = dynamicViewHolder.addFilterProperty(relations.get(relations.size() - 1), property.getOfbizFieldName());
                            relPropertyName = relPropertyName + " NULLS LAST";
                            orderBy.add(orderByItem.isDescending() ? "-" + relPropertyName : relPropertyName);
                            return;
                        }
                    }
                    if (uriResource instanceof UriResourcePrimitiveProperty) {
                        EdmProperty edmProperty = ((UriResourcePrimitiveProperty) uriResource).getProperty();
                        String propertyAlias = dynamicViewHolder.addOrderByProperty(lastEntityAlias, edmProperty.getName());
                        propertyAlias = propertyAlias + " NULLS LAST";
                        orderBy.add(orderByItem.isDescending() ? "-" + propertyAlias : propertyAlias);
                    }
                }
            }
        }
        // 如果没有orderBy，那缺省就是按照sequenceNum来排序
        ModelEntity currModelEntity = csdlEntityType != null ?
                delegator.getModelEntity(csdlEntityType.getOfbizEntity()) : modelEntity;
        if (UtilValidate.isEmpty(orderBy) && currModelEntity.areFields(UtilMisc.toList("sequenceNum"))) {
            orderBy = UtilMisc.toList("sequenceNum");
        }
    }

    private void retrieveModelEntity() throws ODataException {
        if (this.edmEntityType == null && UtilValidate.isNotEmpty(edmParams)) {
            if (edmParams.get("edmBindingTarget") != null) {
                this.edmEntityType = ((EdmBindingTarget) edmParams.get("edmBindingTarget")).getEntityType();
            } else if (edmParams.get("edmSingleton") != null) {
                this.edmEntityType = ((EdmSingleton) edmParams.get("edmSingleton")).getEntityType();
            } else if (edmParams.get("edmEntityType") != null) {
                this.edmEntityType = (EdmEntityType) edmParams.get("edmEntityType");
            }
        }
        // entityName是接下来要查询ofbiz的真正主对象。比如edmEntityType是Party，edmTypeFilter是Person
        // 在查询时，不能只查Party，也不能只查Person，要把Party和Person联合起来查。
        // ofbiz的entity定义中有个PartyPersonPartyGroup，就是给到Party和Person联合起来查
        if (this.entityName == null) {
            OfbizCsdlEntityType ofbizCsdlEntityType = null;
            if (this.edmEntityType != null) {
                ofbizCsdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(this.edmEntityType.getFullQualifiedName());
            } else if (UtilValidate.isNotEmpty(edmParams) && edmParams.get("edmSingleton") != null) {
                EdmEntityType singletonEntityType = ((EdmSingleton) edmParams.get("edmSingleton")).getEntityType();
                ofbizCsdlEntityType = (OfbizCsdlEntityType) this.edmProvider.getEntityType(this.edmEntityType.getFullQualifiedName());
            }
            if (ofbizCsdlEntityType != null) {
                this.entityName = OdataProcessorHelper.getEntityNameToFind(ofbizCsdlEntityType,
                        (String) odataContext.get("sapContextId"), edmProvider);
            }
        }
        if (this.entityName != null) {
            try {
                this.modelEntity = delegator.getModelReader().getModelEntity(this.entityName);
            } catch (GenericEntityException e) {
                Debug.logWarning(e.getMessage(), module);
            }
        }
    }

    protected Map<String, Object> runService(ModelService modelService, Map<String, Object> serviceParameters, String returnName)
            throws ODataException {
        Debug.logInfo("runService Param in :" + serviceParameters, module);
        Map<String, Object> result = null;
        try {
            result = dispatcher.runSync(modelService.name, serviceParameters);
        } catch (GenericServiceException e) {
            e.printStackTrace();
            throw new ODataException(e.getMessage());
        }
        if (ServiceUtil.isSuccess(result)) {
            List<String> outParamNames = modelService.getParameterNames("OUT", true, false);
            if (UtilValidate.isNotEmpty(returnName)) {
                Object outValue = result.get(returnName);
                return UtilMisc.toMap(returnName, outValue);
            } else if (UtilValidate.isNotEmpty(outParamNames)) {
                String outParamName = outParamNames.get(0);
                Object outValue = result.get(outParamName);
                return UtilMisc.toMap(outParamName, outValue);
            } else {
                return null;
            }
        } else {
            Debug.logError("run service error:" + result.toString(), module);
            if (result.get("errorMessageList") != null) {
                throw new ODataException((String) result.get("errorMessageList"));
            } else {
                throw new ODataException((String) result.get("errorMessage"));
            }
        }
    }

    public ComplexValue objectToComplexValue(Object object) {
        ComplexValue complexValue = new ComplexValue();
        Map<String, Object> fields = Util.objectFieldValueMap(object, false);
        List<Property> propertys = complexValue.getValue();
        for (String key : fields.keySet()) {
            Object filedObject = fields.get(key);
            if (null == filedObject) {
                propertys.add(new Property(null, key, ValueType.PRIMITIVE, null));
                continue;
            }
            if (Util.isBaseType(filedObject)) {
                propertys.add(new Property(null, key, ValueType.PRIMITIVE, filedObject));
                continue;
            }
            if (filedObject instanceof List<?>
            ) {
                Property propertyList = null;
                List<?> objList = (List<?>) filedObject;
                Object objectHead = objList.get(0);
                if (Util.isBaseType(objectHead)) {
                    propertyList = new Property(filedObject.getClass().getName(), key, ValueType.COLLECTION_PRIMITIVE, filedObject);
                    propertys.add(propertyList);
                } else {
                    List<ComplexValue> complexPropertys = new ArrayList<>();
                    for (Object rowObj : objList) {
                        ComplexValue rowComplex = objectToComplexValue(rowObj);
                        complexPropertys.add(rowComplex);
                    }
                    Property complexProperty = new Property(null, key, ValueType.COLLECTION_COMPLEX, complexPropertys);
                    propertys.add(complexProperty);
                }
            } else {
                ComplexValue rowComplex = objectToComplexValue(filedObject);
                Property complexProperty = new Property(null, key, ValueType.COMPLEX, rowComplex);
                propertys.add(complexProperty);
            }
        }
        return complexValue;
    }

    public ComplexValue mapToComplexValue(Map<String, Object> objectMap, EdmComplexType edmComplexType) {
        ComplexValue complexValue = new ComplexValue();
        List<Property> properties = complexValue.getValue();
        for (String key : objectMap.keySet()) {
            Object fieldValue = objectMap.get(key);
            if (null == fieldValue) {
                properties.add(new Property(null, key, ValueType.PRIMITIVE, null));
                continue;
            }
            if (Util.isBaseType(fieldValue)) {
                properties.add(new Property(null, key, ValueType.PRIMITIVE, fieldValue));
                continue;
            }
            if (fieldValue instanceof List<?>) {
                Property propertyList = null;
                List<?> objList = (List<?>) fieldValue;
                if (objList.size() == 0) {
                    properties.add(new Property(null, key, ValueType.COLLECTION_PRIMITIVE, null));
                    continue;
                }
                Object objectHead = objList.get(0);
                if (Util.isBaseType(objectHead)) {
                    propertyList = new Property(null, key, ValueType.COLLECTION_PRIMITIVE, fieldValue);
                    properties.add(propertyList);
                } else if (objectHead instanceof Map) {
                    EdmElement edmElement = edmComplexType.getProperty(key);
                    EdmComplexType innerComplexType = (EdmComplexType) edmElement.getType();
                    List<ComplexValue> complexProperties = new ArrayList<>();
                    for (Object rowObj : objList) {
                        ComplexValue rowComplex = mapToComplexValue((Map<String, Object>) rowObj, innerComplexType);
                        complexProperties.add(rowComplex);
                    }
                    Property complexProperty = new Property(innerComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), key, ValueType.COLLECTION_COMPLEX, complexProperties);
                    properties.add(complexProperty);
                }
            } else if (fieldValue instanceof Map) {
                EdmElement edmElement = edmComplexType.getProperty(key);
                EdmComplexType innerComplexType = (EdmComplexType) edmElement.getType();
                ComplexValue rowComplex = mapToComplexValue((Map<String, Object>) fieldValue, innerComplexType);
                Property complexProperty = new Property(innerComplexType.getFullQualifiedName().getFullQualifiedNameAsString(), key, ValueType.COMPLEX, rowComplex);
                properties.add(complexProperty);
            } else { // 是个其它对象，不支持
                return null;
            }
        }
        return complexValue;
    }

    public Object complexValueToObject(ComplexValue complexValue, String ofbizClass) {
        List<Property> properties = complexValue.getValue();
        Object newInstance = null;
        Map<String, Object> propertyFieldMap = new HashMap<>();
        for (Property property : properties) {
            if (property.getValue() != null && property.getValue().getClass().equals(java.lang.Double.class)) {
                propertyFieldMap.put(property.getName(), new BigDecimal((Double) property.getValue()));
            } else {
                propertyFieldMap.put(property.getName(), property.getValue());
            }
        }
        try {
            Class<?> theOfbizClass = Class.forName(ofbizClass);
            Constructor<?> constructor = theOfbizClass.getConstructor();
            newInstance = constructor.newInstance();
            List<Field> fieldList = new ArrayList<>();
            while (theOfbizClass != null) {
                fieldList.addAll(Arrays.asList(theOfbizClass.getDeclaredFields()));
                theOfbizClass = theOfbizClass.getSuperclass();
            }
            for (Field field : fieldList) {
                String fieldName = field.getName();
                field.setAccessible(true);
                if (null != propertyFieldMap.get(field.getName()) && propertyFieldMap.containsKey(field.getName())) {
                    if (Util.isBaseType(propertyFieldMap.get(field.getName()))) {
                        field.set(newInstance, propertyFieldMap.get(field.getName()));
                    } else {
                        if (propertyFieldMap.get(field.getName()).getClass().equals(java.util.ArrayList.class)) {
                            List<ComplexValue> innerComplexValue = (List<ComplexValue>) propertyFieldMap.get(field.getName());
                            String ofbizTypeFullName = innerComplexValue.get(0).getTypeName();
                            // String innerClassName = edmWebConfig.getComplexTypeMap().get(ofbizTypeName).getOfbizClass();
                            OfbizCsdlComplexType csdlComplexType = (OfbizCsdlComplexType) edmProvider.getComplexType(new FullQualifiedName(ofbizTypeFullName));
                            String innerClassName = csdlComplexType.getOfbizClass();
                            List<Object> objList = new ArrayList<>();
                            for (ComplexValue cv : innerComplexValue) {
                                objList.add(complexValueToObject(cv, innerClassName));
                            }
                            field.set(newInstance, objList);
                        } else {
                            ComplexValue innerComplexValue = (ComplexValue) propertyFieldMap.get(field.getName());
                            String ofbizTypeFullName = innerComplexValue.getTypeName();
                            OfbizCsdlComplexType csdlComplexType = (OfbizCsdlComplexType) edmProvider.getComplexType(new FullQualifiedName(ofbizTypeFullName));
                            // String innerClassName = edmWebConfig.getComplexTypeMap().get(ofbizTypeName).getOfbizClass();
                            String innerClassName = csdlComplexType.getOfbizClass();
                            field.set(newInstance, complexValueToObject(innerComplexValue
                                    , innerClassName));
                        }
                    }
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | IllegalAccessException | InvocationTargetException | ODataException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return newInstance;
    }

    protected OdataOfbizEntity objectToEntity(FullQualifiedName fqn, Object object) throws OfbizODataException {
        if (object == null) {
            return null;
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(fqn);
        OdataOfbizEntity e1 = new OdataOfbizEntity();
        e1.setType(fqn.getFullQualifiedNameAsString());
        List<CsdlProperty> csdlProperties = csdlEntityType.getProperties();
        Map<String, Object> cartFieldAndValueMap = Util.objectFieldValueMap(object, true);
        for (CsdlProperty csdlProperty : csdlProperties) {
            String propertyName = csdlProperty.getName();
            Object value = cartFieldAndValueMap.get(propertyName);
            if (UtilValidate.isNotEmpty(value)) {
                //如果是数组复杂对象，会有问题
                if (!(value instanceof Collection)) {
                    Property property = new Property(csdlProperty.getTypeAsFQNObject().getFullQualifiedNameAsString(), propertyName,
                            ValueType.PRIMITIVE, value);
                    e1.addProperty(property);
                }
            }
        }
        return e1;
    }


    protected void addExpandOption(ExpandOption expandOption, OdataOfbizEntity entity, EdmEntityType edmEntityType)
            throws OfbizODataException {
        if (expandOption == null) {
            return;
        }
        List<ExpandItem> expandItems = expandOption.getExpandItems();
        ExpandItem firstExpandItem = expandItems.get(0);
        if (firstExpandItem.isStar()) {
            LevelsExpandOption levelsExpandOption = firstExpandItem.getLevelsOption();
            int expandLevel = 1;
            if (levelsExpandOption != null) {
                expandLevel = levelsExpandOption.getValue();
            }
            List<String> navigationNames = edmEntityType.getNavigationPropertyNames();
            for (String navigationName : navigationNames) {
                EdmNavigationProperty navigationProperty = edmEntityType.getNavigationProperty(navigationName);
                addExpandNavigation(entity, edmEntityType, navigationProperty, expandLevel);
            }
        } else {
            for (ExpandItem expandItem : expandItems) {
                addExpandItem(entity, expandItem, edmEntityType);
            }
        }
        Debug.logInfo("finished adding all expand items", module);
    }

    private void addExpandItem(OdataOfbizEntity entity, ExpandItem expandItem, EdmEntityType edmEntityType) throws OfbizODataException {
        EdmNavigationProperty edmNavigationProperty = null;
        LevelsExpandOption levelsExpandOption = expandItem.getLevelsOption();
        int expandLevel = 1;
        if (levelsExpandOption != null) {
            expandLevel = levelsExpandOption.getValue();
        }
        List<UriResource> expandItemPath = expandItem.getResourcePath().getUriResourceParts();
        UriResource uriResource = expandItemPath.get(0);
        if (uriResource instanceof UriResourceNavigation) {
            edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
        }
        if (edmNavigationProperty == null) {
            return;
        }
        String navPropName = edmNavigationProperty.getName();
        Debug.logInfo("adding expand option with name = " + navPropName, module);
        if (edmNavigationProperty.isCollection()) { // expand的对象是collection
            ExpandOption nestedExpandOption = expandItem.getExpandOption(); // expand nested in expand
            if (nestedExpandOption == null && expandLevel > 1) {
                ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
                LevelsOptionImpl levelsOptionImpl = (LevelsOptionImpl) levelsExpandOption;
                levelsOptionImpl.setValue(expandLevel--);
                expandOptionImpl.addExpandItem(expandItem);
                nestedExpandOption = expandOptionImpl;
            }
            Map<String, QueryOption> embeddedQueryOptions = UtilMisc.toMap("expandOption", nestedExpandOption, "orderByOption", expandItem.getOrderByOption(),
                    "selectOption", expandItem.getSelectOption(), "searchOption", expandItem.getSearchOption(), "filterOption", expandItem.getFilterOption());
            expandCollection(entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
        } else { // expand对象不是collection
            // 此处改过
            Map<String, QueryOption> embeddedQueryOptions = UtilMisc.toMap("expandOption", expandItem.getExpandOption(),
                    "orderByOption", expandItem.getOrderByOption(), "selectOption", expandItem.getSelectOption(), "filterOption", expandItem.getFilterOption());
            expandNonCollection(entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
        } // end expand对象不是collection
    }

    // expand=*时会调用此方法
    private void addExpandNavigation(OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                     EdmNavigationProperty edmNavigationProperty,
                                     int expandLevel) throws OfbizODataException {
        ExpandOption nestedExpandOption = null;
        if (expandLevel > 1) {
            ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
            ExpandItemImpl expandItemImpl = new ExpandItemImpl();
            LevelsOptionImpl levelsOptionImpl = new LevelsOptionImpl();
            levelsOptionImpl.setValue(expandLevel--);
            expandItemImpl.setSystemQueryOption(levelsOptionImpl);
            expandItemImpl.setIsStar(true);
            expandOptionImpl.addExpandItem(expandItemImpl);
            nestedExpandOption = expandOptionImpl;
        }
        Map<String, QueryOption> embeddedQueryOptions = UtilMisc.toMap("expandOption", nestedExpandOption);
        if (edmNavigationProperty.isCollection()) { // expand的对象是collection
            expandCollection(entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
        } else { // expand对象不是collection
            expandNonCollection(entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
        } // end expand对象不是collection
    }


    private EntityCollection getExpandData(OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                           EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> embeddedQueryOptions) throws OfbizODataException {
        Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmEntityType", edmEntityType, "edmNavigationProperty", edmNavigationProperty);
        OdataReader reader = new OdataReader(getOdataContext(), embeddedQueryOptions, embeddedEdmParams);
        return reader.findRelatedList(entity, edmNavigationProperty, embeddedQueryOptions, null);
    }

    private void expandNonCollection(OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                     EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, edmEntityType, edmNavigationProperty, queryOptions);
        if (null != expandEntityCollection && UtilValidate.isNotEmpty(expandEntityCollection.getEntities())) {
            Entity expandEntity = expandEntityCollection.getEntities().get(0);
            expandEntityCollection.setCount(expandEntityCollection.getEntities().size());
            Link link = new Link();
            String navPropName = edmNavigationProperty.getName();
            link.setTitle(navPropName);
            link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
            link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
            link.setInlineEntity(expandEntity);
            if (entity.getId() != null) {
                String linkHref = entity.getId().toString() + "/" + navPropName;
                link.setHref(linkHref);
            }
            entity.getNavigationLinks().add(link);
        }
    }

    private void expandCollection(OdataOfbizEntity entity, EdmEntityType edmEntityType,
                                  EdmNavigationProperty edmNavigationProperty, Map<String, QueryOption> queryOptions) throws OfbizODataException {
        EntityCollection expandEntityCollection = getExpandData(entity, edmEntityType, edmNavigationProperty, queryOptions);
        String navPropName = edmNavigationProperty.getName();
        Link link = new Link();
        link.setTitle(navPropName);
        link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
        link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
        link.setInlineEntitySet(expandEntityCollection);
        expandEntityCollection.setCount(expandEntityCollection.getEntities().size());
        if (entity.getId() != null) { // TODO:要检查一下为什么会有id为null的情况
            String linkHref = entity.getId().toString() + "/" + navPropName;
            link.setHref(linkHref);
        }
        entity.getNavigationLinks().add(link);
    }

    /**
     * 判断一个Navigation查询的整个流程是否缺省的
     */
    protected boolean isDefaultQuery(EdmEntityType edmEntityType, EdmNavigationProperty edmNavigationProperty, OfbizAppEdmProvider edmProvider) {
        String edmEntityHandler = HandlerFactory.getHandlerImpl(edmProvider, UtilMisc.toList(edmNavigationProperty.getName()), delegator);
        String edmNavigationHandler = HandlerFactory.getHandlerImpl(edmProvider, UtilMisc.toList(edmEntityType.getName(), edmNavigationProperty.getName()), delegator);
        return UtilValidate.isEmpty(edmEntityHandler) && UtilValidate.isEmpty(edmNavigationHandler);
    }

    protected String getFieldShortValue(List<String> fieldName, GenericValue genericValue) {
        StringBuilder stringBuilder = new StringBuilder();
        fieldName.forEach(e -> stringBuilder.append(genericValue.getString(e)));
        return stringBuilder.toString();
    }

    /**
     * 批量处理，如果Navigation是单纯的relation，所有实体只做一次查询
     */
    protected void addExpandOption(ExpandOption expandOption, Collection<Entity> entityList,EdmBindingTarget edmBindingTarget, EdmEntityType edmEntityType)
            throws OfbizODataException {
        if (expandOption == null) {
            return;
        }
        List<ExpandItem> expandItems = expandOption.getExpandItems();
        ExpandItem firstExpandItem = expandItems.get(0);
        if (firstExpandItem.isStar()) {
            LevelsExpandOption levelsExpandOption = firstExpandItem.getLevelsOption();
            int expandLevel = 1;
            if (levelsExpandOption != null) {
                expandLevel = levelsExpandOption.getValue();
            }
            List<String> navigationNames = edmEntityType.getNavigationPropertyNames();
            for (String navigationName : navigationNames) {
                EdmNavigationProperty navigationProperty = edmEntityType.getNavigationProperty(navigationName);
                addExpandNavigation(entityList, edmEntityType, navigationProperty, expandLevel);
            }
        } else {
            for (ExpandItem expandItem : expandItems) {
                addAllExpandItem(entityList, expandItem, edmBindingTarget, edmEntityType);
            }
        }
    }


    private void addAllExpandItem(Collection<Entity> entityList, ExpandItem expandItem, EdmBindingTarget edmBindingTarget, EdmEntityType edmEntityType) throws OfbizODataException {
        EdmNavigationProperty edmNavigationProperty = null;
        LevelsExpandOption levelsExpandOption = expandItem.getLevelsOption();
        int expandLevel = 1;
        if (levelsExpandOption != null) {
            expandLevel = levelsExpandOption.getValue();
        }
        UriResource uriResource = expandItem.getResourcePath().getUriResourceParts().get(0);
        if (uriResource instanceof UriResourceNavigation) {
            edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
        }
        if (edmNavigationProperty == null) {
            return;
        }
        //如果当前expand是缺省的ofbizRelation，直接向数据库做一次查询，否者通过Handler处理
        if (isDefaultQuery(edmEntityType, edmNavigationProperty, edmProvider)) {
            Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmBindingTarget", edmBindingTarget, "edmEntityType", edmEntityType, "edmNavigationProperty", edmNavigationProperty);
            Map<String, QueryOption> embeddedQueryOptions = UtilMisc.toMap("expandOption", expandItem.getExpandOption(),
                    "orderByOption", expandItem.getOrderByOption(), "selectOption", expandItem.getSelectOption(), "filterOption", expandItem.getFilterOption(),
                    "skipOption", expandItem.getSkipOption(), "topOption", expandItem.getTopOption());
            if (edmNavigationProperty.isCollection()) {
                ExpandOption nestedExpandOption = expandItem.getExpandOption();
                if (nestedExpandOption == null && expandLevel > 1) {
                    ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
                    LevelsOptionImpl levelsOptionImpl = (LevelsOptionImpl) levelsExpandOption;
                    levelsOptionImpl.setValue(expandLevel--);
                    expandOptionImpl.addExpandItem(expandItem);
                    nestedExpandOption = expandOptionImpl;
                    embeddedQueryOptions.put("expandOption", nestedExpandOption);
                }
            }
            OdataReader reader = new OdataReader(getOdataContext(), embeddedQueryOptions, embeddedEdmParams);
            reader.addDefaultExpandLink(entityList, edmNavigationProperty, embeddedQueryOptions);
        } else {
            for (Entity entity : entityList) {
                addExpandItem((OdataOfbizEntity) entity, expandItem, edmEntityType);
            }
        }
    }

    /**
     * 将expand实体添加到主实体的link中
     */
    protected void addEntityToLink(Entity entity, EdmNavigationProperty edmNavigationProperty, Entity expandEntity) {
        if (entity == null || expandEntity == null) {
            return;
        }
        String navPropName = edmNavigationProperty.getName();
        boolean collection = edmNavigationProperty.isCollection();
        Link navigationLink = entity.getNavigationLink(navPropName);
        if (navigationLink != null) {
            //已存在的link
            if (collection) {
                EntityCollection inlineEntityCollection = navigationLink.getInlineEntitySet();
                List<Entity> entities = inlineEntityCollection.getEntities();
                entities.add(expandEntity);
                inlineEntityCollection.setCount(entities.size());
            }
        } else {
            //新增一个link
            Link link = new Link();
            link.setTitle(navPropName);
            link.setType(Constants.ENTITY_NAVIGATION_LINK_TYPE);
            link.setRel(Constants.NS_ASSOCIATION_LINK_REL + navPropName);
            if (entity.getId() != null) {
                String linkHref = entity.getId().toString() + "/" + navPropName;
                link.setHref(linkHref);
            }
            if (collection) {
                EntityCollection currentExpands = new EntityCollection();
                currentExpands.setCount(1);
                currentExpands.getEntities().add(expandEntity);
                link.setInlineEntitySet(currentExpands);
            } else {
                link.setInlineEntity(expandEntity);
            }
            entity.getNavigationLinks().add(link);
        }
    }


    // expand=*时会调用此方法
    private void addExpandNavigation(Collection<Entity> entityList, EdmEntityType edmEntityType,
                                     EdmNavigationProperty edmNavigationProperty, int expandLevel) throws OfbizODataException {
        ExpandOption nestedExpandOption = null;
        if (expandLevel > 1) {
            ExpandOptionImpl expandOptionImpl = new ExpandOptionImpl();
            ExpandItemImpl expandItemImpl = new ExpandItemImpl();
            LevelsOptionImpl levelsOptionImpl = new LevelsOptionImpl();
            levelsOptionImpl.setValue(expandLevel--);
            expandItemImpl.setSystemQueryOption(levelsOptionImpl);
            expandItemImpl.setIsStar(true);
            expandOptionImpl.addExpandItem(expandItemImpl);
            nestedExpandOption = expandOptionImpl;
        }
        Map<String, QueryOption> embeddedQueryOptions = UtilMisc.toMap("expandOption", nestedExpandOption);
        Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmEntityType", edmEntityType, "edmNavigationProperty", edmNavigationProperty);
        if (isDefaultQuery(edmEntityType, edmNavigationProperty, edmProvider)) {
            OdataReader reader = new OdataReader(getOdataContext(), embeddedQueryOptions, embeddedEdmParams);
            reader.addDefaultExpandLink(entityList, edmNavigationProperty, embeddedQueryOptions);
        } else {
            for (Entity entity : entityList) {
                if (edmNavigationProperty.isCollection()) { // expand的对象是collection
                    expandCollection((OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
                } else {
                    expandNonCollection((OdataOfbizEntity) entity, edmEntityType, edmNavigationProperty, embeddedQueryOptions);
                }
            }
        }
    }

}
