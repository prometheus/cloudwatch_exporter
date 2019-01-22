package io.prometheus.cloudwatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HealthServlet extends HttpServlet {

    private final CloudWatchCollector collector;

    public HealthServlet(CloudWatchCollector collector) {
        super();
        this.collector = collector;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");
        if (collector.isScrapeError()) {
            resp.getWriter().print(CloudWatchCollector.SCRAPE_ERROR_MSG);
            resp.setStatus(500);
        } else {
            resp.getWriter().print("ok");
        }
    }
}
