package io.prometheus.cloudwatch;

import io.prometheus.client.Counter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.DimensionFilter;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;

final class DefaultDimensionSource implements DimensionSource {

  private static final Logger LOGGER = Logger.getLogger(DefaultDimensionSource.class.getName());
  private final Counter cloudwatchRequests;
  private final CloudWatchClient cloudWatchClient;

  public DefaultDimensionSource(CloudWatchClient cloudWatchClient, Counter cloudwatchRequests) {
    this.cloudWatchClient = cloudWatchClient;
    this.cloudwatchRequests = cloudwatchRequests;
  }

  public DimensionData getDimensions(MetricRule rule, List<String> tagBasedResourceIds) {
    if (rule.awsDimensions != null
        && rule.awsDimensionSelect != null
        && !rule.awsDimensions.isEmpty()
        && rule.awsDimensions.size() == rule.awsDimensionSelect.size()
        && rule.awsDimensionSelect.keySet().containsAll(rule.awsDimensions)
        && rule.awsTagSelect == null) {
      // The full list of dimensions is known so no need to request it from cloudwatch.
      return new DimensionData(permuteDimensions(rule.awsDimensions, rule.awsDimensionSelect));
    } else {
      return new DimensionData(listDimensions(rule, tagBasedResourceIds, cloudWatchClient));
    }
  }

  private List<List<Dimension>> permuteDimensions(
      List<String> dimensions, Map<String, List<String>> dimensionValues) {
    ArrayList<List<Dimension>> result = new ArrayList<>();

    if (dimensions.isEmpty()) {
      result.add(new ArrayList<>());
    } else {
      List<String> dimensionsCopy = new ArrayList<>(dimensions);
      String dimensionName = dimensionsCopy.remove(dimensionsCopy.size() - 1);
      for (List<Dimension> permutation : permuteDimensions(dimensionsCopy, dimensionValues)) {
        for (String dimensionValue : dimensionValues.get(dimensionName)) {
          Dimension.Builder dimensionBuilder = Dimension.builder();
          dimensionBuilder.value(dimensionValue);
          dimensionBuilder.name(dimensionName);
          ArrayList<Dimension> permutationCopy = new ArrayList<>(permutation);
          permutationCopy.add(dimensionBuilder.build());
          result.add(permutationCopy);
        }
      }
    }
    return result;
  }

  private List<List<Dimension>> listDimensions(
      MetricRule rule, List<String> tagBasedResourceIds, CloudWatchClient cloudWatchClient) {
    List<List<Dimension>> dimensions = new ArrayList<>();
    if (rule.awsDimensions == null) {
      dimensions.add(new ArrayList<>());
      return dimensions;
    }

    ListMetricsRequest.Builder requestBuilder = ListMetricsRequest.builder();
    requestBuilder.namespace(rule.awsNamespace);
    requestBuilder.metricName(rule.awsMetricName);

    // 10800 seconds is 3 hours, this setting causes metrics older than 3 hours to not be listed
    if (rule.rangeSeconds < 10800) {
      requestBuilder.recentlyActive("PT3H");
    }

    List<DimensionFilter> dimensionFilters = new ArrayList<>();
    for (String dimension : rule.awsDimensions) {
      dimensionFilters.add(DimensionFilter.builder().name(dimension).build());
    }
    requestBuilder.dimensions(dimensionFilters);

    String nextToken = null;
    do {
      requestBuilder.nextToken(nextToken);
      ListMetricsResponse response = cloudWatchClient.listMetrics(requestBuilder.build());
      cloudwatchRequests.labels("listMetrics", rule.awsNamespace).inc();
      for (Metric metric : response.metrics()) {
        if (metric.dimensions().size() != dimensionFilters.size()) {
          // AWS returns all the metrics with dimensions beyond the ones we ask for,
          // so filter them out.
          continue;
        }
        if (useMetric(rule, tagBasedResourceIds, metric)) {
          dimensions.add(metric.dimensions());
        }
      }
      nextToken = response.nextToken();
    } while (nextToken != null);
    if (rule.warnOnEmptyListDimensions && dimensions.isEmpty()) {
      LOGGER.warning(
          String.format(
              "(listDimensions) ignoring metric %s:%s due to dimensions mismatch",
              rule.awsNamespace, rule.awsMetricName));
    }
    return dimensions;
  }

  /**
   * Check if a metric should be used according to `aws_dimension_select`,
   * `aws_dimension_select_regex` and dynamic `aws_tag_select`
   */
  private boolean useMetric(MetricRule rule, List<String> tagBasedResourceIds, Metric metric) {
    if (rule.awsDimensionSelect != null && !metricsIsInAwsDimensionSelect(rule, metric)) {
      return false;
    }
    if (rule.awsDimensionSelectRegex != null && !metricIsInAwsDimensionSelectRegex(rule, metric)) {
      return false;
    }
    if (rule.awsTagSelect != null && !metricIsInAwsTagSelect(rule, tagBasedResourceIds, metric)) {
      return false;
    }
    return true;
  }

  /** Check if a metric is matched in `aws_dimension_select` */
  private boolean metricsIsInAwsDimensionSelect(MetricRule rule, Metric metric) {
    Set<String> dimensionSelectKeys = rule.awsDimensionSelect.keySet();
    for (Dimension dimension : metric.dimensions()) {
      String dimensionName = dimension.name();
      String dimensionValue = dimension.value();
      if (dimensionSelectKeys.contains(dimensionName)) {
        List<String> allowedDimensionValues = rule.awsDimensionSelect.get(dimensionName);
        if (!allowedDimensionValues.contains(dimensionValue)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Check if a metric is matched in `aws_dimension_select_regex` */
  private boolean metricIsInAwsDimensionSelectRegex(MetricRule rule, Metric metric) {
    Set<String> dimensionSelectRegexKeys = rule.awsDimensionSelectRegex.keySet();
    for (Dimension dimension : metric.dimensions()) {
      String dimensionName = dimension.name();
      String dimensionValue = dimension.value();
      if (dimensionSelectRegexKeys.contains(dimensionName)) {
        List<String> allowedDimensionValues = rule.awsDimensionSelectRegex.get(dimensionName);
        if (!regexListMatch(allowedDimensionValues, dimensionValue)) {
          return false;
        }
      }
    }
    return true;
  }

  /** Check if any regex string in a list matches a given input value */
  protected static boolean regexListMatch(List<String> regexList, String input) {
    for (String regex : regexList) {
      if (Pattern.matches(regex, input)) {
        return true;
      }
    }
    return false;
  }

  /** Check if a metric is matched in `aws_tag_select` */
  private boolean metricIsInAwsTagSelect(
      MetricRule rule, List<String> tagBasedResourceIds, Metric metric) {
    if (rule.awsTagSelect.tagSelections == null) {
      return true;
    }
    for (Dimension dimension : metric.dimensions()) {
      String dimensionName = dimension.name();
      String dimensionValue = dimension.value();
      if (rule.awsTagSelect.resourceIdDimension.equals(dimensionName)
          && !tagBasedResourceIds.contains(dimensionValue)) {
        return false;
      }
    }
    return true;
  }
}
