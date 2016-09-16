package io.prometheus.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class HealthCheckServlet extends HttpServlet {
    private AmazonCloudWatchClient client;

    public HealthCheckServlet(AmazonCloudWatchClient client) {
        this.client = client;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();
        resp.setContentType("text/plain; charset=utf-8");
        try {
            this.client.listMetrics();
            resp.setStatus(200);
            writer.write("Ok");
        } catch (com.amazonaws.AmazonClientException e) {
            resp.setStatus(500);
            writer.write("Error when connecting to AWS Cloudwatch: " + e.getMessage());
        } finally {
            writer.flush();
            writer.close();
        }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doGet(req, resp);
    }
}