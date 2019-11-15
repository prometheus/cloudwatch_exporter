package io.prometheus.cloudwatch;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.DimensionFilter;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest;
import com.amazonaws.services.cloudwatch.model.ListMetricsResult;
import com.amazonaws.services.cloudwatch.model.Metric;
import com.amazonaws.services.resourcegroupstaggingapi.AWSResourceGroupsTaggingAPI;
import com.amazonaws.services.resourcegroupstaggingapi.AWSResourceGroupsTaggingAPIClientBuilder;
import com.amazonaws.services.resourcegroupstaggingapi.model.GetResourcesRequest;
import com.amazonaws.services.resourcegroupstaggingapi.model.GetResourcesResult;
import com.amazonaws.services.resourcegroupstaggingapi.model.ResourceTagMapping;
import com.amazonaws.services.resourcegroupstaggingapi.model.Tag;
import com.amazonaws.services.resourcegroupstaggingapi.model.TagFilter;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;

import java.io.FileReader;
import java.io.Reader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

public class CloudWatchCollector extends Collector {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    static class ActiveConfig implements Cloneable {
        ArrayList<MetricRule> rules;
        AmazonCloudWatch cloudWatchClient;
        AWSResourceGroupsTaggingAPI taggingClient;

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static class MetricRule {
      String awsNamespace;
      String awsMetricName;
      int periodSeconds;
      int rangeSeconds;
      int delaySeconds;
      List<String> awsStatistics;
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

    private static final List<String> brokenDynamoMetrics = Arrays.asList(
            "ConsumedReadCapacityUnits", "ConsumedWriteCapacityUnits",
            "ProvisionedReadCapacityUnits", "ProvisionedWriteCapacityUnits",
            "ReadThrottleEvents", "WriteThrottleEvents");

    public CloudWatchCollector(Reader in) throws IOException {
        loadConfig(in, null, null);
    }
    public CloudWatchCollector(String yamlConfig) {
        this((Map<String, Object>)new Yaml().load(yamlConfig), null, null);
    }

    /* For unittests. */
    protected CloudWatchCollector(String jsonConfig, AmazonCloudWatch cloudWatchClient, AWSResourceGroupsTaggingAPI taggingClient) {
        this((Map<String, Object>)new Yaml().load(jsonConfig), cloudWatchClient, taggingClient);
    }

    private CloudWatchCollector(Map<String, Object> config, AmazonCloudWatch cloudWatchClient, AWSResourceGroupsTaggingAPI taggingClient) {
        loadConfig(config, cloudWatchClient, taggingClient);
    }

    protected void reloadConfig() throws IOException {
        LOGGER.log(Level.INFO, "Reloading configuration");
        FileReader reader = null;
        try {
          reader = new FileReader(WebServer.configFilePath);
          loadConfig(reader, activeConfig.cloudWatchClient, activeConfig.taggingClient);
        } finally {
          if (reader != null) {
            reader.close();
          }
          
        }
    }

    protected void loadConfig(Reader in, AmazonCloudWatch cloudWatchClient, AWSResourceGroupsTaggingAPI taggingClient) throws IOException {
        loadConfig((Map<String, Object>)new Yaml().load(in), cloudWatchClient, taggingClient);
    }

    private void loadConfig(Map<String, Object> config, AmazonCloudWatch cloudWatchClient, AWSResourceGroupsTaggingAPI taggingClient) {
        if(config == null) {  // Yaml config empty, set config to empty map.
            config = new HashMap<String, Object>();
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
        
        if (cloudWatchClient == null) {
          AmazonCloudWatchClientBuilder clientBuilder = AmazonCloudWatchClientBuilder.standard();

          if (config.containsKey("role_arn")) {
            clientBuilder.setCredentials(getRoleCredentialProvider(config));
          }
          Region region = getRegion(config);
          clientBuilder.setEndpointConfiguration(new EndpointConfiguration(getMonitoringEndpoint(region), region.getName()));

          cloudWatchClient = clientBuilder.build();
        }
        
        if (taggingClient == null) {
          AWSResourceGroupsTaggingAPIClientBuilder clientBuilder = AWSResourceGroupsTaggingAPIClientBuilder.standard();

          if (config.containsKey("role_arn")) {
            clientBuilder.setCredentials(getRoleCredentialProvider(config));
          }
          clientBuilder.setRegion(getRegion(config).getName());
          taggingClient = clientBuilder.build();
        }

        if (!config.containsKey("metrics")) {
          throw new IllegalArgumentException("Must provide metrics");
        }

        ArrayList<MetricRule> rules = new ArrayList<MetricRule>();

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
            rule.awsStatistics = (List<String>)yamlMetricRule.get("aws_statistics");
          } else if (!yamlMetricRule.containsKey("aws_extended_statistics")) {
            rule.awsStatistics = new ArrayList(Arrays.asList("Sum", "SampleCount", "Minimum", "Maximum", "Average"));
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
            if (!yamlAwsTagSelect.containsKey("resource_type_selection") || !yamlAwsTagSelect.containsKey("resource_id_dimension") ||
              !yamlAwsTagSelect.containsKey("tag_selections")) {
              throw new IllegalArgumentException("Must provide resource_type_selection, resource_id_dimension and tag_selections");
            }
            AWSTagSelect awsTagSelect = new AWSTagSelect();
            rule.awsTagSelect = awsTagSelect;

            awsTagSelect.resourceTypeSelection = (String)yamlAwsTagSelect.get("resource_type_selection");
            awsTagSelect.resourceIdDimension = (String)yamlAwsTagSelect.get("resource_id_dimension");
            awsTagSelect.tagSelections = (Map<String, List<String>>)yamlAwsTagSelect.get("tag_selections");
          }
        }

        loadConfig(rules, cloudWatchClient, taggingClient);
    }

