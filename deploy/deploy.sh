#!/bin/bash

set -e

GIT_UNTRACKED_FILES=`git ls-files --others --exclude-standard | wc -l`
GIT_UNSTAGED_DIFF=`git diff --no-ext-diff | wc -l`
GIT_STAGED_DIFF=`git diff-index --cached HEAD -- | wc -l`
DEPLOYMENT_YAML='deploy/deployment.yaml'
SERVICE_YAML='deploy/service.yaml'
SERVICE_MONITOR_YAML='deploy/service-monitor.yaml'

echo "Pulling latest code"
#git pull origin master

echo "Building image"
echo `pwd`
TAG=`git rev-parse HEAD`
docker run -v `pwd`:/cloudwatch_exporter -v ${M2_DIR}:/root/.m2 ${ORG}/${BUILD_IMAGE_NAME} /bin/bash /cloudwatch_exporter/deploy/build.sh
docker build -t ${ORG}/${IMAGE_NAME}:${TAG} .

echo "Pushing new image"
docker push ${ORG}/${IMAGE_NAME}:${TAG}

echo "Creating yamls"
cp deploy/deployment.sample.yaml ${DEPLOYMENT_YAML}
cp deploy/service.sample.yaml ${SERVICE_YAML}
cp deploy/service-monitor.sample.yaml ${SERVICE_MONITOR_YAML}

sed -i "s#{{tag}}#${TAG}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{org}}#${ORG}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{aws_access_key_id}}#${AWS_ACCESS_KEY_ID}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{aws_secret_access_key}}#${AWS_SECRET_ACCESS_KEY}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{image_secret}}#${IMAGE_SECRET}#g" ${DEPLOYMENT_YAML}

echo "Rolling update first status"
kubectl --context=k8sd.practodev.com apply -f ${DEPLOYMENT_YAML}
kubectl --context=k8sd.practodev.com apply -f ${SERVICE_YAML}
kubectl --context=k8sd.practodev.com apply -f ${SERVICE_MONITOR_YAML}
kubectl --context=k8sd.practodev.com get pods -n ${NAMESPACE}

echo "Check rollout status using"
echo "kubectl get pods -n ${NAMESPACE}"
