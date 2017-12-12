package io.prometheus.cloudwatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import io.prometheus.client.CollectorRegistry;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class CloudWatchCollectorTest {
  AmazonCloudWatchClient client;
  CollectorRegistry registry;

  @Before
  public void setUp() {
    client = Mockito.mock(AmazonCloudWatchClient.class);
    registry = new CollectorRegistry();
  }
  
  class ListMetricsRequestMatcher extends ArgumentMatcher {
    String namespace;
    String metricName;
    String nextToken;
    List<DimensionFilter> dimensions = new ArrayList<DimensionFilter>();

    public ListMetricsRequestMatcher Namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }
    public ListMetricsRequestMatcher MetricName(String metricName) {
      this.metricName = metricName;
      return this;
    }
    public ListMetricsRequestMatcher NextToken(String nextToken) {
      this.nextToken = nextToken;
      return this;
    }
    public ListMetricsRequestMatcher Dimensions(String... dimensions) {
      this.dimensions = new ArrayList<DimensionFilter>();
      for (int i = 0; i < dimensions.length; i++) {
        this.dimensions.add(new DimensionFilter().withName(dimensions[i]));
      }
      return this;
    }
    
    public boolean matches(Object o) {
     ListMetricsRequest request = (ListMetricsRequest) o;
     if (request == null) return false;
     if (namespace != null && !namespace.equals(request.getNamespace())){
       return false;
     }
     if (metricName != null && !metricName.equals(request.getMetricName())){
       return false;
     }
     if (nextToken == null ^ request.getNextToken() == null) {
       return false;
     }
     if (nextToken != null && !nextToken.equals(request.getNextToken())) {
       return false;
     }
     if (!dimensions.equals(request.getDimensions())) {
       return false;
     }
     return true;
    }
  }

  class GetMetricStatisticsRequestMatcher extends ArgumentMatcher {
    String namespace;
    String metricName;
    List<Dimension> dimensions = new ArrayList<Dimension>();
    Integer period;

    public GetMetricStatisticsRequestMatcher Namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }
    public GetMetricStatisticsRequestMatcher MetricName(String metricName) {
      this.metricName = metricName;
      return this;
    }
    public GetMetricStatisticsRequestMatcher Dimension(String name, String value) {
      dimensions.add(new Dimension().withName(name).withValue(value));
      return this;
    }
    public GetMetricStatisticsRequestMatcher Period(int period) {
      this.period = period;
      return this;
    }

    public boolean matches(Object o) {
     GetMetricStatisticsRequest request = (GetMetricStatisticsRequest) o;
     if (request == null) return false;
     if (namespace != null && !namespace.equals(request.getNamespace())){
       return false;
     }
     if (metricName != null && !metricName.equals(request.getMetricName())){
       return false;
     }
     if (!dimensions.equals(request.getDimensions())) {
       return false;
     }
     if (period != null && !period.equals(request.getPeriod())) {
         return false;
     }
     return true;
    }
  }

  @Test
  public void testMetricPeriod() {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  period_seconds: 100\n  range_seconds: 200\n  delay_seconds: 300", client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""});


    Mockito.verify(client).getMetricStatistics((GetMetricStatisticsRequest) argThat(
            new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB")
                    .MetricName("RequestCount")
                    .Period(100)
    ));
  }

  @Test
  public void testDefaultPeriod() {
    new CloudWatchCollector(
                    "---\nregion: reg\nperiod_seconds: 100\nrange_seconds: 200\ndelay_seconds: 300\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""});


    Mockito.verify(client).getMetricStatistics((GetMetricStatisticsRequest) argThat(
            new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB")
                    .MetricName("RequestCount")
                    .Period(100)
    ));
  }

  @Test
  public void testAllStatistics() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(1.0)
                .withMaximum(2.0).withMinimum(3.0).withSampleCount(4.0).withSum(5.0)));

    assertEquals(1.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_maximum", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_minimum", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(4.0, registry.getSampleValue("aws_elb_request_count_sample_count", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(5.0, registry.getSampleValue("aws_elb_request_count_sum", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
  }

  @Test
  public void testUsesNewestDatapoint() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date(1)).withAverage(1.0),
            new Datapoint().withTimestamp(new Date(3)).withAverage(3.0),
            new Datapoint().withTimestamp(new Date(2)).withAverage(2.0)));

    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
  }

  @Test
  public void testDimensions() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName", client).register(registry);
    
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB"), new Dimension().withName("ThisExtraDimensionIsIgnored").withValue("dummy")),
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myOtherLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "b", "myOtherLB"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name", "this_extra_dimension_is_ignored"}, new String[]{"aws_elb", "", "a", "myLB", "dummy"}));
  }

  @Test
  public void testDimensionSelect() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB", client).register(registry);
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "b", "myLB"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testDimensionSelectRegex() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select_regex:\n    LoadBalancerName:\n    - myLB(.*)", client).register(registry);

    Mockito.when(client.listMetrics((ListMetricsRequest) argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB1")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB2")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myLB2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB1"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "b", "myLB2"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testGetDimensionsUsesNextToken() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB", client).register(registry);
    
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withNextToken("ABC"));
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName").NextToken("ABC"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB"))));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB"}), .01);
  }

  @Test
  public void testGetMonitoringEndpoint() throws Exception {
    Region usEast = RegionUtils.getRegion("us-east-1");
    CloudWatchCollector us_collector = new CloudWatchCollector("---\nregion: us-east-1\nmetrics: []\n");
    assertEquals("https://monitoring.us-east-1.amazonaws.com", us_collector.getMonitoringEndpoint(usEast));

    Region cnNorth = RegionUtils.getRegion("cn-north-1");
    CloudWatchCollector cn_collector = new CloudWatchCollector("---\nregion: cn-north-1\nmetrics: []\n");
    assertEquals("https://monitoring.cn-north-1.amazonaws.com.cn", cn_collector.getMonitoringEndpoint(cnNorth));
  }

  @Test
  public void testExtendedStatistics() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: Latency\n  aws_extended_statistics:\n  - p95\n  - p99.99", client).register(registry);

    HashMap<String, Double> extendedStatistics = new HashMap<String, Double>();
    extendedStatistics.put("p95", 1.0);
    extendedStatistics.put("p99.99", 2.0);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("Latency"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withExtendedStatistics(extendedStatistics)));

    assertEquals(1.0, registry.getSampleValue("aws_elb_latency_p95", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_latency_p99_99", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
  }

  @Test
  public void testDynamoIndexDimensions() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: OnlineIndexConsumedWriteCapacity\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName", client).register(registry);
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex"))));
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex"))));
    Mockito.when(client.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"))));

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(1.0)));
    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(2.0)));
    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(3.0)));

    assertEquals(1.0, registry.getSampleValue("aws_dynamodb_consumed_read_capacity_units_index_sum", new String[]{"job", "instance", "table_name", "global_secondary_index_name"}, new String[]{"aws_dynamodb", "", "myTable", "myIndex"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_dynamodb_online_index_consumed_write_capacity_sum", new String[]{"job", "instance", "table_name", "global_secondary_index_name"}, new String[]{"aws_dynamodb", "", "myTable", "myIndex"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_dynamodb_consumed_read_capacity_units_sum", new String[]{"job", "instance", "table_name"}, new String[]{"aws_dynamodb", "", "myTable"}), .01);
  }
}
