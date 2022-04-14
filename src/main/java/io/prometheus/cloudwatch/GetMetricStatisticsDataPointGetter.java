package io.prometheus.cloudwatch;

import java.util.Date;
import java.util.List;

import io.prometheus.cloudwatch.CloudWatchCollector.MetricRule;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import io.prometheus.client.Counter;

class GetMetricStatisticsDataPointGetter implements DataPointsGetter {
    private long start;
    private MetricRule rule;
    private CloudWatchClient client;
    private Counter counter;
    
    GetMetricStatisticsDataPointGetter(CloudWatchClient client, long start,MetricRule rule, Counter counter){
        this.client = client;
        this.start = start;
        this.rule = rule;
        this.counter = counter;
    }
    
    private GetMetricStatisticsRequest.Builder metricStatisticsRequestBuilder(){
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
    public List<Datapoint> GetDataPoints(List<Dimension> dimensions) {
        GetMetricStatisticsRequest.Builder builder = metricStatisticsRequestBuilder();
        builder.dimensions(dimensions);
        GetMetricStatisticsResponse response = client.getMetricStatistics(builder.build());
        counter.labels("getMetricStatistics", rule.awsNamespace).inc();
        return response.datapoints();
    }
    
}
