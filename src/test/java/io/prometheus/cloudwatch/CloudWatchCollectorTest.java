package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.cloudwatch.RequestsMatchers.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesRequest;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesResponse;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.ResourceTagMapping;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.Tag;

public class CloudWatchCollectorTest {
  CloudWatchClient cloudWatchClient;
  ResourceGroupsTaggingApiClient taggingClient;
  CollectorRegistry registry;

  @BeforeEach
  public void setUp() {
    cloudWatchClient = Mockito.mock(CloudWatchClient.class);
    taggingClient = Mockito.mock(ResourceGroupsTaggingApiClient.class);
    registry = new CollectorRegistry();
  }

  @Test
  public void testMetricPeriod() {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  period_seconds: 100\n  range_seconds: 200\n  delay_seconds: 300",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) any()))
        .thenReturn(GetMetricStatisticsResponse.builder().build());

    registry.getSampleValue(
        "aws_elb_request_count_average",
        new String[] {"job", "instance"},
        new String[] {"aws_elb", ""});

    Mockito.verify(cloudWatchClient)
        .getMetricStatistics(
            argThat(
                new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB").MetricName("RequestCount").Period(100)));
  }

  @Test
  public void testMetricPeriodUsingGetMetricData() {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  period_seconds: 100\n  range_seconds: 200\n  delay_seconds: 300\n  use_get_metric_data: true\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) any()))
        .thenReturn(GetMetricStatisticsResponse.builder().build());

    Mockito.when(cloudWatchClient.getMetricData((GetMetricDataRequest) any()))
        .thenReturn(GetMetricDataResponse.builder().build());
    registry.getSampleValue(
        "aws_elb_request_count_average",
        new String[] {"job", "instance"},
        new String[] {"aws_elb", ""});

