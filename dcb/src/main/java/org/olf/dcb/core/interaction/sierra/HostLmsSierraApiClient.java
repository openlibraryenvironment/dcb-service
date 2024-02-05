package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.utils.DCBStringUtilities.toCsv;
import static reactor.core.publisher.Mono.empty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.olf.dcb.core.interaction.HttpResponsePredicates;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.model.HostLms;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.retry.annotation.Retryable;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.LinkResult;
import services.k_int.interaction.sierra.SierraApiClient;
import services.k_int.interaction.sierra.SierraError;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchResultSet;
import services.k_int.interaction.sierra.configuration.PatronMetadata;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.patrons.*;

@Secondary
@Prototype
public class HostLmsSierraApiClient implements SierraApiClient {
	private static final String CLIENT_SECRET = "secret";
	private static final String CLIENT_KEY = "key";
	private static final String CLIENT_BASE_URL = "base-url";
	private static final Argument<SierraError> ERROR_TYPE = Argument.of(SierraError.class);

	private final Logger log = LoggerFactory.getLogger(HostLmsSierraApiClient.class);
	private final URI rootUri;
	private final HostLms lms;
	private final HttpClient client;
	private final SierraResponseErrorMatcher sierraResponseErrorMatcher = new SierraResponseErrorMatcher();

	private AuthToken currentToken;

	public HostLmsSierraApiClient() {
		// No args constructor needed for Micronaut bean
		// context to not freak out when deciding which bean of the interface type
		// implemented it should use. Even though this one is "Secondary" the
		// constructor
		// args are still found to not exist without this constructor.
		throw new IllegalStateException();
	}

