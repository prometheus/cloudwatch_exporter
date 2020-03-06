package io.prometheus.cloudwatch;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.resourcegroupstaggingapi.AWSResourceGroupsTaggingAPI;
import com.amazonaws.services.resourcegroupstaggingapi.model.GetResourcesRequest;
import com.amazonaws.services.resourcegroupstaggingapi.model.GetResourcesResult;
import com.amazonaws.services.resourcegroupstaggingapi.model.ResourceTagMapping;
import com.amazonaws.services.resourcegroupstaggingapi.model.Tag;
import com.amazonaws.services.resourcegroupstaggingapi.model.TagFilter;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CloudWatchCollectorTest {
  AmazonCloudWatch cloudWatchClient;
  AWSResourceGroupsTaggingAPI taggingClient;
  CollectorRegistry registry;

  @Before
  public void setUp() {
    cloudWatchClient = Mockito.mock(AmazonCloudWatch.class);
    taggingClient = Mockito.mock(AWSResourceGroupsTaggingAPI.class);
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

  class GetResourcesRequestMatcher extends ArgumentMatcher {
    String paginationToken = "";
    List<String> resourceTypeFilters = new ArrayList<String>();
    List<TagFilter> tagFilters = new ArrayList<TagFilter>();

    public GetResourcesRequestMatcher PaginationToken(String paginationToken) {
      this.paginationToken = paginationToken;
      return this;
    }
    public GetResourcesRequestMatcher ResourceTypeFilter(String resourceTypeFilter) {
      resourceTypeFilters.add(resourceTypeFilter);
      return this;
    }
    public GetResourcesRequestMatcher TagFilter(String key, List<String> values ) {
      tagFilters.add(new TagFilter().withKey(key).withValues(values));
      return this;
    }
    
    public boolean matches(Object o) {
     GetResourcesRequest request = (GetResourcesRequest) o;
     if (request == null) return false;
     if (paginationToken == "" ^ request.getPaginationToken() == "") {
       return false;
     }
     if (paginationToken != "" && !paginationToken.equals(request.getPaginationToken())) {
       return false;
     }
     if (!resourceTypeFilters.equals(request.getResourceTypeFilters())) {
       return false;
     }
     if (!tagFilters.equals(request.getTagFilters())) {
       return false;
     }
     return true;
    }
  }
  
  @Test
  public void testMetricPeriod() {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  period_seconds: 100\n  range_seconds: 200\n  delay_seconds: 300", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""});


    Mockito.verify(cloudWatchClient).getMetricStatistics((GetMetricStatisticsRequest) argThat(
            new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB")
                    .MetricName("RequestCount")
                    .Period(100)
    ));
  }

  @Test
  public void testDefaultPeriod() {
    new CloudWatchCollector(
                    "---\nregion: reg\nperiod_seconds: 100\nrange_seconds: 200\ndelay_seconds: 300\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance"}, new String[]{"aws_elb", ""});


    Mockito.verify(cloudWatchClient).getMetricStatistics((GetMetricStatisticsRequest) argThat(
            new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB")
                    .MetricName("RequestCount")
                    .Period(100)
    ));
  }

  @Test
  public void testAllStatistics() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
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
  public void testCloudwatchTimestamps() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  set_timestamp: true\n- aws_namespace: AWS/ELB\n  aws_metric_name: HTTPCode_Backend_2XX\n  set_timestamp: false"
            , cloudWatchClient, taggingClient).register(registry);

    Date timestamp = new Date();
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
            new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount"))))
            .thenReturn(new GetMetricStatisticsResult().withDatapoints(
                    new Datapoint().withTimestamp(timestamp).withAverage(1.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
            new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("HTTPCode_Backend_2XX"))))
            .thenReturn(new GetMetricStatisticsResult().withDatapoints(
                    new Datapoint().withTimestamp(timestamp).withAverage(1.0)));

    assertMetricTimestampEquals(registry, "aws_elb_request_count_average", timestamp.getTime());
    assertMetricTimestampEquals(registry, "aws_elb_httpcode_backend_2_xx_average", null);

  }

  void assertMetricTimestampEquals(CollectorRegistry registry, String name, Long expectedTimestamp) {
    Enumeration<Collector.MetricFamilySamples> metricFamilySamplesEnumeration = registry.metricFamilySamples();
    Set<String> metricNames = new HashSet<String>();
    while(metricFamilySamplesEnumeration.hasMoreElements()) {
      Collector.MetricFamilySamples samples = metricFamilySamplesEnumeration.nextElement();
      for(Collector.MetricFamilySamples.Sample s: samples.samples) {
        metricNames.add(s.name);
        if(s.name.equals(name)) {
          assertEquals(expectedTimestamp, (Long)s.timestampMs);
          return;
        }
      }
    }
    fail(String.format("Metric %s not found in registry. Metrics found: %s", name, metricNames));
  }

  @Test
  public void testUsesNewestDatapoint() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
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
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName", cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB"), new Dimension().withName("ThisExtraDimensionIsIgnored").withValue("dummy")),
          new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
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
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB", cloudWatchClient, taggingClient).register(registry);
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "b", "myLB"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myOtherLB"}));
  }

  @Test
  public void testAllSelectDimensionsKnown() throws Exception {
    new CloudWatchCollector(
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB\n    AvailabilityZone:\n    - a\n    - b", cloudWatchClient, taggingClient).register(registry);
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
            new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
            .thenReturn(new GetMetricStatisticsResult().withDatapoints(
                    new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
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
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select_regex:\n    LoadBalancerName:\n    - myLB(.*)", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest) argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB1")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancerName").withValue("myLB2")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myOtherLB"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest) argThat(
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
            "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancerName\n  aws_dimension_select:\n    LoadBalancerName:\n    - myLB", cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName"))))
        .thenReturn(new ListMetricsResult().withNextToken("ABC"));
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancerName").NextToken("ABC"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancerName").withValue("myLB"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancerName", "myLB"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "", "a", "myLB"}), .01);
  }

  @Test
  public void testExtendedStatistics() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: Latency\n  aws_extended_statistics:\n  - p95\n  - p99.99", cloudWatchClient, taggingClient).register(registry);

    HashMap<String, Double> extendedStatistics = new HashMap<String, Double>();
    extendedStatistics.put("p95", 1.0);
    extendedStatistics.put("p99.99", 2.0);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("Latency"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withExtendedStatistics(extendedStatistics)));

    assertEquals(1.0, registry.getSampleValue("aws_elb_latency_p95", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_latency_p99_99", new String[]{"job", "instance"}, new String[]{"aws_elb", ""}), .01);
  }

  @Test
  public void testDynamoIndexDimensions() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: OnlineIndexConsumedWriteCapacity\n  aws_dimensions:\n  - TableName\n  - GlobalSecondaryIndexName\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: ConsumedReadCapacityUnits\n  aws_dimensions:\n  - TableName", cloudWatchClient, taggingClient).register(registry);
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex"))));
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimensions("TableName", "GlobalSecondaryIndexName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"), new Dimension().withName("GlobalSecondaryIndexName").withValue("myIndex"))));
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimensions("TableName"))))
        .thenReturn(new ListMetricsResult().withMetrics(
          new Metric().withDimensions(new Dimension().withName("TableName").withValue("myTable"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(1.0)));
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("OnlineIndexConsumedWriteCapacity").Dimension("TableName", "myTable").Dimension("GlobalSecondaryIndexName", "myIndex"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(2.0)));
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("ConsumedReadCapacityUnits").Dimension("TableName", "myTable"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(3.0)));

    assertEquals(1.0, registry.getSampleValue("aws_dynamodb_consumed_read_capacity_units_index_sum", new String[]{"job", "instance", "table_name", "global_secondary_index_name"}, new String[]{"aws_dynamodb", "", "myTable", "myIndex"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_dynamodb_online_index_consumed_write_capacity_sum", new String[]{"job", "instance", "table_name", "global_secondary_index_name"}, new String[]{"aws_dynamodb", "", "myTable", "myIndex"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_dynamodb_consumed_read_capacity_units_sum", new String[]{"job", "instance", "table_name"}, new String[]{"aws_dynamodb", "", "myTable"}), .01);
  }
  
  @Test
  public void testDynamoNoDimensions() throws Exception {
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/DynamoDB\n  aws_metric_name: AccountProvisionedReadCapacityUtilization\n", cloudWatchClient, taggingClient).register(registry);

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/DynamoDB").MetricName("AccountProvisionedReadCapacityUtilization"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withSum(1.0)));

    assertEquals(1.0, registry.getSampleValue("aws_dynamodb_account_provisioned_read_capacity_utilization_sum", new String[]{"job", "instance"}, new String[]{"aws_dynamodb", ""}), .01);
  }
  @Test
  public void testTagSelectEC2() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n", 
        cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().ResourceTypeFilter("ec2:instance").TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(new GetResourcesResult().withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")));
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimensions("InstanceId"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-1")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-2"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    assertEquals(2.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-1"}), .01);
    assertNull(registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-2"}));
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"}), .01);
  }
  
  @Test
  public void testTagSelectALB() throws Exception {
    // Testing "aws_tag_select" with an ALB, which have a fairly complex ARN 
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/ApplicationELB\n  aws_metric_name: RequestCount\n  aws_dimensions:\n  - AvailabilityZone\n  - LoadBalancer\n  aws_tag_select:\n    resource_type_selection: \"elasticloadbalancing:loadbalancer/app\"\n    resource_id_dimension: LoadBalancer\n    tag_selections:\n      Monitoring: [enabled]\n", 
        cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().ResourceTypeFilter("elasticloadbalancing:loadbalancer/app").TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(new GetResourcesResult().withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:elasticloadbalancing:us-east-1:121212121212:loadbalancer/app/myLB/123")));
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/ApplicationELB").MetricName("RequestCount").Dimensions("AvailabilityZone", "LoadBalancer"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancer").withValue("app/myLB/123")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("b"), new Dimension().withName("LoadBalancer").withValue("app/myLB/123")),
            new Metric().withDimensions(new Dimension().withName("AvailabilityZone").withValue("a"), new Dimension().withName("LoadBalancer").withValue("app/myOtherLB/456"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ApplicationELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ApplicationELB").MetricName("RequestCount").Dimension("AvailabilityZone", "b").Dimension("LoadBalancer", "app/myLB/123"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ApplicationELB").MetricName("RequestCount").Dimension("AvailabilityZone", "a").Dimension("LoadBalancer", "app/myOtherLB/456"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(4.0)));
    
    assertEquals(2.0, registry.getSampleValue("aws_applicationelb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer"}, new String[]{"aws_applicationelb", "", "a", "app/myLB/123"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_applicationelb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer"}, new String[]{"aws_applicationelb", "", "b", "app/myLB/123"}), .01);
    assertNull(registry.getSampleValue("aws_applicationelb_request_count_average", new String[]{"job", "instance", "availability_zone", "load_balancer"}, new String[]{"aws_applicationelb", "", "a", "app/myOtherLB/456"}));
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "load_balancer", "tag_Monitoring"}, new String[]{"aws_applicationelb", "", "arn:aws:elasticloadbalancing:us-east-1:121212121212:loadbalancer/app/myLB/123", "app/myLB/123", "enabled"}), .01);
  }
  
  @Test
  public void testTagSelectUsesPaginationToken() throws Exception {
    // Testing "aws_tag_select" with an EC2
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n", 
        cloudWatchClient, taggingClient).register(registry);

    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().ResourceTypeFilter("ec2:instance").TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(new GetResourcesResult().withPaginationToken("ABC").withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1")));
    
    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().PaginationToken("ABC").ResourceTypeFilter("ec2:instance").TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(new GetResourcesResult().withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")));
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimensions("InstanceId"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-1")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-2"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));

    assertEquals(2.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-1"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-2"}), .01);
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"}), .01);
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"}), .01);
  }
  
  @Test
  public void testNoSelection() throws Exception {
    // When no selection is made, all metrics should be returned
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n", 
        cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimensions("InstanceId"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-1")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-2"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));

    assertEquals(2.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-1"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-2"}), .01);
  }
  
  @Test
  public void testMultipleSelection() throws Exception {
    // When multiple selections are made, "and" logic should be applied on metrics
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n    tag_selections:\n      Monitoring: [enabled]\n  aws_dimension_select:\n    InstanceId: [\"i-1\"]", 
        cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().ResourceTypeFilter("ec2:instance").TagFilter("Monitoring", Arrays.asList("enabled")))))
        .thenReturn(new GetResourcesResult().withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1"),
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")));
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimensions("InstanceId"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-1")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-2"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));

    assertEquals(2.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-1"}), .01);
    assertNull(registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-2"}));
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"}), .01);
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"}), .01);
  }
  
  @Test
  public void testOptionalTagSelection() throws Exception {
    // aws_tag_select can be used without tag_selection to activate the aws_resource_info metric on tagged (or previously tagged) resources
    new CloudWatchCollector(
        "---\nregion: reg\nmetrics:\n- aws_namespace: AWS/EC2\n  aws_metric_name: CPUUtilization\n  aws_dimensions:\n  - InstanceId\n  aws_tag_select:\n    resource_type_selection: \"ec2:instance\"\n    resource_id_dimension: InstanceId\n  aws_dimension_select:\n    InstanceId: [\"i-1\", \"i-no-tag\"]", 
        cloudWatchClient, taggingClient).register(registry);
    
    Mockito.when(taggingClient.getResources((GetResourcesRequest)argThat(
        new GetResourcesRequestMatcher().ResourceTypeFilter("ec2:instance"))))
        .thenReturn(new GetResourcesResult().withResourceTagMappingList(
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-1"),
            new ResourceTagMapping().withTags(new Tag().withKey("Monitoring").withValue("enabled")).withResourceARN("arn:aws:ec2:us-east-1:121212121212:instance/i-2")));
    
    Mockito.when(cloudWatchClient.listMetrics((ListMetricsRequest)argThat(
        new ListMetricsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimensions("InstanceId"))))
        .thenReturn(new ListMetricsResult().withMetrics(
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-1")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-2")),
            new Metric().withDimensions(new Dimension().withName("InstanceId").withValue("i-no-tag"))));

    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-1"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(2.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-2"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(3.0)));
    
    Mockito.when(cloudWatchClient.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/EC2").MetricName("CPUUtilization").Dimension("InstanceId", "i-no-tag"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(4.0)));

    assertEquals(2.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-1"}), .01);
    assertNull(registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-2"}));
    assertEquals(4.0, registry.getSampleValue("aws_ec2_cpuutilization_average", new String[]{"job", "instance", "instance_id"}, new String[]{"aws_ec2", "", "i-no-tag"}), .01);
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-1", "i-1", "enabled"}), .01);
    assertEquals(1.0, registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-2", "i-2", "enabled"}), .01);
    assertNull(registry.getSampleValue("aws_resource_info", new String[]{"job", "instance", "arn", "instance_id", "tag_Monitoring"}, new String[]{"aws_ec2", "", "arn:aws:ec2:us-east-1:121212121212:instance/i-no-tag", "i-no-tag", "enabled"}));
  }
}