    Mockito.verify(cloudWatchClient, never())
        .getMetricStatistics(isA(GetMetricStatisticsRequest.class));
    Mockito.verify(cloudWatchClient)
        .getMetricData(
            argThat(
                new GetMetricDataRequestMatcher()
                    .Query(
                        new MetricDataQueryMatcher()
                            .MetricStat(
                                new MetricStatMatcher()
                                    .Period(100)
                                        .metric(
                                            new MetricMatcher()
                                                .Namespace("AWS/ELB")
                                                    .MetricName("RequestCount"))))));
  }

  @Test
  public void testDefaultPeriod() {
    new CloudWatchCollector(
            "---\nregion: reg\nperiod_seconds: 100\nrange_seconds: 200\ndelay_seconds: 300\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) any()))
        .thenReturn(GetMetricStatisticsResponse.builder().build());

    registry.getSampleValue(
        "aws_elb_request_count_average",
        new String[] {"job", "instance"},
        new String[] {"aws_elb", ""});

    Mockito.verify(cloudWatchClient)
        .getMetricStatistics(
            (GetMetricStatisticsRequest)
                argThat(
                    new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/ELB").MetricName("RequestCount").Period(100)));
  }

  @Test
  public void testAllStatistics() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder()
                        .timestamp(new Date().toInstant())
                        .average(1.0)
                        .maximum(2.0)
                        .minimum(3.0)
                        .sampleCount(4.0)
                        .sum(5.0)
                        .build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_maximum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_minimum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_sample_count",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(4.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_sum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(5.0, within(.01));
  }

  @Test
  public void testAllStatisticsUsingGetMetricData() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  use_get_metric_data: true\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);
    List<Instant> timestamps = List.of(new Date().toInstant());
    MetricMatcher metricMatcher =
        new MetricMatcher().Namespace("AWS/ELB").MetricName("RequestCount");

    Mockito.when(
            cloudWatchClient.getMetricData(
                (GetMetricDataRequest)
                    argThat(
                        new GetMetricDataRequestMatcher()
                            .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Average").metric(metricMatcher)))
                                .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Maximum").metric(metricMatcher)))
                                .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Minimum").metric(metricMatcher)))
                                .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("SampleCount").metric(metricMatcher)))
                                .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Sum").metric(metricMatcher))))))
        .thenReturn(
            GetMetricDataResponse.builder()
                .metricDataResults(
                    List.of(
                        MetricDataResult.builder()
                            .label("Average/")
                            .values(List.of(Double.valueOf(1.0)))
                            .timestamps(timestamps)
                            .build(),
                        MetricDataResult.builder()
                            .label("Maximum/")
                            .values(List.of(Double.valueOf(2.0)))
                            .timestamps(timestamps)
                            .build(),
                        MetricDataResult.builder()
                            .label("Minimum/")
                            .values(List.of(Double.valueOf(3.0)))
                            .timestamps(timestamps)
                            .build(),
                        MetricDataResult.builder()
                            .label("SampleCount/")
                            .values(List.of(Double.valueOf(4.0)))
                            .timestamps(timestamps)
                            .build(),
                        MetricDataResult.builder()
                            .label("Sum/")
                            .values(List.of(Double.valueOf(5.0)))
                            .timestamps(timestamps)
                            .build()))
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_maximum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_minimum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_sample_count",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(4.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_sum",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(5.0, within(.01));
  }

  @Test
  public void testCloudwatchTimestamps() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  set_timestamp: true\n- aws_namespace: AWS/ELB\n  aws_metric_name: HTTPCode_Backend_2XX\n  set_timestamp: false",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Date timestamp = new Date();
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(timestamp.toInstant()).average(1.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB").MetricName("HTTPCode_Backend_2XX"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(timestamp.toInstant()).average(1.0).build())
                .build());

    assertMetricTimestampEquals(registry, "aws_elb_request_count_average", timestamp.getTime());
    assertMetricTimestampEquals(registry, "aws_elb_httpcode_backend_2_xx_average", null);
  }

  void assertMetricTimestampEquals(
      CollectorRegistry registry, String name, Long expectedTimestamp) {
    Enumeration<Collector.MetricFamilySamples> metricFamilySamplesEnumeration =
        registry.metricFamilySamples();
    Set<String> metricNames = new HashSet<String>();
    while (metricFamilySamplesEnumeration.hasMoreElements()) {
      Collector.MetricFamilySamples samples = metricFamilySamplesEnumeration.nextElement();
      for (Collector.MetricFamilySamples.Sample s : samples.samples) {
        metricNames.add(s.name);
        if (s.name.equals(name)) {
          assertThat((Long) s.timestampMs).isEqualTo(expectedTimestamp);
          return;
        }
      }
    }
    fail(String.format("Metric %s not found in registry. Metrics found: %s", name, metricNames));
  }

  @Test
  public void testUsesNewestDatapoint() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date(1).toInstant()).average(1.0).build(),
                    Datapoint.builder().timestamp(new Date(3).toInstant()).average(3.0).build(),
                    Datapoint.builder().timestamp(new Date(2).toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(3.0, within(.01));
  }

  @Test
  public void testDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build(),
                            Dimension.builder()
                                .name("ThisExtraDimensionIsIgnored")
                                .value("dummy")
                                .build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("b").build(),
                            Dimension.builder().name("LoadBalancerName").value("myOtherLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancerName", "myOtherLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "b", "myOtherLB"}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {
                  "job",
                  "instance",
                  "availability_zone",
                  "load_balancer_name",
                  "this_extra_dimension_is_ignored"
                },
                new String[] {"aws_elb", "", "a", "myLB", "dummy"}))
        .isNull();
  }

  @Test
  public void testDimensionSelect() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB",
            cloudWatchClient,
            taggingClient)
        .register(registry);
    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("b").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myOtherLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "b", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myOtherLB"}))
        .isNull();
  }

  @Test
  public void testAllSelectDimensionsKnown() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB\n    AvailabilityZone:\n    - a\n    - b",
            cloudWatchClient,
            taggingClient)
        .register(registry);
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "b", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myOtherLB"}))
        .isNull();
  }

  @Test
  public void testAllSelectDimensionsKnownUsingGetMetricData() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB\n    AvailabilityZone:\n    - a\n    - b\n  use_get_metric_data: true\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);
    List<Instant> timestamps = List.of(new Date().toInstant());
    MetricMatcher firstMetric =
        new MetricMatcher()
            .Namespace("AWS/ELB")
                .MetricName("RequestCount")
                .Dimension("AvailabilityZone", "a")
                .Dimension("LoadBalancerName", "myLB");

    MetricMatcher secondMetric =
        new MetricMatcher()
            .Namespace("AWS/ELB")
                .MetricName("RequestCount")
                .Dimension("AvailabilityZone", "b")
                .Dimension("LoadBalancerName", "myLB");

    Mockito.when(
            cloudWatchClient.getMetricData(
                (GetMetricDataRequest)
                    argThat(
                        new GetMetricDataRequestMatcher()
                            .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Average").metric(firstMetric)))
                                .Query(
                                    new MetricDataQueryMatcher()
                                        .MetricStat(
                                            new MetricStatMatcher()
                                                .Stat("Average").metric(secondMetric))))))
        .thenReturn(
            GetMetricDataResponse.builder()
                .metricDataResults(
                    List.of(
                        MetricDataResult.builder()
                            .label("Average/AvailabilityZone=a,LoadBalancerName=myLB")
                            .values(List.of(Double.valueOf(2.0)))
                            .timestamps(timestamps)
                            .build(),
                        MetricDataResult.builder()
                            .label("Average/AvailabilityZone=b,LoadBalancerName=myLB")
                            .values(List.of(Double.valueOf(3.0)))
                            .timestamps(timestamps)
                            .build()))
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "b", "myLB"}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myOtherLB"}))
        .isNull();
  }

  @Test
  public void testDimensionSelectRegex() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select_regex:\n    LoadBalancerName:\n    - myLB(.*)",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("b").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB2").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myOtherLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancerName", "myLB2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "b", "myLB2"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myOtherLB"}))
        .isNull();
  }

  @Test
  public void testGetDimensionsUsesNextToken() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(ListMetricsResponse.builder().nextToken("ABC").build());
    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName")
                                .NextToken("ABC"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
  }

  @Test
  public void testExtendedStatistics() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: Latency\n  aws_extended_statistics:\n  - p95\n  - p99.99",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    HashMap<String, Double> extendedStatistics = new HashMap<String, Double>();
    extendedStatistics.put("p95", 1.0);
    extendedStatistics.put("p99.99", 2.0);

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB").MetricName("Latency"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder()
                        .timestamp(new Date().toInstant())
                        .extendedStatistics(extendedStatistics)
                        .build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_latency_p95",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_latency_p99_99",
                new String[] {"job", "instance"},
                new String[] {"aws_elb", ""}))
        .isCloseTo(2.0, within(.01));
  }

  @Test
  public void testDynamoIndexDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: OnlineIndexConsumedWriteCapacity\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName",
            cloudWatchClient,
            taggingClient)
        .register(registry);
    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("ConsumedReadCapacityUnits")
                                .Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("TableName").value("myTable").build(),
                            Dimension.builder()
                                .name("GlobalSecondaryIndexName")
                                .value("myIndex")
                                .build())
                        .build())
                .build());
    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("OnlineIndexConsumedWriteCapacity")
                                .Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("TableName").value("myTable").build(),
                            Dimension.builder()
                                .name("GlobalSecondaryIndexName")
                                .value("myIndex")
                                .build())
                        .build())
                .build());
    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("ConsumedReadCapacityUnits")
                                .Dimensions("TableName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("TableName").value("myTable").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("ConsumedReadCapacityUnits")
                                .Dimension("TableName", "myTable")
                                .Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(Datapoint.builder().timestamp(new Date().toInstant()).sum(1.0).build())
                .build());
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("OnlineIndexConsumedWriteCapacity")
                                .Dimension("TableName", "myTable")
                                .Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(Datapoint.builder().timestamp(new Date().toInstant()).sum(2.0).build())
                .build());
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("ConsumedReadCapacityUnits")
                                .Dimension("TableName", "myTable"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(Datapoint.builder().timestamp(new Date().toInstant()).sum(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_dynamodb_consumed_read_capacity_units_index_sum",
                new String[] {"job", "instance", "table_name", "global_secondary_index_name"},
                new String[] {"aws_dynamodb", "", "myTable", "myIndex"}))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_dynamodb_online_index_consumed_write_capacity_sum",
                new String[] {"job", "instance", "table_name", "global_secondary_index_name"},
                new String[] {"aws_dynamodb", "", "myTable", "myIndex"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_dynamodb_consumed_read_capacity_units_sum",
                new String[] {"job", "instance", "table_name"},
                new String[] {"aws_dynamodb", "", "myTable"}))
        .isCloseTo(3.0, within(.01));
  }

  @Test
  public void testDynamoNoDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: AccountProvisionedReadCapacityUtilization\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/DynamoDB")
                                .MetricName("AccountProvisionedReadCapacityUtilization"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(Datapoint.builder().timestamp(new Date().toInstant()).sum(1.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_dynamodb_account_provisioned_read_capacity_utilization_sum",
                new String[] {"job", "instance"},
                new String[] {"aws_dynamodb", ""}))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testTagSelectEC2() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(
                        new GetResourcesRequestMatcher()
                            .ResourceTypeFilter("ec2:instance")
                                .TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimensions("InstanceId"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-2").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-2"}))
        .isNull();
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testTagSelectWebACL() {
    // Testing "aws_tag_select" with an WAF WebAcl, which have a non-standard resource id in
    // metrics.
    // The regexp to get the resource id from the arn is specified in the rule
    new CloudWatchCollector(
            "---\nregion: eu-west-1\nmetrics:\n- aws_namespace: AWS/WAFV2\n  aws_metric_name: CountedRequests\n  aws_dimensions: [Region, Rule, WebACL]\n  aws_tag_select:\n    resource_type_selection: \"wafv2:regional/webacl\"\n    resource_id_dimension: WebACL\n    arn_resource_id_regexp: \"([^/]+)/[^/]+$\"\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                argThat(
                    new GetResourcesRequestMatcher().ResourceTypeFilter("wafv2:regional/webacl"))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN(
                            "arn:aws:wafv2:eu-west-1:123456789:regional/webacl/svc-integration-xxxx/d177aaf1-b18f-4f84-aa8e-f1c5c40fc426")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                argThat(
                    new ListMetricsRequestMatcher()
                        .Namespace("AWS/WAFV2")
                            .MetricName("CountedRequests")
                            .Dimensions("Region", "Rule", "WebACL"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("Region").value("eu-west-1").build(),
                            Dimension.builder().name("Rule").value("WebAclLog").build(),
                            Dimension.builder()
                                .name("WebACL")
                                .value("svc-integration-xxxx")
                                .build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                argThat(
                    new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/WAFV2")
                            .MetricName("CountedRequests")
                            .Dimension("Region", "eu-west-1")
                            .Dimension("Rule", "WebAclLog")
                            .Dimension("WebACL", "svc-integration-xxxx"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).sum(200.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_wafv2_counted_requests_sum",
                new String[] {"job", "instance", "region", "rule", "web_acl"},
                new String[] {"aws_wafv2", "", "eu-west-1", "WebAclLog", "svc-integration-xxxx"}))
        .isCloseTo(200.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "web_acl", "tag_Monitoring"},
                new String[] {
                  "aws_wafv2",
                  "",
                  "arn:aws:wafv2:eu-west-1:123456789:regional/webacl/svc-integration-xxxx/d177aaf1-b18f-4f84-aa8e-f1c5c40fc426",
                  "svc-integration-xxxx",
                  "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testTagSelectTargetGroup() {
    // Testing "aws_tag_select" with an ALB target group, which have a non-standard resource id in
    // metrics
    // The regexp to get the resource id from the arn is specified in the rule
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ApplicationELB\n  aws_metric_name: UnHealthyHostCount\n  aws_dimensions:\n  - TargetGroup\n  - LoadBalancer\n  aws_tag_select:\n    resource_type_selection: \"elasticloadbalancing:targetgroup\"\n    resource_id_dimension: TargetGroup\n    tag_selections:\n      Monitoring: [enabled]\n    arn_resource_id_regexp: \"(targetgroup/.*)$\"\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                argThat(
                    new GetResourcesRequestMatcher()
                        .ResourceTypeFilter("elasticloadbalancing:targetgroup")
                            .TagFilter("Monitoring", List.of("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN(
                            "arn:aws:elasticloadbalancing:us-east-1:121212121212:targetgroup/abc-123")
                        .build(),
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN(
                            "arn:aws:elasticloadbalancing:us-east-1:121212121212:targetgroup/abc-234")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                argThat(
                    new ListMetricsRequestMatcher()
                        .Namespace("AWS/ApplicationELB")
                            .MetricName("UnHealthyHostCount")
                            .Dimensions("TargetGroup", "LoadBalancer"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder()
                                .name("TargetGroup")
                                .value("targetgroup/abc-123")
                                .build(),
                            Dimension.builder().name("LoadBalancer").value("app/myLB/123").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder()
                                .name("TargetGroup")
                                .value("targetgroup/abc-234")
                                .build(),
                            Dimension.builder().name("LoadBalancer").value("app/myLB/123").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                argThat(
                    new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/ApplicationELB")
                            .MetricName("UnHealthyHostCount")
                            .Dimension("TargetGroup", "targetgroup/abc-123")
                            .Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                argThat(
                    new GetMetricStatisticsRequestMatcher()
                        .Namespace("AWS/ApplicationELB")
                            .MetricName("UnHealthyHostCount")
                            .Dimension("TargetGroup", "targetgroup/abc-234")
                            .Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_applicationelb_un_healthy_host_count_average",
                new String[] {"job", "instance", "target_group", "load_balancer"},
                new String[] {"aws_applicationelb", "", "targetgroup/abc-123", "app/myLB/123"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_applicationelb_un_healthy_host_count_average",
                new String[] {"job", "instance", "target_group", "load_balancer"},
                new String[] {"aws_applicationelb", "", "targetgroup/abc-234", "app/myLB/123"}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "target_group", "tag_Monitoring"},
                new String[] {
                  "aws_applicationelb",
                  "",
                  "arn:aws:elasticloadbalancing:us-east-1:121212121212:targetgroup/abc-123",
                  "targetgroup/abc-123",
                  "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testTagSelectALB() throws Exception {
    // Testing "aws_tag_select" with an ALB, which have a fairly complex ARN
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ApplicationELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancer\n  aws_tag_select:\n    resource_type_selection: \"elasticloadbalancing:loadbalancer/app\"\n    resource_id_dimension: LoadBalancer\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(
                        new GetResourcesRequestMatcher()
                            .ResourceTypeFilter("elasticloadbalancing:loadbalancer/app")
                                .TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN(
                            "arn:aws:elasticloadbalancing:us-east-1:121212121212:loadbalancer/app/myLB/123")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ApplicationELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancer"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancer").value("app/myLB/123").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("b").build(),
                            Dimension.builder().name("LoadBalancer").value("app/myLB/123").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder()
                                .name("LoadBalancer")
                                .value("app/myOtherLB/456")
                                .build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ApplicationELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ApplicationELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ApplicationELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancer", "app/myOtherLB/456"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(4.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_applicationelb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer"},
                new String[] {"aws_applicationelb", "", "a", "app/myLB/123"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_applicationelb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer"},
                new String[] {"aws_applicationelb", "", "b", "app/myLB/123"}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_applicationelb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer"},
                new String[] {"aws_applicationelb", "", "a", "app/myOtherLB/456"}))
        .isNull();
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "load_balancer", "tag_Monitoring"},
                new String[] {
                  "aws_applicationelb",
                  "",
                  "arn:aws:elasticloadbalancing:us-east-1:121212121212:loadbalancer/app/myLB/123",
                  "app/myLB/123",
                  "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testTagSelectUsesPaginationToken() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(
                        new GetResourcesRequestMatcher()
                            .ResourceTypeFilter("ec2:instance")
                                .TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .paginationToken("ABC")
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")
                        .build())
                .build());

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(
                        new GetResourcesRequestMatcher()
                            .PaginationToken("ABC")
                                .ResourceTypeFilter("ec2:instance")
                                .TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimensions("InstanceId"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-2").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-2"}))
        .isCloseTo(3.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testNoSelection() throws Exception {
    // When no selection is made, all metrics should be returned
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimensions("InstanceId"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-2").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-2"}))
        .isCloseTo(3.0, within(.01));
  }

  @Test
  public void testMultipleSelection() throws Exception {
    // When multiple selections are made, "and" logic should be applied on metrics
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n  aws_dimension_select:\n    InstanceId: [\"i-1\"]",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(
                        new GetResourcesRequestMatcher()
                            .ResourceTypeFilter("ec2:instance")
                                .TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")
                        .build(),
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimensions("InstanceId"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-2").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-2"}))
        .isNull();
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
  }

  @Test
  public void testOptionalTagSelection() throws Exception {
    // aws_tag_select can be used without tag_selection to activate the aws_resource_info metric on
    // tagged (or previously tagged) resources
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n  aws_dimension_select:\n    InstanceId: [\"i-1\", \"i-no-tag\"]",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            taggingClient.getResources(
                (GetResourcesRequest)
                    argThat(new GetResourcesRequestMatcher().ResourceTypeFilter("ec2:instance"))))
        .thenReturn(
            GetResourcesResponse.builder()
                .resourceTagMappingList(
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")
                        .build(),
                    ResourceTagMapping.builder()
                        .tags(Tag.builder().key("Monitoring").value("enabled").build())
                        .resourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimensions("InstanceId"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build(),
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-2").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("InstanceId").value("i-no-tag").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-1"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-2"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/EC2")
                                .MetricName("CPUUtilization")
                                .Dimension("InstanceId", "i-no-tag"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(4.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-1"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-2"}))
        .isNull();
    assertThat(
            registry.getSampleValue(
                "aws_ec2_cpuutilization_average",
                new String[] {"job", "instance", "instance_id"},
                new String[] {"aws_ec2", "", "i-no-tag"}))
        .isCloseTo(4.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
                }))
        .isCloseTo(1.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_resource_info",
                new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
                new String[] {
                  "aws_ec2",
                  "",
                  "arn:aws:ec2:us-east-1:121212121212:instance/i-no-tag",
                  "i-no-tag",
                  "enabled"
                }))
        .isNull();
  }

  @Test
  public void testNotRecentlyActive() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  range_seconds: 12000",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName")
                                .RecentlyActive(null))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build(),
                            Dimension.builder()
                                .name("ThisExtraDimensionIsIgnored")
                                .value("dummy")
                                .build())
                        .build(),
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("b").build(),
                            Dimension.builder().name("LoadBalancerName").value("myOtherLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());
    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "b")
                                .Dimension("LoadBalancerName", "myOtherLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(3.0).build())
                .build());

    registry.getSampleValue(
        "aws_elb_request_count_average",
        new String[] {"job", "instance"},
        new String[] {"aws_elb", ""});

    Mockito.verify(cloudWatchClient)
        .listMetrics(
            (ListMetricsRequest)
                argThat(
                    new ListMetricsRequestMatcher()
                        .RecentlyActive(null)
                            .Namespace("AWS/ELB")
                            .MetricName("RequestCount")
                            .Dimensions("AvailabilityZone", "LoadBalancerName")));
  }

  @Test
  public void testBuildInfo() throws Exception {
    new BuildInfoCollector().register(registry);
    final Properties properties = new Properties();
    properties.load(CloudWatchCollector.class.getClassLoader().getResourceAsStream(".properties"));
    String buildVersion = properties.getProperty("BuildVersion");
    String releaseDate = properties.getProperty("ReleaseDate");

    assertThat(
            registry.getSampleValue(
                "cloudwatch_exporter_build_info",
                new String[] {"build_version", "release_date"},
                new String[] {
                  buildVersion != null ? buildVersion : "unknown",
                  releaseDate != null ? releaseDate : "unknown"
                }))
        .isCloseTo(1L, within(.00001));
  }

  @Test
  public void testDimensionsWithDefaultCache() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nlist_metrics_cache_ttl: 500\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));

    Mockito.verify(cloudWatchClient).listMetrics(any(ListMetricsRequest.class));
    Mockito.verify(cloudWatchClient, times(2))
        .getMetricStatistics(any(GetMetricStatisticsRequest.class));
  }

  @Test
  public void testDimensionsWithMetricLevelCache() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  list_metrics_cache_ttl: 500\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient)
        .register(registry);

    Mockito.when(
            cloudWatchClient.listMetrics(
                (ListMetricsRequest)
                    argThat(
                        new ListMetricsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(
                            Dimension.builder().name("AvailabilityZone").value("a").build(),
                            Dimension.builder().name("LoadBalancerName").value("myLB").build())
                        .build())
                .build());

    Mockito.when(
            cloudWatchClient.getMetricStatistics(
                (GetMetricStatisticsRequest)
                    argThat(
                        new GetMetricStatisticsRequestMatcher()
                            .Namespace("AWS/ELB")
                                .MetricName("RequestCount")
                                .Dimension("AvailabilityZone", "a")
                                .Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));
    assertThat(
            registry.getSampleValue(
                "aws_elb_request_count_average",
                new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
                new String[] {"aws_elb", "", "a", "myLB"}))
        .isCloseTo(2.0, within(.01));

    Mockito.verify(cloudWatchClient).listMetrics(any(ListMetricsRequest.class));
    Mockito.verify(cloudWatchClient, times(2))
        .getMetricStatistics(any(GetMetricStatisticsRequest.class));
  }

  @Test
  public void loadConfigFromReaderRejectsEmptyYaml() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n",
            cloudWatchClient,
            taggingClient);

    assertThatThrownBy(
            () -> collector.loadConfig(new StringReader(""), cloudWatchClient, taggingClient))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Must provide metrics");
  }

  @Test
  public void rejectsConfigWithoutMetrics() {
    assertThatThrownBy(
            () -> new CloudWatchCollector("---\nregion: reg\n", cloudWatchClient, taggingClient))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Must provide metrics");
  }

  @Test
  public void rejectsMetricWithoutRequiredCloudWatchNames() {
    assertThatThrownBy(
            () ->
                new CloudWatchCollector(
                    "---\nmetrics:\n- aws_namespace: AWS/ELB\n", cloudWatchClient, taggingClient))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Must provide aws_namespace and aws_metric_name");
  }

  @Test
  public void parsesGlobalDefaultsAndMetricHelp() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\n"
                + "period_seconds: 15\n"
                + "range_seconds: 30\n"
                + "delay_seconds: 45\n"
                + "set_timestamp: false\n"
                + "use_get_metric_data: true\n"
                + "warn_on_empty_list_dimensions: true\n"
                + "list_metrics_cache_ttl: 120\n"
                + "metrics:\n"
                + "- aws_namespace: AWS/ELB\n"
                + "  aws_metric_name: RequestCount\n"
                + "  help: Custom help\n"
                + "  aws_dimensions: [LoadBalancerName]\n"
                + "  aws_extended_statistics: [p99]\n",
            cloudWatchClient,
            taggingClient);

    MetricRule rule = collector.activeConfig.rules.get(0);

    assertThat(rule.periodSeconds).isEqualTo(15);
    assertThat(rule.rangeSeconds).isEqualTo(30);
    assertThat(rule.delaySeconds).isEqualTo(45);
    assertThat(rule.cloudwatchTimestamp).isFalse();
    assertThat(rule.useGetMetricData).isTrue();
    assertThat(rule.warnOnEmptyListDimensions).isTrue();
    assertThat(rule.listMetricsCacheTtl).isEqualTo(Duration.ofSeconds(120));
    assertThat(rule.help).isEqualTo("Custom help");
    assertThat(rule.awsDimensions).containsExactly("LoadBalancerName");
    assertThat(rule.awsExtendedStatistics).containsExactly("p99");
    assertThat(rule.awsStatistics).isNull();
  }

  @Test
  public void stringConstructorParsesConfigWithProvidedClients() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n",
            cloudWatchClient,
            taggingClient);

    assertThat(collector.activeConfig.rules).hasSize(1);
  }

  @Test
  public void readerConstructorParsesConfig() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            new StringReader(
                "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n"));

    assertThat(collector.activeConfig.rules).hasSize(1);
    assertThat(collector.activeConfig.cloudWatchClient).isNotNull();
    assertThat(collector.activeConfig.taggingClient).isNotNull();
  }

  @Test
  public void rejectsCombinedDimensionSelectAndRegex() {
    assertThatThrownBy(
            () ->
                new CloudWatchCollector(
                    "---\n"
                        + "metrics:\n"
                        + "- aws_namespace: AWS/ELB\n"
                        + "  aws_metric_name: RequestCount\n"
                        + "  aws_dimension_select:\n"
                        + "    LoadBalancerName: [lb]\n"
                        + "  aws_dimension_select_regex:\n"
                        + "    LoadBalancerName: [lb.*]\n",
                    cloudWatchClient,
                    taggingClient))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Must not provide aws_dimension_select and aws_dimension_select_regex at the same time");
  }

  @Test
  public void rejectsIncompleteTagSelect() {
    assertThatThrownBy(
            () ->
                new CloudWatchCollector(
                    "---\n"
                        + "metrics:\n"
                        + "- aws_namespace: AWS/EC2\n"
                        + "  aws_metric_name: CPUUtilization\n"
                        + "  aws_tag_select:\n"
                        + "    resource_type_selection: ec2:instance\n",
                    cloudWatchClient,
                    taggingClient))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Must provide resource_type_selection and resource_id_dimension");
  }

  @Test
  public void collectReportsScrapeErrorWhenCloudWatchRequestFails() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n",
            cloudWatchClient,
            taggingClient);
    Mockito.when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenThrow(new RuntimeException("boom"));

    List<Collector.MetricFamilySamples> samples = collector.collect();

    assertThat(errorSample(samples)).isEqualTo(1.0);
  }

  @Test
  public void stringConstructorBuildsClientsWithoutAwsCalls() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n");

    assertThat(collector.activeConfig.rules).hasSize(1);
    assertThat(collector.activeConfig.cloudWatchClient).isNotNull();
    assertThat(collector.activeConfig.taggingClient).isNotNull();
  }

  @Test
  public void parsesExplicitStatisticsAndMetricWarnOverride() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\n"
                + "warn_on_empty_list_dimensions: false\n"
                + "metrics:\n"
                + "- aws_namespace: AWS/ELB\n"
                + "  aws_metric_name: RequestCount\n"
                + "  aws_statistics: [Sum, Average]\n"
                + "  warn_on_empty_list_dimensions: true\n",
            cloudWatchClient,
            taggingClient);

    MetricRule rule = collector.activeConfig.rules.get(0);

    assertThat(rule.awsStatistics).containsExactly(Statistic.SUM, Statistic.AVERAGE);
    assertThat(rule.warnOnEmptyListDimensions).isTrue();
  }

  @Test
  public void reloadConfigUsesExistingClientsAndUpdatedFile() throws Exception {
    Path config =
        Files.writeString(
            Files.createTempFile("cloudwatch-exporter-reload", ".yml"),
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n");
    String previousConfigFilePath = WebServer.configFilePath;
    WebServer.configFilePath = config.toString();
    try {
      CloudWatchCollector collector =
          new CloudWatchCollector(
              "---\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n",
              cloudWatchClient,
              taggingClient);

      collector.reloadConfig();

      assertThat(collector.activeConfig.rules).hasSize(1);
      assertThat(collector.activeConfig.rules.get(0).awsNamespace).isEqualTo("AWS/ELB");
      assertThat(collector.activeConfig.cloudWatchClient).isSameAs(cloudWatchClient);
      assertThat(collector.activeConfig.taggingClient).isSameAs(taggingClient);
    } finally {
      WebServer.configFilePath = previousConfigFilePath;
      Files.deleteIfExists(config);
    }
  }

  @Test
  public void customHelpIsUsedForMetricFamily() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\n"
                + "metrics:\n"
                + "- aws_namespace: AWS/ELB\n"
                + "  aws_metric_name: RequestCount\n"
                + "  help: Custom metric help\n",
            cloudWatchClient,
            taggingClient);
    Mockito.when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Collector.MetricFamilySamples average =
        metricFamily(collector.collect(), "aws_elb_request_count_average");

    assertThat(average.help).isEqualTo("Custom metric help");
  }

  @Test
  public void resourceInfoIsEmittedOnceForDuplicateTagMappings() {
    CloudWatchCollector collector =
        new CloudWatchCollector(
            "---\n"
                + "region: reg\n"
                + "metrics:\n"
                + "- aws_namespace: AWS/EC2\n"
                + "  aws_metric_name: CPUUtilization\n"
                + "  aws_dimensions: [InstanceId]\n"
                + "  aws_tag_select:\n"
                + "    resource_type_selection: ec2:instance\n"
                + "    resource_id_dimension: InstanceId\n",
            cloudWatchClient,
            taggingClient);
    ResourceTagMapping mapping =
        ResourceTagMapping.builder()
            .resourceARN("arn:aws:ec2:reg:123456789012:instance/i-1")
            .tags(Tag.builder().key("Name").value("example").build())
            .build();
    Mockito.when(taggingClient.getResources(any(GetResourcesRequest.class)))
        .thenReturn(
            GetResourcesResponse.builder().resourceTagMappingList(mapping, mapping).build());
    Mockito.when(cloudWatchClient.listMetrics(any(ListMetricsRequest.class)))
        .thenReturn(
            ListMetricsResponse.builder()
                .metrics(
                    Metric.builder()
                        .dimensions(Dimension.builder().name("InstanceId").value("i-1").build())
                        .build())
                .build());
    Mockito.when(cloudWatchClient.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
        .thenReturn(
            GetMetricStatisticsResponse.builder()
                .datapoints(
                    Datapoint.builder().timestamp(new Date().toInstant()).average(2.0).build())
                .build());

    Collector.MetricFamilySamples info = metricFamily(collector.collect(), "aws_resource_info");

    assertThat(info.samples).hasSize(1);
    assertThat(info.samples.get(0).labelNames).contains("instance_id", "tag_Name");
    assertThat(info.samples.get(0).labelValues).contains("i-1", "example");
  }

  private Collector.MetricFamilySamples metricFamily(
      List<Collector.MetricFamilySamples> samples, String name) {
    return samples.stream().filter(sample -> sample.name.equals(name)).findFirst().orElseThrow();
  }

  private double errorSample(List<Collector.MetricFamilySamples> samples) {
    return samples.stream()
        .filter(sample -> sample.name.equals("cloudwatch_exporter_scrape_error"))
        .findFirst()
        .orElseThrow()
        .samples
        .get(0)
        .value;
  }
}
