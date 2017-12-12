package io.prometheus.cloudwatch;

import io.prometheus.client.exporter.MetricsServlet;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public class WebServer {

    public static String configFilePath;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: WebServer <port> <yml configuration file>");
            System.exit(1);
        }

        configFilePath = args[1];
        CloudWatchCollector collector = new CloudWatchCollector(new FileReader(configFilePath)).register();

        ReloadSignalHandler.start(collector);

        int port = Integer.parseInt(args[0]);
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
        context.addServlet(new ServletHolder(new DynamicReloadServlet(collector)), "/-/reload");
        context.addServlet(new ServletHolder(new HomePageServlet()), "/");
        server.start();
        server.join();
    }
}

