package io.prometheus.cloudwatch;

import java.io.FileReader;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.prometheus.client.exporter.MetricsServlet;

public class WebServer {

    public static String configFilePath;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: WebServer <port> <yml configuration file>");
            System.exit(1);
        }

        configFilePath = args[1];
        CloudWatchCollector collector = null;
        FileReader reader = null;

        try {
          reader = new FileReader(configFilePath);
          collector = new CloudWatchCollector(new FileReader(configFilePath)).register();
        } finally {
          if (reader != null) {
            reader.close();
          }
        }

        ReloadSignalHandler.start(collector);

        int port = Integer.parseInt(args[0]);
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

