package io.prometheus.cloudwatch;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class CachingDimensionSource implements DimensionSource {

  private static final int MAX_ENTRIES = 500;
  private final DimensionSource delegate;
  private final Cache<DimensionCacheKey, DimensionData> cache;

  CachingDimensionSource(DimensionSource delegate, Cache<DimensionCacheKey, DimensionData> cache) {
    this.delegate = delegate;
    this.cache = cache;
  }

  /**
   * Create a new DimensionSource that will cache the results from another {@link DimensionSource}
   *
   * @param source
   * @param config - config used to configure the expiry (ttl) for entries in the cache
   * @return a new CachingDimensionSource
   */
  static DimensionSource create(DimensionSource source, DimensionCacheConfig config) {
    Cache<DimensionCacheKey, DimensionData> cache =
        Caffeine.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfter(new DimensionExpiry(config.defaultExpiry, config.metricConfig))
            .build();

    return new CachingDimensionSource(source, cache);
  }

  @Override
  public DimensionData getDimensions(MetricRule rule, List<String> tagBasedResourceIds) {
    DimensionData cachedDimensions =
        this.cache.getIfPresent(new DimensionCacheKey(rule, tagBasedResourceIds));
    if (cachedDimensions != null) {
      return cachedDimensions;
    }
    DimensionData dimensions = delegate.getDimensions(rule, tagBasedResourceIds);
    this.cache.put(new DimensionCacheKey(rule, tagBasedResourceIds), dimensions);
    return dimensions;
  }

  static class DimensionExpiry implements Expiry<DimensionCacheKey, DimensionData> {

    private final Duration defaultExpiry;
    private final Map<MetricRule, Duration> durationMap;

    public DimensionExpiry(Duration defaultExpiry, List<MetricRule> expiryOverrides) {
      this.defaultExpiry = defaultExpiry;
      this.durationMap =
          expiryOverrides.stream()
              .collect(Collectors.toMap(Function.identity(), dcp -> dcp.listMetricsCacheTtl));
    }

    @Override
    public long expireAfterCreate(DimensionCacheKey key, DimensionData value, long currentTime) {
      return durationMap.getOrDefault(key.rule, this.defaultExpiry).toNanos();
    }

    @Override
    public long expireAfterUpdate(
        DimensionCacheKey key, DimensionData value, long currentTime, long currentDuration) {
      return currentDuration;
    }

    @Override
    public long expireAfterRead(
        DimensionCacheKey key, DimensionData value, long currentTime, long currentDuration) {
      return currentDuration;
    }
  }

  static class DimensionCacheConfig {
    final Duration defaultExpiry;
    final List<MetricRule> metricConfig = new ArrayList<>();

    DimensionCacheConfig(Duration defaultExpiry) {
      this.defaultExpiry = defaultExpiry;
    }

    /**
     * Add a MetricRule to be used to configure a custom TTL using the value from {@link
     * MetricRule#listMetricsCacheTtl} to override the default expiry
     *
     * @param metricRule
     * @return this
     */
    DimensionCacheConfig addOverride(MetricRule metricRule) {
      this.metricConfig.add(metricRule);
      return this;
    }
  }

  static class DimensionCacheKey {
    private final MetricRule rule;
    private final List<String> tagBasedResourceIds;

    DimensionCacheKey(MetricRule rule, List<String> tagBasedResourceIds) {
      this.rule = rule;
      this.tagBasedResourceIds = tagBasedResourceIds;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DimensionCacheKey that = (DimensionCacheKey) o;

      if (!Objects.equals(rule, that.rule)) return false;
      return Objects.equals(tagBasedResourceIds, that.tagBasedResourceIds);
    }

    @Override
    public int hashCode() {
      int result = rule != null ? rule.hashCode() : 0;
      result = 31 * result + (tagBasedResourceIds != null ? tagBasedResourceIds.hashCode() : 0);
      return result;
    }
  }
}
