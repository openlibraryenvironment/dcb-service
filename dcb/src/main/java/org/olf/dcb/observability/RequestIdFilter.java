package org.olf.dcb.observability;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Gives every inbound request an id, puts it in the log MDC, and returns it in
 * {@link RequestId#HEADER}. Ordered first so a request refused by a later filter still has one.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter {

	@RequestFilter
	public void mark(HttpRequest<?> request, MutablePropagatedContext context) {
		final var id = RequestId.resolve(request.getHeaders().get(RequestId.HEADER));

		request.setAttribute(RequestId.MDC_KEY, id);
		context.add(new MdcRequestId(id));
	}

	@ResponseFilter
	public void echo(HttpRequest<?> request, MutableHttpResponse<?> response) {
		request.getAttribute(RequestId.MDC_KEY, String.class)
			.ifPresent(id -> response.header(RequestId.HEADER, id));
	}
}
