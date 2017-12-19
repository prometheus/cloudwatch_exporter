#!/bin/bash

set -e

GIT_UNTRACKED_FILES=`git ls-files --others --exclude-standard | wc -l`
GIT_UNSTAGED_DIFF=`git diff --no-ext-diff | wc -l`
GIT_STAGED_DIFF=`git diff-index --cached HEAD -- | wc -l`
NAMESPACE='monitoring'
ORG='practodev'
IMAGE_NAME='cloudwatch-exporter'
IMAGE_SECRET='dev-secret-docker'
DEPLOYMENT_YAML='deploy/deployment.yaml'

echo "Pulling latest code"
#git pull origin master

echo "Building image"
TAG=`git rev-parse HEAD`
docker build -t ${ORG}/${IMAGE_NAME}:${TAG} .

echo "Pushing new image"
docker push ${ORG}/${IMAGE_NAME}:${TAG}

echo "Creating new deployment yaml"
cp deploy/deployment.sample.yaml ${DEPLOYMENT_YAML}
sed -i "s#{{tag}}#${TAG}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{org}}#${ORG}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{aws_access_key_id}}#${AWS_ACCESS_KEY_ID}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{aws_secret_access_key}}#${AWS_SECRET_ACCESS_KEY}#g" ${DEPLOYMENT_YAML}
sed -i "s#{{image_secret}}#${IMAGE_SECRET}#g" ${DEPLOYMENT_YAML}

echo "Rolling update first status"
kubectl apply -f ${DEPLOYMENT_YAML}
kubectl get pods -n ${NAMESPACE} | grep cloudwatch

echo "Check rollout status using"
echo kubectl get pods -n ${NAMESPACE} | grep cloudwatch 
