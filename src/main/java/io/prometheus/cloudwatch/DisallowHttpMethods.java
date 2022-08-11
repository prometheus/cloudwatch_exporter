package io.prometheus.cloudwatch;

import java.util.EnumSet;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

class DisallowHttpMethods implements HttpConfiguration.Customizer {
  private final EnumSet<HttpMethod> disallowedMethods;

  public DisallowHttpMethods(EnumSet<HttpMethod> disallowedMethods) {
    this.disallowedMethods = disallowedMethods;
  }

  @Override
  public void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
    HttpMethod httpMethod = HttpMethod.fromString(request.getMethod());
    if (disallowedMethods.contains(httpMethod)) {
      request.setHandled(true);
      request.getResponse().setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
    }
  }
}
