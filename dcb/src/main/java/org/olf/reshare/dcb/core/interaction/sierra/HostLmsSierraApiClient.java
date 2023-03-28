package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.olf.reshare.dcb.core.model.HostLms;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.retry.annotation.Retryable;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.SierraError;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.items.ResultSet;

@Secondary
@Prototype
public class HostLmsSierraApiClient implements SierraApiClient {
	
	private final Logger log = LoggerFactory.getLogger(HostLmsSierraApiClient.class);
	
	private static final String CLIENT_SECRET = "secret";
	private static final String CLIENT_KEY = "key";
	private static final String CLIENT_BASE_URL = "base-url";
	private AuthToken currentToken;
	private final URI rootUri;
	private final HostLms lms;
	private final HttpClient client;
	private static final Argument<SierraError> errorType = Argument.of(SierraError.class);

	public HostLmsSierraApiClient() {
		
		// No args constructor needed for Micronaut bean
		// context to not freak out when deciding which bean of the interface type
		// implemented it should use. Even though this one is "Secondary" the constructor
		// args are still found to not exist without this constructor.
		throw new IllegalStateException();
	}
	
	@Creator
	public HostLmsSierraApiClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client) {
		
		log.debug("Creating Sierra HostLms client for HostLms {}", hostLms.getName());
		
		URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		rootUri = resolve(hostUri, UriBuilder.of("/iii/sierra-api/v6/").build());
		
		lms = hostLms;
		this.client = client;
	}

	@Override
	@SingleResult
	@Retryable
	@Get("bibs")
	public Publisher<BibResultSet> bibs(Integer limit, Integer offset, String createdDate, String updatedDate,
			Iterable<String> fields, Boolean deleted, String deletedDate, Boolean suppressed, Iterable<String> locations) {

		return getRequest("bibs")
			.map(req -> req.uri(theUri -> {
				theUri
					.queryParam("limit", limit)
					.queryParam("offset", offset)
					.queryParam("createdDate", createdDate)
					.queryParam("updatedDate", updatedDate)
					.queryParam("fields", iterableToArray(fields))
					.queryParam("deleted", deleted)
					.queryParam("deletedDate", deletedDate)
					.queryParam("suppressed", suppressed)
					.queryParam("locations", iterableToArray(locations))
					;
			}))
			.flatMap(this::ensureToken)
			.flatMap(req ->	doRetrieve(req, BibResultSet.class) );
	}

	@Override
	@SingleResult
	@Retryable
	@Get("items")
	public Publisher<ResultSet> items(Integer limit, Integer offset,
		Iterable<String> id, Iterable<String> fields, String createdDate,
		String updatedDate, String deletedDate, Boolean deleted, Iterable<String> bibIds,
		String status, String dueDate, Boolean suppressed, Iterable<String> locations) {

		// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/items/Get_a_list_of_items_get_1
		return getRequest("items")
			.map(req -> req.uri(theUri -> theUri
				.queryParam("limit", limit)
				.queryParam("offset", offset)
				.queryParam("id", id)
				.queryParam("fields", iterableToArray(fields))
				.queryParam("createdDate", createdDate)
				.queryParam("updatedDate", updatedDate)
				.queryParam("deletedDate", deletedDate)
				.queryParam("deleted", deleted)
				.queryParam("bibIds", iterableToArray(bibIds))
				.queryParam("status", status)
				// Case for due date is deliberate to match inconsistency in Sierra API
				.queryParam("duedate", dueDate)
				.queryParam("suppressed", suppressed)
				.queryParam("locations", iterableToArray(locations))))
			.flatMap(this::ensureToken)
			.flatMap(req ->	doRetrieve(req, ResultSet.class));
	}
	
	private <T> Mono<T> handleResponseErrors ( final Mono<T> current ) {
		
		return current.onErrorMap(throwable -> {
			// On a 401 we should clear the token before propagating the error.
			if (HttpClientResponseException.class.isAssignableFrom(throwable.getClass())) {
        HttpClientResponseException e = (HttpClientResponseException) throwable;
        int code = e.getStatus().getCode();
        
        switch (code) {
        	case 401:
        		log.debug("Clearing token to trigger reauthentication");
        		this.currentToken = null;
        		break;
        }
			}
			return throwable;
		});
	}
	
	private <T> Mono<T> doRetrieve( MutableHttpRequest<?> request, Class<T> type) {
		return doRetrieve(request, type, true);
	}
	
	private <T> Mono<T> doRetrieve( MutableHttpRequest<?> request, Class<T> type, boolean mapErrors) {
		var response = Mono.from( client.retrieve(request, Argument.of(type), errorType) );
		return mapErrors ? handleResponseErrors( response ) : response;
	}
	
	private <T> Object[] iterableToArray( Iterable<T> iterable ) {
		if (iterable == null) return null;
		final List<T> list = new ArrayList<T>();
		iterable.forEach(list::add);
		
		return list.size() > 0 ? list.toArray() : null;
	}

	@Override
	@SingleResult
