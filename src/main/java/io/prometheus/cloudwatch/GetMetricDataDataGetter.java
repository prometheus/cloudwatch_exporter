package io.prometheus.cloudwatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.prometheus.cloudwatch.CloudWatchCollector.MetricRule;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.MetricStat;
import software.amazon.awssdk.services.cloudwatch.model.ScanBy;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import io.prometheus.client.Counter;

class GetMetricDataDataGetter implements DataGetter {
    private final static int MAX_QUERIES_PER_REQUEST = 500;
    private long start;
    private MetricRule rule;
    private CloudWatchClient client;
    private Counter counter;
    private Map<String, MetricRuleData> results;

    private static String dimentionToString(Dimension d) {
        return String.format("%s=%s", d.name(), d.value());
    }

    private static String dimentionsToKey(List<Dimension> dimentions) {
        return String.join(",", dimentions.stream().map(d -> dimentionToString(d)).sorted().toList());
    }

    private List<String> buildStatsList(MetricRule rule) {
        List<String> stats = new ArrayList<>();
        if (rule.awsStatistics != null) {
            stats.addAll(rule.awsStatistics.stream().map(s -> s.toString()).toList());
        }
        if (rule.awsExtendedStatistics != null) {
            stats.addAll(rule.awsExtendedStatistics);
        }
        return stats;
    }

    private List<MetricDataQuery> buildMetricDataQueries(MetricRule rule, List<List<Dimension>> dimentionsList) {
        List<MetricDataQuery> queries = new ArrayList<>();
        for (String stat : buildStatsList(rule)) {
            for (List<Dimension> dl : dimentionsList) {
                Metric metric = buildMetric(rule, dl);
                MetricStat metricStat = buildMetricStat(rule, stat, metric);
                MetricDataQuery query = buildQuery(stat, dl, metricStat);
                queries.add(query);
            }
        }
        return queries;
    }

    private MetricDataQuery buildQuery(String stat, List<Dimension> dl, MetricStat metric) {
        // random id - we don't care about it
        String id = "i" + UUID.randomUUID().toString().replace("-", "");
        MetricDataQuery.Builder builder = MetricDataQuery.builder();
        builder.id(id);

        // important - used to locate back the results
        String label = MetricLabels.labelFor(stat, dl);
        builder.label(label);
        builder.metricStat(metric);
        return builder.build();
    }

    private MetricStat buildMetricStat(MetricRule rule2, String stat, Metric metric) {
        MetricStat.Builder builder = MetricStat.builder();
        builder.period(rule.periodSeconds);
        builder.stat(stat);
        builder.metric(metric);
        return builder.build();
    }

    private Metric buildMetric(MetricRule rule2, List<Dimension> dl) {
        Metric.Builder builder = Metric.builder();
        builder.namespace(rule.awsNamespace);
        builder.metricName(rule.awsMetricName);
        builder.dimensions(dl);
        return builder.build();
    }

    public static <T> List<List<T>> partitionByMaxSize(List<T> list, int maxPartitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        List<T> remaining = list;
        while (remaining.size() > 0) {
            if (remaining.size() < maxPartitionSize) {
                partitions.add(remaining);
                break;
            } else {
                partitions.add(remaining.subList(0, maxPartitionSize));
                remaining = remaining.subList(maxPartitionSize, remaining.size());
            }
        }
        return partitions;
    }

    private List<GetMetricDataRequest> buildMetricDataRequests(MetricRule rule, List<List<Dimension>> dimentionsList) {
        Date startDate = new Date(start - 1000 * rule.delaySeconds);
        Date endDate = new Date(start - 1000 * (rule.delaySeconds + rule.rangeSeconds));
        GetMetricDataRequest.Builder builder = GetMetricDataRequest.builder();
        builder.endTime(startDate.toInstant());
        builder.startTime(endDate.toInstant());
        builder.scanBy(ScanBy.TIMESTAMP_DESCENDING);
        List<MetricDataQuery> queries = buildMetricDataQueries(rule, dimentionsList);
        List<GetMetricDataRequest> requests = new ArrayList<>();
        for (List<MetricDataQuery> queriesPartition : partitionByMaxSize(queries, MAX_QUERIES_PER_REQUEST)) {
            requests.add(builder.metricDataQueries(queriesPartition).build());
        }
        return requests;
    }

    private Map<String, MetricRuleData> fetchAllDataPoints(List<List<Dimension>> dimentionsList) {
        List<MetricDataResult> results = new ArrayList<>();
        for (GetMetricDataRequest request : buildMetricDataRequests(rule, dimentionsList)) {
            GetMetricDataResponse response = client.getMetricData(request);
            counter.labels("getMetricData", rule.awsNamespace).inc();
            results.addAll(response.metricDataResults());
        }
        return toMap(results);
    }

    private Map<String, MetricRuleData> toMap(List<MetricDataResult> metricDataResults) {
        Map<String, MetricRuleData> res = new HashMap<>();
        for (MetricDataResult dataResult : metricDataResults) {
            if (dataResult.timestamps().isEmpty() || dataResult.values().isEmpty()) {
                continue;
            }
            StatAndDimentions statAndDimentions = MetricLabels.decode(dataResult.label());
            String statString = statAndDimentions.stat;
            String labelsKey = statAndDimentions.dimetionsAsString;
            Instant timestamp = dataResult.timestamps().get(0);
            Double value = dataResult.values().get(0);
            MetricRuleData vals = res.getOrDefault(labelsKey, new MetricRuleData(timestamp));
            Statistic stat = Statistic.fromValue(statString);
            if (stat == Statistic.UNKNOWN_TO_SDK_VERSION) {
                vals.extendedValues.put(statString, value);
            } else {
                vals.statisticValues.put(stat, value);
            }
            res.put(labelsKey, vals);
        }
        return res;
    }

    GetMetricDataDataGetter(CloudWatchClient client, long start, MetricRule rule, Counter counter,
            List<List<Dimension>> dimentionsList) {
        this.client = client;
        this.start = start;
        this.rule = rule;
        this.counter = counter;
        this.results = fetchAllDataPoints(dimentionsList);
    }

    @Override
    public MetricRuleData metricRuleDataFor(List<Dimension> dimensions) {
        return results.get(dimentionsToKey(dimensions));
    }

    static private class StatAndDimentions {
        String dimetionsAsString;
        String stat;

        StatAndDimentions(String stat, String dimetionsAsString) {
            this.stat = stat;
            this.dimetionsAsString = dimetionsAsString;
        }
    }

    static class MetricLabels {
        static String labelFor(String stat, List<Dimension> dimensions) {
            return String.format("%s/%s", stat, dimentionsToKey(dimensions));
        }

        static StatAndDimentions decode(String label) {
            String[] labelParts = label.split("/", 1);
            // TODO: throw exception if size != 2
            String statString = labelParts[0];
            String labelsKey = labelParts[1];
            return new StatAndDimentions(statString, labelsKey);
        }

        static class UnexpectedLabel extends Exception {
        }
    }

}
