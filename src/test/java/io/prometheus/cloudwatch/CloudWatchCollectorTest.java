package io.prometheus.cloudwatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.cloudwatch.RequestsMatchers.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;

import org.junit.Before;
import org.junit.Test;
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

  @Before
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
            taggingClient,
            new HashMap<>())
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
            taggingClient,
            new HashMap<>())
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
            taggingClient,
            new HashMap<>())
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
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_maximum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_elb_request_count_minimum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        4.0,
        registry.getSampleValue(
            "aws_elb_request_count_sample_count",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        5.0,
        registry.getSampleValue(
            "aws_elb_request_count_sum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
  }

  @Test
  public void testAllStatisticsUsingGetMetricData() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  use_get_metric_data: true\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_maximum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_elb_request_count_minimum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        4.0,
        registry.getSampleValue(
            "aws_elb_request_count_sample_count",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        5.0,
        registry.getSampleValue(
            "aws_elb_request_count_sum",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
  }

  @Test
  public void testCloudwatchTimestamps() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  set_timestamp: true\n- aws_namespace: AWS/ELB\n  aws_metric_name: HTTPCode_Backend_2XX\n  set_timestamp: false",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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
          assertEquals(expectedTimestamp, (Long) s.timestampMs);
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
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
  }

  @Test
  public void testDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "b", "myOtherLB"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {
              "job",
              "instance",
              "availability_zone",
              "load_balancer_name",
              "this_extra_dimension_is_ignored"
            },
            new String[] {"aws_elb", "", "a", "myLB", "dummy"}));
  }

  @Test
  public void testDimensionSelect() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "b", "myLB"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testAllSelectDimensionsKnown() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB\n    AvailabilityZone:\n    - a\n    - b",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "b", "myLB"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testAllSelectDimensionsKnownUsingGetMetricData() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB\n    AvailabilityZone:\n    - a\n    - b\n  use_get_metric_data: true\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "b", "myLB"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testDimensionSelectRegex() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select_regex:\n    LoadBalancerName:\n    - myLB(.*)",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB1"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "b", "myLB2"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testGetDimensionsUsesNextToken() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
  }

  @Test
  public void testExtendedStatistics() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: Latency\n  aws_extended_statistics:\n  - p95\n  - p99.99",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_elb_latency_p95", new String[] {"job", "instance"}, new String[] {"aws_elb", ""}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_latency_p99_99",
            new String[] {"job", "instance"},
            new String[] {"aws_elb", ""}),
        .01);
  }

  @Test
  public void testDynamoIndexDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: OnlineIndexConsumedWriteCapacity\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_dynamodb_consumed_read_capacity_units_index_sum",
            new String[] {"job", "instance", "table_name", "global_secondary_index_name"},
            new String[] {"aws_dynamodb", "", "myTable", "myIndex"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_dynamodb_online_index_consumed_write_capacity_sum",
            new String[] {"job", "instance", "table_name", "global_secondary_index_name"},
            new String[] {"aws_dynamodb", "", "myTable", "myIndex"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_dynamodb_consumed_read_capacity_units_sum",
            new String[] {"job", "instance", "table_name"},
            new String[] {"aws_dynamodb", "", "myTable"}),
        .01);
  }

  @Test
  public void testDynamoNoDimensions() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: AccountProvisionedReadCapacityUtilization\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_dynamodb_account_provisioned_read_capacity_utilization_sum",
            new String[] {"job", "instance"},
            new String[] {"aws_dynamodb", ""}),
        .01);
  }

  @Test
  public void testTagSelectEC2() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-1"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-2"}));
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
            }),
        .01);
  }

  @Test
  public void testTagSelectALB() throws Exception {
    // Testing "aws_tag_select" with an ALB, which have a fairly complex ARN
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ApplicationELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancer\n  aws_tag_select:\n    resource_type_selection: \"elasticloadbalancing:loadbalancer/app\"\n    resource_id_dimension: LoadBalancer\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_applicationelb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer"},
            new String[] {"aws_applicationelb", "", "a", "app/myLB/123"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_applicationelb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer"},
            new String[] {"aws_applicationelb", "", "b", "app/myLB/123"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_applicationelb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer"},
            new String[] {"aws_applicationelb", "", "a", "app/myOtherLB/456"}));
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "load_balancer", "tag_Monitoring"},
            new String[] {
              "aws_applicationelb",
              "",
              "arn:aws:elasticloadbalancing:us-east-1:121212121212:loadbalancer/app/myLB/123",
              "app/myLB/123",
              "enabled"
            }),
        .01);
  }

  @Test
  public void testTagSelectUsesPaginationToken() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-1"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-2"}),
        .01);
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
            }),
        .01);
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
            }),
        .01);
  }

  @Test
  public void testNoSelection() throws Exception {
    // When no selection is made, all metrics should be returned
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-1"}),
        .01);
    assertEquals(
        3.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-2"}),
        .01);
  }

  @Test
  public void testMultipleSelection() throws Exception {
    // When multiple selections are made, "and" logic should be applied on metrics
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n  aws_dimension_select:\n    InstanceId: [\"i-1\"]",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-1"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-2"}));
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
            }),
        .01);
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
            }),
        .01);
  }

  @Test
  public void testOptionalTagSelection() throws Exception {
    // aws_tag_select can be used without tag_selection to activate the aws_resource_info metric on
    // tagged (or previously tagged) resources
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n  aws_dimension_select:\n    InstanceId: [\"i-1\", \"i-no-tag\"]",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-1"}),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-2"}));
    assertEquals(
        4.0,
        registry.getSampleValue(
            "aws_ec2_cpuutilization_average",
            new String[] {"job", "instance", "instance_id"},
            new String[] {"aws_ec2", "", "i-no-tag"}),
        .01);
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"
            }),
        .01);
    assertEquals(
        1.0,
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"
            }),
        .01);
    assertNull(
        registry.getSampleValue(
            "aws_resource_info",
            new String[] {"job", "instance", "arn", "instance_id", "tag_Monitoring"},
            new String[] {
              "aws_ec2",
              "",
              "arn:aws:ec2:us-east-1:121212121212:instance/i-no-tag",
              "i-no-tag",
              "enabled"
            }));
  }

  @Test
  public void testNotRecentlyActive() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  range_seconds: 12000",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        1L,
        registry.getSampleValue(
            "cloudwatch_exporter_build_info",
            new String[] {"build_version", "release_date"},
            new String[] {
              buildVersion != null ? buildVersion : "unknown",
              releaseDate != null ? releaseDate : "unknown"
            }),
        .00001);
  }

  @Test
  public void testDimensionsWithDefaultCache() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nlist_metrics_cache_ttl: 500\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);

    Mockito.verify(cloudWatchClient).listMetrics(any(ListMetricsRequest.class));
    Mockito.verify(cloudWatchClient, times(2))
        .getMetricStatistics(any(GetMetricStatisticsRequest.class));
  }

  @Test
  public void testDimensionsWithMetricLevelCache() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  list_metrics_cache_ttl: 500\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
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

    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);
    assertEquals(
        2.0,
        registry.getSampleValue(
            "aws_elb_request_count_average",
            new String[] {"job", "instance", "availability_zone", "load_balancer_name"},
            new String[] {"aws_elb", "", "a", "myLB"}),
        .01);

    Mockito.verify(cloudWatchClient).listMetrics(any(ListMetricsRequest.class));
    Mockito.verify(cloudWatchClient, times(2))
        .getMetricStatistics(any(GetMetricStatisticsRequest.class));
  }
  @Test
  public void testGlobalCacheCanCache() {
    CloudWatchCollector cwc = new CloudWatchCollector(
            "---\nregion: reg\nglobal_cache_ttl: 10\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount",
            cloudWatchClient,
            taggingClient,
            new HashMap<>())
            .register(registry);

    Mockito.when(
                    cloudWatchClient.getMetricStatistics(
                            (GetMetricStatisticsRequest)
                                    argThat(
                                            new GetMetricStatisticsRequestMatcher()
                                                    .Namespace("AWS/ELB").MetricName("RequestCount"))))
            .thenReturn(
                    GetMetricStatisticsResponse.builder() // First
                            .datapoints(
                                    Datapoint.builder()
                                            .timestamp(new Date().toInstant())
                                            .average(1.0)
                                            .maximum(2.0)
                                            .build())
                            .build(),
                    GetMetricStatisticsResponse.builder() // Second
                            .datapoints(
                                    Datapoint.builder()
                                            .timestamp(new Date().toInstant())
                                            .average(2.0)
                                            .maximum(4.0)
                                            .build())
                            .build(),
                    GetMetricStatisticsResponse.builder() // Third
                            .datapoints(
                                    Datapoint.builder()
                                            .timestamp(new Date().toInstant())
                                            .average(4.0)
                                            .maximum(8.0)
                                            .build())
                            .build());


    for (Collector.MetricFamilySamples it : Collections.list(registry.metricFamilySamples())) {
      if (it.name.equals("cloudwatch_exporter_cached_answer")) assertEquals(0.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_average")) assertEquals(1.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_maximum")) assertEquals(2.0, it.samples.get(0).value, .01);
    }

    cwc.lastCall = Instant.now().minus(1, ChronoUnit.SECONDS);

    for (Collector.MetricFamilySamples it : Collections.list(registry.metricFamilySamples())) {
      if (it.name.equals("cloudwatch_exporter_cached_answer")) assertEquals(1.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_average")) assertEquals(1.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_maximum")) assertEquals(2.0, it.samples.get(0).value, .01);
    }

    cwc.lastCall = Instant.now().minus(11, ChronoUnit.SECONDS);

    for (Collector.MetricFamilySamples it : Collections.list(registry.metricFamilySamples())) {
      if (it.name.equals("cloudwatch_exporter_cached_answer")) assertEquals(0.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_average")) assertEquals(2.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_maximum")) assertEquals(4.0, it.samples.get(0).value, .01);
    }

    cwc.lastCall = Instant.now().minus(11, ChronoUnit.SECONDS);

    for (Collector.MetricFamilySamples it : Collections.list(registry.metricFamilySamples())) {
      if (it.name.equals("cloudwatch_exporter_cached_answer")) assertEquals(0.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_average")) assertEquals(4.0, it.samples.get(0).value, .01);
      if (it.name.equals("aws_elb_request_count_maximum")) assertEquals(8.0, it.samples.get(0).value, .01);
    }
  }
}
