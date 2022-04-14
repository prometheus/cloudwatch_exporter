package io.prometheus.cloudwatch;

import java.util.List;

import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

interface DataPointsGetter {
    List<Datapoint> GetDataPoints(List<Dimension> dimensions);
}