//	@Retryable
	@Post("token")
	@Produces(value = MULTIPART_FORM_DATA)
	public Publisher<AuthToken> login(BasicAuth creds, MultipartBody body) {
		return postRequest("token")
				.map(req ->
					req.basicAuth(creds.getUsername(), creds.getPassword())
						.contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
						.body(body))
				.flatMap(req -> doRetrieve(req, AuthToken.class, false) );
	}
	
	private <T> Mono<MutableHttpRequest<T>> ensureToken( MutableHttpRequest<T> request ) {
		
		return Mono.justOrEmpty(currentToken)
		  .filter( token -> !token.isExpired() )
			.switchIfEmpty(	aquireAccessToken() )
			
			.map( validToken -> {
				
				final String token = validToken.toString();
				log.debug("Using Auth token: {}", token);
				
				return request.header(HttpHeaders.AUTHORIZATION, token);
			})
			.defaultIfEmpty(request);
	}
	
	private Mono<AuthToken> aquireAccessToken() {
		
		final Map<String, Object> conf = lms.getClientConfig();
		final String key = (String) conf.get(CLIENT_KEY);
		final String secret = (String) conf.get(CLIENT_SECRET);
		return Mono.from(login(key, secret))
			.map( newToken -> {
				currentToken = newToken;
				return newToken;
			});
	}

	private <T> Mono<MutableHttpRequest<T>> getRequest(String uri) {
		return createRequest(HttpMethod.GET, uri);
	}

	private <T> Mono<MutableHttpRequest<T>> postRequest(String uri) {
		return createRequest(HttpMethod.POST, uri);
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String uri) {
		return Mono.just(UriBuilder.of(uri).build()).map(this::resolve)
				.map(resolvedUri -> {
					MutableHttpRequest<T> req = HttpRequest.create(method, resolvedUri.toString());
					return req.accept(MediaType.APPLICATION_JSON);
				});
	}
	
	private URI resolve(URI relativeURI) {
		return resolve(rootUri, relativeURI);
	}

	private static URI resolve(URI baseUri, URI relativeURI) {
		URI thisUri = baseUri;
		// if the URI features credentials strip this out
		if (StringUtils.isNotEmpty(thisUri.getUserInfo())) {
			try {
				thisUri = new URI(thisUri.getScheme(), null, thisUri.getHost(), thisUri.getPort(), thisUri.getPath(),
						thisUri.getQuery(), thisUri.getFragment());
			} catch (URISyntaxException e) {
				throw new IllegalStateException("URI is invalid: " + e.getMessage(), e);
			}
		}
		String rawQuery = thisUri.getRawQuery();
		if (StringUtils.isNotEmpty(rawQuery)) {
			return thisUri.resolve(relativeURI + "?" + rawQuery);
		} else {
			return thisUri.resolve(relativeURI);
		}
	}

}
