package io.prometheus.cloudwatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DynamicReloadServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(CloudWatchCollector.class.getName());

    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        WebServer.collector.reloadConfig();

        resp.setContentType("text/plain");
        resp.getWriter().print("OK");
    }
}
