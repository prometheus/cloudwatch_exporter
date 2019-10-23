package io.prometheus.cloudwatch;

import java.io.FileReader;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.prometheus.client.exporter.MetricsServlet;

public class WebServer {
    private static final Logger LOGGER = Logger.getLogger(WebServer.class.getPackage().getName());

    private static Options options = new Options();

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
        new HelpFormatter().printHelp("cloudwatch_exporter [OPTIONS] <port> <yml configuration file>", options, false);
        System.out.println("123456789012345678901234567890");
    }

    private static CommandLine parseArgs(String[] args) {
      options.addOption(new Option("h", "help", false, "print this message"));
      options.addOption(new Option("v", "version", false, "print version"));
      options.addOption(new Option("d", "debug", false, "enable debug logs"));
      CommandLineParser parser = new DefaultParser();
      CommandLine line = null;
      try {
        line = parser.parse(options, args);
        if (line.hasOption("help")) {
          printHelp();
          System.exit(0);
        }
        else if (line.hasOption("version")) {
          System.out.println(WebServer.class.getPackage().getImplementationVersion());
          System.exit(0);
        }
      }
      catch(ParseException exp) {
        System.err.println("Parsing arguments failed. Reason: " + exp.getMessage());
        System.exit(1);
      }
      finally {
        return line;
      }
    }

    public static String configFilePath;
    public static int port;

    public static void main(String[] args) throws Exception {
        CommandLine line = parseArgs(args);
        if (line.hasOption("debug")) {
          enableDebugLogs();
        }
        String[] positionalArgs = line.getArgs();
        if (positionalArgs.length < 2) {
          printHelp();
          System.exit(0);
        }
        port = Integer.parseInt(positionalArgs[0]);
        configFilePath = positionalArgs[1];

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

