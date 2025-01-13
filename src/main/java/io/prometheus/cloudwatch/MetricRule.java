package io.prometheus.cloudwatch;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

class MetricRule {
  String awsNamespace;
  String awsMetricName;
  List<String> awsAccountIds;  // support for multiple account ids
  String awsAccountLabel;  // label to use for account id
  int periodSeconds;
  int rangeSeconds;
  int delaySeconds;
  List<Statistic> awsStatistics;
  List<String> awsExtendedStatistics;
  List<String> awsDimensions;
  Map<String, List<String>> awsDimensionSelect;
  Map<String, List<String>> awsDimensionSelectRegex;
  CloudWatchCollector.AWSTagSelect awsTagSelect;
  String help;
  boolean cloudwatchTimestamp;
  boolean useGetMetricData;
  Duration listMetricsCacheTtl;
  boolean warnOnEmptyListDimensions;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MetricRule that = (MetricRule) o;

    if (periodSeconds != that.periodSeconds) return false;
    if (rangeSeconds != that.rangeSeconds) return false;
    if (delaySeconds != that.delaySeconds) return false;
    if (cloudwatchTimestamp != that.cloudwatchTimestamp) return false;
    if (useGetMetricData != that.useGetMetricData) return false;
    if (!Objects.equals(awsNamespace, that.awsNamespace)) return false;
    if (!Objects.equals(awsMetricName, that.awsMetricName)) return false;
    if (!Objects.equals(awsStatistics, that.awsStatistics)) return false;
    if (!Objects.equals(awsExtendedStatistics, that.awsExtendedStatistics)) return false;
    if (!Objects.equals(awsDimensions, that.awsDimensions)) return false;
    if (!Objects.equals(awsDimensionSelect, that.awsDimensionSelect)) return false;
    if (!Objects.equals(awsDimensionSelectRegex, that.awsDimensionSelectRegex)) return false;
    if (!Objects.equals(awsTagSelect, that.awsTagSelect)) return false;
    if (!Objects.equals(help, that.help)) return false;
    return Objects.equals(listMetricsCacheTtl, that.listMetricsCacheTtl);
  }

  @Override
  public int hashCode() {
    int result = awsNamespace != null ? awsNamespace.hashCode() : 0;
    result = 31 * result + (awsMetricName != null ? awsMetricName.hashCode() : 0);
    result = 31 * result + periodSeconds;
    result = 31 * result + rangeSeconds;
    result = 31 * result + delaySeconds;
    result = 31 * result + (awsStatistics != null ? awsStatistics.hashCode() : 0);
    result = 31 * result + (awsExtendedStatistics != null ? awsExtendedStatistics.hashCode() : 0);
    result = 31 * result + (awsDimensions != null ? awsDimensions.hashCode() : 0);
    result = 31 * result + (awsDimensionSelect != null ? awsDimensionSelect.hashCode() : 0);
    result =
        31 * result + (awsDimensionSelectRegex != null ? awsDimensionSelectRegex.hashCode() : 0);
    result = 31 * result + (awsTagSelect != null ? awsTagSelect.hashCode() : 0);
    result = 31 * result + (help != null ? help.hashCode() : 0);
    result = 31 * result + (cloudwatchTimestamp ? 1 : 0);
    result = 31 * result + (useGetMetricData ? 1 : 0);
    result = 31 * result + (listMetricsCacheTtl != null ? listMetricsCacheTtl.hashCode() : 0);
    return result;
  }
}
