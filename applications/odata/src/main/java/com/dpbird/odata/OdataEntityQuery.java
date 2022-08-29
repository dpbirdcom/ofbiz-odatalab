package com.dpbird.odata;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.model.DynamicViewEntity;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OdataEntityQuery extends EntityQuery {
    private String entityName = null;
    private DynamicViewEntity dynamicViewEntity = null;
    private final Set<String> fieldsToSelect = new HashSet<>();

    /**
     * Construct an EntityQuery object for use against the specified Delegator
     *
     * @param delegator The delegator instance to use for the query
     */
    public OdataEntityQuery(Delegator delegator) {
        super(delegator);
    }

    public static OdataEntityQuery use(Delegator delegator) {
        return new OdataEntityQuery(delegator);
    }

    public List<GenericValue> queryList(final int skip, final int top) throws GenericEntityException {
        try (EntityListIterator genericValueEli = queryIterator()) {
            return Util.getSkipTopList(genericValueEli, skip, top);
        }
    }

    @Override
    public OdataEntityQuery from(String entityName) {
        super.from(entityName);
        this.entityName = entityName;
        return this;
    }

    @Override
    public OdataEntityQuery from(DynamicViewEntity dynamicViewEntity) {
        super.from(dynamicViewEntity);
        this.dynamicViewEntity = dynamicViewEntity;
        return this;
    }

    /**
     * Set the group field
     *
     * @param fieldNames - Group by this fields
     * @return this
     */
    public OdataEntityQuery groupBy(String... fieldNames) {
        initDynamicView();
        for (String fieldName : fieldNames) {
            dynamicViewEntity.addAlias(entityName, fieldName, fieldName, null, false, true, null);
            fieldsToSelect.add(fieldName);
        }
        super.from(dynamicViewEntity);
        super.select(fieldsToSelect);
        return this;
    }

    /**
     * Set the group field.
     *
     * @param fieldNames - Group by this fields
     * @return this
     */
    public OdataEntityQuery groupBy(List<String> fieldNames) {
        groupBy(fieldNames.toArray(new String[0]));
        return this;
    }

    /**
     * Using function queries
     *
     * @param fieldName The field name to evaluate
     * @param function  Function Type. must be: min, max, sum, avg, count or count-distinct
     * @param alias The alias of the field used to return data
     * @return this
     */
    public OdataEntityQuery function(String fieldName, String function, String alias) {
        initDynamicView();
        dynamicViewEntity.addAlias(entityName, alias, fieldName, null, false, false, function);
        fieldsToSelect.add(alias);
        super.from(dynamicViewEntity);
        super.select(fieldsToSelect);
        return this;
    }

    /**
     * Initializes the dynamic entity
     */
    private void initDynamicView() {
        if (dynamicViewEntity == null) {
            if (entityName == null) {
                throw new NullPointerException("This method requires an entity name. The from() method must be called before this");
            }
            dynamicViewEntity = new DynamicViewEntity();
            dynamicViewEntity.addMemberEntity(entityName, entityName);
            dynamicViewEntity.addAliasAll(entityName, null,null);
        }
    }

}
