package io.prometheus.cloudwatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HealthServlet extends HttpServlet {
  private static final long serialVersionUID = 5543118274931292897L;
  private final CloudWatchCollector collector;

  public HealthServlet(CloudWatchCollector collector) {
    this.collector = collector;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (this.collector.hasError()) {
      resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    } else {
      resp.setContentType("text/plain");
      resp.getWriter().print("ok");
    }
  }
}
