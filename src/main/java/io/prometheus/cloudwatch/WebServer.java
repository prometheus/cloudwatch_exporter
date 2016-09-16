package io.prometheus.cloudwatch;

import io.prometheus.client.exporter.MetricsServlet;
import java.io.FileReader;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class WebServer {
   public static void main(String[] args) throws Exception {
     if (args.length < 2) {
       System.err.println("Usage: WebServer <port> <json configuration file>");
       System.exit(1);
     }
     CloudWatchCollector cc = new CloudWatchCollector(new FileReader(args[1])).register();

     int port = Integer.parseInt(args[0]);
     Server server = new Server(port);
     ServletContextHandler context = new ServletContextHandler();
     context.setContextPath("/");
     server.setHandler(context);
     context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
     context.addServlet(new ServletHolder(new HealthCheckServlet(cc.client)), "/status");
     server.start();
     server.join();
   }
}
