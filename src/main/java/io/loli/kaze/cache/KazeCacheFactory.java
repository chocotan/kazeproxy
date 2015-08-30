package io.loli.kaze.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;
import javax.cache.spi.CachingProvider;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

public class KazeCacheFactory {
    private static final Logger logger = Logger
            .getLogger(KazeCacheFactory.class);
    private static Map<String, Cache<String, KazeCacheData>> cacheMap = new HashMap<>();

    public static Cache<String, KazeCacheData> getCache(String name) {
        if (cacheMap.get(name) == null) {
            Cache<String, KazeCacheData> cache = createCache(name);
            cacheMap.put(name, cache);
        }
        return cacheMap.get(name);
    }

    private static Cache<String, KazeCacheData> createCache(String name) {
        Properties prop = new Properties();
        try {
            prop.load(CacheFilter.class
                    .getResourceAsStream("/cache.properties"));
        } catch (IOException e) {
            logger.warn("Failed to load cache.properties, use blank prop instead");
        }
        String str = prop.getProperty("jcs.expirein");
        Integer expirein = 0;
        if (StringUtils.isBlank(str)) {
            try {
                expirein = Integer.parseInt(str);
            } catch (Exception e) {
            }
        }
        Configuration<String, KazeCacheData> config = null;
        if (expirein != 0) {
            config = new MutableConfiguration<String, KazeCacheData>()
                    .setTypes(String.class, KazeCacheData.class)
                    .setExpiryPolicyFactory(
                            AccessedExpiryPolicy.factoryOf(new Duration(
                                    TimeUnit.SECONDS, expirein)));

        } else {
            config = new MutableConfiguration<String, KazeCacheData>()
                    .setTypes(String.class, KazeCacheData.class);
        }
        CachingProvider cachingProvider = Caching.getCachingProvider();
        CacheManager cacheManager = cachingProvider.getCacheManager(
                cachingProvider.getDefaultURI(),
                cachingProvider.getDefaultClassLoader(), prop);
        Cache<String, KazeCacheData> cache = cacheManager.createCache(name,
                config);
        return cache;
    }
}
