package services.k_int.interaction.sierra;

import static io.micronaut.http.HttpHeaders.ACCEPT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.http.annotation.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.retry.annotation.Retryable;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.configuration.BranchResultSet;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.interaction.sierra.bibs.BibParams.BibParamsBuilder;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.PatronMetadata;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.patrons.*;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.holds.SierraPatronHold;

@Client(value = "${" + SierraApiClient.CONFIG_ROOT + ".api.url:/iii/sierra-api/v6}", errorType = SierraError.class)
@Header(name = ACCEPT, value = APPLICATION_JSON)
	public interface SierraApiClient {
	String CONFIG_ROOT = "sierra.client";
	Logger log = LoggerFactory.getLogger(SierraApiClient.class);
	
	private static <T> Collection<T> nullIfEmpty (Collection<T> collection) {
		if (collection == null || collection.size() < 1) return null;
		return collection;
	}
	
	@SingleResult
	default Publisher<BibResultSet> bibs (BibParams params) {
		return bibs (
				params.getLimit(),
				params.getOffset(),
				Objects.toString(params.getCreatedDate(), null),
				Objects.toString(params.getUpdatedDate(), null),
				nullIfEmpty(params.getFields()),
				params.getDeleted(),
				Objects.toString(params.getDeletedDate(), null),
				params.getSuppressed(),
				nullIfEmpty(params.getLocations()));
	}
	
	@SingleResult
	default Publisher<BibResultSet> bibs (Consumer<BibParamsBuilder> consumer) {
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
	default Publisher<ResultSet> items (Params params) {
		return items(
			params.getLimit(),
			params.getOffset(),
			nullIfEmpty(params.getId()),
			nullIfEmpty(params.getFields()),
			Objects.toString(params.getCreatedDate(), null),
			Objects.toString(params.getUpdatedDate(), null),
			Objects.toString(params.getDeletedDate(), null),
			params.getDeleted(),
			nullIfEmpty(params.getBibIds()),
			params.getStatus(),
			Objects.toString(params.getDuedate(), null),
			params.getSuppressed(),
			nullIfEmpty(params.getLocations()));
	}

	@SingleResult
	default Publisher<ResultSet> items (Consumer<Params.ParamsBuilder> consumer) {
		return items(Params.build(consumer));
	}

	@SingleResult
	@Get("/items/")
	@Retryable
	Publisher<ResultSet> items(
		@Nullable @QueryValue("limit") final Integer limit,
		@Nullable @QueryValue("offset") final Integer offset,
		@Nullable @QueryValue("id") final Iterable<String> id,
		@Nullable @QueryValue("fields") @Format("CSV") final Iterable<String> fields,
		@Nullable @QueryValue("createdDate") final String createdDate,
		@Nullable @QueryValue("updatedDate") final String updatedDate,
		@Nullable @QueryValue("deletedDate") final String deletedDate,
		@Nullable @QueryValue("deleted") final Boolean deleted,
		@Nullable @QueryValue("bibIds") final Iterable<String> bibIds,
		@Nullable @QueryValue("status") final String status,
		@Nullable @QueryValue("duedate") final String duedate,
		@Nullable @QueryValue("suppressed") final Boolean suppressed,
		@Nullable @QueryValue("locations") @Format("CSV") final Iterable<String> locations);

	@SingleResult
	@Post("/patrons")
	Publisher<PatronResult> patrons(@Body final PatronPatch patronPatch);

	@SingleResult
	@Get("/patrons/find")
	Publisher<Result> patronFind(
		@Nullable @QueryValue("varFieldTag") final String varFieldTag,
		@Nullable @QueryValue("varFieldContent") final String varFieldContent);

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

    @SingleResult
    @Retryable
    @Get("/branches/")
    public Publisher<BranchResultSet> branches(Integer limit, Integer offset, Iterable<String> fields );

    @SingleResult
    @Retryable
    @Get("/branches/pickupLocations")
    public Publisher<List<PickupLocationInfo>> pickupLocations();

    @SingleResult
    @Retryable
    @Get("/patrons/metadata")
    public Publisher<List<PatronMetadata>> patronMetadata();

	@SingleResult
	@Retryable
	@Get("/patrons/holds")
	public Publisher<SierraPatronHoldResultSet> getAllPatronHolds(
                        @Nullable @QueryValue("limit") final Integer limit,
                        @Nullable @QueryValue("offset") final Integer offset);
}
