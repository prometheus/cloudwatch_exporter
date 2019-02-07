CloudWatch Exporter
=====

An exporter for [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/), for Prometheus.

## Building and running

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

## Configuration
The configuration is in YAML, an example with common options:
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
Name     | Description
---------|------------
region   | Required. The AWS region to connect to.
role_arn   | Optional. The AWS role to assume. Useful for retrieving cross account metrics.
metrics  | Required. A list of CloudWatch metrics to retrieve and export
aws_namespace  | Required. Namespace of the CloudWatch metric.
aws_metric_name  | Required. Metric name of the CloudWatch metric.
aws_dimensions | Optional. Which dimension to fan out over.
aws_dimension_select | Optional. Which dimension values to filter. Specify a map from the dimension name to a list of values to select from that dimension.
aws_dimension_select_regex | Optional. Which dimension values to filter on with a regular expression. Specify a map from the dimension name to a list of regexes that will be applied to select from that dimension.
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
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="mylb",availability_zone="eu-west-1c",} 42.0
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="myotherlb",availability_zone="eu-west-1c",} 7.0
```

All metrics are exported as gauges.

In addition `cloudwatch_exporter_scrape_error` will be non-zero if an error
occurred during the scrape, and `cloudwatch_exporter_scrape_duration_seconds`
contains the duration of that scrape.

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

Amazon charges for every API request, see the [current charges](http://aws.amazon.com/cloudwatch/pricing/).

Every metric retrieved requires one API request, which can include multiple
statistics. In addition, when `aws_dimensions` is provided, the exporter needs
to do API requests to determine what metrics to request. This should be
negligible compared to the requests for the metrics themselves.

If you have 100 API requests every minute, with the price of USD$10 per million
requests (as of Aug 2018), that is around $45 per month. The
`cloudwatch_requests_total` counter tracks how many requests are being made.

## Docker Image

To run the CloudWatch exporter on Docker, you can use the [prom/cloudwatch-exporter](https://hub.docker.com/r/prom/cloudwatch-exporter/)
image. It exposes port 9106 and expects the config in `/config/config.yml`. To
configure it, you can bind-mount a config from your host:

```
$ docker run -p 9106 -v /path/on/host/config.yml:/config/config.yml prom/cloudwatch-exporter
```

Specify the config as the CMD:

```
$ docker run -p 9106 -v /path/on/host/us-west-1.yml:/config/us-west-1.yml prom/cloudwatch-exporter /config/us-west-1.yml
```

Or create a config file named /config/config.yml along with following
Dockerfile in the same directory and build it with `docker build`:

```
FROM prom/cloudwatch-exporter
```
