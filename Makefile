# This is a dummy Makefile to satisfy expectations from common workflows.
# Since this is not a Go project, we do not import Makefile.common from
# github.com/prometheus/prometheus, but some common workflows depend on targets
# there.

DOCKER_IMAGE_NAME = cloudwatch-exporter
DOCKER_REPO ?= prom

.PHONY: docker-repo-name
docker-repo-name: common-docker-repo-name

.PHONY: common-docker-repo-name
common-docker-repo-name:
	@echo "$(DOCKER_REPO)/$(DOCKER_IMAGE_NAME)"
