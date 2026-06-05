package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.prometheus.client.Counter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;

public class GetMetricDataGetterTest {
  @Test
  public void testPartition() {
    List<Integer> originalList = List.copyOf(Collections.nCopies(28, 0));
    List<List<Integer>> partitions = GetMetricDataDataGetter.partitionByMaxSize(originalList, 40);
    for (List<Integer> p : partitions) {
      assertThat(p.size()).isLessThanOrEqualTo(40);
      assertThat(p.size()).isPositive();
    }
  }

  @Test
  public void partitionReturnsEmptyListForEmptyInput() {
    assertThat(GetMetricDataDataGetter.partitionByMaxSize(List.of(), 40)).isEmpty();
  }

  @Test
  public void partitionSplitsInputIntoMaxSizedPartitions() {
    List<Integer> originalList = List.copyOf(Collections.nCopies(85, 0));

    List<List<Integer>> partitions = GetMetricDataDataGetter.partitionByMaxSize(originalList, 40);

    assertThat(partitions).hasSize(3);
    assertThat(partitions).extracting(List::size).containsExactly(40, 40, 5);
  }

  @Test
  public void metricLabelsEncodeStatAndSortedDimensions() {
    String label =
        GetMetricDataDataGetter.MetricLabels.labelFor(
            "Average",
            List.of(
                Dimension.builder().name("InstanceId").value("i-123").build(),
                Dimension.builder().name("AutoScalingGroupName").value("asg").build()));

    assertThat(label).isEqualTo("Average/AutoScalingGroupName=asg,InstanceId=i-123");
  }

  @Test
  public void metricLabelsRejectLabelsWithoutStatSeparator() {
    assertThatThrownBy(() -> GetMetricDataDataGetter.MetricLabels.decode("Average"))
        .isInstanceOf(GetMetricDataDataGetter.MetricLabels.UnexpectedLabel.class)
        .hasMessage("Cannot decode label Average");
  }

  @Test
  public void metricRuleDataForMapsExtendedStatisticsAndSkipsEmptyResults() {
    CloudWatchClient client = mock(CloudWatchClient.class);
    when(client.getMetricData(any(GetMetricDataRequest.class)))
        .thenReturn(
            GetMetricDataResponse.builder()
                .metricDataResults(
                    MetricDataResult.builder()
                        .label("p99/InstanceId=i-123")
                        .timestamps(List.of(Instant.parse("2024-01-01T00:00:00Z")))
                        .values(List.of(99.0))
                        .build(),
                    MetricDataResult.builder()
                        .label("p95/InstanceId=i-123")
                        .timestamps(List.of())
                        .values(List.of(95.0))
                        .build(),
                    MetricDataResult.builder()
                        .label("p90/InstanceId=i-123")
                        .timestamps(List.of(Instant.parse("2024-01-01T00:00:00Z")))
                        .values(List.of())
                        .build())
                .build());
    MetricRule rule = new MetricRule();
    rule.awsNamespace = "AWS/EC2";
    rule.awsMetricName = "CPUUtilization";
    rule.awsExtendedStatistics = List.of("p99");
    rule.periodSeconds = 60;
    rule.rangeSeconds = 120;
    rule.delaySeconds = 30;
    Dimension dimension = Dimension.builder().name("InstanceId").value("i-123").build();

    DataGetter.MetricRuleData data =
        new GetMetricDataDataGetter(
                client,
                1_704_067_200_000L,
                rule,
                counter("get_metric_data_api_requests"),
                counter("get_metric_data_metrics_requested"),
                List.of(List.of(dimension)))
            .metricRuleDataFor(List.of(dimension));

    assertThat(data.timestamp).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    assertThat(data.extendedValues).containsOnly(Map.entry("p99", 99.0));
    assertThat(data.statisticValues).isEmpty();
  }

  private Counter counter(String name) {
    return Counter.build().name(name).help(name).labelNames("a", "b").create();
  }
}
