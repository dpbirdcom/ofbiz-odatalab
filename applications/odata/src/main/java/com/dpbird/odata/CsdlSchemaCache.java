package com.dpbird.odata;

import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;

public class CsdlSchemaCache extends AbstractCache<String, CsdlSchema> {

    protected CsdlSchemaCache(String delegatorName) {
		super(delegatorName, "csdlschema");
    }

	public CsdlSchema put(String edmWebApp, CsdlSchema csdlSchema) {
	    UtilCache<String, CsdlSchema> csdlSchemaCache = getOrCreateCache(edmWebApp);
	    return csdlSchemaCache.put(edmWebApp, csdlSchema);
	}
	
	public void remove(String edmWebApp) {
	    UtilCache.clearCache(getCacheName(edmWebApp));
	}
	
	public CsdlSchema get(String edmWebApp) {
	    UtilCache<String, CsdlSchema> csdlSchemaCache = getCache(edmWebApp);
	    if (csdlSchemaCache == null) return null;
	    return csdlSchemaCache.get(edmWebApp);
	}
}
