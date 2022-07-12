package com.dpbird.odata;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.entity.util.EntityQuery;

import java.util.List;

public class OdataEntityQuery extends EntityQuery {
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

}
