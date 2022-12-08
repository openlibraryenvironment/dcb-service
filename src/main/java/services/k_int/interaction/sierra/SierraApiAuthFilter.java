package services.k_int.interaction.sierra;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.HttpClientFilter;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;

@Filter(patternStyle = FilterPatternStyle.REGEX, patterns = "/iii/sierra-api/v6/(?!token($|/)).*" )
@Requirements({
	@Requires(property = SierraApiClient.CONFIG_ROOT + ".api.key"),
	@Requires(property = SierraApiClient.CONFIG_ROOT + ".api.secret")
})
public class SierraApiAuthFilter implements HttpClientFilter {
	static final Logger log = LoggerFactory.getLogger(SierraApiAuthFilter.class);
	
	@Property(name = SierraApiClient.CONFIG_ROOT + ".api.key")
	private String key;
	
	@Property(name = SierraApiClient.CONFIG_ROOT + ".api.secret")
	private String secret;
	
	private AuthToken currentToken;
	
	private final BeanProvider<SierraApiClient> authClientProvider;
	
	public SierraApiAuthFilter(BeanProvider<SierraApiClient> authClientProvider) {
		this.authClientProvider = authClientProvider;
	}

	@Override
	public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {

		return Mono.justOrEmpty(currentToken)
		  .filter( token -> !currentToken.isExpired() )
			.switchIfEmpty(
					Mono.from( authClientProvider.get().login(key, secret) )
							.doOnNext( newToken -> this.currentToken = newToken ))
				
			.map( validToken -> {
				
				final String token = validToken.toString();
				
				log.debug("Using Auth token: {}", token);
				return request.header(HttpHeaders.AUTHORIZATION, token);
			})
			
			.flatMap( req -> Mono.from( chain.proceed( req ) ))
		;
	}
	
}
