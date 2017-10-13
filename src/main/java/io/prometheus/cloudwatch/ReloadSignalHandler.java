package io.prometheus.cloudwatch;

import io.prometheus.cloudwatch.WebServer;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.Signal;
import sun.misc.SignalHandler;

class ReloadSignalHandler {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    protected static void start() {
        Signal.handle(new Signal("HUP"), new SignalHandler() {
            public void handle(Signal signal) {
                try {
                    WebServer.collector.reloadConfig();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Configuration reload failed", e);
                }
            }
        });
    }
}
