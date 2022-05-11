package io.prometheus.cloudwatch;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class DynamicReloadServlet extends HttpServlet {
  private static final long serialVersionUID = 9078784531819993933L;
  private final CloudWatchCollector collector;

  public DynamicReloadServlet(CloudWatchCollector collector) {
    this.collector = collector;
  }

  static final String CONTENT_TYPE = "text/plain";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    resp.setContentType(CONTENT_TYPE);
    try {
      resp.getWriter().print("Only POST requests allowed");
    } catch (IOException e) {
      // Ignored
    }
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      collector.reloadConfig();
    } catch (IOException e) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      resp.setContentType(CONTENT_TYPE);
      try {
        resp.getWriter().print("Reloading config failed");
      } catch (IOException ee) {
        // Ignored
      }
      return;
    }

    resp.setContentType(CONTENT_TYPE);
    try {
      resp.getWriter().print("OK");
    } catch (IOException e) {
      // Ignored
    }
  }
}
