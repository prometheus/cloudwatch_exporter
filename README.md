CloudWatch Exporter for S3, API Gateway, Cloudwatch Events, Lambda
=====

docker run -e AWS_ACCESS_KEY_ID=... -e AWS_SECRET_ACCESS_KEY=... -p 9106:9106 -v $(pwd)/config.yml:/config/config.yml prom/cloudwatch-exporter
