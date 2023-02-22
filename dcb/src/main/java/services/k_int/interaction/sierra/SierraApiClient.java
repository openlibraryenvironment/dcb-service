package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.retry.annotation.Retryable;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.interaction.sierra.bibs.BibResultSet;

// @Client(value = "${" + SierraApiClient.CONFIG_ROOT + ".api.url:`https://sandbox.iii.com/iii/sierra-api/v6`}", errorType = SierraError.class)
@Client(value = "${" + SierraApiClient.CONFIG_ROOT + ".api.url:/iii/sierra-api/v6}", errorType = SierraError.class)
@Header(name = ACCEPT, value = APPLICATION_JSON)
//@MutableRequests
public interface SierraApiClient {

	public final static String CONFIG_ROOT = "sierra.client";
	static final Logger log = LoggerFactory.getLogger(SierraApiClient.class);
	
	private static <T> Collection<T> nullIfEmpty (Collection<T> collection) {
		if (collection == null || collection.size() < 1) return null;
		return collection;
	}
	
	@SingleResult
	default Publisher<BibResultSet> bibs (BibParams params) {
		return bibs (
				params.limit(),
				params.offset(),
				Objects.toString(params.createdDate(), null),
				Objects.toString(params.updatedDate(), null),
				nullIfEmpty(params.fields()),
				params.deleted(),
				Objects.toString(params.deletedDate(), null),
				params.suppressed(),
				nullIfEmpty(params.locations()));
	}
	
	@SingleResult
	public default Publisher<BibResultSet> bibs (Consumer<BibParams.Builder> consumer) {
		return bibs(BibParams.build(consumer));
	}
	
	@SingleResult
	@Get("/bibs/")
	@Retryable
	Publisher<BibResultSet> bibs (
			@Nullable @QueryValue("limit") final Integer limit, 
			@Nullable @QueryValue("offset") final Integer offset,
			@Nullable @QueryValue("createdDate") final String createdDate,
			@Nullable @QueryValue("updatedDate") final String updatedDate,
			@Nullable @QueryValue("fields") @Format("CSV") final Iterable<String> fields,
			@Nullable @QueryValue("deleted") final Boolean deleted,
			@Nullable @QueryValue("deletedDate") final String deletedDate,
			@Nullable @QueryValue("suppressed") final Boolean suppressed,
			@Nullable @QueryValue("locations") @Format("CSV") final Iterable<String> locations);
	

	@SingleResult
	default Publisher<AuthToken> login( BasicAuth creds ) {
		return login( creds, MultipartBody.builder()
        .addPart("grant_type", "client_credentials")
        .build());
	}
	
	// Basic auth is auto bound to the request.
	@SingleResult
	@Post("/token")
	@Produces(value = MULTIPART_FORM_DATA)
	Publisher<AuthToken> login( BasicAuth creds, @Body MultipartBody body);
	
	@SingleResult
	default Publisher<AuthToken> login( final String key, final String secret ) {
		return login(new BasicAuth(key, secret));
	}
	
}
