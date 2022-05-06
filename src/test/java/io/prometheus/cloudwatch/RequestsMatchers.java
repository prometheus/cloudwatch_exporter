package io.prometheus.cloudwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.DimensionFilter;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.GetResourcesRequest;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.TagFilter;

public class RequestsMatchers {

  static class ListMetricsRequestMatcher extends BaseMatcher<ListMetricsRequest> {
    String namespace;
    String metricName;
    String nextToken;
    String recentlyActive;
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
        this.dimensions.add(DimensionFilter.builder().name(dimensions[i]).build());
      }
      return this;
    }

    public ListMetricsRequestMatcher RecentlyActive(String recentlyActive) {
      this.recentlyActive = recentlyActive;
      return this;
    }

    public boolean matches(Object o) {
      ListMetricsRequest request = (ListMetricsRequest) o;
      if (request == null) return false;
      if (namespace != null && !namespace.equals(request.namespace())) {
        return false;
      }
      if (metricName != null && !metricName.equals(request.metricName())) {
        return false;
      }
      if (nextToken == null ^ request.nextToken() == null) {
        return false;
      }
      if (nextToken != null && !nextToken.equals(request.nextToken())) {
        return false;
      }
      if (!dimensions.equals(request.dimensions())) {
        return false;
      }
      if (recentlyActive != null && !recentlyActive.equals(request.recentlyActive())) {
        return false;
      }
      return true;
    }

    public void describeTo(Description description) {
      description.appendText("list metrics request");
    }
  }

  static class GetMetricStatisticsRequestMatcher extends BaseMatcher<GetMetricStatisticsRequest> {
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
      dimensions.add(Dimension.builder().name(name).value(value).build());
      return this;
    }

    public GetMetricStatisticsRequestMatcher Period(int period) {
      this.period = period;
      return this;
    }

    public boolean matches(Object o) {
      GetMetricStatisticsRequest request = (GetMetricStatisticsRequest) o;
      if (request == null) return false;
      if (namespace != null && !namespace.equals(request.namespace())) {
        return false;
      }
      if (metricName != null && !metricName.equals(request.metricName())) {
        return false;
      }
      if (!dimensions.equals(request.dimensions())) {
        return false;
      }
      if (period != null && !period.equals(request.period())) {
        return false;
      }
      return true;
    }

    public void describeTo(Description description) {
      description.appendText("get metrics statistics request");
    }
  }

  static class GetResourcesRequestMatcher extends BaseMatcher<GetResourcesRequest> {
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

    public GetResourcesRequestMatcher TagFilter(String key, List<String> values) {
      tagFilters.add(TagFilter.builder().key(key).values(values).build());
      return this;
    }

    public boolean matches(Object o) {
      GetResourcesRequest request = (GetResourcesRequest) o;
      if (request == null) return false;
      if (paginationToken == "" ^ request.paginationToken() == "") {
        return false;
      }
      if (paginationToken != "" && !paginationToken.equals(request.paginationToken())) {
        return false;
      }
      if (!resourceTypeFilters.equals(request.resourceTypeFilters())) {
        return false;
      }
      if (!tagFilters.equals(request.tagFilters())) {
        return false;
      }
      return true;
    }

    public void describeTo(Description description) {
      description.appendText("get resources request");
    }
  }

  static class MetricMatcher extends BaseMatcher<Metric> {
    String namespace;
    String metricName;
    List<Dimension> dimensions = new ArrayList<Dimension>();

    public MetricMatcher Namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public MetricMatcher MetricName(String metricName) {
      this.metricName = metricName;
      return this;
    }

    public MetricMatcher Dimension(String name, String value) {
      dimensions.add(Dimension.builder().name(name).value(value).build());
      return this;
    }

    @Override
    public boolean matches(Object o) {
      Metric metric = (Metric) o;
      if (metric == null) return false;
      if (namespace != null && !namespace.equals(metric.namespace())) {
        return false;
      }
      if (metricName != null && !metricName.equals(metric.metricName())) {
        return false;
      }
      if (!dimensions.equals(metric.dimensions())) {
        return false;
      }
      return true;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Metric");
      List<String> properties = new ArrayList<>();
      if (namespace != null) {
        properties.add(String.format("namespace=%s", namespace));
      }
      if (metricName != null) {
        properties.add(String.format("metricName=%s", metricName));
      }
      String dimentions =
          dimensions.stream()
              .map((d) -> d.name() + ":" + d.value())
              .collect(Collectors.joining(", "));
      properties.add(String.format("dimentions=[%s]", dimentions));
      description.appendValueList("(", ",", ")", properties);
    }
  }

  static class MetricStatMatcher extends BaseMatcher<MetricStat> {
    MetricMatcher metricMatcher;
    String stat;
    Integer period;

    public MetricStatMatcher metric(MetricMatcher metricMatcher) {
      this.metricMatcher = metricMatcher;
      return this;
    }

    public MetricStatMatcher Period(int period) {
      this.period = period;
      return this;
    }

    public MetricStatMatcher Stat(String stat) {
      this.stat = stat;
      return this;
    }

    @Override
    public boolean matches(Object o) {
      MetricStat metricStat = (MetricStat) o;
      if (metricStat == null) return false;
      if (period != null && !period.equals(metricStat.period())) {
        return false;
      }
      if (metricMatcher != null && !metricMatcher.matches(metricStat.metric())) {
        return false;
      }
      if (stat != null && !stat.equals(metricStat.stat())) {
        return false;
      }
      return true;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("A metric stat");
      if (stat != null) {
        description.appendText(String.format(" with stat %s", stat));
      }
      if (period != null) {
        description.appendText(String.format(" with period %s", period));
      }
      if (metricMatcher != null) {
        description.appendText(" with metric ");
        metricMatcher.describeTo(description);
      }
    }
  }

  static class MetricDataQueryMatcher extends BaseMatcher<MetricDataQuery> {
    String label;
    MetricStatMatcher metricStat;

    public MetricDataQueryMatcher Label(String label) {
      this.label = label;
      return this;
    }

    public MetricDataQueryMatcher MetricStat(MetricStatMatcher metricStat) {
      this.metricStat = metricStat;
      return this;
    }

    @Override
    public boolean matches(Object o) {
      MetricDataQuery query = (MetricDataQuery) o;
      if (query == null) return false;
      if (label != null && !label.equals(query.label())) {
        return false;
      }
      if (metricStat != null && !metricStat.matches(query.metricStat())) {
        return false;
      }
      return true;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("metric data query");
      if (label != null) {
        description.appendText(String.format(" with label '%s'", label));
      }
      if (metricStat != null) {
        description.appendText(" with stat: ");
        metricStat.describeTo(description);
      }
    }
  }

  static class GetMetricDataRequestMatcher extends BaseMatcher<GetMetricDataRequest> {
    List<MetricDataQueryMatcher> queries = new ArrayList<>();

    public GetMetricDataRequestMatcher Query(MetricDataQueryMatcher query) {
      this.queries.add(query);
      return this;
    }

    private Matcher<Iterable<MetricDataQuery>> queriesMatcher() {
      return Matchers.hasItems(queries.stream().toArray(MetricDataQueryMatcher[]::new));
    }

    @Override
    public boolean matches(Object o) {
      GetMetricDataRequest request = (GetMetricDataRequest) o;
      if (request == null) {
        return false;
      }
      if (!queries.isEmpty() && !queriesMatcher().matches(request.metricDataQueries())) {
        return false;
      }
      ;
      return true;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("a GetMetricDataRequest");
      if (!queries.isEmpty()) {
        description.appendText("with queries:\n");
        for (MetricDataQueryMatcher qmatcher : queries) {
          description.appendText("\t- ");
          qmatcher.describeTo(description);
          description.appendText("\n");
        }
      }
    }
  }
}
