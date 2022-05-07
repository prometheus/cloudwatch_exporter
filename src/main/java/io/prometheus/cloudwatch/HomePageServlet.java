package io.prometheus.cloudwatch;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HomePageServlet extends HttpServlet {
  private static final long serialVersionUID = 3239704246954810347L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    resp.setContentType("text/html");
    try {
      resp.getWriter()
          .print(
              "<html>\n"
                  + "<head><title>CloudWatch Exporter</title></head>\n"
                  + "<body>\n"
                  + "<h1>CloudWatch Exporter</h1>\n"
                  + "<p><a href=\"/metrics\">Metrics</a></p>\n"
                  + "</body>\n"
                  + "</html>");
    } catch (IOException e) {
      // Ignored
    }
  }
}
