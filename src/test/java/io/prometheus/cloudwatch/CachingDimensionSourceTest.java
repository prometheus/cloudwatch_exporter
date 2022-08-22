package io.prometheus.cloudwatch;

import static io.prometheus.cloudwatch.DimensionSource.DimensionData;
import static org.junit.Assert.assertEquals;

import io.prometheus.cloudwatch.CachingDimensionSource.DimensionCacheConfig;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

public class CachingDimensionSourceTest {

  @Test
  public void cachedFromDelegate() {
    DimensionCacheConfig config = new DimensionCacheConfig(Duration.ofSeconds(60));
    FakeDimensionSource source = new FakeDimensionSource();
    DimensionSource sut = CachingDimensionSource.create(source, config);

    sut.getDimensions(createMetricRule("AWS/Redshift", "WriteIOPS"), Collections.emptyList());
    sut.getDimensions(createMetricRule("AWS/Redshift", "WriteIOPS"), Collections.emptyList());
    DimensionData expected =
        sut.getDimensions(createMetricRule("AWS/Redshift", "WriteIOPS"), Collections.emptyList());

    Dimension dimension = Dimension.builder().name("AWS/Redshift").value("WriteIOPS").build();
    assertEquals(1, source.called);
    assertEquals(dimension, expected.getDimensions().get(0).get(0));
  }

  private MetricRule createMetricRule(String namespace, String name) {
    MetricRule metricRule = new MetricRule();
    metricRule.awsNamespace = namespace;
    metricRule.awsMetricName = name;
    return metricRule;
  }

  static class FakeDimensionSource implements DimensionSource {
    int called = 0;

    @Override
    public DimensionData getDimensions(MetricRule rule, List<String> tagBasedResourceIds) {
      called++;
      return new DimensionData(
          List.of(List.of(Dimension.builder().name("AWS/Redshift").value("WriteIOPS").build())));
    }
  }
}
