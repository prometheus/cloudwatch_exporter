package io.prometheus.cloudwatch;

final class DimensionNameValueTuple {
  final String name;
  final String value;

  DimensionNameValueTuple(String name, String value) {
    this.name = name;
    this.value = value;
  }
}