	@Creator
	public HostLmsSierraApiClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client) {

		log.debug("Creating Sierra HostLms client for HostLms {}", hostLms);

		URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		URI relativeURI = UriBuilder.of("/iii/sierra-api/v6/").build();
		rootUri = RelativeUriResolver.resolve(hostUri, relativeURI);

		lms = hostLms;
		this.client = client;
	}

	@Override
	@SingleResult
	@Retryable
	public Publisher<BibResultSet> bibs(Integer limit, Integer offset, String createdDate, String updatedDate,
			Iterable<String> fields, Boolean deleted, String deletedDate, Boolean suppressed, Iterable<String> locations) {

		return get("bibs", Argument.of(BibResultSet.class),
				uri -> uri.queryParam("limit", limit).queryParam("offset", offset).queryParam("createdDate", createdDate)
						.queryParam("updatedDate", updatedDate).queryParam("fields", toCsv(fields)).queryParam("deleted", deleted)
						.queryParam("deletedDate", deletedDate).queryParam("suppressed", suppressed)
						.queryParam("locations", iterableToArray(locations)));
	}

	@SingleResult
	@Retryable
	public Publisher<List<PatronMetadata>> patronMetadata() {
		// A little bit of wriggle here, Sierra returns a flat list of PickupLocations
		// in an array without a wrapper
		// so we need to specify the inner type via Argument.listOf
		return get("patrons/metadata", Argument.listOf(PatronMetadata.class),
				uri -> uri.queryParam("offset", 0).queryParam("limit", 1000).queryParam("deleted", false));
	}

	@SingleResult
	@Retryable
	public Publisher<List<PickupLocationInfo>> pickupLocations() {
		// A little bit of wriggle here, Sierra returns a flat list of PickupLocations
		// in an array without a wrapper
		// so we need to specify the inner type via Argument.listOf
		return get("branches/pickupLocations", Argument.listOf(PickupLocationInfo.class), uri -> {
		});
	}

	@SingleResult
	@Retryable
	public Publisher<BranchResultSet> branches(Integer limit, Integer offset, Iterable<String> fields) {

		return get("branches", Argument.of(BranchResultSet.class),
				uri -> uri.queryParam("limit", limit).queryParam("offset", offset).queryParam("fields", toCsv(fields)));
	}

	@Override
	@SingleResult
	@Retryable
	public Publisher<ResultSet> items(Integer limit, Integer offset, Iterable<String> id, Iterable<String> fields,
			String createdDate, String updatedDate, String deletedDate, Boolean deleted, Iterable<String> bibIds,
			String status, String dueDate, Boolean suppressed, Iterable<String> locations) {

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/items/Get_a_list_of_items_get_1
		return get("items", Argument.of(ResultSet.class),
				uri -> uri.queryParam("limit", limit).queryParam("offset", offset).queryParam("id", id)
						.queryParam("fields", toCsv(fields)).queryParam("createdDate", createdDate)
						.queryParam("updatedDate", updatedDate).queryParam("deletedDate", deletedDate)
						.queryParam("deleted", deleted).queryParam("bibIds", iterableToArray(bibIds)).queryParam("status", status)
						// Case for due date is deliberate to match inconsistency in Sierra API
						.queryParam("duedate", dueDate).queryParam("suppressed", suppressed)
						.queryParam("locations", iterableToArray(locations)));
	}

	@Override
	@SingleResult
	public Publisher<LinkResult> createItem(ItemPatch itemPatch) {
		return postRequest("items").map(request -> request.body(itemPatch)).flatMap(this::ensureToken)
				.flatMap(request -> doRetrieve(request, Argument.of(LinkResult.class)));
	}

	@Override
	@SingleResult
	public Mono<Void> updateItem(final String itemId, final ItemPatch body) {
		return putRequest("items/" + itemId).map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doExchange(req, Object.class)).then();
	}

	@Override
	@SingleResult
	@Retryable
	public Publisher<SierraItem> getItem(final String itemId) {
		return get("items/" + itemId, Argument.of(SierraItem.class), uri -> {
		});
	}

	@SingleResult
	public Publisher<LinkResult> patrons(PatronPatch body) {
		// See
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Create_a_patron_record_post_0
		return postRequest("patrons").map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, Argument.of(LinkResult.class)));
	}

	@SingleResult
	public Publisher<LinkResult> bibs(BibPatch body) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/bibs/Create_a_Bib_record_post_0
		return postRequest("bibs").map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, Argument.of(LinkResult.class)));
	}

	@SingleResult
	public Mono<Void> updateBib(final String bibId, final BibPatch body) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/bibs/Create_a_Bib_record_post_0
		return putRequest("bibs/" + bibId).map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doExchange(req, Object.class)).then();
	}

	@SingleResult
	public Publisher<SierraPatronRecord> patronFind(String varFieldTag, String varFieldContent) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Find_a_patron_by_varField_fieldTag_and_varField_content_get_6
		return get("patrons/find", Argument.of(SierraPatronRecord.class),
				uri -> uri.queryParam("varFieldTag", varFieldTag).queryParam("varFieldContent", varFieldContent).queryParam(
						"fields",
						"id,names,barcodes,deleted,suppressed,expirationDate,patronType,patronCodes,blockInfo,autoBlockInfo,homeLibraryCode,message,uniqueIds,emails,fixedFields"));
	}

	@SingleResult
	public Publisher<String> validatePatronCredentials(InternalPatronValidation body) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/validate_patron_credentials_post_2
		return postRequest("patrons/auth").map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, Argument.of(String.class)));
	}

        @SingleResult
        public Publisher<Boolean> validatePatron(final PatronValidation body) {
		return postRequest("patrons/validate").map(req -> req.body(body)).flatMap(this::ensureToken)
				.flatMap(req -> doExchange(req, Object.class))
				.doOnNext(res -> log.debug("Result of validate {} {}",res,res.getStatus()))
				.flatMap(res -> {
                                	return Mono.just(Boolean.TRUE);
                                })
				.onErrorResume(throwable -> Mono.just(Boolean.FALSE));
	}


	@SingleResult
	public Publisher<SierraPatronHoldResultSet> patronHolds(String patronId) {
		log.debug("patronHolds({})", patronId);

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		return get("patrons/" + patronId + "/holds", Argument.of(SierraPatronHoldResultSet.class),
				uri -> uri.queryParam("fields", "id,placed,location,pickupLocation,status,note,recordType,notNeededAfterDate"));
	}

	@Override
	@SingleResult
	public Publisher<AuthToken> login(BasicAuth creds, MultipartBody body) {
		return postRequest("token")
				.map(req -> req.basicAuth(creds.getUsername(), creds.getPassword())
						.contentType(MediaType.MULTIPART_FORM_DATA_TYPE).body(body))
				.flatMap(req -> doRetrieve(req, Argument.of(AuthToken.class), false,
					this::handleResponseErrors));
	}

	@SingleResult
	public Mono<Void> placeHoldRequest(String id, PatronHoldPost body) {
		return createRequest(POST, "patrons/" + id + "/holds/requests").map(req -> req.body(body))
				.flatMap(this::ensureToken).flatMap(req -> doExchange(req, Object.class)).then();
	}

	@SingleResult
	public Publisher<SierraPatronHoldResultSet> getAllPatronHolds(final Integer limit, final Integer offset) {

		log.debug("getAllPatronHolds(limit:{}, offset:{})", limit, offset);

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_all_patrons_holds_data_get_8
		return get("patrons/holds", Argument.of(SierraPatronHoldResultSet.class),
				uri -> uri.queryParam("limit", limit).queryParam("offset", offset));
	}

	@SingleResult
	public Publisher<SierraPatronRecord> getPatron(@Nullable final Long patronId) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		return get("patrons/" + patronId, Argument.of(SierraPatronRecord.class), uri -> uri.queryParam("fields",
				"id,updatedDate,createdDate,expirationDate,names,barcodes,patronType,patronCodes,homeLibraryCode,emails,message,uniqueIds,emails,fixedFields"));
	}

	@SingleResult
	public Publisher<SierraPatronRecord> updatePatron(@Nullable final Long patronId, PatronPatch patronPatch) {

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Update_the_Patron_record_put_19
		return createRequest(HttpMethod.PUT, "patrons/" + patronId).map(request -> request.body(patronPatch))
				.flatMap(this::ensureToken).flatMap(req -> doRetrieve(req, Argument.of(SierraPatronRecord.class)));
	}

	@SingleResult
	public Publisher<SierraPatronHold> getHold(@Nullable final Long holdId) {
		log.debug("getHold({})", holdId);

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		return get("patrons/holds/" + holdId, Argument.of(SierraPatronHold.class), uriBuilder -> {
		});
	}

	private <T> Mono<T> get(String path, Argument<T> argumentType,
		Consumer<UriBuilder> uriBuilderConsumer) {

		return createRequest(GET, path)
			.map(req -> req.uri(uriBuilderConsumer))
			.flatMap(this::ensureToken)
			.flatMap(req -> doRetrieve(req, argumentType))
			.onErrorResume(sierraResponseErrorMatcher::isNoRecordsError, _t -> empty());
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	private <T> Mono<T> handleResponseErrors(final Mono<T> current) {
		// We used to do
		// .transform(this::handle404AsEmpty)
		// Immediately after current, but some downstream chains rely upon the 404 so
		// for now we use .transform directly in the caller
		return current.doOnError(HttpResponsePredicates::isUnauthorised, _t -> clearToken());
	}

	private void clearToken() {
		log.debug("Clearing token to trigger re-authentication");
		this.currentToken = null;
	}

	private <T> Mono<MutableHttpRequest<T>> postRequest(String path) {
		return createRequest(POST, path);
	}

	private <T> Mono<MutableHttpRequest<T>> putRequest(String path) {
		return createRequest(PUT, path);
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String path) {
		return Mono.just(UriBuilder.of(path).build()).map(this::resolve)
				.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> argumentType) {
		return doRetrieve(request, argumentType, true, this::handleResponseErrors);
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Class<T> type) {
		return Mono.from(client.exchange(request, Argument.of(type), ERROR_TYPE))
			.doOnError(HttpResponsePredicates::isUnauthorised, _t -> clearToken())
			// This has to happen after other error handlers related to HttpClientResponseException
			.onErrorMap(HttpClientResponseException.class, responseException ->
				unexpectedResponseProblem(responseException, request, null));
	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request,
		Argument<T> argumentType, boolean mapErrors,
		Function<Mono<T>, Publisher<T>> errorHandlingTransformer) {

		var response = Mono.from(client.retrieve(request, argumentType, ERROR_TYPE));

		return mapErrors
			? response.transform(errorHandlingTransformer)
			: response;
	}

	private <T> Object[] iterableToArray(Iterable<T> iterable) {
		if (iterable == null)
			return null;

		final List<T> list = new ArrayList<T>();

		iterable.forEach(list::add);

		return list.size() > 0 ? list.toArray() : null;
	}

	private <T> Mono<MutableHttpRequest<T>> ensureToken(MutableHttpRequest<T> request) {
		return Mono.justOrEmpty(currentToken).filter(token -> !token.isExpired()).switchIfEmpty(acquireAccessToken())
				.map(validToken -> {
					final String token = validToken.toString();
					log.debug("Using Auth token: {}", token);

					return request.header(HttpHeaders.AUTHORIZATION, token);
				}).defaultIfEmpty(request);
	}

	private Mono<AuthToken> acquireAccessToken() {
		final Map<String, Object> conf = lms.getClientConfig();
		final String key = (String) conf.get(CLIENT_KEY);
		final String secret = (String) conf.get(CLIENT_SECRET);

		return Mono.from(login(key, secret)).map(newToken -> {
			currentToken = newToken;
			return newToken;
		});
	}

	@SingleResult
	public Publisher<LinkResult> checkOutItemToPatron(String itemBarcode, String patronBarcode) {

		CheckoutPatch checkoutPatch = CheckoutPatch.builder().itemBarcode(itemBarcode).patronBarcode(patronBarcode).build();

		return postRequest("patrons/checkout").map(request -> request.body(checkoutPatch)).flatMap(this::ensureToken)
				.flatMap(request -> doRetrieve(request, Argument.of(LinkResult.class)));
	}

	public Publisher<HttpStatus> deleteItem(String id) {
		return Mono.just(HttpStatus.OK);
	}

	public Publisher<HttpStatus> deleteBib(String id) {
		return Mono.just(HttpStatus.OK);
	}

}
