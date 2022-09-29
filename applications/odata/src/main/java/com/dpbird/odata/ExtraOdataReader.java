package com.dpbird.odata;

import org.apache.http.HttpStatus;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.condition.EntityComparisonOperator;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceCount;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.api.uri.queryoption.expression.*;

import java.util.*;
import java.util.stream.Stream;

/**
 * 为OfbizOdataReader做扩展，主要处理一些ofbiz无法直接支持的odata查询
 */
public class ExtraOdataReader extends OfbizOdataReader {
    public static final String MODULE = OfbizOdataReader.class.getName();

    public ExtraOdataReader(Map<String, Object> odataContext, Map<String, QueryOption> queryOptions, Map<String, Object> edmParams) {
        super(odataContext, queryOptions, edmParams);
    }

    @Override
    public EntityCollection findList() throws ODataException {
        //如果父类支持就直接处理
        if (useOfbizReader(queryOptions)) {
            return super.findList();
        }
        //ofbizReader不能处理就用java实现，不支持太大的数据量
        initPageValue();
        EntityCollection entityCollection = super.findList();
        if (entityCollection.getEntities().size() > EXTRA_QUERY_MAX_RAW) {
            throw new OfbizODataException(String.valueOf(HttpStatus.SC_NOT_IMPLEMENTED), "Current ODATA statements do not support querying entities with large amounts of data.");
        }
        extraOdataFilter(entityCollection);
        extraOdataOrderBy(entityCollection);
        entityCollectionPage(entityCollection);
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
        }
    }

    /**
     * 处理filter
     */
    private void extraOdataFilter(EntityCollection entityCollection) throws OfbizODataException, ExpressionVisitException, ODataApplicationException {
        FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
        if (filterOption == null) {
            return;
        }
        //根据子对象数量做筛选: http://.../Products?$filter=Navigation/$count eq 100
        ExtraCondition extraCondition = (ExtraCondition) filterOption.getExpression().accept(new ExtraExpressionVisitor());
        Debug.logInfo("extraCondition: " + extraCondition.toString(), MODULE);
        if (extraCondition.getLeftOption() instanceof UriResourceNavigation) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) extraCondition.getLeftOption();
            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
            Integer condValue = Integer.valueOf(extraCondition.getRightOption().toString());
            EntityComparisonOperator<?, ?> operator = extraCondition.getOperator();
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
                OfbizOdataReader ofbizOdataReader = new OfbizOdataReader(embeddedOdataContext, new HashMap<>(), embeddedEdmParams);
                EntityCollection relEntityCollection = ofbizOdataReader.findRelatedEntityCollection(entity, edmNavigationProperty, null, false);
                relCount = relEntityCollection.getEntities().size();
            }
            relationCountMap.put(entity, relCount);
        }
        return relationCountMap;
    }


    /**
     * 对结果集分页
     */
    private void entityCollectionPage(EntityCollection entityCollection) {
        retrieveFindOption();
        List<Entity> entities = entityCollection.getEntities();
        List<Entity> entitiesPage;
        if ((skipValue + topValue) > entities.size()) {
            if (entities.size() <= skipValue) {
                return;
            }
            entitiesPage = new ArrayList<>(entities.subList(skipValue, entities.size()));
        } else {
            entitiesPage = new ArrayList<>(entities.subList(skipValue, skipValue + topValue));
        }
        entityCollection.getEntities().clear();
        entityCollection.getEntities().addAll(entitiesPage);
    }

    /**
     * 判断父类是否可以支持这些OdataOptions
     */
    private boolean useOfbizReader(Map<String, QueryOption> queryOptions) {
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
        return true;

    }

    private void initPageValue() {
        skipValue = 0;
        topValue = MAX_ROWS;
    }

    private static class ExtraExpressionVisitor implements ExpressionVisitor<Object> {
        @Override
        public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right) throws ExpressionVisitException, ODataApplicationException {
            EntityComparisonOperator<?, ?> entityComparisonOperator = OdataExpressionVisitor.COMPARISONOPERATORMAP.get(operator);
            if (left == null && right instanceof ExtraCondition) return right;
            if (right == null && left instanceof ExtraCondition) return left;
            if (left == null || right == null) return null;
            if (left instanceof ExtraCondition && right instanceof ExtraCondition) {
                //TODO: 多个条件 可能需要返回一个List
            }
            return new ExtraCondition(left, entityComparisonOperator, right);
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

    private static class ExtraCondition {
        private Object leftOption;
        private EntityComparisonOperator<?, ?> operator;
        private Object rightOption;

        public ExtraCondition(Object leftOption, EntityComparisonOperator<?, ?> operator, Object rightOption) {
            this.leftOption = leftOption;
            this.operator = operator;
            this.rightOption = rightOption;
        }

        public Object getLeftOption() {
            return leftOption;
        }

        public void setLeftOption(Object leftOption) {
            this.leftOption = leftOption;
        }

        public EntityComparisonOperator<?, ?> getOperator() {
            return operator;
        }

        public void setOperator(EntityComparisonOperator<?, ?> operator) {
            this.operator = operator;
        }

        public Object getRightOption() {
            return rightOption;
        }

        public void setRightOption(Object rightOption) {
            this.rightOption = rightOption;
        }

        @Override
        public String toString() {
            return leftOption.toString() +
                    operator.toString() +
                    rightOption.toString();
        }
    }

}
