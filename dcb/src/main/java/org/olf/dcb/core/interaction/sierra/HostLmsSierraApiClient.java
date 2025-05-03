package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.http.HttpMethod.DELETE;
import static io.micronaut.http.HttpMethod.GET;
import static io.micronaut.http.HttpMethod.POST;
import static io.micronaut.http.HttpMethod.PUT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.utils.DCBStringUtilities.toCsv;
import static reactor.core.publisher.Mono.empty;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.olf.dcb.core.interaction.AbstractHttpResponseProblem;
import org.olf.dcb.core.interaction.HttpResponsePredicates;
import org.olf.dcb.core.interaction.RecordIsNotAvailableProblem;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.utils.CollectionUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.ConversionService;
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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.retry.annotation.Retryable;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;
import services.k_int.interaction.auth.AuthToken;
import services.k_int.interaction.sierra.*;
import services.k_int.interaction.sierra.bibs.*;
import services.k_int.interaction.sierra.configuration.*;
import services.k_int.interaction.sierra.holds.*;
import services.k_int.interaction.sierra.items.*;
import services.k_int.interaction.sierra.patrons.*;

@Secondary
@Prototype
public class HostLmsSierraApiClient implements SierraApiClient {
	private static final String CLIENT_SECRET = "secret";
	private static final String CLIENT_KEY = "key";
	private static final String CLIENT_BASE_URL = "base-url";
	private static final Argument<SierraError> ERROR_TYPE = Argument.of(SierraError.class);

