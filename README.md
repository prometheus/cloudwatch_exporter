CloudWatch Exporter
=====

An exporter for [Amazon CloudWatch](http://aws.amazon.com/cloudwatch/), for Prometheus.

## Building and running

`mvn package` to build.

`java -jar target/cloudwatch_exporter-0.1-SNAPSHOT-jar-with-dependencies.jar 9106 example.json` to run.

## Credentials and permissions

The CloudWatch Exporter uses the
[AWS Java SDK](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/welcome.html),
which offers [a variety of ways to provide credentials](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html).
This includes the `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment
variables.

The `cloudwatch:ListMetrics` and `cloudwatch:GetMetricStatistics` IAM permissions are required.

## Configuration
The configuration is in JSON, an example with common options:
```
{
  "region": "us-east-1"
  "metrics": [
    {
      "aws_namespace": "AWS/ELB",
      "aws_metric_name": "RequestCount",
      "aws_dimensions": [
        "AvailabilityZone",
        "LoadBalancerName"
      ],
      "aws_dimension_select": {
        "LoadBalancerName": [
          "myLB"
        ]
      },
      "aws_statistics": [
        "Sum"
      ]
    }
  ]
}
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
aws_statistics | Optional. A list of statistics to retrieve, values can include Sum, SampleCount, Minimum, Maximum, Average. Defaults to all statistics.
delay_seconds | Optional. The newest data to request. Used to avoid collecting data that has not fully converged. Defaults to 600s. Can be set globally and per metric.
range_seconds | Optional. How far back to request data for. Useful for cases such as Billing metrics that are only set every few hours. Defaults to 600s. Can be set globally and per metric.
period_seconds | Optional. [Period](http://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/cloudwatch_concepts.html#CloudWatchPeriods) to request the metric for. Only the most recent data point is used. Defaults to 60s. Can be set globally and per metric.

The above config will export time series such as 
```
# HELP aws_elb_request_count_sum CloudWatch metric AWS/ELB RequestCount Dimensions: ["AvailabilityZone","LoadBalancerName"] Statistic: Sum Unit: Count
# TYPE aws_elb_request_count_sum gauge
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="mylb",availability_zone="eu-west-1c",} 42.0
aws_elb_request_count_sum{job="aws_elb",load_balancer_name="myotherlb",availability_zone="eu-west-1c",} 7.0
```

All metrics are exported as gauges.

Timestamps from CloudWatch are not passed to Prometheus, pending resolution of
[#398](https://github.com/prometheus/prometheus/issues/398). CloudWatch has
been observed to sometimes take minutes for reported values to converge. The
default `delay_seconds` will result in data that is at least 5 minutes old
being requested to mitigate this.

In addition `cloudwatch_exporter_scrape_error` will be non-zero if an error
occurred during the scrape, and `cloudwatch_exporter_scrape_duration_seconds`
contains the duration of that scrape.

### Cost

Amazon charges for every API request, see the [current charges](http://aws.amazon.com/cloudwatch/pricing/).

Every metric retrieved requires one API request, which can include multiple
statistics. In addition, when `aws_dimensions` is provided, the exporter needs
to do API requests to determine what metrics to request. This should be
negligible compared to the requests for the metrics themselves.

If you have 100 API requests every minute, with the price of USD$10 per million
requests (as of Jan 2015), that is around $45 per month. The
`cloudwatch_requests_total` counter tracks how many requests are being made.

## Docker Image

To run the CloudWatch exporter on Docker, you can use the [prom/cloudwatch-exporter](https://hub.docker.com/r/prom/cloudwatch-exporter/)
image. It exposes port 9106 and expects the config in `/config.yml`. To
configure it, you can either bind-mount a config from your host:

```
$ docker run -p 9106 -v /path/on/host/config.yml:/config.yml prom/cloudwatch-exporter
```

Or you create a config file named config.yml along with following
Dockerfile in the same directory and build it with `docker build`:

```
FROM prom/cloudwatch-exporter
```
