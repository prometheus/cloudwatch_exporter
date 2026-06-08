package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.prometheus.cloudwatch.CachingDimensionSource.DimensionCacheKey;
import io.prometheus.cloudwatch.CachingDimensionSource.DimensionExpiry;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CachingDimensionExpiryTest {

  private final DimensionSource.DimensionData emptyData =
      new DimensionSource.DimensionData(Collections.emptyList());

  @Test
  public void expireAfterCreateUsesDefaultWithEmptyOverrides() {
    Duration defaultExpiry = Duration.ofSeconds(35);
    List<MetricRule> expiryOverrides = Collections.emptyList();
    DimensionExpiry sut = new DimensionExpiry(defaultExpiry, expiryOverrides);

    long afterCreate =
        sut.expireAfterCreate(
            createDimensionCacheKey("AWS/Redshift", "WriteIOPS", 0),
            emptyData,
            Instant.now().toEpochMilli());

    assertThat(Duration.ofNanos(afterCreate).toSeconds()).isEqualTo(35);
  }

  @Test
  public void expireAfterCreateUsesMetricLevelOverride() {
    Duration defaultExpiry = Duration.ofSeconds(35);
    List<MetricRule> expiryOverrides = List.of(createMetricRule("AWS/S3", "BucketSizeBytes", 100));
    DimensionExpiry sut = new DimensionExpiry(defaultExpiry, expiryOverrides);

    long afterCreate =
        sut.expireAfterCreate(
            createDimensionCacheKey("AWS/S3", "BucketSizeBytes", 100),
            emptyData,
            Instant.now().toEpochMilli());

    assertThat(Duration.ofNanos(afterCreate).toSeconds()).isEqualTo(100);
  }

  @Test
  public void expireAfterCreateUsesDefaultIfNoMatchedOverride() {
    Duration defaultExpiry = Duration.ofSeconds(35);
    List<MetricRule> expiryOverrides = List.of(createMetricRule("AWS/S3", "BucketSizeBytes", 100));
    DimensionExpiry sut = new DimensionExpiry(defaultExpiry, expiryOverrides);

    long afterCreate =
        sut.expireAfterCreate(
            createDimensionCacheKey("AWS/Redshift", "WriteIOPS", 120),
            emptyData,
            Instant.now().toEpochMilli());

    assertThat(Duration.ofNanos(afterCreate).toSeconds()).isEqualTo(35);
  }

  @Test
  public void expireAfterUpdateUsesCurrentDuration() {
    Duration defaultExpiry = Duration.ofSeconds(35);
    List<MetricRule> expiryOverrides = Collections.emptyList();
    DimensionExpiry sut = new DimensionExpiry(defaultExpiry, expiryOverrides);

    long afterUpdate =
        sut.expireAfterUpdate(
            createDimensionCacheKey("AWS/Redshift", "WriteIOPS", 120),
            emptyData,
            Instant.now().toEpochMilli(),
            10_000_000);

    assertThat(afterUpdate).isEqualTo(10_000_000);
  }

  @Test
  public void expireAfterReadUsesCurrentDuration() {
    Duration defaultExpiry = Duration.ofSeconds(35);
    List<MetricRule> expiryOverrides = Collections.emptyList();
    DimensionExpiry sut = new DimensionExpiry(defaultExpiry, expiryOverrides);

    long afterRead =
        sut.expireAfterRead(
            createDimensionCacheKey("AWS/Redshift", "WriteIOPS", 100),
            emptyData,
            Instant.now().toEpochMilli(),
            20_000_000);
    assertThat(afterRead).isEqualTo(20_000_000);
  }

  @Test
  public void dimensionCacheKeyEqualsHandlesIdentityNullDifferentTypesAndFields() {
    DimensionCacheKey key = createDimensionCacheKey("AWS/S3", "BucketSizeBytes", 100);
    DimensionCacheKey same = createDimensionCacheKey("AWS/S3", "BucketSizeBytes", 100);
    DimensionCacheKey differentRule = createDimensionCacheKey("AWS/EC2", "CPUUtilization", 100);
    DimensionCacheKey differentTags =
        new DimensionCacheKey(
            createMetricRule("AWS/S3", "BucketSizeBytes", 100), List.of("bucket-a"));

    assertThat(key).isEqualTo(key);
    assertThat(key).isEqualTo(same);
    assertThat(key).isNotEqualTo(null);
    assertThat(key).isNotEqualTo("not a key");
    assertThat(key).isNotEqualTo(differentRule);
    assertThat(key).isNotEqualTo(differentTags);
    assertThat(key.hashCode()).isEqualTo(same.hashCode());
  }

  @Test
  public void dimensionCacheKeyHashCodeHandlesNullFields() {
    assertThat(new DimensionCacheKey(null, null).hashCode()).isZero();
  }

  private DimensionCacheKey createDimensionCacheKey(
      String namespace, String name, int ttlInSeconds) {
    return new DimensionCacheKey(
        createMetricRule(namespace, name, ttlInSeconds), Collections.emptyList());
  }

  private MetricRule createMetricRule(String namespace, String name, int ttlInSeconds) {
    MetricRule metricRule = new MetricRule();
    metricRule.awsNamespace = namespace;
    metricRule.awsMetricName = name;
    metricRule.listMetricsCacheTtl = Duration.ofSeconds(ttlInSeconds);
    return metricRule;
  }
}
