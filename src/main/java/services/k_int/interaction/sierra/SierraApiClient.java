package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import java.time.LocalDate;
import java.util.Arrays;

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
import services.k_int.interaction.auth.OAuthToken;

@Client("${" + SierraApiClient.CONFIG_ROOT + ".api.url:`https://sandbox.iii.com/iii/sierra-api/v6`}")
@Header(name = ACCEPT, value = APPLICATION_JSON)
public interface SierraApiClient {

	public final static String CONFIG_ROOT = "sierra.client";
	static final Logger log = LoggerFactory.getLogger(SierraApiClient.class);
	
	
	
	@SingleResult
	@Get("/bibs/")
	public Publisher<BibResultSet> bibs ( @Nullable @QueryValue("limit") final Integer limit, @Nullable @QueryValue("offest") final Integer offset, @QueryValue("createdDate") @Nullable LocalDate createdDate, @Nullable @QueryValue("fields") @Format("CSV") Iterable<String> fields);
	// fields:'id,createdDate,updatedDate,deletedDate,title,author,materialType,bibLevel,marc,items',
	// fields:'id,createdDate,updatedDate,deletedDate,title',
	// createdDate:'2000-10-25'
	//createdDate:'[2022-11-01,2022-12-01]'

	@SingleResult
	public default Publisher<BibResultSet> bibs ( final int limit, final int offset, LocalDate createdDate, String... fields ) {
		return bibs (limit, offset, createdDate, Arrays.asList(fields));
	}
	
	@SingleResult
	public default Publisher<BibResultSet> bibs () {
		return bibs (null, null, null, null);
	}
	
	@SingleResult
	public default Publisher<BibResultSet> bibs ( final Integer limit, final Integer offset, String... fields ) {
		return bibs (limit, offset, null, Arrays.asList(fields));
	}
	
	@SingleResult
	public default Publisher<OAuthToken> login( final String key, final String secret ) {
		return login(new BasicAuth(key, secret));
	}
	
	@SingleResult
	public default Publisher<OAuthToken> login( BasicAuth creds ) {
		return login( creds, MultipartBody.builder()
        .addPart("grant_type", "client_credentials")
        .build());
	}
	
	// Basic auth is auto bound to the request.
	@SingleResult
	@Post("/token")
	@Produces(value = MULTIPART_FORM_DATA)
	public Publisher<OAuthToken> login( BasicAuth creds, @Body MultipartBody body);
	
}
