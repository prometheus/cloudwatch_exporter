package io.prometheus.cloudwatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

class DisallowHttpMethodsTest {

  private final Request request = mock(Request.class);
  private final Response response = mock(Response.class);
  private final Callback callback = mock(Callback.class);

  @Test
  void returnsMethodNotAllowedForDisallowedMethod() throws Exception {
    when(request.getMethod()).thenReturn("TRACE");
    DisallowHttpMethods handler = new DisallowHttpMethods(EnumSet.of(HttpMethod.TRACE));

    boolean handled = handler.handle(request, response, callback);

    assertThat(handled).isTrue();
    verify(response).setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
    verify(callback).succeeded();
  }

  @Test
  void delegatesAllowedMethodToWrappedHandler() throws Exception {
    when(request.getMethod()).thenReturn("GET");
    Handler wrappedHandler = mock(Handler.class);
    when(wrappedHandler.handle(request, response, callback)).thenReturn(true);
    DisallowHttpMethods handler = new DisallowHttpMethods(EnumSet.of(HttpMethod.TRACE));
    handler.setHandler(wrappedHandler);

    boolean handled = handler.handle(request, response, callback);

    assertThat(handled).isTrue();
    verify(wrappedHandler).handle(request, response, callback);
    verify(response, never()).setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
    verify(callback, never()).succeeded();
  }
}
