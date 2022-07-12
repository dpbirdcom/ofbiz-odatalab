package com.dpbird.odata;

import com.dpbird.odata.edm.OfbizCsdlEntityType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import java.util.Map;

public class EntityRef {
    private FullQualifiedName fullQualifiedName;
    private String action;
    private String draftEntity;
    private Map<String, Object> keyMap;

    public EntityRef(FullQualifiedName fullQualifiedName, Map<String, Object> keyMap, String draftEntity, String action) {
        this.fullQualifiedName = fullQualifiedName;
        this.keyMap = keyMap;
        this.draftEntity = draftEntity;
        this.action = action;
    }

    public FullQualifiedName getFullQualifiedName() {
        return fullQualifiedName;
    }

    public void setFullQualifiedName(FullQualifiedName fullQualifiedName) {
        this.fullQualifiedName = fullQualifiedName;
    }

    public String getEntityTypeName() {
        return fullQualifiedName.getName();
    }

    public String getFullQualifiedNameAsString() {
        return fullQualifiedName.getFullQualifiedNameAsString();
    }

    public Map<String, Object> getKeyMap() {
        return keyMap;
    }

    public void setKeyMap(Map<String, Object> keyMap) {
        this.keyMap = keyMap;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDraftEntity() {
        return draftEntity;
    }

    public void setDraftEntity(String draftEntity) {
        this.draftEntity = draftEntity;
    }

    public boolean equals(Object o) {
        boolean result = true;
        if (!(o instanceof EntityRef)) {
            return false;
        }
        EntityRef other = (EntityRef) o;
        if (!this.getKeyMap().equals(other.getKeyMap())) {
            result = false;
        }
        if (result && !this.getAction().equals(other.getAction())) {
            result = false;
        }
        if (result && !this.getFullQualifiedName().equals(other.getFullQualifiedName())) {
            result = false;
        }
        return result;
    }
}
