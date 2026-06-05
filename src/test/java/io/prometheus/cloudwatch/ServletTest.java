package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

public class ServletTest {
  private final HttpServletRequest request = mock(HttpServletRequest.class);

  @Test
  public void healthServletReturnsPlainTextOk() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter responseBody = responseBody(response);

    new HealthServlet().doGet(request, response);

    verify(response).setContentType("text/plain");
    assertThat(responseBody.toString()).isEqualTo("ok");
  }

  @Test
  public void homePageServletReturnsHtmlLinks() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter responseBody = responseBody(response);

    new HomePageServlet().doGet(request, response);

    verify(response).setContentType("text/html");
    assertThat(responseBody.toString())
        .contains("<h1>CloudWatch Exporter</h1>")
        .contains("<a href=\"/metrics\">Metrics</a>");
  }

  @Test
  public void dynamicReloadServletRejectsGetRequests() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter responseBody = responseBody(response);

    new DynamicReloadServlet(mock(CloudWatchCollector.class)).doGet(request, response);

    verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
    assertThat(responseBody.toString()).isEqualTo("Only POST requests allowed");
  }

  @Test
  public void dynamicReloadServletReloadsConfigForPostRequests() throws Exception {
    CloudWatchCollector collector = mock(CloudWatchCollector.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter responseBody = responseBody(response);

    new DynamicReloadServlet(collector).doPost(request, response);

    verify(collector).reloadConfig();
    verify(response, never()).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
    assertThat(responseBody.toString()).isEqualTo("OK");
  }

  @Test
  public void dynamicReloadServletReturnsErrorWhenReloadFails() throws Exception {
    CloudWatchCollector collector = mock(CloudWatchCollector.class);
    doThrow(new IOException("boom")).when(collector).reloadConfig();
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter responseBody = responseBody(response);

    new DynamicReloadServlet(collector).doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
    assertThat(responseBody.toString()).isEqualTo("Reloading config failed");
  }

  @Test
  public void homePageServletIgnoresWriterFailures() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenThrow(new IOException("boom"));

    new HomePageServlet().doGet(request, response);

    verify(response).setContentType("text/html");
  }

  @Test
  public void dynamicReloadServletIgnoresGetWriterFailures() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenThrow(new IOException("boom"));

    new DynamicReloadServlet(mock(CloudWatchCollector.class)).doGet(request, response);

    verify(response).setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
  }

  @Test
  public void dynamicReloadServletIgnoresSuccessWriterFailures() throws Exception {
    CloudWatchCollector collector = mock(CloudWatchCollector.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenThrow(new IOException("boom"));

    new DynamicReloadServlet(collector).doPost(request, response);

    verify(collector).reloadConfig();
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
  }

  @Test
  public void dynamicReloadServletIgnoresErrorWriterFailures() throws Exception {
    CloudWatchCollector collector = mock(CloudWatchCollector.class);
    doThrow(new IOException("reload boom")).when(collector).reloadConfig();
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenThrow(new IOException("writer boom"));

    new DynamicReloadServlet(collector).doPost(request, response);

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    verify(response).setContentType(DynamicReloadServlet.CONTENT_TYPE);
  }

  private StringWriter responseBody(HttpServletResponse response) throws IOException {
    StringWriter responseBody = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    return responseBody;
  }
}
