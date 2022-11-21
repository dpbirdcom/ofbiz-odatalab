package com.dpbird.odata;

import com.dpbird.odata.edm.EntityTypeRelAlias;
import com.dpbird.odata.edm.OfbizCsdlEntityType;
import com.dpbird.odata.edm.OfbizCsdlNavigationProperty;
import com.dpbird.odata.edm.OfbizCsdlProperty;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelRelation;
import org.apache.ofbiz.entity.model.ModelViewEntity;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceLambdaVariable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DynamicViewHolder {
    private OfbizCsdlEntityType csdlEntityType;
    private OfbizAppEdmProvider edmProvider;
    private Delegator delegator;
    private LocalDispatcher dispatcher;
    private GenericValue userLogin;
    private DynamicViewEntity dynamicViewEntity = new DynamicViewEntity();
    private EntityCondition entityCondition = null;
    private List<String> orderByFields = null;
    private Map<String,String> lambdaName = new HashMap<>();

    //这个map的作用是存RelAlias中的condition字符串的
    private final Map<String,String> relAliasConditionMap = new HashMap<>();
    public DynamicViewHolder(OfbizCsdlEntityType csdlEntityType, OfbizAppEdmProvider edmProvider,
                             Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin) {
        this.csdlEntityType = csdlEntityType;
        this.edmProvider = edmProvider;
        this.delegator = delegator;
        this.dispatcher = dispatcher;
        this.userLogin = userLogin;
        //mainEntity、groupBy
        dynamicViewEntity.addMemberEntity(csdlEntityType.getName(), csdlEntityType.getOfbizEntity());
        dynamicViewEntity.addAliasAll(csdlEntityType.getName(), null, null, csdlEntityType.isGroupBy());
    }

    /**
     * 这个map用来储存一些Alias字段的EdmProperty
     */
    public Map<String, Object> edmPropertyMap = new HashMap<>();


    public EntityCondition getEntityCondition() {
        return entityCondition;
    }

    public DynamicViewEntity getDynamicViewEntity() {
        return dynamicViewEntity;
    }

    public void addNavigationRelAlias(String navigationName) {
        OfbizCsdlNavigationProperty csdlNavigationProperty = (OfbizCsdlNavigationProperty) csdlEntityType.getNavigationProperty(navigationName);
        EntityTypeRelAlias relAlias = csdlNavigationProperty.getRelAlias();
        addRelAlias(null, relAlias);
    }

    public void addPropertyRelAlias(String propertyName) {
        OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(propertyName);
        EntityTypeRelAlias relAlias = csdlProperty.getRelAlias();
        if (relAlias != null) {
            addRelAlias(null, relAlias);
        }
    }


    public String addLambdaMultiParts(List<UriResource> resourceParts) throws OfbizODataException {
        UriResourceLambdaVariable lambdaVariable = (UriResourceLambdaVariable) resourceParts.get(0);
        EdmEntityType lambdaEntityType = (EdmEntityType) lambdaVariable.getType();
        OfbizCsdlEntityType currentCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(lambdaEntityType.getFullQualifiedName());

        String returnEntityAlias = null;
        for (int i = 1; i < resourceParts.size() - 1; i++) {
            String resourcePart = resourceParts.get(i).getSegmentValue();
            OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty) currentCsdlEntityType.getNavigationProperty(resourcePart);
            String relEntityOfbizName = navigationProperty.getTypeFQN().getName();

            //添加当前的ViewLink
            EntityTypeRelAlias relAlias = navigationProperty.getRelAlias();
            EntityCondition relCondition = relAlias.getRelationsCondition().get(resourcePart);
            if (relCondition != null) {
                //如果有条件，这些条件的字段也要加到Alias中
                ModelViewEntity.ViewEntityCondition viewEntityCondition = new ModelViewEntity.ViewEntityCondition(dynamicViewEntity.makeModelViewEntity(delegator), null, false, false, null, resourcePart, null, relCondition);
                if (!hasMemberEntity(relEntityOfbizName, relEntityOfbizName)) {
                    dynamicViewEntity.addMemberEntity(relEntityOfbizName, relEntityOfbizName);
                    ModelViewEntity.ViewConditionList conditionList = (ModelViewEntity.ViewConditionList) viewEntityCondition.whereCondition;
                    for (ModelViewEntity.ViewCondition viewCondition : conditionList.conditionList) {
                        ModelViewEntity.ViewConditionExpr expr = (ModelViewEntity.ViewConditionExpr) viewCondition;
                        dynamicViewEntity.addAlias(relEntityOfbizName, expr.fieldName, expr.fieldName, null, null, false, null);
                    }
                }
            }
            //处理 RelAlias
            returnEntityAlias = addRelAlias(null, relAlias);
            currentCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navigationProperty.getTypeFQN());
        }
        return returnEntityAlias;
    }

    /**
     * 添加Attribute对象
     *
     * @param attrCsdlEntity 如果不为空就是多段式的Attr字段查询，否则就是普通的Attr字段查询
     * @param propertyName Attribute字段的名称
     */
    public void addPropertyAttr(OfbizCsdlEntityType attrCsdlEntity, String propertyName) {
        if (attrCsdlEntity == null) {
            attrCsdlEntity = csdlEntityType;
        }
        OfbizCsdlProperty ofbizCsdlProperty = (OfbizCsdlProperty) attrCsdlEntity.getProperty(propertyName);
        String attrEntityName = ofbizCsdlProperty.isAttribute() ? attrCsdlEntity.getAttrEntityName() :
                ofbizCsdlProperty.isNumericAttribute() ? attrCsdlEntity.getAttrNumericEntityName() : attrCsdlEntity.getAttrDateEntityName();
        //add memberEntity
        String attrEntityAlias = propertyName + attrEntityName;
        if (!hasMemberEntity(attrEntityAlias, attrEntityName)) {
            //一个对象会有许多的attr字段，这个member对象的别名就使用字段名+AttrEntityName
            dynamicViewEntity.addMemberEntity(attrEntityAlias, attrEntityName);
        }

        //add alias 其实要对比的字段是attrValue
        if (!hasAliases(attrEntityAlias, "attrValue")) {
            dynamicViewEntity.addAlias(attrEntityAlias, propertyName, "attrValue", null, null, false, null);
        }

        //add viewLink
        if (!hasViewLink(attrCsdlEntity.getName(), attrEntityAlias)) {
            ModelEntity modelEntity = delegator.getModelEntity(attrCsdlEntity.getOfbizEntity());
            ModelRelation relation = modelEntity.getRelation(attrEntityName);
            ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
            EntityExpr entityExpr = EntityCondition.makeCondition("attrName", propertyName);
            ModelViewEntity.ViewEntityCondition viewEntityCondition =
                    new ModelViewEntity.ViewEntityCondition(modelViewEntity, null, false, false, null, attrEntityAlias, null, entityExpr);
            ModelViewEntity.ModelViewLink modelViewLink = new ModelViewEntity.ModelViewLink(attrCsdlEntity.getName(), attrEntityAlias, true, viewEntityCondition, relation.getKeyMaps());
            dynamicViewEntity.addViewLink(modelViewLink);
        }
    }

    /**
     * @param firstCsdlEntityType 为空就是直接查询主对象的RelAlias字段, 不为空就是多段式的最后一个对象
     * @param relAlias relAlias
     * @return 返回最后一个RelAlias的name
     */
    public String addRelAlias(OfbizCsdlEntityType firstCsdlEntityType, EntityTypeRelAlias relAlias) {
        if (firstCsdlEntityType == null){
            firstCsdlEntityType = csdlEntityType;
        }
        //每一个Entity和ViewLink都要加入到dynamicViewEntity中
        List<String> relations = relAlias.getRelations();
        Map<String, ModelRelation> relationsEntity = relAlias.getRelationsEntity();
        Map<String, EntityCondition> relationsCondition = relAlias.getRelationsCondition();
        // relations的名字或许不是实体名称
        // FixedAsset -> InstanceOfProduct -> PrimaryCategory
        String lastRalAlias = null;
        for (int i = 0; i < relations.size(); i++) {
            //add MemberEntity
            String relation = relations.get(i);
            ModelRelation modelRelation = relationsEntity.get(relation);
            String entityAlias = modelRelation.getModelEntity().getEntityName();
            //也有可能是lambda变量来查询relAlias字段...
            if (lambdaName.get(entityAlias) != null){
                entityAlias = lambdaName.get(entityAlias);
            }
            //加前缀 主对象除外，主对象也可能取了别名,所以取entityType的name
            entityAlias = lastRalAlias != null ? lastRalAlias :
                    entityAlias.equals(getMainEntityAlias()) ? firstCsdlEntityType.getName() : entityAlias;
            String relEntityAlias = modelRelation.getCombinedName();
            String relEntityName = modelRelation.getRelEntityName();
            //最后一个，可能是lambda
            if (i == relations.size() - 1 && lambdaName.get(relAlias.getName()) != null) {
                relEntityAlias = lambdaName.get(relAlias.getName());
            }
            EntityCondition relCondition = relationsCondition.get(relation);
            if (hasMemberEntity(relEntityAlias, relEntityName)) {
                //如果已经存在这个MemberEntity 但是当前的跟已经存在的condition不一样 那么修改别名再添加到dynamicView
                String navCondition = relAliasConditionMap.get(entityAlias + "_" + relEntityAlias);
                if ((navCondition == null && relCondition != null) || (relCondition != null && !navCondition.equals(relCondition.toString()))) {
                    relEntityAlias = relAlias.getName() + relEntityAlias;
                    dynamicViewEntity.addMemberEntity(relEntityAlias, relEntityName);
                }
            } else {
                dynamicViewEntity.addMemberEntity(relEntityAlias, relEntityName);
            }
            //add Alias
            if (relCondition != null) {
                EntityConditionList<EntityCondition> entityConditionList = (EntityConditionList<EntityCondition>) relCondition;
                Iterator<EntityCondition> conditionIterator = entityConditionList.getConditionIterator();
                while (conditionIterator.hasNext()) {
                    EntityExpr expr = (EntityExpr) conditionIterator.next();
                    String fieldName = expr.getLhs().toString();
                    if (!hasAliases(relEntityAlias, expr.getLhs().toString())) {
                        dynamicViewEntity.addAlias(relEntityAlias, fieldName, fieldName, null, null, false, null);
                    }
                }
            }
            //add ViewLink
            ModelViewEntity modelViewEntity = dynamicViewEntity.makeModelViewEntity(delegator);
            if (!hasViewLink(entityAlias, relEntityAlias)) {
                ModelViewEntity.ViewEntityCondition viewEntityCondition = null;
                if (relCondition != null) {
                    relAliasConditionMap.put(entityAlias + "_" +relEntityAlias, relCondition.toString());
                    viewEntityCondition = new ModelViewEntity.ViewEntityCondition(modelViewEntity, null, false, false, null, relEntityAlias, null, relCondition);
                }
                ModelViewEntity.ModelViewLink modelViewLink = new ModelViewEntity.ModelViewLink(entityAlias, relEntityAlias, true, viewEntityCondition, modelRelation.getKeyMaps());
                dynamicViewEntity.addViewLink(modelViewLink);
            }
            lastRalAlias = relEntityAlias;
        }
        return lastRalAlias;
    }

    // alias可能为null，如果alias不为null，那传入的可能是lambda表达式的any之前的部分
    // 如果alias为null，那传入的可能是普通的非collection的navigation路径
    // 返回：添加的对后一个entity对应的alias
    public String addMultiParts(List<String> resourceParts, String alias) throws OfbizODataException {
        String returnEntityAlias = null;
        if (alias != null){
            //保存lambda变量名
            lambdaName.put(resourceParts.get(resourceParts.size() - 1), alias);
        }
        OfbizCsdlEntityType currentCsdlEntityType = null;
        //这个for用来处理url中的多段式
        for (int i = 0; i < resourceParts.size(); i++) {
            if (i == 0) {
                //第一个是主对象
                currentCsdlEntityType = csdlEntityType;
            }
            String resourcePart = resourceParts.get(i);
            OfbizCsdlNavigationProperty navigationProperty = (OfbizCsdlNavigationProperty) currentCsdlEntityType.getNavigationProperty(resourceParts.get(i));
            String entityAlias = alias != null ? alias : resourcePart;
            String relEntityOfbizName = navigationProperty.getTypeFQN().getName();
            returnEntityAlias = entityAlias;

            //添加当前的ViewLink
            EntityTypeRelAlias relAlias = navigationProperty.getRelAlias();
            ModelViewEntity.ViewEntityCondition viewEntityCondition = null;
            EntityCondition relCondition = relAlias.getRelationsCondition().get(resourcePart);
            if (relCondition != null) {
                //如果有条件，这些条件的字段也要加到Alias中
                viewEntityCondition = new ModelViewEntity.ViewEntityCondition(dynamicViewEntity.makeModelViewEntity(delegator), null, false, false, null, entityAlias, null, relCondition);
                if (!hasMemberEntity(relEntityOfbizName, relEntityOfbizName)) {
                    dynamicViewEntity.addMemberEntity(relEntityOfbizName, relEntityOfbizName);
                    ModelViewEntity.ViewConditionList conditionList = (ModelViewEntity.ViewConditionList) viewEntityCondition.whereCondition;
                    for (ModelViewEntity.ViewCondition viewCondition : conditionList.conditionList) {
                        ModelViewEntity.ViewConditionExpr expr = (ModelViewEntity.ViewConditionExpr) viewCondition;
                        dynamicViewEntity.addAlias(relEntityOfbizName, expr.fieldName, expr.fieldName, null, null, false, null);
                    }
                }
            }
            //处理 RelAlias
            returnEntityAlias = addRelAlias(null, relAlias);
            currentCsdlEntityType = (OfbizCsdlEntityType) edmProvider.getEntityType(navigationProperty.getTypeFQN());
        }
        return returnEntityAlias;
    }

    // 返回的是最终dynamicView中这个字段的名称
    // alias如果为null，代表添加的propertyName是主对象的property
    // propertyName可能是个RelAlias字段
    public String addFilterProperty(String alias, String propertyName) {
        //避免重复添加,同一个字段很多个or的情况
        String viewProperName = alias == null ? propertyName : alias + propertyName;
        if (!hasAliases(alias, propertyName)) {
            dynamicViewEntity.addAlias(alias, viewProperName, propertyName, null, null, false, null);
        }
        return viewProperName;
    }

    //返回这个OrderBy的字段
    public String addOrderByProperty(String alias, String propertyName) {
        //RelAlias字段或者Attribute字段 OrderBy
        if (alias == null) {
            OfbizCsdlProperty csdlProperty = (OfbizCsdlProperty) csdlEntityType.getProperty(propertyName);
            if (csdlProperty.getRelAlias() != null) {
                //RelAlias字段
                EntityTypeRelAlias relAlias = csdlProperty.getRelAlias();
                addRelAlias(null, relAlias);
                List<String> relations = relAlias.getRelations();
                //这个字段属于Relations最后一个Entity
                String lastEntityAlias = relations.get(relations.size() - 1);
                if (!hasAliases(lastEntityAlias, propertyName)) {
                    dynamicViewEntity.addAlias(lastEntityAlias, propertyName, csdlProperty.getOfbizFieldName(), null, null, false, null);
                }
                return propertyName;
            } else {
                //Attribute字段
                addPropertyAttr(null, propertyName);
                return propertyName;
            }
        } else {
            //多段式 OrderBy
            String propertyAlias = alias + propertyName;
            if (!hasAliases(alias, propertyName)) {
                dynamicViewEntity.addAlias(alias, propertyAlias, propertyName, null, null, false, null);
            }
            return propertyAlias;
        }
    }

    public String getMainEntityAlias() {
        // 对于主对象的alias，entityName就是alias的名字
        return csdlEntityType.getOfbizEntity();
    }

    //是否已经存在这个memberEntity
    public boolean hasMemberEntity(String entityAlias, String EntityName) {
        Map<String, ModelViewEntity.ModelMemberEntity> memberEntities = (Map<String, ModelViewEntity.ModelMemberEntity>) getViewEntityField("memberModelMemberEntities");
        for (Map.Entry<String, ModelViewEntity.ModelMemberEntity> stringModelMemberEntityEntry : memberEntities.entrySet()) {
            if (stringModelMemberEntityEntry.getValue().entityAlias.equals(entityAlias) && stringModelMemberEntityEntry.getValue().entityName.equals(EntityName)) {
                return true;
            }
        }
        return false;
    }

    //是否已经存在这个alias
    private boolean hasAliases(String entityAlias, String field) {
        List<ModelViewEntity.ModelAlias> aliases = (List<ModelViewEntity.ModelAlias>) getViewEntityField("aliases");
        return aliases != null && aliases.stream().anyMatch(e -> e.entityAlias.equals(entityAlias) && e.field.equals(field));
    }

    //是否已经存在这个viewLink
    public boolean hasViewLink(String entityAlias, String relEntityAlias) {
        List<ModelViewEntity.ModelViewLink> viewLinks = (List<ModelViewEntity.ModelViewLink>) getViewEntityField("viewLinks");
        return viewLinks != null && viewLinks.stream().anyMatch(e -> e.entityAlias.equals(entityAlias) && e.relEntityAlias.equals(relEntityAlias));
    }

    //获取dynamicViewEntity中的属性
    private Object getViewEntityField(String fieldName) {
        try {
            Field declaredField = dynamicViewEntity.getClass().getDeclaredField(fieldName);
            declaredField.setAccessible(true);
            return declaredField.get(dynamicViewEntity);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
