package com.dpbird.odata;

import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.condition.*;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceCount;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.OrderByItem;
import org.apache.olingo.server.api.uri.queryoption.expression.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * 为OfbizOdataReader做扩展，主要处理一些ofbiz无法直接支持的odata查询
 */
public class ExtraOdataReader extends OdataReader {
    public static final String MODULE = OdataReader.class.getName();

    public ExtraOdataReader(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    @Override
    public EntityCollection findList() throws OfbizODataException {
        //如果父类支持就直接处理
        if (useOdataReader(queryOptions)) {
            return super.findList();
        }
        //OdataReader不能处理的QueryOption就用java实现，不支持太大的数据量
        int top = getTopOption(queryOptions);
        int skip = getSkipOption(queryOptions);
        queryOptions.remove("topOption");
        queryOptions.remove("skipOption");
        EntityCollection entityCollection = super.findList();
        if (entityCollection.getEntities().size() > EXTRA_QUERY_MAX_RAW) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_IMPLEMENTED), "Current ODATA statements do not support querying entities with large amounts of data.");
        }
        extraOdataFilter(entityCollection);
        extraOdataOrderBy(entityCollection);
        entityCollectionCount(entityCollection);
        entityCollectionPage(entityCollection, top, skip);
        return entityCollection;
    }

    /**
     * 处理orderby
     */
    private void extraOdataOrderBy(EntityCollection entityCollection) throws OfbizODataException {
        OrderByOption orderByOption = (OrderByOption) queryOptions.get("orderByOption");
        if (orderByOption == null) {
            return;
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        OrderByItem orderByItem = orderByOption.getOrders().stream()
                .filter(item -> item.getExpression().toString().contains("$count")).findFirst().orElse(null);
        if (orderByItem != null) {
            UriInfoResource resourcePath = ((Member) orderByItem.getExpression()).getResourcePath();
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourcePath.getUriResourceParts().get(0);
            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
            Map<Entity, Integer> relationCountMap = readEntityRelationCount(entityCollection, edmNavigationProperty);
            entityCollection.getEntities().clear();
            if (orderByItem.isDescending()) {
                //desc
                relationCountMap.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                        .forEachOrdered(entry -> entityCollection.getEntities().add(entry.getKey()));
            } else {
                //asc
                relationCountMap.entrySet().stream().sorted(Map.Entry.comparingByValue())
                        .forEachOrdered(entry -> entityCollection.getEntities().add(entry.getKey()));
            }
        } else if (Util.isExtraOrderby(orderByOption, csdlEntityType, delegator)){
            Util.orderbyEntityCollection(entityCollection, orderByOption, edmEntityType, edmProvider);
        }
    }

    /**
     * 处理filter
     */
    private void extraOdataFilter(EntityCollection entityCollection) throws OfbizODataException {
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        if (filterOption == null) {
            return;
        }
        try {
            List<EntityExpr> extraConditionList = (List<EntityExpr>) filterOption.getExpression().accept(new ExtraExpressionVisitor());
            //根据子对象数量做筛选: http://.../Products?$filter=Navigation/$count eq 100
            Debug.logInfo("extraCondition: " + extraConditionList.toString(), MODULE);
            for (EntityExpr extraCondition : extraConditionList) {
                if (extraCondition.getLhs() instanceof UriResourceNavigation) {
                    UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) extraCondition.getLhs();
                    EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                    Integer condValue = Integer.valueOf(extraCondition.getRhs().toString());
                    EntityOperator<?, ?, ?> operator = extraCondition.getOperator();
                    Map<Entity, Integer> relationCountMap = readEntityRelationCount(entityCollection, edmNavigationProperty);
                    entityCollection.getEntities().clear();
                    //不同的运算符比较
                    Stream<Map.Entry<Entity, Integer>> mapStream = relationCountMap.entrySet().stream();
                    if (operator.equals(EntityOperator.EQUALS)) {
                        mapStream = mapStream.filter(en -> en.getValue().equals(condValue));
                    } else if (operator.equals(EntityOperator.NOT_EQUAL)) {
                        mapStream = mapStream.filter(en -> !en.getValue().equals(condValue));
                    } else if (operator.equals(EntityOperator.LESS_THAN)) {
                        mapStream = mapStream.filter(en -> en.getValue() < condValue);
                    } else if (operator.equals(EntityOperator.LESS_THAN_EQUAL_TO)) {
                        mapStream = mapStream.filter(en -> en.getValue() <= condValue);
                    } else if (operator.equals(EntityOperator.GREATER_THAN)) {
                        mapStream = mapStream.filter(en -> en.getValue() > condValue);
                    } else if (operator.equals(EntityOperator.GREATER_THAN_EQUAL_TO)) {
                        mapStream = mapStream.filter(en -> en.getValue() >= condValue);
                    }
                    mapStream.forEachOrdered(forE -> entityCollection.getEntities().add(forE.getKey()));
                }
            }

        } catch (ExpressionVisitException |ODataApplicationException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
    }

    /**
     * 获取实体的某个关联实体数量
     *
     * @return relationCountMap key是entity, value是关联实体的数量
     */
    private Map<Entity, Integer> readEntityRelationCount(EntityCollection entityCollection, EdmNavigationProperty edmNavigationProperty) throws OfbizODataException {
        Map<Entity, Integer> relationCountMap = new HashMap<>();
        for (Entity entity : entityCollection.getEntities()) {
            Link navigationLink = entity.getNavigationLink(edmNavigationProperty.getName());
            int relCount;
            if (navigationLink != null) {
                //如果存在Link里, 就不用再查询
                relCount = navigationLink.getInlineEntitySet().getEntities().size();
            } else {
                //查询子对象
                Map<String, Object> embeddedOdataContext = UtilMisc.toMap("delegator", delegator, "dispatcher", dispatcher,
                        "edmProvider", edmProvider, "userLogin", userLogin, "locale", locale, "httpServletRequest", httpServletRequest);
                Map<String, Object> embeddedEdmParams = UtilMisc.toMap("edmEntityType", edmEntityType, "edmNavigationProperty", edmNavigationProperty);
                OdataReader reader = new OdataReader(embeddedOdataContext, new HashMap<>(), embeddedEdmParams);
                EntityCollection relEntityCollection = reader.findRelatedList(entity, edmNavigationProperty, new HashMap<>(), null);
                relCount = relEntityCollection.getEntities().size();
            }
            relationCountMap.put(entity, relCount);
        }
        return relationCountMap;
    }

    /**
     * count
     */
    private void entityCollectionCount(EntityCollection entityCollection) {
        if (UtilValidate.isEmpty(entityCollection.getCount())) {
            entityCollection.setCount(entityCollection.getEntities().size());
        }
    }

    /**
     * 分页
     */
    private void entityCollectionPage(EntityCollection entityCollection, int top, int skip) {
        Util.pageEntityCollection(entityCollection, skip, top);
    }

    /**
     * 判断父类是否可以支持这些OdataOptions
     */
    private boolean useOdataReader(Map<String, QueryOption> queryOptions) throws OfbizODataException {
        OrderByOption orderByOption = (OrderByOption) queryOptions.get("orderByOption");
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");

        // http://.../Products?$orderby=Navigation/$count desc
        if (orderByOption != null && orderByOption.getText().contains("$count")) {
            return false;
        }
        // http://.../Products?$filter=Navigation/$count eq 100
        if (filterOption != null && filterOption.getExpression().toString().contains("$count")) {
            return false;
        }
        OfbizCsdlEntityType csdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(edmEntityType.getFullQualifiedName());
        if (Util.isExtraOrderby(orderByOption, csdlEntityType, delegator)) {
            return false;
        }
        return true;

    }

    @SuppressWarnings("unchecked")
    public static class ExtraExpressionVisitor implements ExpressionVisitor<Object> {
        @Override
        public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right) throws ExpressionVisitException, ODataApplicationException {
            EntityComparisonOperator<?, ?> entityComparisonOperator = OdataExpressionVisitor.COMPARISONOPERATORMAP.get(operator);
            if (left == null && right instanceof List) return right;
            if (right == null && left instanceof List) return left;
            if (left == null || right == null) return null;
            if (left instanceof List && right instanceof List) {
                EntityJoinOperator joinOperator = OdataExpressionVisitor.JOINOPERATORMAP.get(operator);
                if (joinOperator.equals(EntityOperator.AND)) {
                    List<EntityExpr> entityExprs = (List<EntityExpr>) left;
                    entityExprs.addAll((List<EntityExpr>) right);
                    return entityExprs;
                } else if (joinOperator.equals(EntityOperator.OR)) {
                    //TODO: 不支持or
                    throw new ExpressionVisitException("The current syntax does not support 'or'.");
                }
            }
            return UtilMisc.toList(new EntityExpr(left, entityComparisonOperator, right));
        }

        @Override
        public Object visitUnaryOperator(UnaryOperatorKind operator, Object operand) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitMethodCall(MethodKind methodCall, List<Object> parameters) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
            return literal.getText();
        }

        @Override
        public Object visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
            List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();
            UriResource uriResource = uriResourceParts.get(uriResourceParts.size() - 1);
            if (uriResource instanceof UriResourceCount) {
                //Navigation/$count
                return uriResourceParts.get(0);
            }

            return null;
        }

        @Override
        public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitEnum(EdmEnumType type, List<String> enumValues) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }

        @Override
        public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, List<Object> right) throws ExpressionVisitException, ODataApplicationException {
            return null;
        }
    }

}
