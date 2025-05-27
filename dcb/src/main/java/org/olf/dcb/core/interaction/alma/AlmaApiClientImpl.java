package org.olf.dcb.core.interaction.alma;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.uri.UriBuilder;

import io.micronaut.serde.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.interaction.RelativeUriResolver;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

import services.k_int.interaction.alma.*;
import services.k_int.interaction.alma.types.*;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;
import services.k_int.interaction.alma.types.error.AlmaException;
import services.k_int.interaction.alma.types.items.*;
import services.k_int.interaction.alma.types.userRequest.*;
import services.k_int.interaction.alma.types.holdings.*;

import static io.micronaut.http.HttpStatus.NO_CONTENT;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.HttpMethod.*;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Slf4j
@Secondary
@Prototype
public class AlmaApiClientImpl implements AlmaApiClient {
  private static final String APIKEY_KEY = "apikey";
  private static final String CLIENT_BASE_URL = "base-url";
	private static final String ALMA_URL = "alma-url";

  private final URI rootUri;
  private final HostLms lms;
  private final HttpClient client;

  private final String apikey;
  private final ConversionService conversionService;
	private final ObjectMapper objectMapper;

  public AlmaApiClientImpl() {
    // No args constructor needed for Micronaut bean
    // context to not freak out when deciding which bean of the interface type
    // implemented it should use. Even though this one is "Secondary" the
    // constructor
    // args are still found to not exist without this constructor.
    throw new IllegalStateException();
  }

  @Creator
  public AlmaApiClientImpl(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client,
													 ConversionService conversionService, ObjectMapper objectMapper) {

		log.debug("Creating Alma HostLms client for HostLms {}", hostLms);

		URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(ALMA_URL)).build();
		apikey = (String) hostLms.getClientConfig().get(APIKEY_KEY);
		URI relativeURI = UriBuilder.of("/almaws/v1/").build();
		rootUri = RelativeUriResolver.resolve(hostUri, relativeURI);

		lms = hostLms;
		this.client = client;
		this.conversionService = conversionService;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void logClientConfig() {
		if (client instanceof DefaultHttpClient) {
			DefaultHttpClient defaultClient = (DefaultHttpClient) client;
			log.info("Alma client read timeout: {}", defaultClient.getConfiguration().getReadTimeout().orElse(null));
		}
	}

