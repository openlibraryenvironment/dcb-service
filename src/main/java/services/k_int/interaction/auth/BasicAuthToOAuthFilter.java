package services.k_int.interaction.auth;

import java.util.function.Function;

import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.ingest.IngestRecordBuilder;
import org.reactivestreams.Publisher;

import io.micronaut.context.BeanProvider;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.HttpClientFilter;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraApiClient;

@Filter(patternStyle = FilterPatternStyle.REGEX, patterns = "/iii/sierra-api/v6/(?!token($|/)).*" )
public class BasicAuthToOAuthFilter implements HttpClientFilter {
	
	private final String username = "9/FnXtNb5qWahEcHpMiL/OlSJe0b";
	private final String secret = "I8g9uNbzs3";
	
	private OAuthToken currentToken;
	
	private final BeanProvider<SierraApiClient> authClientProvider;
	
	public BasicAuthToOAuthFilter(BeanProvider<SierraApiClient> authClientProvider) {
		this.authClientProvider = authClientProvider;
	}
	
	protected <T> boolean trackNulls ( final @Nullable T value, final @NotNull Function<T, IngestRecordBuilder> builderFunction ) {
		builderFunction.apply(value);
		return (value != null);
	}

	@Override
	public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {

		return Mono.justOrEmpty(currentToken)
		  .filter( token -> !currentToken.isExpired() )
			.switchIfEmpty(
					Mono.from( authClientProvider.get().login(username, secret) )
							.doOnNext( newToken -> this.currentToken = newToken ))
				
			.map( validToken -> request.header(HttpHeaders.AUTHORIZATION, validToken.toString()))
			
			.flatMap( req -> Mono.from( chain.proceed( req ) ))
		;
	}
	
}
