package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.prometheus.client.Counter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;

class DefaultDimensionSourceTest {

  @Test
  void usesSelectedDimensionValuesWithoutCallingCloudWatchWhenAllDimensionsAreKnown() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    MetricRule rule = metricRule();
    rule.awsDimensions = List.of("LoadBalancerName", "AvailabilityZone");
    rule.awsDimensionSelect =
        Map.of("LoadBalancerName", List.of("lb-a", "lb-b"), "AvailabilityZone", List.of("us-a"));

    DimensionSource.DimensionData data = source(client).getDimensions(rule, List.of());

    assertThat(data.getDimensions())
        .containsExactlyInAnyOrder(
            List.of(dimension("LoadBalancerName", "lb-a"), dimension("AvailabilityZone", "us-a")),
            List.of(dimension("LoadBalancerName", "lb-b"), dimension("AvailabilityZone", "us-a")));
    verify(client, never()).listMetrics(any(ListMetricsRequest.class));
  }

  @Test
  void returnsEmptyDimensionsWhenListMetricsReturnsNoMatchesAndWarningEnabled() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    when(client.listMetrics(any(ListMetricsRequest.class)))
        .thenReturn(ListMetricsResponse.builder().metrics(List.of()).build());
    MetricRule rule = metricRule();
    rule.awsDimensions = List.of("LoadBalancerName");
    rule.warnOnEmptyListDimensions = true;

    DimensionSource.DimensionData data = source(client).getDimensions(rule, List.of());

    assertThat(data.getDimensions()).isEmpty();
    verify(client).listMetrics(any(ListMetricsRequest.class));
  }

  private DefaultDimensionSource source(CloudWatchClient client) {
    return new DefaultDimensionSource(
        client,
        Counter.build()
            .name("default_dimension_source_test_cloudwatch_requests")
            .help("requests")
            .labelNames("action", "namespace")
            .create());
  }

  private MetricRule metricRule() {
    MetricRule rule = new MetricRule();
    rule.awsNamespace = "AWS/ELB";
    rule.awsMetricName = "RequestCount";
    rule.rangeSeconds = 60;
    return rule;
  }

  private Dimension dimension(String name, String value) {
    return Dimension.builder().name(name).value(value).build();
  }
}
