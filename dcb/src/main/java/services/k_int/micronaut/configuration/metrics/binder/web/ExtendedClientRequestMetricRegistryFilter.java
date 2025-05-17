package services.k_int.micronaut.configuration.metrics.binder.web;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

import java.util.Optional;

import org.reactivestreams.Publisher;

import io.micronaut.configuration.metrics.binder.web.ClientRequestMetricRegistryFilter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * The core filter used to drop through to the request URI if no template was present.
 * This behaviour has been altered, in the core library, to return UNKNOWN if the URI is not templated and not include the host (only service ID)
 * This method reinstates the old behaviour as defaults for unset values.
 * 
 * @author Steve Osguthorpe
 */
@Slf4j
@Filter("${micronaut.metrics.http.client.path:/**}")
@Requires(bean = ClientRequestMetricRegistryFilter.class)
@Requires(property = "k-int.metrics.use-extended-client-metrics", notEquals = FALSE)
public class ExtendedClientRequestMetricRegistryFilter implements HttpClientFilter {
	
	// Run last as we are setting defaults when no value present.
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * @param meterRegistry The metrics registry
	 */
	public ExtendedClientRequestMetricRegistryFilter() {
		log.info("Extended metrics enabled");
	}

	@Override
	public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {

		return Flux.just(request)
			.map(this::ensurePath)
			.map(this::defaultToHostAsServiceId)
			.flatMap(chain::proceed);
	}
	
	private <T extends MutableHttpRequest<?>> T ensurePath(T request) {

		Optional<String> route = request.getAttribute(URI_TEMPLATE, String.class);
		if (route.isEmpty()) {
			String path = request.getPath();
			request.setAttribute(URI_TEMPLATE, path);
		}

		return request;
	}

	private <T extends MutableHttpRequest<?>> T defaultToHostAsServiceId(T request) {

		Optional<String> svcId = request.getAttribute(SERVICE_ID.toString(), String.class);
		if (svcId.isEmpty()) {
			String host = resolveHostFromRequest(request);
			request.setAttribute(SERVICE_ID, host);
		}
		return request;
	}

	private String resolveHostFromRequest(MutableHttpRequest<?> request) {
		Optional<String> host = request.getHeaders().get(HttpHeaders.HOST, String.class);
		return host.orElse(request.getUri().getHost());
	}
}