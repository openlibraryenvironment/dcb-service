package services.k_int.micronaut.configuration.metrics.binder.web;

import static io.micronaut.core.util.StringUtils.FALSE;
import static io.micronaut.http.HttpAttributes.SERVICE_ID;
import static io.micronaut.http.HttpAttributes.URI_TEMPLATE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import io.micronaut.configuration.metrics.binder.web.ClientRequestMetricRegistryFilter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.uri.UriMatchTemplate;
import jakarta.validation.constraints.NotNull;
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
@Requires(property = ExtendedClientRequestMetricRegistryFilter.METRIC_CONFIG_ROOT + ".use-extended-client-metrics", notEquals = FALSE)
public class ExtendedClientRequestMetricRegistryFilter implements HttpClientFilter {
	protected static final String METRIC_CONFIG_ROOT = "k-int.metrics";
	
	private static final String METRIC_TEMPLATE_DEFAULT = METRIC_CONFIG_ROOT + ".uri.template";
	
	public static <T extends Object> HttpRequest<T> defaultUriTemplateForRequestMetrics( HttpRequest<T> request, @NotNull @NonNull String template ) {
		return request.setAttribute(METRIC_TEMPLATE_DEFAULT, template);
	}
	
	private final Map<String, UriMatchTemplate> defaultTemplateReplacements;
	
	/**
	 * @param meterRegistry The metrics registry
	 */
	public ExtendedClientRequestMetricRegistryFilter( @Value("${" + METRIC_CONFIG_ROOT + ".static-templates}") List<String> defaultTemplateReplacements) {
		this.defaultTemplateReplacements = Stream.ofNullable(defaultTemplateReplacements)
			.flatMap( List::stream )
			.map( tmp -> Map.entry(tmp, UriMatchTemplate.of(tmp)) )
			.collect( Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue) );
			
		log.info("Extended metrics enabled");
	}

	private <T extends MutableHttpRequest<?>> T defaultToHostAsServiceId(T request) {

		Optional<String> svcId = request.getAttribute(SERVICE_ID.toString(), String.class);
		if (svcId.isEmpty()) {
			String host = resolveHostFromRequest(request);
			request.setAttribute(SERVICE_ID, host);
		}
		return request;
	}
	
	private Predicate<Map.Entry<String, UriMatchTemplate>> templateMapEntryPredicate( final String requestPath ) {
		return toTest -> toTest.getValue().match(requestPath).isPresent();
	}
	
	private Optional<String> findMatchedStaticTemplate(HttpRequest<?> request) {

		final String requestPath = StringUtils.prependUri("/", request.getUri().getPath()); 
		
		return defaultTemplateReplacements.entrySet().stream()
			.filter(templateMapEntryPredicate(requestPath))
			.findFirst()
			.map( entry -> {
				String matchedTemplate = entry.getKey();
				if (log.isDebugEnabled()) {
					log.debug("Uri [{}] matched template [{}]", requestPath, matchedTemplate);
				}
				return matchedTemplate;
			});
	}

	@Override
	public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {

		return Flux.just(request)
			.map(this::ensurePath)
			.map(this::defaultToHostAsServiceId)
			.flatMap(chain::proceed);
	}
	
	private <T extends MutableHttpRequest<?>> T ensurePath(T request) {
		
		Optional<String> staticRoute = findMatchedStaticTemplate( request );
		
		// Always use the URI_TEMPLATE if present, to avoid breaking expected core behaviour.
		Optional<String> definedRoute = request.getAttribute(URI_TEMPLATE, String.class);
		
		if (staticRoute.isPresent() || definedRoute.isEmpty()) {

			// Allows a default to be supplied if the URI_TEMPLATE isn't present.
			String path = staticRoute
				.or(() -> request.getAttribute(METRIC_TEMPLATE_DEFAULT, String.class))
				.orElse(request.getPath());
			
			// Write it to the template attribute.
			request.setAttribute(URI_TEMPLATE, path);
		}

		return request;
	}

	// Run last as we are setting defaults when no value present.
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	private String resolveHostFromRequest(MutableHttpRequest<?> request) {
		Optional<String> host = request.getHeaders().get(HttpHeaders.HOST, String.class);
		return host.orElse(request.getUri().getHost());
	}
}