package io.prometheus.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import java.io.Reader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class CloudWatchCollector extends Collector {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    AmazonCloudWatchClient client; 

    static class MetricRule {
      String awsNamespace;
      String awsMetricName;
      int periodSeconds;
      int rangeSeconds;
      int delaySeconds;
      List<String> awsStatistics;
      List<String> awsDimensions;
      String help;
    }

    private static final Counter cloudwatchRequests = Counter.build()
      .name("cloudwatch_requests_total").help("API requests made to CloudWatch").register();

    ArrayList<MetricRule> rules = new ArrayList<MetricRule>();

    public CloudWatchCollector(Reader in) throws IOException, ParseException {
        this((JSONObject)new JSONParser().parse(in), null);
    }
    public CloudWatchCollector(String jsonConfig) throws ParseException {
        this((JSONObject)new JSONParser().parse(jsonConfig), null);
    }

    /* For unittests. */
    protected CloudWatchCollector(String jsonConfig, AmazonCloudWatchClient client) throws ParseException {
        this((JSONObject)new JSONParser().parse(jsonConfig), client);
    }

    private CloudWatchCollector(JSONObject config, AmazonCloudWatchClient client) throws ParseException {
        if (!config.containsKey("region")) {
          throw new IllegalArgumentException("Must provide region");
        }
        String region = (String)config.get("region");
        int defaultPeriod = 60;
        if (config.containsKey("period_seconds")) {
          defaultPeriod = (Integer)config.get("period_Seconds");
        }
        int defaultRange = 600;
        if (config.containsKey("range_seconds")) {
          defaultRange = (Integer)config.get("range_seconds");
        }
        int defaultDelay = 600;
        if (config.containsKey("delay_seconds")) {
          defaultDelay = (Integer)config.get("delay_seconds");
        }

        if (client == null) {
          this.client = new AmazonCloudWatchClient();
          this.client.setEndpoint("https://monitoring." + region + ".amazonaws.com");
        } else {
          this.client = client;
        }

        if (!config.containsKey("metrics")) {
          throw new IllegalArgumentException("Must provide metrics");
        }
        for (Object ruleObject : (JSONArray) config.get("metrics")) {
          JSONObject jsonMetricRule = (JSONObject) ruleObject;
          MetricRule rule = new MetricRule();
          rules.add(rule);
          if (!jsonMetricRule.containsKey("aws_namespace") || !jsonMetricRule.containsKey("aws_metric_name")) {
            throw new IllegalArgumentException("Must provide aws_namespace and aws_metric_name");
          }
          rule.awsNamespace = (String)jsonMetricRule.get("aws_namespace");
          rule.awsMetricName = (String)jsonMetricRule.get("aws_metric_name");
          if (jsonMetricRule.containsKey("help")) {
            rule.help = (String)jsonMetricRule.get("help");
          }
          if (jsonMetricRule.containsKey("aws_dimensions")) {
            rule.awsDimensions = (JSONArray)jsonMetricRule.get("aws_dimensions");
          }
          if (jsonMetricRule.containsKey("aws_statistics")) {
            rule.awsStatistics = (JSONArray)jsonMetricRule.get("aws_statistics");
          } else {
            rule.awsStatistics = new ArrayList(Arrays.asList("Sum", "SampleCount", "Minimum", "Maximum", "Average"));
          }
          if (jsonMetricRule.containsKey("period_seconds")) {
            rule.periodSeconds = (Integer)jsonMetricRule.get("period_seconds");
          } else {
            rule.periodSeconds = defaultPeriod;
          }
          if (jsonMetricRule.containsKey("range_seconds")) {
            rule.rangeSeconds = (Integer)jsonMetricRule.get("range_seconds");
          } else {
            rule.rangeSeconds = defaultRange;
          }
          if (jsonMetricRule.containsKey("delay_seconds")) {
            rule.delaySeconds = (Integer)jsonMetricRule.get("delay_seconds");
          } else {
            rule.delaySeconds = defaultDelay;
          }
        }
    }
    
    private List<List<Dimension>> getDimensions(MetricRule rule) {
      List<List<Dimension>> dimensions = new ArrayList<List<Dimension>>();
      if (rule.awsDimensions == null) {
        dimensions.add(new ArrayList<Dimension>());
        return dimensions;
      }

      ListMetricsRequest request = new ListMetricsRequest();
      request.setNamespace(rule.awsNamespace);
      request.setMetricName(rule.awsMetricName);
      List<DimensionFilter> dimensionFilters = new ArrayList<DimensionFilter>();
      for (String dimension: rule.awsDimensions) {
        dimensionFilters.add(new DimensionFilter().withName(dimension));
      }
      request.setDimensions(dimensionFilters);

      String nextToken = null;
      do {
        request.setNextToken(nextToken);
        ListMetricsResult result = client.listMetrics(request);
        cloudwatchRequests.inc();
        for (Metric metric: result.getMetrics()) {
          if (metric.getDimensions().size() != dimensionFilters.size()) {
            // AWS returns all the metrics with dimensions beyond the ones we ask for,
            // so filter them out.
            continue;
          }
          dimensions.add(metric.getDimensions());
        }
        nextToken = result.getNextToken();
      } while (nextToken != null);

      return dimensions;
    }


    private Datapoint getNewestDatapoint(java.util.List<Datapoint> datapoints) {
      Datapoint newest = null;
      for (Datapoint d: datapoints) {
        if (newest == null || newest.getTimestamp().before(d.getTimestamp())) {
          newest = d;
        }
      }
      return newest;
    }

    private String toSnakeCase(String str) {
      return str.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private String safeName(String s) {
      // Change invalid chars to underscore, and merge underscores.
      return s.replaceAll("[^a-zA-Z0-9:_]", "_").replaceAll("__+", "_");
    }

    private String help(MetricRule rule, String unit, String statistic) {
      return "CloudWatch metric " + rule.awsNamespace + " " + rule.awsMetricName
          + " Dimensions: " + rule.awsDimensions + " Statistic: " + statistic
          + " Unit: " + unit;
    }

    private void scrape(List<MetricFamilySamples> mfs) {
      long start = System.currentTimeMillis();
      for (MetricRule rule: rules) {
        Date startDate = new Date(start - 1000 * rule.delaySeconds);
        Date endDate = new Date(start - 1000 * (rule.delaySeconds + rule.rangeSeconds));
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
        request.setNamespace(rule.awsNamespace);
        request.setMetricName(rule.awsMetricName);
        request.setStatistics(rule.awsStatistics);
        request.setEndTime(startDate);
        request.setStartTime(endDate);
        request.setPeriod(rule.periodSeconds);

        String baseName = safeName(rule.awsNamespace.toLowerCase() + "_" + toSnakeCase(rule.awsMetricName));
        String jobName = safeName(rule.awsNamespace.toLowerCase());
        List<MetricFamilySamples.Sample> sumSamples = new ArrayList<MetricFamilySamples.Sample>();
        List<MetricFamilySamples.Sample> sampleCountSamples = new ArrayList<MetricFamilySamples.Sample>();
        List<MetricFamilySamples.Sample> minimumSamples = new ArrayList<MetricFamilySamples.Sample>();
        List<MetricFamilySamples.Sample> maximumSamples = new ArrayList<MetricFamilySamples.Sample>();
        List<MetricFamilySamples.Sample> averageSamples = new ArrayList<MetricFamilySamples.Sample>();

        String unit = null;

        for (List<Dimension> dimensions: getDimensions(rule)) {
          request.setDimensions(dimensions);

          GetMetricStatisticsResult result = client.getMetricStatistics(request);
          cloudwatchRequests.inc();
          Datapoint dp = getNewestDatapoint(result.getDatapoints());
          if (dp == null) {
            continue;
          }
          unit = dp.getUnit();

          List<String> labelNames = new ArrayList<String>();
          List<String> labelValues = new ArrayList<String>();
          labelNames.add("job");
          labelValues.add(jobName);
          for (Dimension d: dimensions) {
            labelNames.add(safeName(toSnakeCase(d.getName())));
            labelValues.add(d.getValue());
          }

          if (dp.getSum() != null) {
            sumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sum", labelNames, labelValues, dp.getSum()));
          }
          if (dp.getSampleCount() != null) {
            sampleCountSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sample_count", labelNames, labelValues, dp.getSampleCount()));
          }
          if (dp.getMinimum() != null) {
            minimumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_minimum", labelNames, labelValues, dp.getMinimum()));
          }
          if (dp.getMaximum() != null) {
            maximumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_maximum",labelNames, labelValues, dp.getMaximum()));
          }
          if (dp.getAverage() != null) {
            averageSamples.add(new MetricFamilySamples.Sample(
                baseName + "_average", labelNames, labelValues, dp.getAverage()));
          }
        }

        if (!sumSamples.isEmpty()) {
          mfs.add(new MetricFamilySamples(baseName + "_sum", Type.GAUGE, help(rule, unit, "Sum"), sumSamples));
        }
        if (!sampleCountSamples.isEmpty()) {
          mfs.add(new MetricFamilySamples(baseName + "_sample_count", Type.GAUGE, help(rule, unit, "SampleCount"), sampleCountSamples));
        }
        if (!minimumSamples.isEmpty()) {
          mfs.add(new MetricFamilySamples(baseName + "_minimum", Type.GAUGE, help(rule, unit, "Minimum"), minimumSamples));
        }
        if (!maximumSamples.isEmpty()) {
          mfs.add(new MetricFamilySamples(baseName + "_maximum", Type.GAUGE, help(rule, unit, "Maximum"), maximumSamples));
        }
        if (!averageSamples.isEmpty()) {
          mfs.add(new MetricFamilySamples(baseName + "_average", Type.GAUGE, help(rule, unit, "Average"), averageSamples));
        }
      }
    }

    public List<MetricFamilySamples> collect() {
      long start = System.nanoTime();
      double error = 0;
      List<MetricFamilySamples> mfs = new ArrayList<MetricFamilySamples>(); 
      try {
        scrape(mfs);
      } catch (Exception e) {
        error = 1;
        LOGGER.log(Level.WARNING, "CloudWatch scrape failed", e);
      }
      List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
      samples.add(new MetricFamilySamples.Sample(
          "cloudwatch_exporter_scrape_duration_seconds", new ArrayList<String>(), new ArrayList<String>(), (System.nanoTime() - start) / 1.0E9));
      mfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_duration_seconds", Type.GAUGE, "Time this CloudWatch scrape took, in seconds.", samples));

      samples = new ArrayList<MetricFamilySamples.Sample>();
      samples.add(new MetricFamilySamples.Sample(
          "cloudwatch_exporter_scrape_error", new ArrayList<String>(), new ArrayList<String>(), error));
      mfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", samples));
      return mfs;
    }

    /**
     * Convenience function to run standalone.
     */
    public static void main(String[] args) throws Exception {
      String region = "eu-west-1";
      if (args.length > 0) {
        region = args[0];
      }
      CloudWatchCollector jc = new CloudWatchCollector(("{"
      + "`region`: `" + region + "`,"
      + "`metrics`: [{`aws_namespace`: `AWS/ELB`, `aws_metric_name`: `RequestCount`, `aws_dimensions`: [`AvailabilityZone`, `LoadBalancerName`]}] ,"
      + "}").replace('`', '"'));
      for(MetricFamilySamples mfs : jc.collect()) {
        System.out.println(mfs);
      }
    }
}

