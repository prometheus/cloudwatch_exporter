package io.prometheus.cloudwatch;

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.prometheus.cloudwatch.CloudWatchCollector.MetricRule;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import io.prometheus.client.Counter;

class GetMetricStatisticsDataGetter implements DataGetter {
    private long start;
    private MetricRule rule;
    private CloudWatchClient client;
    private Counter apiRequestsCounter;
    private Counter metricsRequestedCounter;

    GetMetricStatisticsDataGetter(CloudWatchClient client, long start, MetricRule rule, Counter apiRequestsCounter, Counter metricsRequestedCounter) {
        this.client = client;
        this.start = start;
        this.rule = rule;
        this.apiRequestsCounter = apiRequestsCounter;
        this.metricsRequestedCounter = metricsRequestedCounter;
    }

    private GetMetricStatisticsRequest.Builder metricStatisticsRequestBuilder() {
        Date startDate = new Date(start - 1000 * rule.delaySeconds);
        Date endDate = new Date(start - 1000 * (rule.delaySeconds + rule.rangeSeconds));
        GetMetricStatisticsRequest.Builder builder = GetMetricStatisticsRequest.builder();
        builder.namespace(rule.awsNamespace);
        builder.metricName(rule.awsMetricName);
        builder.statistics(rule.awsStatistics);
        builder.extendedStatistics(rule.awsExtendedStatistics);
        builder.endTime(startDate.toInstant());
        builder.startTime(endDate.toInstant());
        builder.period(rule.periodSeconds);
        return builder;
    }

    @Override
    public MetricRuleData metricRuleDataFor(List<Dimension> dimensions) {
        GetMetricStatisticsRequest.Builder builder = metricStatisticsRequestBuilder();
        builder.dimensions(dimensions);
        GetMetricStatisticsResponse response = client.getMetricStatistics(builder.build());
        apiRequestsCounter.labels("getMetricStatistics", rule.awsNamespace).inc();
        metricsRequestedCounter.labels(rule.awsMetricName, rule.awsNamespace).inc();
        Datapoint latestDp = getNewestDatapoint(response.datapoints());
        return toMetricValues(latestDp);
    }

    private Datapoint getNewestDatapoint(java.util.List<Datapoint> datapoints) {
        Datapoint newest = null;
        for (Datapoint d : datapoints) {
            if (newest == null || newest.timestamp().isBefore(d.timestamp())) {
                newest = d;
            }
        }
        return newest;
    }

    private MetricRuleData toMetricValues(Datapoint dp) {
        if (dp == null) {
            return null;
        }
        MetricRuleData values = new MetricRuleData(dp.timestamp());
        if (dp.sum() != null) {
            values.statisticValues.put(Statistic.SUM, dp.sum());
        }
        if (dp.sampleCount() != null) {
            values.statisticValues.put(Statistic.SAMPLE_COUNT, dp.sampleCount());
        }
        if (dp.minimum() != null) {
            values.statisticValues.put(Statistic.MINIMUM, dp.minimum());
        }
        if (dp.maximum() != null) {
            values.statisticValues.put(Statistic.MAXIMUM, dp.maximum());
        }
        if (dp.average() != null) {
            values.statisticValues.put(Statistic.AVERAGE, dp.average());
        }
        if (dp.extendedStatistics() != null) {
            for (Map.Entry<String, Double> entry : dp.extendedStatistics().entrySet()) {
                values.extendedValues.put(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }
}
