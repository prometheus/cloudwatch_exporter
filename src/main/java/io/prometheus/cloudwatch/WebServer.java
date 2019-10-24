package io.prometheus.cloudwatch;

import java.io.FileReader;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.prometheus.client.exporter.MetricsServlet;

public class WebServer {
    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getPackage().getName());

    public static String configFilePath;
    public static int port;

    private static void enableDebugLogs() {
      LOGGER.setLevel(Level.FINEST);
      ConsoleHandler handler = new ConsoleHandler();
      LOGGER.addHandler(handler);
      for (Handler h: LOGGER.getHandlers()) {
        h.setLevel(Level.FINEST);
      }
      LOGGER.log(Level.FINEST, "debug logging is on");
    }

    private static void printUsage() {
        System.out.println("cloudwatch_exporter [-d] <port> <yml configuration file>");
        System.out.println("-d,--debug     enable debug logs");
    }

    private static void parseOptions(String[] args) {
        // usage can be either:
        // -d <port> <yml configuration file>
        // OR
        // <port> <yml configuration file>
        int argsOffset = 0;
        if (args[0].equals("-d")) {
          enableDebugLogs();
          argsOffset++;
        }
        if ((args.length - argsOffset) < 2) {
          printUsage();
          System.exit(0);
        }
        port = Integer.parseInt(args[argsOffset + 0]);
        configFilePath = args[argsOffset + 1];
    }

    public static void main(String[] args) throws Exception {
        parseOptions(args);

        CloudWatchCollector collector = null;
        FileReader reader = null;
        try {
          reader = new FileReader(configFilePath);
          collector = new CloudWatchCollector(new FileReader(configFilePath)).register();
          reader.close();
          ReloadSignalHandler.start(collector);
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, "failed to start collector");
          LOGGER.log(Level.SEVERE, e.toString());
          System.exit(1);
        }

        Server server = null;
        try {
          server = new Server(port);
          ServletContextHandler context = new ServletContextHandler();
          context.setContextPath("/");
          server.setHandler(context);
          context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
          context.addServlet(new ServletHolder(new DynamicReloadServlet(collector)), "/-/reload");
          context.addServlet(new ServletHolder(new HealthServlet()), "/-/healthy");
          context.addServlet(new ServletHolder(new HealthServlet()), "/-/ready");
          context.addServlet(new ServletHolder(new HomePageServlet()), "/");
          server.start();
        } catch (Exception e) {
          LOGGER.log(Level.SEVERE, String.format("failed to start sever on port %d", port));
          LOGGER.log(Level.SEVERE, e.toString());
          System.exit(1);
        } finally {
          server.join();
        }

    }
}

