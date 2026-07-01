package io.prometheus.cloudwatch;

import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet;
import java.io.FileReader;
import java.util.EnumSet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/** Embedded Jetty web server that exposes CloudWatch metrics via HTTP. */
public class WebServer {

  /** Path to the YAML configuration file set from command-line arguments. */
  public static String configFilePath;

  /** Constructs a WebServer. */
  public WebServer() {}

  /**
   * Starts the web server.
   *
   * @param args command line arguments; args[0] is the port, args[1] is the YAML config file path
   * @throws Exception if the server fails to start
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: WebServer <port> <yml configuration file>");
      System.exit(1);
    }

    configFilePath = args[1];
    CloudWatchCollector collector = null;
    new BuildInfoCollector().register();
    try (FileReader reader = new FileReader(configFilePath); ) {
      collector = new CloudWatchCollector(reader).register();
    }
    DefaultExports.initialize();

    ReloadSignalHandler.start(collector);

    int port = Integer.parseInt(args[0]);
    Server server = new Server();
    HttpConfiguration httpConfig = new HttpConfiguration();
    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
    connector.setPort(port);
    server.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
    context.addServlet(new ServletHolder(new DynamicReloadServlet(collector)), "/-/reload");
    context.addServlet(new ServletHolder(new HealthServlet()), "/-/healthy");
    context.addServlet(new ServletHolder(new HealthServlet()), "/-/ready");
    context.addServlet(new ServletHolder(new HomePageServlet()), "/");

    DisallowHttpMethods disallowHandler = new DisallowHttpMethods(EnumSet.of(HttpMethod.TRACE));
    disallowHandler.setHandler(context);
    server.setHandler(disallowHandler);

    server.start();
    server.join();
  }
}
