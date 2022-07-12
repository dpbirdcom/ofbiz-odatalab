package com.dpbird.odata;
import org.apache.ofbiz.base.util.cache.UtilCache;

public abstract class AbstractCache<K, V> {
    protected final String delegatorName, id, cacheNamePrefix;

    protected AbstractCache(String delegatorName, String id) {
        this.delegatorName = delegatorName;
        this.id = id;
        this.cacheNamePrefix = "edmcache.".concat(id).concat(".").concat(delegatorName).concat(".");
    }

    public String getDelegatorName() {
        return this.delegatorName;
    }

    public void remove(String edmWebApp) {
        UtilCache.clearCache(getCacheName(edmWebApp));
    }

    public void clear() {
        UtilCache.clearCachesThatStartWith(getCacheNamePrefix());
    }

    public String getCacheNamePrefix() {
        return cacheNamePrefix;
    }

    public String[] getCacheNamePrefixes() {
        return new String[] {
            "edmcache." + id + ".${tenant-id}.",
            cacheNamePrefix
        };
    }

    public String getCacheName(String edmWebApp) {
        return getCacheNamePrefix() + edmWebApp;
    }

    public String[] getCacheNames(String edmWebApp) {
        String[] prefixes = getCacheNamePrefixes();
        String[] names = new String[prefixes.length * 2];
        for (int i = 0; i < prefixes.length; i++) {
            names[i] = prefixes[i] + "${tenant-id}";
        }
        for (int i = prefixes.length, j = 0; j < prefixes.length; i++, j++) {
            names[i] = prefixes[j] + edmWebApp;
        }
        return names;
    }

    protected UtilCache<K, V> getCache(String edmWebApp) {
        return UtilCache.findCache(getCacheName(edmWebApp));
    }

    protected UtilCache<K, V> getOrCreateCache(String edmWebApp) {
        String name = getCacheName(edmWebApp);
        return UtilCache.getOrCreateUtilCache(name, 0, 0, 0, true, getCacheNames(edmWebApp));
    }
}
