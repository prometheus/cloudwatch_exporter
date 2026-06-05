package io.prometheus.cloudwatch;

import java.util.EnumSet;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

class DisallowHttpMethods extends Handler.Wrapper {
  private final EnumSet<HttpMethod> disallowedMethods;

  public DisallowHttpMethods(EnumSet<HttpMethod> disallowedMethods) {
    this.disallowedMethods = disallowedMethods;
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    if (disallowedMethods.contains(HttpMethod.fromString(request.getMethod()))) {
      response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
      callback.succeeded();
      return true;
    }
    return super.handle(request, response, callback);
  }
}
