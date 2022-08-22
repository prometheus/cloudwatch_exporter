package io.prometheus.cloudwatch;

import java.util.List;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

interface DimensionSource {

  DimensionData getDimensions(MetricRule rule, List<String> tagBasedResourceIds);

  class DimensionData {
    private final List<List<Dimension>> dimensions;

    DimensionData(List<List<Dimension>> dimensions) {
      this.dimensions = dimensions;
    }

    List<List<Dimension>> getDimensions() {
      return dimensions;
    }
  }
}