    private void loadConfig(ArrayList<MetricRule> rules, AmazonCloudWatch cloudWatchClient, AWSResourceGroupsTaggingAPI taggingClient) {
        synchronized (activeConfig) {
            activeConfig.cloudWatchClient = cloudWatchClient;
            activeConfig.taggingClient = taggingClient;
            activeConfig.rules = rules;
        }
    }

    private AWSCredentialsProvider getRoleCredentialProvider(Map<String, Object> config) {
      STSAssumeRoleSessionCredentialsProvider credentialsProvider = new STSAssumeRoleSessionCredentialsProvider.Builder(
	  (String) config.get("role_arn"), "cloudwatch_exporter").build();
      return credentialsProvider;
    }
    
    private Region getRegion(Map<String, Object> config) {
      Region region = RegionUtils.getRegion((String) config.get("region"));
      if (region == null) {
        region = Regions.getCurrentRegion();
        if (region == null) {
          throw new IllegalArgumentException("No region provided and EC2 metadata failed");
        }
      }
      return region;
    }
    
    public String getMonitoringEndpoint(Region region) {
      return "https://" + region.getServiceEndpoint("monitoring");
    }

    private List<ResourceTagMapping> getResourceTagMappings(MetricRule rule, AWSResourceGroupsTaggingAPI taggingClient) {
      if (rule.awsTagSelect == null) {
        return Collections.emptyList();
      }

      List<TagFilter> tagFilters = new ArrayList<TagFilter>();
      for (Map.Entry<String, List<String>> entry : rule.awsTagSelect.tagSelections.entrySet()) {
        tagFilters.add(new TagFilter().withKey(entry.getKey()).withValues(entry.getValue()));
      }

      List<ResourceTagMapping> resourceTagMappings = new ArrayList<ResourceTagMapping>();
      GetResourcesRequest request = new GetResourcesRequest().withTagFilters(tagFilters).withResourceTypeFilters(rule.awsTagSelect.resourceTypeSelection);
      String paginationToken = "";
      do {
        request.setPaginationToken(paginationToken);

        GetResourcesResult result = taggingClient.getResources(request);
        resourceTagMappings.addAll(result.getResourceTagMappingList());

        paginationToken = result.getPaginationToken();
      } while (paginationToken != null && paginationToken != "");

      return resourceTagMappings;
    }
    
