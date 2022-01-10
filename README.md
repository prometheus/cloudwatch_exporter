CloudWatch Exporter
=====

A Prometheus exporter for [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/).

## Alternatives

For ECS workloads, there is also an [ECS exporter](https://github.com/prometheus-community/ecs_exporter).

For a different approach to CloudWatch metrics, with automatic discovery, consider [Yet Another CloudWatch Exporter (YACE)](https://github.com/nerdswords/yet-another-cloudwatch-exporter).

## Building and running

Cloudwatch Exporter requires at least Java 8.

`mvn package` to build.

`java -jar target/cloudwatch_exporter-*-SNAPSHOT-jar-with-dependencies.jar 9106 example.yml` to run.

The most recent pre-built JAR can be found at http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22cloudwatch_exporter%22

## Credentials and permissions

The CloudWatch Exporter uses the
[AWS Java SDK](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/welcome.html),
which offers [a variety of ways to provide credentials](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html).
This includes the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment
variables.

The `cloudwatch:ListMetrics` and `cloudwatch:GetMetricStatistics` IAM permissions are required.
The `tag:GetResources` IAM permission is also required to use the `aws_tag_select` feature.

## Configuration
The configuration is in YAML.

An example with common options and `aws_dimension_select`:
```
---
region: eu-west-1
metrics:
 - aws_namespace: AWS/ELB
   aws_metric_name: RequestCount
   aws_dimensions: [AvailabilityZone, LoadBalancerName]
   aws_dimension_select:
     LoadBalancerName: [myLB]
   aws_statistics: [Sum]
```

A similar example with common options and `aws_tag_select`:
```
---
region: eu-west-1
metrics:
 - aws_namespace: AWS/ELB
   aws_metric_name: RequestCount
   aws_dimensions: [AvailabilityZone, LoadBalancerName]
   aws_tag_select:
     tag_selections:
       Monitoring: ["enabled"]
     resource_type_selection: "elasticloadbalancing:loadbalancer"
     resource_id_dimension: LoadBalancerName
   aws_statistics: [Sum]
```
**Note:** configuration examples for different namespaces can be found in [examples](./examples) directory

**Note:** A configuration builder can be found [here](https://github.com/djloude/cloudwatch_exporter_metrics_config_builder).

Name     | Description
---------|------------
region   | Optional. The AWS region to connect to. If none is provided, an attempt will be made to determine the region from the [default region provider chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-region-selection.html#default-region-provider-chain).
role_arn   | Optional. The AWS role to assume. Useful for retrieving cross account metrics.
metrics  | Required. A list of CloudWatch metrics to retrieve and export
aws_namespace  | Required. Namespace of the CloudWatch metric.
aws_metric_name  | Required. Metric name of the CloudWatch metric.
aws_dimensions | Required. This should contain exactly all the dimensions available for a metric. Run `aws cloudwatch list-metrics` to find out which dimensions you need to include for your metric.
aws_dimension_select | Optional. Which dimension values to filter. Specify a map from the dimension name to a list of values to select from that dimension.
aws_dimension_select_regex | Optional. Which dimension values to filter on with a regular expression. Specify a map from the dimension name to a list of regexes that will be applied to select from that dimension.
aws_tag_select | Optional. A tag configuration to filter on, based on mapping from the tagged resource ID to a CloudWatch dimension.
tag_selections | Optional, under `aws_tag_select`. Specify a map from a tag key to a list of tag values to apply [tag filtering](https://docs.aws.amazon.com/resourcegroupstagging/latest/APIReference/API_GetResources.html#resourcegrouptagging-GetResources-request-TagFilters) on resources from which metrics will be gathered.
resource_type_selection | Required, under `aws_tag_select`. Specify the [resource type](https://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html#genref-aws-service-namesspaces) to filter on. `resource_type_selection` should be comprised as `service:resource_type`, as per the [resource group tagging API](https://docs.aws.amazon.com/resourcegroupstagging/latest/APIReference/API_GetResources.html#resourcegrouptagging-GetResources-request-TagFilters).
resource_id_dimension | Required, under `aws_tag_select`. For the current metric, specify which CloudWatch dimension maps to the ARN [resource ID](https://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html#arns-syntax).
aws_statistics | Optional. A list of statistics to retrieve, values can include Sum, SampleCount, Minimum, Maximum, Average. Defaults to all statistics unless extended statistics are requested.
aws_extended_statistics | Optional. A list of extended statistics to retrieve. Extended statistics currently include percentiles in the form `pN` or `pN.N`.
delay_seconds | Optional. The newest data to request. Used to avoid collecting data that has not fully converged. Defaults to 600s. Can be set globally and per metric.
range_seconds | Optional. How far back to request data for. Useful for cases such as Billing metrics that are only set every few hours. Defaults to 600s. Can be set globally and per metric.
period_seconds | Optional. [Period](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#CloudWatchPeriods) to request the metric for. Only the most recent data point is used. Defaults to 60s. Can be set globally and per metric.
set_timestamp | Optional. Boolean for whether to set the Prometheus metric timestamp as the original Cloudwatch timestamp. For some metrics which are updated very infrequently (such as S3/BucketSize), Prometheus may refuse to scrape them if this is set to true (see #100). Defaults to true. Can be set globally and per metric.

The above config will export time series such as
```
# HELP aws_elb_request_count_sum CloudWatch metric AWS/ELB RequestCount Dimensions: ["AvailabilityZone","LoadBalancerName"] Statistic: Sum Unit: Count
# TYPE aws_elb_request_count_sum gauge
aws_elb_request_count_sum{job="aws_elb",instance="",load_balancer_name="mylb",availability_zone="eu-west-1c",} 42.0
aws_elb_request_count_sum{job="aws_elb",instance="",load_balancer_name="myotherlb",availability_zone="eu-west-1c",} 7.0
```

If the `aws_tag_select` feature was used, an additional information metric will be exported for each AWS tagged resource matched by the resource type selection and tag selection (if specified), such as
```
# HELP aws_resource_info AWS information available for resource
# TYPE aws_resource_info gauge
aws_resource_info{job="aws_elb",instance="",arn="arn:aws:elasticloadbalancing:eu-west-1:121212121212:loadbalancer/mylb",load_balancer_name="mylb",tag_Monitoring="enabled",tag_MyOtherKey="MyOtherValue",} 1.0
```
aws_recource_info can be joined with other metrics using group_left in PromQL such as the following:
```
  aws_elb_request_count_sum
* on(load_balancer_name) group_left(tag_MyOtherKey)
  aws_resource_info
```
All metrics are exported as gauges.

In addition `cloudwatch_exporter_scrape_error` will be non-zero if an error
occurred during the scrape, and `cloudwatch_exporter_scrape_duration_seconds`
contains the duration of that scrape. `cloudwatch_exporter_build_info` will be
non-zero if an error occurred scraping the build info.

### Build Info Metric (New)
`cloudwatch_exporter_build_info` is a new default cloudwatch exporter metric that contains the current
cloudwatch exporter version and release date as label values. If the numeric metric value is non-zero
the build information scrap failed. 

To display the metrics label values in Grafana:
```
1. Query for the cloudwatch_exporter_build_info
2. Set the Legend to the desired label (ex. {{build_version}} or {{release_date}})
3. Select Stat visualization
4. Under stat styles select Name
5. To remove the graph, select Graph Mode None
```

### CloudWatch doesn't always report data

Cloudwatch reports data either always or only in some cases, example only if there is a non-zero value. The CloudWatch Exporter mirrors this behavior, so you should refer to the Cloudwatch documentation to find out if your metric is always reported or not.

### Timestamps

CloudWatch has been observed to sometimes take minutes for reported values to converge. The
default `delay_seconds` will result in data that is at least 10 minutes old
being requested to mitigate this. The samples exposed will have the timestamps of the
data from CloudWatch, so usual staleness semantics will not apply and values will persist
for 5m for instant vectors.

In practice this means that if you evaluate an instant vector at the current
time, you will not see data from CloudWatch. An expression such as
`aws_elb_request_count_sum offset 10m` will allow you to access the data, and
should be used in recording rules and alerts.

For certain metrics which update relatively rarely, such as from S3,
`set_timestamp` should be configured to false so that they are not exposed with
a timestamp. This is as the true timestamp from CloudWatch could be so old that
Prometheus would reject the sample.

### Special handling for certain DynamoDB metrics

The DynamoDB metrics listed below break the usual CloudWatch data model.

 * ConsumedReadCapacityUnits
 * ConsumedWriteCapacityUnits
 * ProvisionedReadCapacityUnits
 * ProvisionedWriteCapacityUnits
 * ReadThrottleEvents
 * WriteThrottleEvents

When these metrics are requested in the TableName dimension CloudWatch will
return data only for the table itself, not for its Global Secondary Indexes.
Retrieving data for indexes requires requesting data across both the TableName
and GlobalSecondaryIndexName dimensions. This behaviour is different to that
of every other CloudWatch namespace and requires that the exporter handle these
metrics differently to avoid generating duplicate HELP and TYPE lines.

When exporting one of the problematic metrics for an index the exporter will use
a metric name in the format `aws_dynamodb_METRIC_index_STATISTIC` rather than
the usual `aws_dynamodb_METRIC_STATISTIC`. The regular naming scheme will still
be used when exporting these metrics for a table, and when exporting any other
DynamoDB metrics not listed above.

### Reloading Configuration

There are two ways to reload configuration:

1. Send a SIGHUP signal to the pid: `kill -HUP 1234`
2. POST to the `reload` endpoint: `curl -X POST localhost:9106/-/reload`

If an error occurs during the reload, check the exporter's log output.

### Cost

Amazon charges for every CloudWatch API request, see the [current charges](http://aws.amazon.com/cloudwatch/pricing/).

Every metric retrieved requires one API request, which can include multiple
statistics. In addition, when `aws_dimensions` is provided, the exporter needs
to do API requests to determine what metrics to request. This should be
negligible compared to the requests for the metrics themselves.

In the case that all `aws_dimensions` are provided in the `aws_dimension_select` list, the exporter will not perform the
above API request.  It will request all possible combination of values for those dimensions.
This will reduce cost as the values for the dimensions do not need to be queried anymore, assuming that all possible value combinations are present in CloudWatch.

If you have 100 API requests every minute, with the price of USD$10 per million
requests (as of Aug 2018), that is around $45 per month. The
`cloudwatch_requests_total` counter tracks how many requests are being made.

When using the `aws_tag_select` feature, additional requests are made to the Resource Groups Tagging API, but these are [free](https://aws.amazon.com/blogs/aws/new-aws-resource-tagging-api/).
The `tagging_api_requests_total` counter tracks how many requests are being made for these.

## Docker Images

To run the CloudWatch exporter on Docker, you can use the image from

* [prom/cloudwatch-exporter](https://hub.docker.com/r/prom/cloudwatch-exporter/)
* [quay.io/prometheus/cloudwatch-exporter](https://quay.io/repository/prometheus/cloudwatch-exporter)

The available tags are

* `main`: snapshot updated on every push to the main branch
* `latest`: the latest released version
* `vX.Y.Z`: the specific version X.Y.Z. Note that up to version 0.11.0, the format was `cloudwatch-exporter_X.Y.Z`.

The image exposes port 9106 and expects the config in `/config/config.yml`.
To configure it, you can bind-mount a config from your host:

```sh
docker run -p 9106 -v /path/on/host/config.yml:/config/config.yml quay.io/prometheus/cloudwatch-exporter
```

Specify the config as the CMD:

```sh
docker run -p 9106 -v /path/on/host/us-west-1.yml:/config/us-west-1.yml quay.io/prometheus/cloudwatch-exporter /config/us-west-1.yml
```

Or create a config file named `config.yml` along with following
Dockerfile in the same directory and build it with `docker build`:

```Dockerfile
FROM prom/cloudwatch-exporter
ADD config.yml /config/
```
