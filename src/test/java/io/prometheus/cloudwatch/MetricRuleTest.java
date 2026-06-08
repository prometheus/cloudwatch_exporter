package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

public class MetricRuleTest {

  @Test
  public void equalRulesHaveSameHashCode() {
    MetricRule left = populatedRule();
    MetricRule right = populatedRule();
    right.awsTagSelect = left.awsTagSelect;

    assertThat(left).isEqualTo(right);
    assertThat(left.hashCode()).isEqualTo(right.hashCode());
  }

  @Test
  public void equalsHandlesIdentityNullAndDifferentTypes() {
    MetricRule rule = populatedRule();

    assertThat(rule).isEqualTo(rule);
    assertThat(rule).isNotEqualTo(null);
    assertThat(rule).isNotEqualTo("not a metric rule");
  }

  @Test
  public void equalsDetectsDifferentFields() {
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.periodSeconds = 61));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.rangeSeconds = 121));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.delaySeconds = 31));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.cloudwatchTimestamp = false));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.useGetMetricData = false));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.awsNamespace = "AWS/S3"));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.awsMetricName = "Latency"));
    assertThat(populatedRule())
        .isNotEqualTo(changedRule(rule -> rule.awsStatistics = List.of(Statistic.SUM)));
    assertThat(populatedRule())
        .isNotEqualTo(changedRule(rule -> rule.awsExtendedStatistics = List.of("p99")));
    assertThat(populatedRule())
        .isNotEqualTo(changedRule(rule -> rule.awsDimensions = List.of("BucketName")));
    assertThat(populatedRule())
        .isNotEqualTo(
            changedRule(
                rule -> rule.awsDimensionSelect = Map.of("LoadBalancerName", List.of("b"))));
    assertThat(populatedRule())
        .isNotEqualTo(
            changedRule(
                rule -> rule.awsDimensionSelectRegex = Map.of("LoadBalancerName", List.of("b.*"))));
    assertThat(populatedRule())
        .isNotEqualTo(
            changedRule(rule -> rule.awsTagSelect = new CloudWatchCollector.AWSTagSelect()));
    assertThat(populatedRule()).isNotEqualTo(changedRule(rule -> rule.help = "other help"));
    assertThat(populatedRule())
        .isNotEqualTo(changedRule(rule -> rule.listMetricsCacheTtl = Duration.ofMinutes(2)));
  }

  @Test
  public void equalsDetectsDifferentFieldsAfterEqualTagSelect() {
    MetricRule left = populatedRule();
    MetricRule right = populatedRule();
    right.awsTagSelect = left.awsTagSelect;
    right.help = "other help";

    assertThat(left).isNotEqualTo(right);
  }

  @Test
  public void warnOnEmptyListDimensionsDoesNotAffectEquality() {
    MetricRule left = populatedRule();
    MetricRule right = populatedRule();
    right.awsTagSelect = left.awsTagSelect;
    right.warnOnEmptyListDimensions = !left.warnOnEmptyListDimensions;

    assertThat(left).isEqualTo(right);
    assertThat(left.hashCode()).isEqualTo(right.hashCode());
  }

  @Test
  public void hashCodeHandlesNullFields() {
    assertThat(new MetricRule().hashCode()).isZero();
  }

  private MetricRule changedRule(RuleChange change) {
    MetricRule rule = populatedRule();
    change.apply(rule);
    return rule;
  }

  private MetricRule populatedRule() {
    MetricRule rule = new MetricRule();
    rule.awsNamespace = "AWS/ELB";
    rule.awsMetricName = "RequestCount";
    rule.periodSeconds = 60;
    rule.rangeSeconds = 120;
    rule.delaySeconds = 30;
    rule.awsStatistics = List.of(Statistic.AVERAGE, Statistic.MAXIMUM);
    rule.awsExtendedStatistics = List.of("p95");
    rule.awsDimensions = List.of("LoadBalancerName");
    rule.awsDimensionSelect = Map.of("LoadBalancerName", List.of("a"));
    rule.awsDimensionSelectRegex = Map.of("LoadBalancerName", List.of("a.*"));
    rule.awsTagSelect = new CloudWatchCollector.AWSTagSelect();
    rule.help = "help text";
    rule.cloudwatchTimestamp = true;
    rule.useGetMetricData = true;
    rule.listMetricsCacheTtl = Duration.ofMinutes(1);
    rule.warnOnEmptyListDimensions = true;
    return rule;
  }

  private interface RuleChange {
    void apply(MetricRule rule);
  }
}