    private List<String> extractResourceIds(List<ResourceTagMapping> resourceTagMappings) {
      List<String> resourceIds = new ArrayList<String>();
      for (ResourceTagMapping resourceTagMapping : resourceTagMappings) {
        resourceIds.add(extractResourceIdFromArn(resourceTagMapping.getResourceARN()));
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
    
    private List<List<Dimension>> getDimensions(MetricRule rule, List<String> tagBasedResourceIds, AmazonCloudWatch cloudWatchClient) {
        if (
                rule.awsDimensions != null &&
                rule.awsDimensionSelect != null &&
                rule.awsDimensions.size() > 0 &&
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
        ArrayList<List<Dimension>> result = new ArrayList<List<Dimension>>();

        if (dimensions.size() == 0) {
            result.add(new ArrayList<Dimension>());
        } else {
            List<String> dimensionsCopy = new ArrayList<String>(dimensions);
            String dimensionName = dimensionsCopy.remove(dimensionsCopy.size() - 1);
            for (List<Dimension> permutation : permuteDimensions(dimensionsCopy, dimensionValues)) {
                for (String dimensionValue : dimensionValues.get(dimensionName)) {
                    Dimension dimension = new Dimension();
                    dimension.setValue(dimensionValue);
                    dimension.setName(dimensionName);
                    ArrayList<Dimension> permutationCopy = new ArrayList<Dimension>(permutation);
                    permutationCopy.add(dimension);
                    result.add(permutationCopy);
                }
            }
        }

        return result;
    }

    private List<List<Dimension>> listDimensions(MetricRule rule, List<String> tagBasedResourceIds, AmazonCloudWatch cloudWatchClient) {
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
        ListMetricsResult result = cloudWatchClient.listMetrics(request);
        cloudwatchRequests.labels("listMetrics", rule.awsNamespace).inc();
        for (Metric metric: result.getMetrics()) {
          if (metric.getDimensions().size() != dimensionFilters.size()) {
            // AWS returns all the metrics with dimensions beyond the ones we ask for,
            // so filter them out.
            continue;
          }
          if (useMetric(rule, tagBasedResourceIds, metric)) {
            dimensions.add(metric.getDimensions());
          }
        }
        nextToken = result.getNextToken();
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
      for (Dimension dimension : metric.getDimensions()) {
        String dimensionName = dimension.getName();
        String dimensionValue = dimension.getValue();
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
      for (Dimension dimension : metric.getDimensions()) {
        String dimensionName = dimension.getName();
        String dimensionValue = dimension.getValue();
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
      for (Dimension dimension : metric.getDimensions()) {
        String dimensionName = dimension.getName();
        String dimensionValue = dimension.getValue();
        if (rule.awsTagSelect.resourceIdDimension.equals(dimensionName)) {
          if (!tagBasedResourceIds.contains(dimensionValue)) {
            return false;
          }
        }
      }
      return true;
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
      if (rule.help != null) {
          return rule.help;
      }
      return "CloudWatch metric " + rule.awsNamespace + " " + rule.awsMetricName
          + " Dimensions: " + rule.awsDimensions + " Statistic: " + statistic
          + " Unit: " + unit;
    }

    private void scrape(List<MetricFamilySamples> mfs) throws CloneNotSupportedException {
      ActiveConfig config = (ActiveConfig) activeConfig.clone();
      Set<String> publishedResourceInfo = new HashSet<String>();

      long start = System.currentTimeMillis();
      for (MetricRule rule: config.rules) {
        Date startDate = new Date(start - 1000 * rule.delaySeconds);
        Date endDate = new Date(start - 1000 * (rule.delaySeconds + rule.rangeSeconds));
        GetMetricStatisticsRequest request = new GetMetricStatisticsRequest();
        request.setNamespace(rule.awsNamespace);
        request.setMetricName(rule.awsMetricName);
        request.setStatistics(rule.awsStatistics);
        request.setExtendedStatistics(rule.awsExtendedStatistics);
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
        HashMap<String, ArrayList<MetricFamilySamples.Sample>> extendedSamples = new HashMap<String, ArrayList<MetricFamilySamples.Sample>>();

        String unit = null;

        if (rule.awsNamespace.equals("AWS/DynamoDB")
                && rule.awsDimensions.contains("GlobalSecondaryIndexName")
                && brokenDynamoMetrics.contains(rule.awsMetricName)) {
            baseName += "_index";
        }

        List<ResourceTagMapping> resourceTagMappings = getResourceTagMappings(rule, config.taggingClient);
        List<String> tagBasedResourceIds = extractResourceIds(resourceTagMappings);
        
        for (List<Dimension> dimensions: getDimensions(rule, tagBasedResourceIds, config.cloudWatchClient)) {
          request.setDimensions(dimensions);

          GetMetricStatisticsResult result = config.cloudWatchClient.getMetricStatistics(request);
          cloudwatchRequests.labels("getMetricStatistics", rule.awsNamespace).inc();
          Datapoint dp = getNewestDatapoint(result.getDatapoints());
          if (dp == null) {
            continue;
          }
          unit = dp.getUnit();

          List<String> labelNames = new ArrayList<String>();
          List<String> labelValues = new ArrayList<String>();
          labelNames.add("job");
          labelValues.add(jobName);
          labelNames.add("instance");
          labelValues.add("");
          for (Dimension d: dimensions) {
            labelNames.add(safeName(toSnakeCase(d.getName())));
            labelValues.add(d.getValue());
          }

          Long timestamp = null;
          if (rule.cloudwatchTimestamp) {
            timestamp = dp.getTimestamp().getTime();
          }

          if (dp.getSum() != null) {
            sumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sum", labelNames, labelValues, dp.getSum(), timestamp));
          }
          if (dp.getSampleCount() != null) {
            sampleCountSamples.add(new MetricFamilySamples.Sample(
                baseName + "_sample_count", labelNames, labelValues, dp.getSampleCount(), timestamp));
          }
          if (dp.getMinimum() != null) {
            minimumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_minimum", labelNames, labelValues, dp.getMinimum(), timestamp));
          }
          if (dp.getMaximum() != null) {
            maximumSamples.add(new MetricFamilySamples.Sample(
                baseName + "_maximum",labelNames, labelValues, dp.getMaximum(), timestamp));
          }
          if (dp.getAverage() != null) {
            averageSamples.add(new MetricFamilySamples.Sample(
                baseName + "_average", labelNames, labelValues, dp.getAverage(), timestamp));
          }
          if (dp.getExtendedStatistics() != null) {
            for (Map.Entry<String, Double> entry : dp.getExtendedStatistics().entrySet()) {
              ArrayList<MetricFamilySamples.Sample> samples = extendedSamples.get(entry.getKey());
              if (samples == null) {
                samples = new ArrayList<MetricFamilySamples.Sample>();
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
          if (!publishedResourceInfo.contains(resourceTagMapping.getResourceARN())) {
            List<String> labelNames = new ArrayList<String>();
            List<String> labelValues = new ArrayList<String>();
            labelNames.add("job");
            labelValues.add(jobName);
            labelNames.add("instance");
            labelValues.add("");
            labelNames.add("arn");
            labelValues.add(resourceTagMapping.getResourceARN());
            labelNames.add(safeName(toSnakeCase(rule.awsTagSelect.resourceIdDimension)));
            labelValues.add(extractResourceIdFromArn(resourceTagMapping.getResourceARN()));
            for (Tag tag: resourceTagMapping.getTags()) {
              // Avoid potential collision between resource tags and other metric labels by adding the "tag_" prefix
              // The AWS tags are case sensitive, so to avoid loosing information and label collisions, tag keys are not snaked cased
              labelNames.add("tag_" + safeName(tag.getKey()));
              labelValues.add(tag.getValue());
            }
            List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
            samples.add(new MetricFamilySamples.Sample("aws_resource_info", labelNames, labelValues, 1));
            mfs.add(new MetricFamilySamples("aws_resource_info", Type.GAUGE, "AWS information available for resource", samples));
            
            publishedResourceInfo.add(resourceTagMapping.getResourceARN());
          }
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

