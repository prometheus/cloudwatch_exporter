package io.prometheus.cloudwatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicReloadServlet extends HttpServlet {

    private static CloudWatchCollector collector;
    private static String configFilePath;

    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    public DynamicReloadServlet(CloudWatchCollector cc, String path) {
        this.collector = cc;
        this.configFilePath = path;
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOGGER.log(Level.INFO, "Reloading configuration file");

        this.collector.loadConfig(new FileReader(this.configFilePath));

        resp.setContentType("text/html");
        resp.getWriter().print("OK");
    }
}