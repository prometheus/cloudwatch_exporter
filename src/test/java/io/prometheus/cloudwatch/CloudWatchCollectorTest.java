package io.prometheus.cloudwatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.argThat;

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
import java.util.List;

import org.json.simple.parser.ParseException;
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
  public void testMetricPeriod() throws ParseException {
    new CloudWatchCollector(
            "{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, `period_seconds`: 100, `range_seconds`: 200, `delay_seconds`: 300}]}"
                    .replace('`', '"'), client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job"}, new String[]{"aws_elb"});


    Mockito.verify(client).getMetricStatistics((GetMetricStatisticsRequest) argThat(
            new GetMetricStatisticsRequestMatcher()
                    .Namespace("AWS/ELB")
                    .MetricName("RequestCount")
                    .Period(100)
    ));
  }

  @Test
  public void testDefaultPeriod() throws ParseException {
    new CloudWatchCollector(
            "{`region`: `reg`, `period_seconds`: 100, `range_seconds`: 200, `delay_seconds`: 300, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`}]}"
                    .replace('`', '"'), client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest) anyObject()))
            .thenReturn(new GetMetricStatisticsResult());

    registry.getSampleValue("aws_elb_request_count_average", new String[]{"job"}, new String[]{"aws_elb"});


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
        "{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`}]}"
        .replace('`', '"'), client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date()).withAverage(1.0)
                .withMaximum(2.0).withMinimum(3.0).withSampleCount(4.0).withSum(5.0)));

    assertEquals(1.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job"}, new String[]{"aws_elb"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_maximum", new String[]{"job"}, new String[]{"aws_elb"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_minimum", new String[]{"job"}, new String[]{"aws_elb"}), .01);
    assertEquals(4.0, registry.getSampleValue("aws_elb_request_count_sample_count", new String[]{"job"}, new String[]{"aws_elb"}), .01);
    assertEquals(5.0, registry.getSampleValue("aws_elb_request_count_sum", new String[]{"job"}, new String[]{"aws_elb"}), .01);
  }

  @Test
  public void testUsesNewestDatapoint() throws Exception {
    new CloudWatchCollector(
        "{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`}]}"
        .replace('`', '"'), client).register(registry);

    Mockito.when(client.getMetricStatistics((GetMetricStatisticsRequest)argThat(
        new GetMetricStatisticsRequestMatcher().Namespace("AWS/ELB").MetricName("RequestCount"))))
        .thenReturn(new GetMetricStatisticsResult().withDatapoints(
            new Datapoint().withTimestamp(new Date(1)).withAverage(1.0),
            new Datapoint().withTimestamp(new Date(3)).withAverage(3.0),
            new Datapoint().withTimestamp(new Date(2)).withAverage(2.0)));

    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job"}, new String[]{"aws_elb"}), .01);
  }

  @Test
  public void testDimensions() throws Exception {
    new CloudWatchCollector(
        ("{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, "
        + "`aws_dimensions`: [`AvailabilityZone`, `LoadBalancerName`]}]}")
        .replace('`', '"'), client).register(registry);
    
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

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "a", "myLB"}), .01);
    assertEquals(3.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "b", "myOtherLB"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name", "this_extra_dimension_is_ignored"}, new String[]{"aws_elb", "a", "myLB", "dummy"}));
  }

  @Test
  public void testDimensionSelect() throws Exception {
    new CloudWatchCollector(
        ("{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, "
            + "`aws_dimensions`: [`AvailabilityZone`, `LoadBalancerName`], `aws_dimension_select`: {`LoadBalancerName`: [`myLB`]}}]}")
            .replace('`', '"'), client).register(registry);

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

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "a", "myLB"}), .01);
    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "b", "myLB"}), .01);
    assertNull(registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "a", "myOtherLB"}));
  }

  @Test
  public void testGetDimensionsUsesNextToken() throws Exception {
    new CloudWatchCollector(
        ("{`region`: `reg`, `metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, "
        + "`aws_dimensions`: [`AvailabilityZone`, `LoadBalancerName`]}]}")
        .replace('`', '"'), client).register(registry);
    
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

    assertEquals(2.0, registry.getSampleValue("aws_elb_request_count_average", new String[]{"job", "availability_zone", "load_balancer_name"}, new String[]{"aws_elb", "a", "myLB"}), .01);
  }
}
