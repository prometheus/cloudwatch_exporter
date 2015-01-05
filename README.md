Cloudwatch Exporter
=====

An exporter for [Amazon Cloudwatch](http://aws.amazon.com/cloudwatch/), for Prometheus.

## Building and running

`mvn package` to build.

`java -jar target/cloudwatch_exporter-0.1-SNAPSHOT-jar-with-dependencies.jar 1234 example.json` to run.

## Credentials and permissions

The Cloudwatch Exporter uses the [AWS Java SDK](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/welcome.html), which offers [a variety of ways to provide credentials](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html). This includes the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` enviroment variables

The `cloudwatch:ListMetrics` and `cloudwatch:GetMetricStatistics` IAM permissions are required.

## Configuration
The configuration is in JSON, an example with common options:
```
{ 
  "region": "eu-west-1",
  "metrics": [
    {"aws_namespace": "AWS/ELB", "aws_metric_name": "RequestCount",
     "aws_dimensions": ["AvailabilityZone", "LoadBalancerName"], "aws_statistics": ["Sum"]},
  ]
}
```
Name     | Description
---------|------------
region   | Required. The AWS region to connect to.
metrics  | Required. A list of Cloudwatch metrics to retrieve and export
aws_namespace  | Required. Namespace of the Cloudwatch metric.
aws_metric_name  | Required. Metric name of the Cloudwatch metric.
aws_dimensions | Optional. Which dimension to fan out over.
aws_statistics | Optional. A list of statistics to retrieve, values can include Sum, SampleCount, Minimum, Maximum, Average. Defaults to all statistics.
range_seconds | Optional. How far back to request data for, useful for cases such as Billing metrics that are only set every few hours. Defaults to 600s. Can be set globally and per metric.
period_seconds | Optional. [Period](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#CloudWatchPeriods) to request the metric for. Only the most recent data point is used. Defaults to 60s. Can be set globally and per metric.

The above config will export time series such as 
```
# HELP aws_elb_request_count_sum Cloudwatch metric AWS/ELB RequestCount Dimensions: ["AvailabilityZone","LoadBalancerName"] Statistic: Sum Unit: Count
# TYPE aws_elb_request_count_sum gauge
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="mylb",availability_zone="eu-west-1c",} 42.0
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="myotherlb",availability_zone="eu-west-1c",} 7.0
```

All metrics are exported as gauges. Timestamps from Cloudwatch are not passed to Prometheus, pending resolution of [#398](https://github.com/prometheus/prometheus/issues/398).

In addition `cloudwatch_exporter_scrape_error` will be non-zero if an error occured during the scrape, and `cloudwatch_exporter_scrape_duration_seconds` has the duration of that scrape.

### Cost

Amazon charges for every API request, see the [current charges](http://aws.amazon.com/cloudwatch/pricing/).

Every metric retrieved requires one API request, which can include multiple statistics. In addition, when `aws\_dimensions` is provided, the exporter needs to do API requests to determine what metrics to request. This should be negligable compared to the requests for the metrics themselvs.

If you've 100 API requests every minute, with the price of USD$10 per million requests (as of Jan 2015), that's around $45 per month. The `cloudwatch_requests_total` counter tracks how many requests are being made.


