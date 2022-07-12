package com.dpbird.odata.edm;

import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.ModelRelation;

import java.util.List;
import java.util.Map;

public class EntityTypeRelAlias {
    private String name;
    private List<String> relations;
    private Map<String, EntityCondition> relationsCondition;
    private Map<String, Map<String, Object>> relationsFieldMap; // 这个Map是ralationCondition中EntityCondition的Map体现，目的是新建Property时候要用
    private Map<String, ModelRelation> relationsEntity; // 每个relation对应的ModelRelation

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRelations() {
        return relations;
    }

    public void setRelations(List<String> relations) {
        this.relations = relations;
    }

    public Map<String, EntityCondition> getRelationsCondition() {
        return relationsCondition;
    }

    public void setRalationsCondition(Map<String, EntityCondition> relationsCondition) {
        this.relationsCondition = relationsCondition;
    }

    public Map<String, Map<String, Object>> getRelationsFieldMap() {
        return relationsFieldMap;
    }

    public void setRelationsFieldMap(Map<String, Map<String, Object>> relationsFieldMap) {
        this.relationsFieldMap = relationsFieldMap;
    }

    public void setRelationsCondition(Map<String, EntityCondition> relationsCondition) {
        this.relationsCondition = relationsCondition;
    }

    public Map<String, ModelRelation> getRelationsEntity() {
        return relationsEntity;
    }

    public void setRelationsEntity(Map<String, ModelRelation> relationsEntity) {
        this.relationsEntity = relationsEntity;
    }
}
