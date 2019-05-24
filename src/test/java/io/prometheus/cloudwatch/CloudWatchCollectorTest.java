package io.prometheus.cloudwatch;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import io.prometheus.client.CollectorRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

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

  @Test
  public void testGetMonitoringEndpoint() throws Exception {
    Region usEast = RegionUtils.getRegion("us-east-1");
    CloudWatchCollector us_collector = new CloudWatchCollector("---\nregion: us-east-1\nmetrics: []\n");
    assertEquals("https://monitoring.us-east-1.amazonaws.com", us_collector.getMonitoringEndpoint(usEast));

    Region cnNorth = RegionUtils.getRegion("cn-north-1");
    CloudWatchCollector cn_collector = new CloudWatchCollector("---\nregion: cn-north-1\nmetrics: []\n");
    assertEquals("https://monitoring.cn-north-1.amazonaws.com.cn", cn_collector.getMonitoringEndpoint(cnNorth));
  }
}

