package services.k_int.micronaut.configuration.metrics.binder.web;

import static io.micronaut.core.util.StringUtils.FALSE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.configuration.metrics.binder.web.config.HttpMetricsConfig;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpAttributes;
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
// NB: this was previously gated on @Requires(bean = ClientRequestMetricRegistryFilter.class).
// Micronaut Micrometer 5.9 deprecated that class for removal and stripped its bean
// annotations, replacing it with the package-private ClientMetricsFilter. The class still
// exists, so the @Requires still compiled -- it simply never matched again, and this filter
// silently stopped being registered. Gate on the same public conditions the replacement
// uses instead. The "Extended metrics enabled" line logged from the constructor is the
// canary that it is actually active.
@Slf4j
@Filter("${micronaut.metrics.http.client.path:/**}")
@RequiresMetrics
@Requires(property = HttpMetricsConfig.ENABLED, notEquals = FALSE)
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
	public ExtendedClientRequestMetricRegistryFilter( @Nullable @Value("${" + METRIC_CONFIG_ROOT + ".static-templates}") List<String> defaultTemplateReplacements) {
		this.defaultTemplateReplacements = Stream.ofNullable(defaultTemplateReplacements)
			.flatMap( List::stream )
			.map( tmp -> Map.entry(tmp, UriMatchTemplate.of(tmp)) )
			.collect( Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue) );
			
		log.info("Extended metrics enabled");
	}

	// HttpAttributes is deprecated for removal in favour of the BasicHttpAttributes
	// accessors, but that class exposes only getServiceId -- there is no setServiceId. The
	// read below uses the accessor; the write has no non-deprecated equivalent, so it still
	// goes through the enum. BasicHttpAttributes.getServiceId reads exactly this attribute,
	// so the two remain consistent. Revisit if a setter is ever added.
	@SuppressWarnings("removal")
	private <T extends MutableHttpRequest<?>> T defaultToHostAsServiceId(T request) {

		Optional<String> svcId = BasicHttpAttributes.getServiceId(request);
		if (svcId.isEmpty()) {
			String host = resolveHostFromRequest(request);
			request.setAttribute(HttpAttributes.SERVICE_ID, host);
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
		
		// Always use the URI template if present, to avoid breaking expected core behaviour.
		Optional<String> definedRoute = BasicHttpAttributes.getUriTemplate(request);

		if (staticRoute.isPresent() || definedRoute.isEmpty()) {

			// Allows a default to be supplied if the URI template isn't present.
			String path = staticRoute
				.or(() -> request.getAttribute(METRIC_TEMPLATE_DEFAULT, String.class))
				.orElse(request.getPath());

			// Write it to the template attribute.
			BasicHttpAttributes.setUriTemplate(request, path);
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