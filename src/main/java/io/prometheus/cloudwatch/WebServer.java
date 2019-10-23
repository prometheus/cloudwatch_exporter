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

    private static void enableDebugLogs() {
      LOGGER.setLevel(Level.FINEST);
      ConsoleHandler handler = new ConsoleHandler();
      LOGGER.addHandler(handler);
      for (Handler h: LOGGER.getHandlers()) {
        h.setLevel(Level.FINEST);
      }
      LOGGER.log(Level.FINEST, "debug logging is on");
    }

    private static void printHelp() {
        System.out.println("cloudwatch_exporter [OPTIONS] <port> <yml configuration file>");
        System.out.println("-d,--debug     enable debug logs");
        System.out.println("-h,--help      print this message");
        System.out.println("-v,--version   print version");
    }

    private static void printVersion() {
      System.out.println(WebServer.class.getPackage().getImplementationVersion());
    }

    private static String[] parseOptions(String[] args) {
      int highestOptionIndex = 0;
      for (String arg : args) {
        if (arg.equals("-h") || arg.equals("--help")) {
          printHelp();
          System.exit(0);
        }
        else if (arg.equals("-v") || arg.equals("--version")) {
          printVersion();
          System.exit(0);
        }
        else if (arg.equals("-d") || arg.equals("--debug")) {
          enableDebugLogs();
          highestOptionIndex++;
        }
      }
      return Arrays.copyOfRange(args, highestOptionIndex, args.length);
    }

    public static String configFilePath;
    public static int port;

    public static void main(String[] args) throws Exception {
        args = parseOptions(args);
        if (args.length < 2) {
          printHelp();
          System.exit(0);
        }
        port = Integer.parseInt(args[0]);
        configFilePath = args[1];

        CloudWatchCollector collector = null;
        FileReader reader = null;

        try {
          reader = new FileReader(configFilePath);
          collector = new CloudWatchCollector(new FileReader(configFilePath)).register();
        } finally {
          reader.close();
        }

        ReloadSignalHandler.start(collector);

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        context.addServlet(new ServletHolder(new DynamicReloadServlet(collector)), "/-/reload");
        context.addServlet(new ServletHolder(new HealthServlet()), "/-/healthy");
        context.addServlet(new ServletHolder(new HealthServlet()), "/-/ready");
        context.addServlet(new ServletHolder(new HomePageServlet()), "/");
        server.start();
        server.join();
    }
}

