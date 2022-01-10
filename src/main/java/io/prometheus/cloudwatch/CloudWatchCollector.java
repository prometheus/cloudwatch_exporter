package io.prometheus.cloudwatch;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.Describable;
import io.prometheus.client.Counter;
import org.yaml.snakeyaml.Yaml;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClientBuilder;
import software.amazon.awssdk.services.cloudwatch.model.*;
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClient;
import software.amazon.awssdk.services.resourcegroupstaggingapi.ResourceGroupsTaggingApiClientBuilder;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.Tag;
import software.amazon.awssdk.services.resourcegroupstaggingapi.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class CloudWatchCollector extends Collector implements Describable {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    public static void set_stuff() throws IOException {
        final Properties properties = new Properties();
        properties.load(CloudWatchCollector.class.getClassLoader().getResourceAsStream(".properties"));
        final String BuildVersion = properties.getProperty("BuildVersion");
        final String BuildDate = properties.getProperty("BuildDate");
    }

    static class ActiveConfig {
        ArrayList<MetricRule> rules;
        CloudWatchClient cloudWatchClient;
        ResourceGroupsTaggingApiClient taggingClient;

        public ActiveConfig(ActiveConfig cfg) {
            this.rules = new ArrayList<>(cfg.rules);
            this.cloudWatchClient = cfg.cloudWatchClient;
            this.taggingClient = cfg.taggingClient;
        }

        public ActiveConfig() {
        }
    }

    static class MetricRule {
      String awsNamespace;
      String awsMetricName;
      int periodSeconds;
      int rangeSeconds;
      int delaySeconds;
      List<Statistic> awsStatistics;
      List<String> awsExtendedStatistics;
      List<String> awsDimensions;
      Map<String,List<String>> awsDimensionSelect;
      Map<String,List<String>> awsDimensionSelectRegex;
      AWSTagSelect awsTagSelect;
      String help;
      boolean cloudwatchTimestamp;
    }

    static class AWSTagSelect {
      String resourceTypeSelection;
      String resourceIdDimension;
      Map<String,List<String>> tagSelections;
    }

    ActiveConfig activeConfig = new ActiveConfig();

    private static final Counter cloudwatchRequests = Counter.build()
            .labelNames("action", "namespace")
            .name("cloudwatch_requests_total").help("API requests made to CloudWatch").register();

    private static final Counter taggingApiRequests = Counter.build()
        .labelNames("action", "resource_type")
        .name("tagging_api_requests_total").help("API requests made to the Resource Groups Tagging API").register();

    private static final List<String> brokenDynamoMetrics = Arrays.asList(
            "ConsumedReadCapacityUnits", "ConsumedWriteCapacityUnits",
            "ProvisionedReadCapacityUnits", "ProvisionedWriteCapacityUnits",
            "ReadThrottleEvents", "WriteThrottleEvents");

    public CloudWatchCollector(Reader in) {
        loadConfig(in, null, null);
    }
    public CloudWatchCollector(String yamlConfig) {
        this((Map<String, Object>)new Yaml().load(yamlConfig), null, null);
    }

    /* For unittests. */
    protected CloudWatchCollector(String jsonConfig, CloudWatchClient cloudWatchClient, ResourceGroupsTaggingApiClient taggingClient) {
        this((Map<String, Object>)new Yaml().load(jsonConfig), cloudWatchClient, taggingClient);
    }

    private CloudWatchCollector(Map<String, Object> config, CloudWatchClient cloudWatchClient, ResourceGroupsTaggingApiClient taggingClient) {
        loadConfig(config, cloudWatchClient, taggingClient);
    }

    @Override
    public List<MetricFamilySamples> describe() {
      return Collections.emptyList();
    }

    protected void reloadConfig() throws IOException {
        LOGGER.log(Level.INFO, "Reloading configuration");
        try (
          FileReader reader = new FileReader(WebServer.configFilePath);
        ) {
          loadConfig(reader, activeConfig.cloudWatchClient, activeConfig.taggingClient);
        }
    }

    protected void loadConfig(Reader in, CloudWatchClient cloudWatchClient, ResourceGroupsTaggingApiClient taggingClient) {
        loadConfig((Map<String, Object>)new Yaml().load(in), cloudWatchClient, taggingClient);
    }

    private void loadConfig(Map<String, Object> config, CloudWatchClient cloudWatchClient, ResourceGroupsTaggingApiClient taggingClient) {
        if(config == null) {  // Yaml config empty, set config to empty map.
            config = new HashMap<>();
        }

        int defaultPeriod = 60;
        if (config.containsKey("period_seconds")) {
          defaultPeriod = ((Number)config.get("period_seconds")).intValue();
        }
        int defaultRange = 600;
        if (config.containsKey("range_seconds")) {
          defaultRange = ((Number)config.get("range_seconds")).intValue();
        }
        int defaultDelay = 600;
        if (config.containsKey("delay_seconds")) {
          defaultDelay = ((Number)config.get("delay_seconds")).intValue();
        }

        boolean defaultCloudwatchTimestamp = true;
        if (config.containsKey("set_timestamp")) {
            defaultCloudwatchTimestamp = (Boolean)config.get("set_timestamp");
        }

        String region = (String) config.get("region");

        if (cloudWatchClient == null) {
          CloudWatchClientBuilder clientBuilder = CloudWatchClient.builder();

          if (config.containsKey("role_arn")) {
            clientBuilder.credentialsProvider(getRoleCredentialProvider(config));
          }

          if (region != null) {
            clientBuilder.region(Region.of(region));
          }

          cloudWatchClient = clientBuilder.build();
        }

        if (taggingClient == null) {
          ResourceGroupsTaggingApiClientBuilder clientBuilder = ResourceGroupsTaggingApiClient.builder();

          if (config.containsKey("role_arn")) {
            clientBuilder.credentialsProvider(getRoleCredentialProvider(config));
          }
          if (region != null) {
            clientBuilder.region(Region.of(region));
          }
          taggingClient = clientBuilder.build();
        }

        if (!config.containsKey("metrics")) {
          throw new IllegalArgumentException("Must provide metrics");
        }

        ArrayList<MetricRule> rules = new ArrayList<>();

        for (Object ruleObject : (List<Map<String,Object>>) config.get("metrics")) {
          Map<String, Object> yamlMetricRule = (Map<String, Object>)ruleObject;
          MetricRule rule = new MetricRule();
          rules.add(rule);
          if (!yamlMetricRule.containsKey("aws_namespace") || !yamlMetricRule.containsKey("aws_metric_name")) {
            throw new IllegalArgumentException("Must provide aws_namespace and aws_metric_name");
          }
          rule.awsNamespace = (String)yamlMetricRule.get("aws_namespace");
          rule.awsMetricName = (String)yamlMetricRule.get("aws_metric_name");
          if (yamlMetricRule.containsKey("help")) {
            rule.help = (String)yamlMetricRule.get("help");
          }
          if (yamlMetricRule.containsKey("aws_dimensions")) {
            rule.awsDimensions = (List<String>)yamlMetricRule.get("aws_dimensions");
          }
          if (yamlMetricRule.containsKey("aws_dimension_select") && yamlMetricRule.containsKey("aws_dimension_select_regex")) {
            throw new IllegalArgumentException("Must not provide aws_dimension_select and aws_dimension_select_regex at the same time");
          }
          if (yamlMetricRule.containsKey("aws_dimension_select")) {
            rule.awsDimensionSelect = (Map<String, List<String>>)yamlMetricRule.get("aws_dimension_select");
          }
          if (yamlMetricRule.containsKey("aws_dimension_select_regex")) {
            rule.awsDimensionSelectRegex = (Map<String,List<String>>)yamlMetricRule.get("aws_dimension_select_regex");
          }
          if (yamlMetricRule.containsKey("aws_statistics")) {
            rule.awsStatistics = new ArrayList<>();
            for (String statistic : (List<String>)yamlMetricRule.get("aws_statistics")) {
              rule.awsStatistics.add(Statistic.fromValue(statistic));
            }
          } else if (!yamlMetricRule.containsKey("aws_extended_statistics")) {
            rule.awsStatistics = new ArrayList<>();
            for (String statistic : Arrays.asList("Sum", "SampleCount", "Minimum", "Maximum", "Average")) {
              rule.awsStatistics.add(Statistic.fromValue(statistic));
            }
          }
          if (yamlMetricRule.containsKey("aws_extended_statistics")) {
            rule.awsExtendedStatistics = (List<String>)yamlMetricRule.get("aws_extended_statistics");
          }
          if (yamlMetricRule.containsKey("period_seconds")) {
            rule.periodSeconds = ((Number)yamlMetricRule.get("period_seconds")).intValue();
          } else {
            rule.periodSeconds = defaultPeriod;
          }
          if (yamlMetricRule.containsKey("range_seconds")) {
            rule.rangeSeconds = ((Number)yamlMetricRule.get("range_seconds")).intValue();
          } else {
            rule.rangeSeconds = defaultRange;
          }
          if (yamlMetricRule.containsKey("delay_seconds")) {
            rule.delaySeconds = ((Number)yamlMetricRule.get("delay_seconds")).intValue();
          } else {
            rule.delaySeconds = defaultDelay;
          }
          if (yamlMetricRule.containsKey("set_timestamp")) {
              rule.cloudwatchTimestamp = (Boolean)yamlMetricRule.get("set_timestamp");
          } else {
              rule.cloudwatchTimestamp = defaultCloudwatchTimestamp;
          }

          if (yamlMetricRule.containsKey("aws_tag_select")) {
            Map<String, Object> yamlAwsTagSelect = (Map<String, Object>) yamlMetricRule.get("aws_tag_select");
            if (!yamlAwsTagSelect.containsKey("resource_type_selection") || !yamlAwsTagSelect.containsKey("resource_id_dimension")) {
              throw new IllegalArgumentException("Must provide resource_type_selection and resource_id_dimension");
            }
            AWSTagSelect awsTagSelect = new AWSTagSelect();
            rule.awsTagSelect = awsTagSelect;

            awsTagSelect.resourceTypeSelection = (String)yamlAwsTagSelect.get("resource_type_selection");
            awsTagSelect.resourceIdDimension = (String)yamlAwsTagSelect.get("resource_id_dimension");

            if (yamlAwsTagSelect.containsKey("tag_selections")) {
              awsTagSelect.tagSelections = (Map<String, List<String>>)yamlAwsTagSelect.get("tag_selections");
            }
          }
        }

        loadConfig(rules, cloudWatchClient, taggingClient);
    }

    private void loadConfig(ArrayList<MetricRule> rules, CloudWatchClient cloudWatchClient, ResourceGroupsTaggingApiClient taggingClient) {
        synchronized (activeConfig) {
            activeConfig.cloudWatchClient = cloudWatchClient;
            activeConfig.taggingClient = taggingClient;
            activeConfig.rules = rules;
        }
    }

    private AwsCredentialsProvider getRoleCredentialProvider(Map<String, Object> config) {
      StsClient stsClient = StsClient.builder().region(Region.of((String) config.get("region"))).build();
      AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
              .roleArn((String) config.get("role_arn"))
              .roleSessionName("cloudwatch_exporter").build();
      return StsAssumeRoleCredentialsProvider.builder()
              .stsClient(stsClient)
              .refreshRequest(assumeRoleRequest).build();
    }

    private List<ResourceTagMapping> getResourceTagMappings(MetricRule rule, ResourceGroupsTaggingApiClient taggingClient) {
      if (rule.awsTagSelect == null) {
        return Collections.emptyList();
      }

      List<TagFilter> tagFilters = new ArrayList<>();
      if (rule.awsTagSelect.tagSelections != null) {
        for (Map.Entry<String, List<String>> entry : rule.awsTagSelect.tagSelections.entrySet()) {
          tagFilters.add(TagFilter.builder().key(entry.getKey()).values(entry.getValue()).build());
        }
      }

      List<ResourceTagMapping> resourceTagMappings = new ArrayList<>();
      GetResourcesRequest.Builder requestBuilder = GetResourcesRequest.builder().tagFilters(tagFilters).resourceTypeFilters(rule.awsTagSelect.resourceTypeSelection);
      String paginationToken = "";
      do {
        requestBuilder.paginationToken(paginationToken);

        GetResourcesResponse response = taggingClient.getResources(requestBuilder.build());
        taggingApiRequests.labels("getResources", rule.awsTagSelect.resourceTypeSelection).inc();

        resourceTagMappings.addAll(response.resourceTagMappingList());

        paginationToken = response.paginationToken();
      } while (paginationToken != null && !paginationToken.isEmpty());

      return resourceTagMappings;
    }

    private List<String> extractResourceIds(List<ResourceTagMapping> resourceTagMappings) {
      List<String> resourceIds = new ArrayList<>();
      for (ResourceTagMapping resourceTagMapping : resourceTagMappings) {
        resourceIds.add(extractResourceIdFromArn(resourceTagMapping.resourceARN()));
      }
      return resourceIds;
    }

    private String extractResourceIdFromArn(String arn) {
      // ARN parsing is based on https://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html
      String[] arnArray = arn.split(":");
      String resourceId = arnArray[arnArray.length - 1];
      if (resourceId.contains("/")) {
        String[] resourceArray = resourceId.split("/", 2);
        resourceId = resourceArray[resourceArray.length - 1];
      }
      return resourceId;
    }

    private List<List<Dimension>> getDimensions(MetricRule rule, List<String> tagBasedResourceIds, CloudWatchClient cloudWatchClient) {
        if (
                rule.awsDimensions != null &&
                rule.awsDimensionSelect != null &&
                !rule.awsDimensions.isEmpty() &&
                rule.awsDimensions.size() == rule.awsDimensionSelect.size() &&
                rule.awsDimensionSelect.keySet().containsAll(rule.awsDimensions) &&
                rule.awsTagSelect == null
        ) {
            // The full list of dimensions is known so no need to request it from cloudwatch.
            return permuteDimensions(rule.awsDimensions, rule.awsDimensionSelect);
        } else {
            return listDimensions(rule, tagBasedResourceIds, cloudWatchClient);
        }
    }

    private List<List<Dimension>> permuteDimensions(List<String> dimensions, Map<String, List<String>> dimensionValues) {
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

    private List<List<Dimension>> listDimensions(MetricRule rule, List<String> tagBasedResourceIds, CloudWatchClient cloudWatchClient) {
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
      for (String dimension: rule.awsDimensions) {
        dimensionFilters.add(DimensionFilter.builder().name(dimension).build());
      }
      requestBuilder.dimensions(dimensionFilters);

      String nextToken = null;
      do {
        requestBuilder.nextToken(nextToken);
        ListMetricsResponse response = cloudWatchClient.listMetrics(requestBuilder.build());
        cloudwatchRequests.labels("listMetrics", rule.awsNamespace).inc();
        for (Metric metric: response.metrics()) {
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

      return dimensions;
    }

    /**
     * Check if a metric should be used according to `aws_dimension_select`, `aws_dimension_select_regex` and dynamic `aws_tag_select`
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

    /**
     * Check if a metric is matched in `aws_dimension_select`
     */
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

    /**
     * Check if a metric is matched in `aws_dimension_select_regex`
     */
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

    /**
     * Check if any regex string in a list matches a given input value
     */
    protected static boolean regexListMatch(List<String> regexList, String input) {
      for (String regex: regexList) {
        if (Pattern.matches(regex, input)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Check if a metric is matched in `aws_tag_select`
     */
    private boolean metricIsInAwsTagSelect(MetricRule rule, List<String> tagBasedResourceIds, Metric metric) {
      if (rule.awsTagSelect.tagSelections == null) {
        return true;
      }
      for (Dimension dimension : metric.dimensions()) {
        String dimensionName = dimension.name();
        String dimensionValue = dimension.value();
        if (rule.awsTagSelect.resourceIdDimension.equals(dimensionName) && !tagBasedResourceIds.contains(dimensionValue)) {
            return false;
        }
      }
      return true;
    }

    private Datapoint getNewestDatapoint(java.util.List<Datapoint> datapoints) {
      Datapoint newest = null;
      for (Datapoint d: datapoints) {
        if (newest == null || newest.timestamp().isBefore(d.timestamp())) {
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

    private String safeLabelName(String s) {
      // Change invalid chars to underscore, and merge underscores.
      return s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("__+", "_");
    }

    private String help(MetricRule rule, String unit, String statistic) {
      if (rule.help != null) {
          return rule.help;
      }
      return "CloudWatch metric " + rule.awsNamespace + " " + rule.awsMetricName
          + " Dimensions: " + rule.awsDimensions + " Statistic: " + statistic
          + " Unit: " + unit;
    }

    private void scrape(List<MetricFamilySamples> mfs) {
      ActiveConfig config = new ActiveConfig(activeConfig);
      Set<String> publishedResourceInfo = new HashSet<>();

      long start = System.currentTimeMillis();
      List<MetricFamilySamples.Sample> infoSamples = new ArrayList<>();
      for (MetricRule rule: config.rules) {
        Date startDate = new Date(start - 1000 * rule.delaySeconds);
        Date endDate = new Date(start - 1000 * (rule.delaySeconds + rule.rangeSeconds));
        GetMetricStatisticsRequest.Builder requestBuilder = GetMetricStatisticsRequest.builder();
        requestBuilder.namespace(rule.awsNamespace);
        requestBuilder.metricName(rule.awsMetricName);
        requestBuilder.statistics(rule.awsStatistics);
        requestBuilder.extendedStatistics(rule.awsExtendedStatistics);
        requestBuilder.endTime(startDate.toInstant());
        requestBuilder.startTime(endDate.toInstant());
        requestBuilder.period(rule.periodSeconds);

        String baseName = safeName(rule.awsNamespace.toLowerCase() + "_" + toSnakeCase(rule.awsMetricName));
        String jobName = safeName(rule.awsNamespace.toLowerCase());
        List<MetricFamilySamples.Sample> sumSamples = new ArrayList<>();
        List<MetricFamilySamples.Sample> sampleCountSamples = new ArrayList<>();
        List<MetricFamilySamples.Sample> minimumSamples = new ArrayList<>();
        List<MetricFamilySamples.Sample> maximumSamples = new ArrayList<>();
        List<MetricFamilySamples.Sample> averageSamples = new ArrayList<>();
        HashMap<String, ArrayList<MetricFamilySamples.Sample>> extendedSamples = new HashMap<>();

        String unit = null;

        if (rule.awsNamespace.equals("AWS/DynamoDB")
                && rule.awsDimensions != null
                && rule.awsDimensions.contains("GlobalSecondaryIndexName")
                && brokenDynamoMetrics.contains(rule.awsMetricName)) {
            baseName += "_index";
        }

        List<ResourceTagMapping> resourceTagMappings = getResourceTagMappings(rule, config.taggingClient);
        List<String> tagBasedResourceIds = extractResourceIds(resourceTagMappings);

        for (List<Dimension> dimensions: getDimensions(rule, tagBasedResourceIds, config.cloudWatchClient)) {
          requestBuilder.dimensions(dimensions);

          GetMetricStatisticsResponse response = config.cloudWatchClient.getMetricStatistics(requestBuilder.build());
          cloudwatchRequests.labels("getMetricStatistics", rule.awsNamespace).inc();
          Datapoint dp = getNewestDatapoint(response.datapoints());
          if (dp == null) {
            continue;
          }
          unit = dp.unitAsString();

          List<String> labelNames = new ArrayList<>();
          List<String> labelValues = new ArrayList<>();
          labelNames.add("job");
          labelValues.add(jobName);
          labelNames.add("instance");
          labelValues.add("");
          for (Dimension d: dimensions) {
            labelNames.add(safeLabelName(toSnakeCase(d.name())));
            labelValues.add(d.value());
          }

          Long timestamp = null;
          if (rule.cloudwatchTimestamp) {
            timestamp = dp.timestamp().toEpochMilli();
          }

          if (dp.sum() != null) {
            sumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sum", labelNames, labelValues, dp.sum(), timestamp));
          }
          if (dp.sampleCount() != null) {
            sampleCountSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sample_count", labelNames, labelValues, dp.sampleCount(), timestamp));
          }
          if (dp.minimum() != null) {
            minimumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_minimum", labelNames, labelValues, dp.minimum(), timestamp));
          }
          if (dp.maximum() != null) {
            maximumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_maximum",labelNames, labelValues, dp.maximum(), timestamp));
          }
          if (dp.average() != null) {
            averageSamples.add(new MetricFamilySamples.Sample(
                baseName + "_average", labelNames, labelValues, dp.average(), timestamp));
          }
          if (dp.extendedStatistics() != null) {
            for (Map.Entry<String, Double> entry : dp.extendedStatistics().entrySet()) {
              ArrayList<MetricFamilySamples.Sample> samples = extendedSamples.get(entry.getKey());
              if (samples == null) {
                samples = new ArrayList<>();
                extendedSamples.put(entry.getKey(), samples);
              }
              samples.add(new MetricFamilySamples.Sample(
                  baseName + "_" + safeName(toSnakeCase(entry.getKey())), labelNames, labelValues, entry.getValue(), timestamp));
            }
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
        for (Map.Entry<String, ArrayList<MetricFamilySamples.Sample>> entry : extendedSamples.entrySet()) {
          mfs.add(new MetricFamilySamples(baseName + "_" + safeName(toSnakeCase(entry.getKey())), Type.GAUGE, help(rule, unit, entry.getKey()), entry.getValue()));
        }

        // Add the "aws_resource_info" metric for existing tag mappings
        for (ResourceTagMapping resourceTagMapping : resourceTagMappings) {
          if (!publishedResourceInfo.contains(resourceTagMapping.resourceARN())) {
            List<String> labelNames = new ArrayList<>();
            List<String> labelValues = new ArrayList<>();
            labelNames.add("job");
            labelValues.add(jobName);
            labelNames.add("instance");
            labelValues.add("");
            labelNames.add("arn");
            labelValues.add(resourceTagMapping.resourceARN());
            labelNames.add(safeLabelName(toSnakeCase(rule.awsTagSelect.resourceIdDimension)));
            labelValues.add(extractResourceIdFromArn(resourceTagMapping.resourceARN()));
            for (Tag tag: resourceTagMapping.tags()) {
              // Avoid potential collision between resource tags and other metric labels by adding the "tag_" prefix
              // The AWS tags are case sensitive, so to avoid loosing information and label collisions, tag keys are not snaked cased
              labelNames.add("tag_" + safeLabelName(tag.key()));
              labelValues.add(tag.value());
            }

            infoSamples.add(new MetricFamilySamples.Sample("aws_resource_info", labelNames, labelValues, 1));

            publishedResourceInfo.add(resourceTagMapping.resourceARN());
          }
        }
      }
      mfs.add(new MetricFamilySamples("aws_resource_info", Type.GAUGE, "AWS information available for resource", infoSamples));
    }

    public List<MetricFamilySamples> collect() {
      long start = System.nanoTime();
      double error = 0;
      List<MetricFamilySamples> mfs = new ArrayList<>();
      List<String> labelNames = new ArrayList<>();
      List<String> labelValues = new ArrayList<>();
      try {
        scrape(mfs);
      } catch (Exception e) {
        error = 1;
        LOGGER.log(Level.WARNING, "CloudWatch scrape failed", e);
      }
      List<MetricFamilySamples.Sample> samples = new ArrayList<>();
      samples.add(new MetricFamilySamples.Sample(
          "cloudwatch_exporter_scrape_duration_seconds", new ArrayList<>(), new ArrayList<>(), (System.nanoTime() - start) / 1.0E9));
      mfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_duration_seconds", Type.GAUGE, "Time this CloudWatch scrape took, in seconds.", samples));

      samples = new ArrayList<>();
      samples.add(new MetricFamilySamples.Sample(
          "cloudwatch_exporter_scrape_error", new ArrayList<>(), new ArrayList<>(), error));
      mfs.add(new MetricFamilySamples("cloudwatch_exporter_scrape_error", Type.GAUGE, "Non-zero if this scrape failed.", samples));

      String buildVersion = "";
      String releaseDate = "";
      int errorFlag = 0;
      try {
          final Properties properties = new Properties();
          properties.load(CloudWatchCollector.class.getClassLoader().getResourceAsStream(".properties"));
          buildVersion = properties.getProperty("BuildVersion");
          releaseDate = properties.getProperty("ReleaseDate");

      }
      catch (IOException e) {
          buildVersion = "Error";
          releaseDate = "Error";
          errorFlag = 1;
          LOGGER.log(Level.WARNING, "CloudWatch build info scrape failed", e);
      }

      labelNames.add("build_version");
      labelValues.add(buildVersion);
      labelNames.add("release_date");
      labelValues.add(releaseDate);

      samples = new ArrayList<>();
      samples.add(new MetricFamilySamples.Sample(
          "cloudwatch_exporter_build_info", labelNames, labelValues, errorFlag));
      mfs.add(new MetricFamilySamples("cloudwatch_exporter_build_info", Type.GAUGE, "Non-zero if build info scrape failed.", samples));

      return mfs;
    }

    /**
     * Convenience function to run standalone.
     */
    public static void main(String[] args) {
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