	private static final Logger log = LoggerFactory.getLogger(HostLmsSierraApiClient.class);
	private final URI rootUri;
	private final HostLms lms;
	private final HttpClient client;
	private final SierraResponseErrorMatcher sierraResponseErrorMatcher = new SierraResponseErrorMatcher();
	private final ConversionService conversionService;

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
	public HostLmsSierraApiClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client, ConversionService conversionService) {

		log.debug("Creating Sierra HostLms client for HostLms {}", hostLms);

		URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		URI relativeURI = UriBuilder.of("/iii/sierra-api/v6/").build();
		rootUri = RelativeUriResolver.resolve(hostUri, relativeURI);

		lms = hostLms;
		this.client = client;
		this.conversionService = conversionService;
	}

	@Override
	@SingleResult
	public Publisher<BibResultSet> bibs(Integer limit, Integer offset, String createdDate, String updatedDate,
			Iterable<String> fields, Boolean deleted, String deletedDate, Boolean suppressed, Iterable<String> locations) {
		return Mono.from(bibsRawResponse(limit, offset, createdDate, updatedDate, fields, deleted, deletedDate, suppressed, locations))
			.map( rawJson -> conversionService.convertRequired(rawJson, Argument.of(BibResultSet.class)));
	}

	@Override
	@SingleResult
	@Retryable
	public Publisher<JsonNode> bibsRawResponse(@Nullable Integer limit, @Nullable Integer offset,
			@Nullable String createdDate, @Nullable String updatedDate, @Nullable Iterable<String> fields,
			@Nullable Boolean deleted, @Nullable String deletedDate, @Nullable Boolean suppressed,
			@Nullable Iterable<String> locations) {
		
		return get("bibs", Argument.of(JsonNode.class),
				uri -> uri.queryParam("limit", limit).queryParam("offset", offset).queryParam("createdDate", createdDate)
						.queryParam("updatedDate", updatedDate).queryParam("fields", toCsv(fields)).queryParam("deleted", deleted)
						.queryParam("deletedDate", deletedDate).queryParam("suppressed", suppressed)
						.queryParam("locations", CollectionUtils.iterableToArray(locations)));
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
		return get("items", Argument.of(ResultSet.class), uri -> uri.queryParam("limit", limit).queryParam("offset", offset)
				.queryParam("id", id).queryParam("fields", toCsv(fields)).queryParam("createdDate", createdDate)
				.queryParam("updatedDate", updatedDate).queryParam("deletedDate", deletedDate).queryParam("deleted", deleted)
				.queryParam("bibIds", CollectionUtils.iterableToArray(bibIds)).queryParam("status", status)
				// Case for due date is deliberate to match inconsistency in Sierra API
				.queryParam("duedate", dueDate).queryParam("suppressed", suppressed)
				.queryParam("locations", CollectionUtils.iterableToArray(locations)));
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
  @Retryable
  public Publisher<SierraItem> getItem(final String itemId, List<String> fields) {
    if ( fields != null )
      return get("items/"+itemId, Argument.of(SierraItem.class), uri -> uri.queryParam("fields", String.join(",",fields)));
      
    return get("items/"+itemId, Argument.of(SierraItem.class), uri -> {});
  }

	@Override
	@SingleResult
	@Retryable
	public Publisher<SierraItem> getItem(final String itemId, String fields) {
		if ( fields != null )
	    return get("items/"+itemId, Argument.of(SierraItem.class), uri -> uri.queryParam("fields", fields));

    return get("items/"+itemId, Argument.of(SierraItem.class), uri -> {});
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

		Consumer<UriBuilder> uriBuilderConsumer = uri -> uri
			.queryParam("varFieldTag", varFieldTag)
			.queryParam("varFieldContent", varFieldContent)
			.queryParam("fields",
			"id,names,barcodes,deleted,suppressed,expirationDate,patronType,blockInfo,autoBlockInfo,homeLibraryCode,message,uniqueIds,emails,fixedFields");

		return get("patrons/find", Argument.of(SierraPatronRecord.class), uriBuilderConsumer);
	}

	@SingleResult
	public Publisher<QueryResultSet> patronsQuery(Integer offset, Integer limit, QueryEntry queryEntry) {
		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Filter_the_records_by_a_query_in_JSON_format_post_13

		Consumer<UriBuilder> uriBuilderConsumer = uri -> uri
			.queryParam("offset", offset)
			.queryParam("limit", limit);

		return postRequest("patrons/query")
			.map(req -> req.uri(uriBuilderConsumer))
			.map(req -> req.body(queryEntry))
			.flatMap(this::ensureToken)
			.flatMap(req -> doRetrieve(req, Argument.of(QueryResultSet.class)));
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
				.doOnNext(res -> log.debug("Result of validate {} {}", res, res.getStatus())).flatMap(res -> {
					return Mono.just(Boolean.TRUE);
				}).onErrorResume(throwable -> Mono.just(Boolean.FALSE));
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
				.flatMap(req -> doRetrieve(req, Argument.of(AuthToken.class)));
	}

	@SingleResult
	public Mono<Void> placeHoldRequest(String id, PatronHoldPost body) {
		return createRequest(POST, "patrons/" + id + "/holds/requests")
			.map(req -> req.body(body)).flatMap(this::ensureToken)
			.flatMap(req -> doExchange(req, Object.class, handleHoldRequestError(body, req)))
			.then();
	}

	public Function<Mono<HttpResponse<Object>>, Mono<HttpResponse<Object>>> handleHoldRequestError(
		PatronHoldPost body, MutableHttpRequest<PatronHoldPost> req) {

		return response -> response
			.onErrorResume(HttpClientResponseException.class, isRecordNotAvailable(body, req));
	}

	private Function<HttpClientResponseException, Mono<HttpResponse<Object>>> isRecordNotAvailable(
		PatronHoldPost body, MutableHttpRequest<PatronHoldPost> request) {

		return httpClientResponseException -> {
			if (sierraResponseErrorMatcher.isRecordNotAvailable(httpClientResponseException)) {
				return createRecordIsNotAvailableProblem(body, request, httpClientResponseException);
			}
			return Mono.error(httpClientResponseException);
		};
	}

	private Mono<HttpResponse<Object>> createRecordIsNotAvailableProblem(
		PatronHoldPost body, MutableHttpRequest<PatronHoldPost> req, HttpClientResponseException ex) {

		return fetchItemState(String.valueOf(body.getRecordNumber()))
			.flatMap(additionalData -> raiseError(new RecordIsNotAvailableProblem(lms.getCode(), req, ex, additionalData)));
	}

	private Mono<Map<String, Object>> fetchItemState(String recordNumber) {
		return get("items/" + recordNumber, Argument.of(SierraItem.class))
			.map(SierraItem::toMap)
			// if the item request fails we create a map with the exception response
			.onErrorResume(error -> Mono.just(Map.of("Failed to retrieve item information", error.toString())))
			.map(itemMap -> Map.of("item", itemMap));
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
				"id,updatedDate,createdDate,expirationDate,names,barcodes,patronType,homeLibraryCode,emails,message,uniqueIds,emails,fixedFields,blockInfo,autoBlockInfo,deleted"));
	}

	@SingleResult
	public Mono<Void> updatePatron(@Nullable final Long patronId, PatronPatch patronPatch) {

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Update_the_Patron_record_put_19
		return putRequest("patrons/" + patronId).map(request -> request.body(patronPatch))
				.flatMap(this::ensureToken).flatMap(req -> doExchange(req, Object.class)).then();
	}

	@SingleResult
	public Publisher<SierraPatronHold> getHold(@Nullable final Long holdId) {
		log.debug("getHold({})", holdId);

		// https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		return get("patrons/holds/" + holdId, Argument.of(SierraPatronHold.class), uriBuilder -> {
		});
	}

	private <T> Mono<T> get(String path, Argument<T> argumentType) { return get(path, argumentType, uriBuilder -> {}); }

	private <T> Mono<T> get(String path, Argument<T> argumentType, Consumer<UriBuilder> uriBuilderConsumer) {

		return createRequest(GET, path).map(req -> req.uri(uriBuilderConsumer)).flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, argumentType,
						response -> response.onErrorResume(sierraResponseErrorMatcher::isNoRecordsError, _t -> empty())));
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
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
	
	private <T> Mono<MutableHttpRequest<T>> deleteRequest(String path) {
		return createRequest(DELETE, path);
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String path) {
		return Mono.just(UriBuilder.of(path).build()).map(this::resolve)
				.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString()).accept(APPLICATION_JSON));
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Class<T> type) {
		return doExchange(request, type, noExtraErrorHandlingExchange());
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Class<T> type,
		Function<Mono<HttpResponse<T>>, Mono<HttpResponse<T>>> errorHandlingTransformer) {

		return Mono.from(client.exchange(request, Argument.of(type), ERROR_TYPE))
			.doOnError(logRequestAndResponseDetails(request))
			.doOnError(HttpResponsePredicates::isUnauthorised, _t -> clearToken())
			.transform(errorHandlingTransformer)
			// This has to happen after other error handlers related to
			// HttpClientResponseException
			.onErrorMap(HttpClientResponseException.class,
					responseException -> unexpectedResponseProblem(responseException, request, null))
			.onErrorResume(error -> {
				if (error instanceof AbstractHttpResponseProblem) {
					return Mono.error(error);
				}

				return raiseError(unexpectedResponseProblem(error, request, lms.getCode()));
			});
	}

	/**
	 * Make HTTP request to a Sierra system
	 *
	 * @param request                  Request to send
	 * @param responseBodyType         Expected type of the response body
	 * @param errorHandlingTransformer method for handling errors after the response
	 *                                 has been received
	 * @return Deserialized response body or error, that might have been transformed
	 *         already by handler
	 * @param <T> Type to deserialize the response to
	 */
	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> responseBodyType,
			Function<Mono<T>, Mono<T>> errorHandlingTransformer) {

		return Mono.from(client.retrieve(request, responseBodyType, ERROR_TYPE))
			.doOnError(logRequestAndResponseDetails(request))
			.doOnError(HttpResponsePredicates::isUnauthorised, _t -> clearToken()).transform(errorHandlingTransformer)
			// This has to go after more specific error handling
			// as will convert any client response exception to a problem
			.onErrorMap(HttpClientResponseException.class,
					responseException -> unexpectedResponseProblem(responseException, request, null))
			.onErrorResume(error -> {
				if (error instanceof Problem) {
					return Mono.error(error);
				}

				return raiseError(unexpectedResponseProblem(error, request, lms.getCode()));
			});
	}

	private static Consumer<Throwable> logRequestAndResponseDetails(MutableHttpRequest<?> request) {
		return error -> {
			try {
				log.error("""
						HTTP Request and Response Details:
						URL: {}
						Method: {}
						Headers: {}
						Body: {}
						Response: {}""",
					request.getUri(),
					request.getMethod(),
					request.getHeaders().asMap(),
					request.getBody().orElse(null),
					error.toString());
			} catch (Exception e) {
				log.error("Couldn't log error request and response details", e);
			}
		};
	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> argumentType) {
		return doRetrieve(request, argumentType, noExtraErrorHandling());
	}


	/**
	 * Utility method to specify that no specialised error handling will be needed
	 * for this request
	 *
	 * @return transformer that provides no additionally error handling
	 * @param <T> Type of response being handled
	 */
	private static <T> Function<Mono<HttpResponse<T>>, Mono<HttpResponse<T>>> noExtraErrorHandlingExchange() {
		return Function.identity();
	}

	/**
	 * Utility method to specify that no specialised error handling will be needed
	 * for this request
	 *
	 * @return transformer that provides no additionally error handling
	 * @param <T> Type of response being handled
	 */
	private static <T> Function<Mono<T>, Mono<T>> noExtraErrorHandling() {
		return Function.identity();
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
	public Publisher<LinkResult> checkOutItemToPatron(String itemBarcode, String patronBarcode, String pin) {

		final var checkoutPatch = CheckoutPatch.builder().itemBarcode(itemBarcode).patronBarcode(patronBarcode);
		final var patchWithPin = pin != null ? checkoutPatch.patronPin(pin).build() : checkoutPatch.build();

		log.debug("Checkout patch used: {}", patchWithPin);

		return postRequest("patrons/checkout").map(request -> request.body(patchWithPin)).flatMap(this::ensureToken)
				.flatMap(request -> doRetrieve(request, Argument.of(LinkResult.class)));
	}

	public Publisher<HttpStatus> deleteItem(@NonNull @NotNull String id) {
		
		return deleteRequest("items/" + id)
			.flatMap(this::ensureToken)
			.flatMap(request -> doRetrieve(request, Argument.of(HttpStatus.class)));
	}

	public Publisher<HttpStatus> deleteBib(@NonNull @NotNull String id) {
		return deleteRequest("bibs/" + id)
			.flatMap(this::ensureToken)
			.flatMap(request -> doRetrieve(request, Argument.of(HttpStatus.class)));
	}

	public Publisher<HttpStatus> deleteHold(@NonNull @NotNull String id) {
		return deleteRequest("patrons/holds/" + id)
			.flatMap(this::ensureToken)
			.flatMap(request -> doRetrieve(request, Argument.of(HttpStatus.class)));
	}


  public Publisher<HttpStatus> deletePatron(@NonNull @NotNull String id) {
    return deleteRequest("patrons/" + id)
      .flatMap(this::ensureToken)
      .flatMap(request -> doRetrieve(request, Argument.of(HttpStatus.class)));
  }


	@Override
	public Publisher<CheckoutResultSet> getItemCheckouts(String patronId) {

		final var path = String.format("items/%s/checkouts", patronId);

		return createRequest(GET, path).flatMap(this::ensureToken)
			.flatMap(req -> doRetrieve(req, Argument.of(CheckoutResultSet.class)));
	}

	@Override
	public Publisher<CheckoutEntry> renewal(String checkoutId) {
		return postRequest(String.format("patrons/checkouts/%s/renewal", checkoutId))
			.flatMap(this::ensureToken)
			.flatMap(request -> doRetrieve(request, Argument.of(CheckoutEntry.class)));
	}

  @SingleResult
  public Publisher<TokenInfo> getTokenInfo() {
		return createRequest(GET,"/info/token")
			.flatMap(this::ensureToken)
			.flatMap(req -> doRetrieve(req, Argument.of(TokenInfo.class)));
	}

	@Override
	@NotNull
	public URI getRootUri() {
		return this.rootUri;
	}

}