	public Publisher<AlmaUserList> users() {
		final String path="/users";
		return createRequest(GET, path)
			.flatMap(req -> doExchange(req, Argument.of(AlmaUserList.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaUserList -> log.info("Got {} users", almaUserList));
	}

	public Mono<AlmaUserList> getUsersByExternalId(String externalId) {
		final String path="/almaws/v1/users";
		final String query="external_id~"+externalId;

		return createRequest(GET, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder
				.queryParam("external_id", externalId)
				.build()))
			.flatMap(req -> doExchange(req, Argument.of(AlmaUserList.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaUserList -> log.info("Got {} users",almaUserList));
	}

	// See https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
	public Publisher<AlmaUser> users(String id) {
		final String path="/users/"+id;
		return createRequest(GET, path)
			.flatMap(req -> doExchange(req, Argument.of(AlmaUser.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaUser -> log.info("Retrieved user {}", almaUser));
	}

	// /almaws/v1/bibs/{mms_id}
	// /almaws/v1/items?item_barcode={barcode}
	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_item.xsd/?tags=GET#item
	// https://swagger.io/blog/api-strategy/difference-between-swagger-and-openapi/
	// 

	// https://developers.exlibrisgroup.com/forums/topic/retrieve-item-via-almaws-v1-itemsbarcode/
	private Mono<AlmaItem> getItem(String item_barcode) {
		return Mono.just(AlmaItem.builder().build());
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String path) {
		log.info("Creating request for {} {}", path, method);

		final String apiKey = "apikey " + apikey;

		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString())
				.accept(APPLICATION_JSON))

			.map(req -> req.header(HttpHeaders.AUTHORIZATION, apiKey)).doOnSuccess(req -> { log.info("Request {}",req);});
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Argument<T> argumentType) {
		log.info("doExchange - Method: {}, URI: {}, argumentType: {}", request.getMethod(), request.getUri(), argumentType);
		log.info("doExchange - Full Request: {}", request);
		return Mono.from(client.exchange(request, argumentType, Argument.of(HttpClientResponseException.class)))
			.flatMap(response -> {

				if (response.getBody().isPresent()) {
					log.info("Response body: {}", response.getBody().get());
					return Mono.just(response);

				} else if (response.getBody().isEmpty() && argumentType.equalsType(Argument.of(Void.class))) {
					log.debug("Response body is empty for request to {} with expected type {}", request.getPath(), argumentType.getType().getSimpleName());
					return Mono.just(response);
				}

				else {
					String errorMsg = String.format("Response body is empty for request to %s with expected type %s",
						request.getPath(), argumentType.getType().getSimpleName());
					log.error(errorMsg);
					return Mono.error(new IllegalStateException(errorMsg));
				}
			})
			.onErrorResume(HttpClientResponseException.class, ex -> {
				HttpStatus status = ex.getStatus();
				Optional<String> responseBody = ex.getResponse().getBody(String.class);

				if (responseBody.isPresent()) {

					Optional<AlmaErrorResponse> almaError = Optional.empty();
					try {
						almaError = Optional.of(objectMapper.readValue(responseBody.get(), AlmaErrorResponse.class));
					} catch (Exception e) {
						log.error("Conversion service failed to convert response body to AlmaErrorResponse: {}", responseBody.get(), e);
					}

					if (almaError.isPresent()) {
						AlmaErrorResponse errorResponse = almaError.get();

						StringBuilder logMsg = new StringBuilder();
						logMsg.append(String.format("Alma API error for %s (HTTP %d)", request.getPath(), status.getCode()));
						if (errorResponse.getErrorList() != null && errorResponse.getErrorList().getError() != null) {
							for (AlmaError e : errorResponse.getErrorList().getError()) {
								logMsg.append(String.format("%n - [%s] %s", e.getErrorCode(), e.getErrorMessage()));
							}
						}
						log.error(logMsg.toString());
						return Mono.error(new AlmaException("Alma API error", errorResponse, status));
					} else {
						log.error("Failed to convert error body to AlmaErrorResponse for request to {}", request.getPath());
					}
				}

				log.error("HTTP {} error for request to {}: {}", status.getCode(), request.getPath(), responseBody.orElse("No body"), ex);
				return Mono.error(ex); // fallback
			});
	}

  @Override
  @NotNull
  public URI getRootUri() {
    return this.rootUri;
  }

	@Override
  public Mono<AlmaUser> createPatron(AlmaUser patron) {
		// POST /almaws/v1/users
		// See: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
		final String path="/almaws/v1/users";
    return createRequest(POST, path)
			.map(request -> request.body(patron))
			.flatMap(request -> doExchange(request, Argument.of(AlmaUser.class)))
			.map(response -> response.getBody().get())
			.doOnNext(user -> log.info("Created user {}",user.getPrimary_id()));
	}

	@Override
	public Mono<AlmaUser> updateUserDetails(String user_id, AlmaUser patron) {
		// PUT /almaws/v1/users
		// See: https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJz/
		final String path="/almaws/v1/users/"+user_id;
		return createRequest(PUT, path)
			.map(request -> request.body(patron))
			.flatMap(request -> doExchange(request, Argument.of(AlmaUser.class)))
			.map(response -> response.getBody().get())
			.doOnNext(user -> log.info("Updated user {}",user.getPrimary_id()));
	}

	// q=primary_id~abc_def
	// https://developers.exlibrisgroup.com/alma/apis/docs/users/R0VUIC9hbG1hd3MvdjEvdXNlcnM=/
	// https://developers.exlibrisgroup.com/alma/apis/users/
	public Mono<AlmaUser> getAlmaUserByUserId(String user_id) {
		final String path="/almaws/v1/users/"+user_id;
    return createRequest(GET, path)
      .flatMap(req -> doExchange(req, Argument.of(AlmaUser.class)))
			.map(response -> response.getBody().get())
			.doOnNext(user -> log.info("User retrieved {}",user.getPrimary_id()));
	}

	public Mono<String> deleteAlmaUser(String user_id) {
		final String path="/almaws/v1/users/"+user_id;
    return createRequest(DELETE, path)
      .flatMap(req -> doExchange(req, Argument.of(Void.class)))
			// This method returns HTTP 204 (No Content)
			// which means that the server successfully processed the request, but is not returning any content.
			.map(response -> NO_CONTENT.equals(response.getStatus()) ? "User deleted" : "User not deleted");
	}

	public Mono<String> deleteBib(String mms_id) {
		final String path="/almaws/v1/bibs/"+mms_id;
		return createRequest(DELETE, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder.queryParam("override", true).build()))
			.flatMap(req -> doExchange(req, Argument.of(Void.class)))
			// This method returns HTTP 204 (No Content)
			// which means that the server successfully processed the request, but is not returning any content.
			.map(response -> NO_CONTENT.equals(response.getStatus()) ? "Bib deleted" : "Bib not deleted");
	}

	public Mono<String> deleteItem(String id, String holdingsId, String mms_id) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings/"+holdingsId+"/items/"+id;
		return createRequest(DELETE, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder.queryParam("override", true).build()))
			.flatMap(req -> doExchange(req, Argument.of(Void.class)))
			// This method returns HTTP 204 (No Content)
			// which means that the server successfully processed the request, but is not returning any content.
			.map(response -> NO_CONTENT.equals(response.getStatus()) ? "Item deleted" : "Item not deleted");
	}

