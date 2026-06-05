package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.prometheus.client.Counter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

class GetMetricStatisticsDataGetterTest {

  @Test
  void returnsNewestDatapointWithStatisticsAndExtendedStatistics() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    when(client.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder()
                        .timestamp(Instant.parse("2024-01-02T00:00:00Z"))
                        .unit(StandardUnit.COUNT)
                        .sum(1.0)
                        .sampleCount(2.0)
                        .minimum(3.0)
                        .maximum(4.0)
                        .average(5.0)
                        .extendedStatistics(Map.of("p99", 6.0))
                        .build(),
                    Datapoint.builder()
                        .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
                        .unit(StandardUnit.COUNT)
                        .sum(100.0)
                        .build())
                .build());

    DataGetter.MetricRuleData data = getter(client).metricRuleDataFor(List.of(dimension()));

    assertThat(data.timestamp).isEqualTo(Instant.parse("2024-01-02T00:00:00Z"));
    assertThat(data.unit).isEqualTo("Count");
    assertThat(data.statisticValues)
        .containsEntry(Statistic.SUM, 1.0)
        .containsEntry(Statistic.SAMPLE_COUNT, 2.0)
        .containsEntry(Statistic.MINIMUM, 3.0)
        .containsEntry(Statistic.MAXIMUM, 4.0)
        .containsEntry(Statistic.AVERAGE, 5.0);
    assertThat(data.extendedValues).containsEntry("p99", 6.0);
  }

  @Test
  void returnsNullWhenCloudWatchReturnsNoDatapoints() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    when(client.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenReturn(GetMetricStatisticsResponse.builder().datapoints(List.of()).build());

    assertThat(getter(client).metricRuleDataFor(List.of(dimension()))).isNull();
  }

  @Test
  void handlesDatapointWithoutExtendedStatistics() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    when(client.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder()
                        .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
                        .unit(StandardUnit.COUNT)
                        .average(7.0)
                        .build())
                .build());

    DataGetter.MetricRuleData data = getter(client).metricRuleDataFor(List.of(dimension()));

    assertThat(data.statisticValues).containsEntry(Statistic.AVERAGE, 7.0);
    assertThat(data.extendedValues).isEmpty();
  }

  private GetMetricStatisticsDataGetter getter(CloudWatchClient client) {
    MetricRule rule = new MetricRule();
    rule.awsNamespace = "AWS/EC2";
    rule.awsMetricName = "CPUUtilization";
    rule.awsStatistics = List.of(Statistic.SUM, Statistic.AVERAGE);
    rule.awsExtendedStatistics = List.of("p99");
    rule.periodSeconds = 60;
    rule.rangeSeconds = 120;
    rule.delaySeconds = 30;
    return new GetMetricStatisticsDataGetter(
        client, 1_704_156_600_000L, rule, counter("api_requests"), counter("metrics_requested"));
  }

  private Dimension dimension() {
    return Dimension.builder().name("InstanceId").value("i-123").build();
  }

  private Counter counter(String name) {
    return Counter.build()
        .name("get_metric_statistics_test_" + name)
        .help(name)
        .labelNames("a", "b")
        .create();
  }
}
