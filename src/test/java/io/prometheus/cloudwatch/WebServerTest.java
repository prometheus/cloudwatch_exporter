package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class WebServerTest {
  @Test
  public void checkConfigAcceptsValidYaml() throws Exception {
    Path config =
        Files.writeString(
            Files.createTempFile("cloudwatch-exporter-check-valid", ".yml"),
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n  aws_metric_name: RequestCount\n");
    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      int status = WebServer.checkConfig(new String[] {"--check-config", config.toString()});
      assertThat(status).isEqualTo(0);
      assertThat(captured.toString()).contains("OK");
    } finally {
      System.setOut(originalOut);
      Files.deleteIfExists(config);
    }
  }

  @Test
  public void checkConfigAcceptsExampleYaml() {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      int status = WebServer.checkConfig(new String[] {"--check-config", "example.yml"});
      assertThat(status).isEqualTo(0);
      assertThat(captured.toString()).contains("OK");
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void checkConfigRejectsMissingMetrics() throws Exception {
    Path config =
        Files.writeString(
            Files.createTempFile("cloudwatch-exporter-check-missing-metrics", ".yml"),
            "---\nregion: reg\n");
    PrintStream originalErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured));
    try {
      int status = WebServer.checkConfig(new String[] {"--check-config", config.toString()});
      assertThat(status).isEqualTo(1);
      assertThat(captured.toString()).contains("Invalid configuration: Must provide metrics");
    } finally {
      System.setErr(originalErr);
      Files.deleteIfExists(config);
    }
  }

  @Test
  public void checkConfigRejectsMissingAwsMetricName() throws Exception {
    Path config =
        Files.writeString(
            Files.createTempFile("cloudwatch-exporter-check-missing-metric-name", ".yml"),
            "---\nmetrics:\n- aws_namespace: AWS/ELB\n");
    PrintStream originalErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured));
    try {
      int status = WebServer.checkConfig(new String[] {"--check-config", config.toString()});
      assertThat(status).isEqualTo(1);
      assertThat(captured.toString())
          .contains("Invalid configuration: Must provide aws_namespace and aws_metric_name");
    } finally {
      System.setErr(originalErr);
      Files.deleteIfExists(config);
    }
  }

  @Test
  public void checkConfigRequiresConfigPath() {
    PrintStream originalErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setErr(new PrintStream(captured));
    try {
      int status = WebServer.checkConfig(new String[] {"--check-config"});
      assertThat(status).isEqualTo(1);
      assertThat(captured.toString())
          .contains("Usage: WebServer --check-config <yml configuration file>");
    } finally {
      System.setErr(originalErr);
    }
  }
}