	public Mono<String> deleteHolding(String holdingsId, String mms_id) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings/"+holdingsId;
		return createRequest(DELETE, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder.queryParam("override", true).build()))
			.flatMap(req -> doExchange(req, Argument.of(Void.class)))
			// This method returns HTTP 204 (No Content)
			// which means that the server successfully processed the request, but is not returning any content.
			.map(response -> NO_CONTENT.equals(response.getStatus()) ? "Holding deleted" : "Holding not deleted");
	}

	// This is failing - User with identifier X of type Y was not found.

	public Mono<String> deleteUserRequest(String userId, String requestId) {
		final String path="/almaws/v1/users/"+userId+"/requests/"+requestId;
		return createRequest(DELETE, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder.queryParam("override", true).build()))
			.flatMap(req -> doExchange(req, Argument.of(Void.class)))
			// This method returns HTTP 204 (No Content)
			// which means that the server successfully processed the request, but is not returning any content.
			.map(response -> NO_CONTENT.equals(response.getStatus()) ? "Hold deleted" : "Hold not deleted");
	}

	@Override
	// https://developers.exlibrisgroup.com/alma/apis/docs/users/UE9TVCAvYWxtYXdzL3YxL3VzZXJzL3t1c2VyX2lkfQ==/
	public Mono<AlmaUser> authenticateOrRefreshUser(String user_id, String password) {
		final String path="/almaws/v1/users/"+user_id;

		return createRequest(GET, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder.queryParam("password", password).build()))
			.flatMap(req -> doExchange(req, Argument.of(AlmaUser.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaUser -> log.info("retrieved user {}", almaUser.getPrimary_id()));
	}

	public Mono<String> test() {
    final String path="/almaws/v1/conf/test";
    return createRequest(GET, path)
      .flatMap(request -> Mono.from(doExchange(request, Argument.of(String.class))))
			.map(response -> response.getBody().get());
	}

	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_holdings.xsd/?tags=GET
  public Mono<AlmaHoldings> getHoldings(String mms_id) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings";
		return createRequest(GET, path)
      .flatMap(request -> doExchange(request, Argument.of(AlmaHoldings.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaHoldings -> log.info("retrieved {}", almaHoldings));
	}

	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_user_request.xsd/?tags=GET
	public Mono<AlmaRequestResponse> retrieveUserRequest(String user_id, String request_id) {
		final String path="/almaws/v1/users/"+user_id+"/requests/"+request_id;
		return createRequest(GET, path)
			.flatMap(request -> doExchange(request, Argument.of(AlmaRequestResponse.class)))
			.map(response -> response.getBody().get())
			.doOnNext(response -> log.info("retrieved almaRequest {}", response));
	}

	public Mono<AlmaItemLoanResponse> createUserLoan(String user_id, String item_pid, AlmaItemLoan loan) {
		final String path="/almaws/v1/users/"+user_id+"/loans";
		return createRequest(POST, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder
				.queryParam("item_pid", item_pid)
				.build()))
			.map(request -> request.body(loan))
			.flatMap(req -> doExchange(req, Argument.of(AlmaItemLoanResponse.class)))
			.map(response -> response.getBody().get())
			.doOnNext(returnedLoan -> log.info("retrieved loan {}", returnedLoan));
	}

	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_holdings.xsd/?tags=GET
  public Mono<AlmaItems> getItemsForHolding(String mms_id, String holding_id) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings/"+holding_id+"/items";
		return createRequest(GET, path)
      .flatMap(request -> Mono.from(doExchange(request, Argument.of(AlmaItems.class))))
			.map(response -> response.getBody().get());
	}

	// IF this doesn't work, one option is to call getAllItems and then manually filter for the pid - same number of API requests
  public Mono<AlmaItem> getItemForPID(String mms_id, String holdingId, String pid) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings/"+holdingId+"/items/"+pid;

		return createRequest(GET, path)
			.flatMap(req -> doExchange(req, Argument.of(AlmaItem.class)))
			.map(response -> response.getBody().get())
			.doOnNext(items -> log.info("Got item {}", items.getItemData().getPid()));
	}

	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_holdings.xsd/?tags=GET
  public Mono<AlmaItems> getAllItems(String mms_id, String holding_id) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings/"+holding_id+"/items";
		return createRequest(GET, path)
      .flatMap(request -> doExchange(request, Argument.of(AlmaItems.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaItems -> log.info("Got {} items",almaItems));
	}

  public Mono<AlmaRequestResponse> placeHold(String userId, AlmaRequest almaRequest) {
		final String path="/almaws/v1/users/"+userId+"/requests";
		final String itemId = getValueOrNull(almaRequest, AlmaRequest::getPId);

		// Trying to understand what's in here
		try {
			String almaRequestJson = objectMapper.writeValueAsString(almaRequest);
			log.info("Full AlmaRequest body to be sent: {}", almaRequestJson);
		} catch (Exception e) {
			log.error("Could not serialize AlmaRequest object for logging", e);
		}
		log.info("Placing hold for user {} and item {}", userId, itemId);
    return createRequest(POST, path)
			.map(req -> req.uri(uriBuilder -> uriBuilder
				.queryParam("item_pid", itemId)
				.build()))
			.doOnNext(req -> log.info("Final request method: {}, URI: {}, Full request: {}", req.getMethod(), req.getUri(), req))
			// I am wondering if the PID being in the body and the query params is causing some issues
			.map(request -> request.body(almaRequest))
			.doOnNext(req-> log.info("Now changing to an Alma request which looks like this {}", req) )
			.flatMap(req -> doExchange(req, Argument.of(AlmaRequestResponse.class)))
			.doOnNext(response -> log.info("doExchange returned response with status: {}, hasBody: {}",
				response.getStatus(), response.getBody().isPresent()))
			.doOnError(error -> log.error("doExchange failed with error: {}", error.getMessage(), error))
			.doOnNext(req -> log.info("The response is {}" , req))
			.map(response -> response.getBody().get())
			.doOnNext(hold -> log.info("Created hold {}",hold.getRequestId()));
	}

	public Mono<AlmaBib> createBib(String almaBib) {
		final String path="/almaws/v1/bibs";

		return createRequest(POST, path)
      .map(request -> request.body(almaBib).contentType(MediaType.APPLICATION_XML))
			.flatMap(req -> doExchange(req, Argument.of(AlmaBib.class)))
			.map(response -> response.getBody().get())
			.doOnNext(bib -> log.info("Created bib {}",bib.getMmsId()));
	}

	public Mono<AlmaHolding> createHolding(String mms_id, String almaHolding) {
		final String path="/almaws/v1/bibs/"+mms_id+"/holdings";
		log.info(almaHolding);
		return createRequest(POST, path)
			.map(request -> request.body(almaHolding).contentType(MediaType.APPLICATION_XML))
			.flatMap(req -> doExchange(req, Argument.of(AlmaHolding.class)))
			.map(response -> response.getBody().get())
			.doOnNext(holding -> log.info("Created holding {}",holding.getHoldingId()));
	}

	public Mono<AlmaItem> createItem(String bibId, String holdingId, AlmaItem item) {
    final String path="/almaws/v1/bibs/"+bibId+"/holdings/"+holdingId+"/items";
    return createRequest(POST, path)
			.map(request -> request.body(item))
			.flatMap(req -> doExchange(req, Argument.of(AlmaItem.class)))
			.map(response -> response.getBody().get())
			.doOnNext(almaItemData -> log.info("Created item {}",almaItemData.getItemData().getPid()));
	}
}
