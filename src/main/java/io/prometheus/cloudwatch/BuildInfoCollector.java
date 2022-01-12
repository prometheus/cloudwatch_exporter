package io.prometheus.cloudwatch;

import io.prometheus.client.Collector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BuildInfoCollector extends Collector {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        List<MetricFamilySamples.Sample> samples;
        List<String> labelNames = new ArrayList<>();
        List<String> labelValues = new ArrayList<>();

        String buildVersion = "";
        String releaseDate = "";
        try {
            final Properties properties = new Properties();
            properties.load(CloudWatchCollector.class.getClassLoader().getResourceAsStream(".properties"));
            buildVersion = properties.getProperty("BuildVersion");
            releaseDate = properties.getProperty("ReleaseDate");

        }
        catch (IOException e) {
            buildVersion = "unknown";
            releaseDate = "unknown";
            LOGGER.log(Level.WARNING, "CloudWatch build info scrape failed", e);
        }

        labelNames.add("build_version");
        labelValues.add(buildVersion);
        labelNames.add("release_date");
        labelValues.add(releaseDate);

        samples = new ArrayList<>();
        samples.add(new MetricFamilySamples.Sample(
                "cloudwatch_exporter_build_info", labelNames, labelValues, 1));
        mfs.add(new MetricFamilySamples("cloudwatch_exporter_build_info",
                Type.GAUGE, "Non-zero if build info scrape failed.", samples));

        return mfs;
    }
}
