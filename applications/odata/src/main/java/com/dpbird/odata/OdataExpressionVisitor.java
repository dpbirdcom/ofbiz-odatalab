package com.dpbird.odata;

import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlProperty;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.*;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.primitivetype.EdmDate;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.apply.AggregateExpression;
import org.apache.olingo.server.api.uri.queryoption.expression.Enumeration;
import org.apache.olingo.server.api.uri.queryoption.expression.*;
import org.apache.olingo.server.core.uri.UriResourcePrimitivePropertyImpl;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OdataExpressionVisitor implements ExpressionVisitor<Object> {
    public static final String module = OdataExpressionVisitor.class.getName();
    private Delegator delegator;
    private LocalDispatcher dispatcher;
    private GenericValue userLogin;
    private OfbizAppEdmProvider edmProvider;
    private OfbizCsdlEntityType csdlEntityType;
    private DynamicViewHolder dynamicViewHolder = null;
    private String lastAlias = null;
    UriResourceNavigation lambdaUriResourceNavigation = null;
    public final static Map<BinaryOperatorKind, EntityComparisonOperator> COMPARISONOPERATORMAP = new HashMap<>();

    static {
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.EQ, EntityOperator.EQUALS);
        // NOT EMPTY
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.NE, EntityOperator.NOT_EQUAL);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.GE, EntityOperator.GREATER_THAN_EQUAL_TO);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.GT, EntityOperator.GREATER_THAN);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.LE, EntityOperator.LESS_THAN_EQUAL_TO);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.LT, EntityOperator.LESS_THAN);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.HAS, EntityOperator.LIKE);
        COMPARISONOPERATORMAP.put(BinaryOperatorKind.IN, EntityOperator.IN);
    }

    public final static Map<BinaryOperatorKind, EntityJoinOperator> JOINOPERATORMAP = new HashMap<BinaryOperatorKind, EntityJoinOperator>();

    static {
        JOINOPERATORMAP.put(BinaryOperatorKind.AND, EntityOperator.AND);
        JOINOPERATORMAP.put(BinaryOperatorKind.OR, EntityOperator.OR);
    }

    public final static Map<AggregateExpression.StandardMethod, String> AGGREGATE_MAP = new HashMap<>();

    static {
        AGGREGATE_MAP.put(AggregateExpression.StandardMethod.SUM, "sum");
        AGGREGATE_MAP.put(AggregateExpression.StandardMethod.MAX, "max");
        AGGREGATE_MAP.put(AggregateExpression.StandardMethod.MIN, "min");
        AGGREGATE_MAP.put(AggregateExpression.StandardMethod.AVERAGE, "avg");
        AGGREGATE_MAP.put(AggregateExpression.StandardMethod.COUNT_DISTINCT, "count-distinct");
    }

    public OdataExpressionVisitor(OfbizCsdlEntityType csdlEntityType, Delegator delegator,
                                  LocalDispatcher dispatcher, GenericValue userLogin,
                                  OfbizAppEdmProvider edmProvider) {
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.userLogin = userLogin;
        this.edmProvider = edmProvider;
        this.csdlEntityType = csdlEntityType;
        if (csdlEntityType.getBaseType() != null) {
            //如果实体有BaseType 查询时需要使用DynamicView
            dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
        }
    }

    /**
     * Called for each traversed {@link Binary} expression
     *
     * @param operator Operator kind
     * @param left     Application return value of left sub tree
     * @param right    Application return value of right sub tree
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right) throws ExpressionVisitException, ODataApplicationException {
        // Binary Operators are split up in three different kinds. Up to the kind of the
        // operator it can be applied to different types
        // - Arithmetic operations like add, minus, modulo, etc. are allowed on numeric
        // types like Edm.Int32
        // - Logical operations are allowed on numeric types and also Edm.String
        // - Boolean operations like and, or are allowed on Edm.Boolean
        // A detailed explanation can be found in OData Version 4.0 Part 2: URL
        // Conventions

        Debug.logInfo("------------------- Entering OfbizExpressionVisitor.visitBinaryOperator", module);
        Debug.logInfo("------------------- operator = " + operator, module);
        Debug.logInfo("------------------- left = " + left, module);
        Debug.logInfo("------------------- right = " + right, module);

        //目前针对前端传来的string类型的时间进行格式化处理，其他则不处理
        try {
            if (left instanceof EntityWhereString) {
                if (right instanceof EntityCondition) {
                    return Util.appendCondition((EntityCondition) left, (EntityCondition) right);
                }
                return EntityCondition.makeConditionWhere(left + COMPARISONOPERATORMAP.get(operator).getCode() + right);
            } else if (left instanceof EntityCondition) {
                EntityJoinOperator joinOperator = JOINOPERATORMAP.get(operator);
                List<EntityCondition> exprs = new ArrayList<>();
                exprs.add((EntityCondition) left);
                if (right instanceof EntityCondition) {
                    exprs.add((EntityCondition) right);
                }
                return EntityCondition.makeCondition(exprs, joinOperator);
            } else if (left instanceof String) {
                if (left.toString().contains("$count")) {
                    return null;
                }
                EntityComparisonOperator comparisonOperator = COMPARISONOPERATORMAP.get(operator);
                String realLeft = (String) left;
//            if (lastAlias != null) {
//                realLeft = lastAlias + left;
//            }
                Object rightValue = right;
                EdmProperty edmProperty = (EdmProperty) dynamicViewHolder.edmPropertyMap.get(left);
                if (!(edmProperty.getType() instanceof EdmEnumType)) {
                    rightValue = Util.readPrimitiveValue(edmProperty, (String) right);
                }
                return EntityCondition.makeCondition(realLeft, comparisonOperator, rightValue);
            } else if (left instanceof EdmProperty) {
                EdmProperty leftEdmProperty = (EdmProperty) left;
                Object rightValue = right;
                if (!(leftEdmProperty.getType() instanceof EdmEnumType)) {
                    rightValue = Util.readPrimitiveValue((EdmProperty) left, (String) right);
                }
                String realFieldName = leftEdmProperty.getName();
                OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(leftEdmProperty.getName());
                if (UtilValidate.isEmpty(csdlProperty) && UtilValidate.isNotEmpty(csdlEntityType.getBaseType())) {
                    csdlProperty = (OfbizCsdlProperty) edmProvider.getEntityType(csdlEntityType.getBaseTypeFQN()).getProperty(leftEdmProperty.getName());
                }
                if (csdlProperty.getOfbizFieldName() != null) {
                    realFieldName = csdlProperty.getOfbizFieldName();
                }
                if (leftEdmProperty.getType() instanceof EdmDate && rightValue != null) {
                    //如果数据库是dateTime类型 但metadata是Date类型 需要做转换
                    ModelEntity modelEntity = delegator.getModelEntity(csdlEntityType.getOfbizEntity());
                    ModelField modelField = modelEntity.getField(realFieldName);
                    if ("date-time".equals(modelField.getType())) {
                        return convertDateToTime(realFieldName, (Date) rightValue, operator);
                    }
                }
                EntityComparisonOperator<?, ?> comparisonOperator = COMPARISONOPERATORMAP.get(operator);
                return EntityCondition.makeCondition(realFieldName, comparisonOperator, rightValue == null ? GenericEntity.NULL_FIELD : rightValue);
            } else if (left instanceof EntityFunction) {
                //function的处理
                String value = right.toString();
                if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                return EntityCondition.makeCondition(left, COMPARISONOPERATORMAP.get(operator), value);
            } else if (UtilValidate.isEmpty(left)) {
                //lambda后面再跟普通条件会来第二次 这个时候left=null,但right已经是完整的条件,直接返回
                return right;
            } else {
                throw new ODataApplicationException("Binary operation " + operator.name() + " is not implemented",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }

    /**
     * Called for each traversed {@link Unary} expression
     *
     * @param arg0 Operator kind
     * @param arg1 return value of sub tree
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitUnaryOperator(UnaryOperatorKind arg0, Object arg1) throws ExpressionVisitException, ODataApplicationException {
        EntityExpr entityExpr = (EntityExpr) arg1;
        EntityOperator<Object, Object, Object> operator = entityExpr.getOperator();
        //not contain、not startsWith、not endsWith
        if ("not".equals(arg0.toString()) && "LIKE".equals(operator.getCode())) {
            return EntityCondition.makeCondition(entityExpr.getLhs(), EntityOperator.NOT_LIKE, entityExpr.getRhs());
        }
        return null;
    }

    /**
     * Called for each traversed {@link Method} expression
     *
     * @param methodCall Method
     * @param parameters List of application return values created by visiting each method parameter
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occurred
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitMethodCall(MethodKind methodCall, List<Object> parameters) throws ExpressionVisitException, ODataApplicationException {
        if (methodCall == MethodKind.CONTAINS || methodCall == MethodKind.STARTSWITH || methodCall == MethodKind.ENDSWITH) {
            //contains、startWith、endWith 必须要两个参数
            final int containsParamCount = 2;
            if (parameters.size() >= containsParamCount) {
                EdmProperty edmProperty = parameters.get(0) instanceof EdmProperty ?
                        (EdmProperty) parameters.get(0) : (EdmProperty) dynamicViewHolder.edmPropertyMap.get(parameters.get(0));
                String valueParam1 = parameters.get(0) instanceof EdmProperty ? edmProperty.getName() : (String) parameters.get(0);
                String valueParam2;
                try {
                    valueParam2 = (String) Util.readPrimitiveValue(edmProperty, (String) parameters.get(1));
                } catch (ODataException e) {
                    throw new ODataApplicationException(e.getMessage(),
                            HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
                }
                valueParam2 = methodCall == MethodKind.CONTAINS ? "%" + valueParam2 + "%"
                        : methodCall == MethodKind.STARTSWITH ? valueParam2 + "%" : "%" + valueParam2;
                return EntityCondition.makeCondition(valueParam1, EntityOperator.LIKE, valueParam2);
            } else {
                throw new ODataApplicationException("Filter method needs two parameters of type Edm.String",
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }
        } else if (methodCall == MethodKind.LENGTH || methodCall == MethodKind.TOUPPER || methodCall == MethodKind.TOLOWER) {
            //length、toUpper、toLower
            EdmProperty edmProperty = parameters.get(0) instanceof EdmProperty ?
                    (EdmProperty) parameters.get(0) : (EdmProperty) dynamicViewHolder.edmPropertyMap.get(parameters.get(0));
            String propertyName = parameters.get(0) instanceof EdmProperty ? edmProperty.getName() : (String) parameters.get(0);
            EntityFunction<String> entityFunction = EntityFunction.UPPER_FIELD(propertyName);
            return methodCall == MethodKind.LENGTH ?
                    EntityFunction.LENGTH(entityFunction) : methodCall == MethodKind.TOUPPER ?
                    EntityFunction.UPPER(entityFunction) : EntityFunction.LOWER(entityFunction);
        } else if (methodCall == MethodKind.CONCAT) {
            if (parameters.size() < 2) {
                throw new ODataApplicationException("Filter method needs two parameters of type Edm.String",
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }
            Object param1 = parameters.get(0), param2 = parameters.get(1);
            String str1 = param1 instanceof EdmProperty ? convertProperty(((EdmProperty) param1).getName()) : param1.toString();
            String str2 = param2 instanceof EdmProperty ? convertProperty(((EdmProperty) param2).getName()) : param2.toString();
            return EntityCondition.makeConditionWhere("CONCAT(" + str1 + "," + str2 + ")");
        }
        return null;
    }

    /**
     * Called for each traversed lambda expression
     *
     * @param lambdaFunction "ALL" or "ANY"
     * @param lambdaVariable Variable name used lambda variable
     * @param expression     Lambda expression
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression)
            throws ExpressionVisitException, ODataApplicationException {
        return expression.accept(this);
    }

    /**
     * Called for each traversed {@link Literal} expression
     *
     * @param literal Literal
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
        // To keep this tutorial simple, our filter expression visitor supports only
        // Edm.Int32 and Edm.String
        // In real world scenarios it can be difficult to guess the type of an literal.
        // We can be sure, that the literal is a valid OData literal because the URI
        // Parser checks
        // the lexicographical structure

        // String literals start and end with an single quotation mark
        String literalAsString = literal.getText();

        // $filter Field ne null
        EdmType edmType = literal.getType();
        if (edmType == null) {
            return null;
        }
        if (edmType instanceof EdmPrimitiveType) {
            return literalAsString;
        } else {
            // Try to convert the literal into an Java Integer
            try {
                return Integer.parseInt(literalAsString);
            } catch (NumberFormatException e) {
                throw new ODataApplicationException("Only Edm.Int32 and Edm.String literals are implemented",
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
        }
    }

    /**
     * Called for each traversed {@link Member} expression
     *
     * @param member UriInfoResource object describing the whole path used to access an data value
     *               (this includes for example the usage of $root and $it inside the URI)
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
        final List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();

        // 例如：Products?$filter=ProductFeatureAppl/any(c:c/productFeatureId eq 'SIZE_2' or c/productFeatureId eq 'SIZE_6')
        // uriResourceParts[0] 是ProductFeatureAppl
        // uriResourceParts[1] 是UriResourceLambdaAnyImpl，其lambdaVariable是c
        // 然后会再次进入这个方法，uriResourceParts[0]是UriResourceLambdaVarImpl，也就是c
        // uriResourceParts[1] 是productFeatureId
        try {
            if (uriResourceParts.size() == 1 && uriResourceParts.get(0) instanceof UriResourcePrimitiveProperty) { // 这里就是简单单字段
                UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResourceParts.get(0);
                // this.lastAlias = null;
                EdmProperty edmProperty = uriResourceProperty.getProperty();
                OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(edmProperty.getName());
                //如果这个普通字段是RelAlias或者是Attribute 那么也需要使用dynamicView
                if (ofbizCsdlProperty != null) {
                    if (ofbizCsdlProperty.getRelAlias() != null ||
                            ofbizCsdlProperty.isAttribute() ||
                            ofbizCsdlProperty.isNumericAttribute() ||
                            ofbizCsdlProperty.isDateAttribute()) {
                        return visitMemberMultiParts(uriResourceParts);
                    }
                }
                if (edmProperty.getType() instanceof EdmPrimitiveType) {
                    return edmProperty;
                }
                return uriResourceProperty.getProperty().getName();
            } else {
                return visitMemberMultiParts(uriResourceParts); // 返回Object，直接返回

            }
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
    }


    private Object visitMemberMultiParts(List<UriResource> uriResourceParts)
            throws OfbizODataException, ExpressionVisitException, ODataApplicationException {
        if (uriResourceParts.get(uriResourceParts.size() - 1) instanceof UriResourceCount) {
            return uriResourceParts.toString();
        }
        if (dynamicViewHolder == null) { // 进入这个方法的，都是复杂查询，需要用到DynamicView
            dynamicViewHolder = new DynamicViewHolder(csdlEntityType, edmProvider, delegator, dispatcher, userLogin);
        }
        // 例如，MainProductAssoc/any(c:c/productIdTo eq 'WG-9943-B3')
        int uriResourcePartsSize = uriResourceParts.size();
        UriResource lastUriResourcePart = uriResourceParts.get(uriResourcePartsSize - 1);
        if (lastUriResourcePart instanceof UriResourceLambdaAny) {
            if (uriResourceParts.get(0) instanceof UriResourceNavigation) {
                lambdaUriResourceNavigation = (UriResourceNavigation) uriResourceParts.get(0);
            }
            UriResourceLambdaAny any = (UriResourceLambdaAny) lastUriResourcePart;
            List<String> resourceParts = new ArrayList<>();
            for (int i = 0; i < uriResourcePartsSize - 1; i++) {
                resourceParts.add(uriResourceParts.get(i).getSegmentValue());
            }
            dynamicViewHolder.addMultiParts(resourceParts, any.getLambdaVariable());
            String prevLastAlias = lastAlias;
            lastAlias = any.getLambdaVariable();
            Object lambdaResult = visitLambdaExpression("ANY", any.getLambdaVariable(), any.getExpression());
            EntityCondition lambdaCondition = (EntityCondition) lambdaResult;
            lastAlias = prevLastAlias; // lastAlias已经用于lambda处理完了，恢复先前的那个lastAlias
            return lambdaCondition;
        }
        UriResource firstUriResourcePart = uriResourceParts.get(0);
        if (firstUriResourcePart instanceof UriResourceLambdaVariable) {
            UriResourceLambdaVariable lambdaVariable = (UriResourceLambdaVariable) firstUriResourcePart;
            String variableName = lambdaVariable.getSegmentValue();
            UriResource resourceProperty = uriResourceParts.get(1);
            String propertyName = resourceProperty.getSegmentValue();
            EdmProperty property = ((UriResourcePrimitivePropertyImpl) resourceProperty).getProperty();
            if (lambdaUriResourceNavigation != null) {
                OfbizCsdlEntityType lambdaEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(lambdaUriResourceNavigation.getProperty().getType().getFullQualifiedName());
                OfbizCsdlProperty lambdaEntityTypeProperty = (OfbizCsdlProperty) lambdaEntityType.getProperty(propertyName);
                //可能lambda的查询是Attribute字段
                if (lambdaEntityTypeProperty.isAttribute() || lambdaEntityTypeProperty.isNumericAttribute() || lambdaEntityTypeProperty.isDateAttribute()) {
                    OfbizCsdlEntityType lastCsdlEntity = cloneCsdlEntityType(lambdaEntityType);
                    dynamicViewHolder.addPropertyAttr(lastCsdlEntity, propertyName);
                    return property;
                }
                //lambda查询relAlias字段
                if (lambdaEntityTypeProperty.getRelAlias() != null) {
                    OfbizCsdlEntityType lastCsdlEntity = cloneCsdlEntityType(lambdaEntityType);
                    dynamicViewHolder.addRelAlias(lastCsdlEntity, lambdaEntityTypeProperty.getRelAlias(), null);
                    List<String> relations = lambdaEntityTypeProperty.getRelAlias().getRelations();
                    String relPropertyName = dynamicViewHolder.addFilterProperty(relations.get(relations.size() - 1), lambdaEntityTypeProperty.getOfbizFieldName());
                    dynamicViewHolder.edmPropertyMap.put(relPropertyName, property);
                    return relPropertyName;
                }
            }
            String filterProperty = dynamicViewHolder.addFilterProperty(variableName, propertyName);
            dynamicViewHolder.edmPropertyMap.put(filterProperty, property);
            return filterProperty;
        }
        // 来自RelAlias或者Attribute的普通字段
        if (firstUriResourcePart instanceof UriResourcePrimitiveProperty) {
            UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResourceParts.get(0);
            EdmProperty edmProperty = uriResourceProperty.getProperty();
            OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(edmProperty.getName());
            //relAlias字段
            if (ofbizCsdlProperty.getRelAlias() != null) {
                String memberAliasName = dynamicViewHolder.addPropertyRelAlias(edmProperty.getName());
//                List<String> relations = ofbizCsdlProperty.getRelAlias().getRelations();
//                String relAlias = ListUtil.getLast(relations);
                //在dynamicView中 这个字段属于relations中最后一个对象
                String relPropertyName = dynamicViewHolder.addFilterProperty(memberAliasName, ofbizCsdlProperty.getOfbizFieldName());
                dynamicViewHolder.edmPropertyMap.put(relPropertyName, edmProperty);
                return relPropertyName;
            } else if (ofbizCsdlProperty.isAttribute() || ofbizCsdlProperty.isNumericAttribute() || ofbizCsdlProperty.isDateAttribute()) {
                dynamicViewHolder.addPropertyAttr(null, edmProperty.getName());
                return edmProperty;
            }
        }
        // 普通的多段式查询，例如/Contents?$filter=DataResource/dataResourceTypeId eq 'ELECTRONIC_TEXT'
        List<String> resourceParts = new ArrayList<>();
        for (int i = 0; i < uriResourcePartsSize - 1; i++) {
            resourceParts.add(uriResourceParts.get(i).getSegmentValue());
        }
        String prevLastAlias = lastAlias;
        lastAlias = dynamicViewHolder.addMultiParts(resourceParts, null);
        // 最后一段是PropertyName
        UriResource resourceProperty = uriResourceParts.get(uriResourcePartsSize - 1);
        String propertyName = resourceProperty.getSegmentValue();
        UriResourceNavigation resourceNavigetion = (UriResourceNavigation) uriResourceParts.get(uriResourcePartsSize - 2);
        OfbizCsdlEntityType navCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(resourceNavigetion.getType().getFullQualifiedName());
        OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) navCsdlEntityType.getProperty(propertyName);
        EdmProperty property = ((UriResourcePrimitivePropertyImpl) resourceProperty).getProperty();
        if (csdlProperty.isAttribute() || csdlProperty.isNumericAttribute() || csdlProperty.isDateAttribute()) {
            //多段式查询Attribute字段
            OfbizCsdlEntityType lastCsdlEntity = cloneCsdlEntityType(navCsdlEntityType);
            dynamicViewHolder.addPropertyAttr(lastCsdlEntity, propertyName);
            return property;
        } else if (csdlProperty.getRelAlias() != null) {
            //多段式查询RelAlias字段 拿到多段式的最后一个对象去addRelAlias
            OfbizCsdlEntityType lastCsdlEntity = cloneCsdlEntityType(navCsdlEntityType);
            OfbizCsdlProperty currProperty = (OfbizCsdlProperty) lastCsdlEntity.getProperty(propertyName);
            EntityTypeRelAlias relAlias = currProperty.getRelAlias();
            dynamicViewHolder.addRelAlias(lastCsdlEntity, relAlias, lastAlias);
            List<String> relations = relAlias.getRelations();
            String relPropertyName = dynamicViewHolder.addFilterProperty(relations.get(relations.size() - 1), currProperty.getOfbizFieldName());
            dynamicViewHolder.edmPropertyMap.put(relPropertyName, property);
            return relPropertyName;
        } else {
            //普通的多段式字段查询
            propertyName = csdlProperty.getOfbizFieldName();
            String filterProperty = dynamicViewHolder.addFilterProperty(lastAlias, propertyName);
            dynamicViewHolder.edmPropertyMap.put(filterProperty, property);
            return filterProperty;
        }
    }

    //只是new一个新的csdlEntity
    private OfbizCsdlEntityType cloneCsdlEntityType(OfbizCsdlEntityType ofbizCsdlEntityType) {
        OfbizCsdlEntityType newCsdlEntity = ofbizCsdlEntityType.clone();
        newCsdlEntity.setName(lastAlias);
        newCsdlEntity.setProperties(ofbizCsdlEntityType.getProperties());
        return newCsdlEntity;
    }

    /**
     * Called for each traversed {@link Alias} expression
     *
     * @param aliasName Name of the alias
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
        return null;
    }

    /**
     * Called for each traversed {@link TypeLiteral} expression
     *
     * @param type EdmType
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
        return null;
    }

    /**
     * Called for each traversed {@link LambdaRef}
     *
     * @param variableName Name of the used lambda variable
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
        return null;
    }

    /**
     * Called for each traversed {@link Enumeration} expression
     *
     * @param type       Type used in the URI before the enumeration values
     * @param enumValues List of enumeration values
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitEnum(EdmEnumType type, List<String> enumValues) throws ExpressionVisitException, ODataApplicationException {
        if (UtilValidate.isNotEmpty(enumValues)) {
            return enumValues.get(0);
        }
        throw new ODataApplicationException("Only com.dpbird.Bool and Enumeration entity was implemented",
                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
    }

    /**
     * Called for each traversed {@link Binary} expression
     *
     * @param operator Operator kind
     * @param left     Application return value of left sub tree
     * @param right    Application return value of right sub tree
     * @return Application return value of type T
     * @throws ExpressionVisitException  Thrown if an exception while traversing occured
     * @throws ODataApplicationException Thrown by the application
     */
    @Override
    public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, List<Object> right) throws ExpressionVisitException, ODataApplicationException {
        //可能是普通字段查询 也有可能是多段式或者lambda
        EdmProperty edmProperty = left instanceof EdmProperty ? (EdmProperty) left : (EdmProperty) dynamicViewHolder.edmPropertyMap.get(left.toString());
        String propertyName = left instanceof EdmProperty ? ((EdmProperty) left).getName() : left.toString();
        try {
            List<Object> primitiveValues = new ArrayList<>();
            for (Object rightValue : right) {
                primitiveValues.add(Util.readPrimitiveValue(edmProperty, (String) rightValue));
            }
            //创建Condition
            return EntityCondition.makeCondition(propertyName, COMPARISONOPERATORMAP.get(operator), primitiveValues);
        } catch (OfbizODataException e) {
            throw new ODataApplicationException(e.getMessage(),
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    public DynamicViewHolder getDynamicViewHolder() {
        return dynamicViewHolder;
    }

    /**
     * 将一个Date条件转换为DateTime的区间查询
     */
    private EntityCondition convertDateToTime(String propertyName, Date date, BinaryOperatorKind operator) {
        Timestamp timestamp = UtilDateTime.toTimestamp(date);
        Timestamp dayStart = UtilDateTime.getDayStart(timestamp);
        Timestamp dayEnd = UtilDateTime.getDayEnd(timestamp);
        //大于等于开始时间条件
        EntityCondition startCond = EntityCondition.makeCondition(propertyName, EntityOperator.GREATER_THAN_EQUAL_TO, dayStart);
        //大于等于结束时间条件
        EntityCondition endCond = EntityCondition.makeCondition(propertyName, EntityOperator.LESS_THAN_EQUAL_TO, dayEnd);
        if (operator.equals(BinaryOperatorKind.EQ)) {
            return EntityCondition.makeCondition(UtilMisc.toList(startCond, endCond));
        } else if (operator.equals(BinaryOperatorKind.GT)) {
            return EntityCondition.makeCondition(propertyName, EntityOperator.GREATER_THAN, dayEnd);
        } else if (operator.equals(BinaryOperatorKind.GE)) {
            EntityCondition gtCond = EntityCondition.makeCondition(propertyName, EntityOperator.GREATER_THAN, dayEnd);
            EntityCondition eqCond = EntityCondition.makeCondition(UtilMisc.toList(startCond, endCond));
            return EntityCondition.makeCondition(UtilMisc.toList(eqCond, gtCond), EntityOperator.OR);
        } else if (operator.equals(BinaryOperatorKind.LT)) {
            return EntityCondition.makeCondition(propertyName, EntityOperator.LESS_THAN, dayStart);
        } else if (operator.equals(BinaryOperatorKind.LE)) {
            EntityCondition ltCond = EntityCondition.makeCondition(propertyName, EntityOperator.LESS_THAN, dayStart);
            EntityCondition eqCond = EntityCondition.makeCondition(UtilMisc.toList(startCond, endCond));
            return EntityCondition.makeCondition(UtilMisc.toList(eqCond, ltCond), EntityOperator.OR);
        } else if (operator.equals(BinaryOperatorKind.NE)) {
            EntityCondition neStartCond = EntityCondition.makeCondition(propertyName, EntityOperator.LESS_THAN, dayStart);
            EntityCondition neEndCond = EntityCondition.makeCondition(propertyName, EntityOperator.GREATER_THAN_EQUAL_TO, dayEnd);
            return EntityCondition.makeCondition(UtilMisc.toList(neStartCond, neEndCond), EntityOperator.OR);
        }
        return null;
    }

    private static String convertProperty(String propertyName) {
        //将字段名称转为大写加下划线 符合数据库格式
        Matcher matcher = Pattern.compile("[A-Z]").matcher(propertyName);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "_" + matcher.group(0));
        }
        matcher.appendTail(sb);
        return sb.toString().toUpperCase();
    }

}
