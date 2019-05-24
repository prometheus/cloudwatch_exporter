package io.prometheus.cloudwatch;

import java.util.Date;
import java.util.List;

final class CloudWatchApiCallsResult {
  final String statisticType;
  final Double value;
  final List<DimensionNameValueTuple> dimensions;
  final Long timestamp;
  final String unitName;

  public CloudWatchApiCallsResult(String statisticType, Double value, List<DimensionNameValueTuple> dimensions, Long timestamp, String unitName) {
    this.statisticType = statisticType;
    this.value = value;
    this.dimensions = dimensions;
    this.timestamp = timestamp;
    this.unitName = unitName;
  }
}
