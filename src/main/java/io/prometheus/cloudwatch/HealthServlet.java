package io.prometheus.cloudwatch;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Servlet that responds with "ok" for health and readiness checks. */
public class HealthServlet extends HttpServlet {
  private static final long serialVersionUID = 5543118274931292897L;

  /** Constructs a HealthServlet. */
  public HealthServlet() {}

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/plain");
    resp.getWriter().print("ok");
  }
}
