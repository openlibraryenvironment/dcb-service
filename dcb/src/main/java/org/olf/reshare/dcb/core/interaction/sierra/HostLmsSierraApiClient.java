package org.olf.reshare.dcb.core.interaction.sierra;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.retry.annotation.Retryable;
import org.olf.reshare.dcb.core.model.HostLms;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.interaction.sierra.holds.SierraPatronHold;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;
import static java.util.Collections.emptyList;

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
        // implemented it should use. Even though this one is "Secondary" the constructor
        // args are still found to not exist without this constructor.
        throw new IllegalStateException();
    }

    @Creator
    public HostLmsSierraApiClient(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client) {

        log.debug("Creating Sierra HostLms client for HostLms {}", hostLms);

        URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
        rootUri = resolve(hostUri, UriBuilder.of("/iii/sierra-api/v6/").build());

        lms = hostLms;
        this.client = client;
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
                .flatMap(req -> doRetrieve(req, BibResultSet.class));
    }


    @SingleResult
    @Retryable
    @Get("patrons/metadata")
    public Publisher<List<PatronMetadata>> patronMetadata() {
      return getRequest("patrons/metadata")
              .map(req -> req.uri(theuri -> {
                  theuri.queryParam("offset", 0)
                          .queryParam("limit",1000)
                          .queryParam("deleted", false);

              }))
              .flatMap(this::ensureToken)
              .flatMap( req -> Mono.from(client.retrieve(req, Argument.listOf(PatronMetadata.class), ERROR_TYPE )));
    }

    @SingleResult
    @Retryable
    @Get("branches/pickupLocations")
    public Publisher<List<PickupLocationInfo>> pickupLocations() {

        // A little bit of wriggle here, Sierra returns a flat list of PickupLocations in an array without a wrapper
        // so we need to specify the inner type via Argument.listOf
        return getRequest("branches/pickupLocations")
                .flatMap(this::ensureToken)
                .flatMap ( req -> Mono.from(client.retrieve(req, Argument.listOf(PickupLocationInfo.class), ERROR_TYPE) ) );
                // .flatMap(req -> doRetrieve(req, PickupLocationResultSet.class));
    }

    @SingleResult
    @Retryable
    @Get("branches")
    public Publisher<BranchResultSet> branches(Integer limit, Integer offset, Iterable<String> fields) {

        return getRequest("branches")
                .map(req -> req.uri(theUri -> {
                    theUri
                            .queryParam("limit", limit)
                                        .queryParam("offset", offset)
                                        .queryParam("fields", iterableToArray(fields))
                                        ;
                        }))
                .flatMap(this::ensureToken)
                .flatMap(req -> doRetrieve(req, BranchResultSet.class) );
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
                .flatMap(req -> doRetrieve(req, ResultSet.class))
                .onErrorReturn(sierraResponseErrorMatcher::isNoRecordsError,
                        ResultSet.builder().entries(emptyList()).build());
    }

		@Override
		@SingleResult
		@Post("items")
		@Produces(value = APPLICATION_JSON)
		public Publisher<LinkResult> createItem(@Body ItemPatch itemPatch) {
			return postRequest("items")
				.map(request -> request.body(itemPatch))
				.flatMap(this::ensureToken)
				.flatMap(request -> doRetrieve(request, LinkResult.class));
		}

		@SingleResult
		@Post("patrons")
		@Produces(value = APPLICATION_JSON)
		public Publisher<LinkResult> patrons(@Body PatronPatch body) {

			// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Create_a_patron_record_post_0
			return postRequest("patrons")
				.map(req -> req.body(body))
				.flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, LinkResult.class));
		}

		@SingleResult
		@Post("bibs")
		@Produces(value = APPLICATION_JSON)
		public Publisher<LinkResult> bibs(@Body BibPatch body) {

			// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/bibs/Create_a_Bib_record_post_0
			return postRequest("bibs")
				.map(req -> req.body(body))
				.flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, LinkResult.class));
		}

		@SingleResult
		@Get("patrons/find")
		@Produces(value = APPLICATION_JSON)
		public Publisher<SierraPatronRecord> patronFind(String varFieldTag, String varFieldContent) {

			// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Find_a_patron_by_varField_fieldTag_and_varField_content_get_6
			return getRequest("patrons/find")
				.map(req -> req.uri(theUri -> theUri
					.queryParam("varFieldTag", varFieldTag)
					.queryParam("varFieldContent", varFieldContent)))
				.flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, SierraPatronRecord.class) )
				.onErrorReturn(sierraResponseErrorMatcher::isNoRecordsError,
					new SierraPatronRecord());
		}

		@SingleResult
		@Get("patrons/{id}/holds")
		@Produces(value = APPLICATION_JSON)
		public Publisher<SierraPatronHoldResultSet> patronHolds(@PathVariable String id) {

			// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
			return getRequest("patrons/" + id + "/holds")
				.flatMap(this::ensureToken)
				.flatMap(req -> doRetrieve(req, SierraPatronHoldResultSet.class) )
				.onErrorReturn(sierraResponseErrorMatcher::isNoRecordsError,
					new SierraPatronHoldResultSet(0, 0, new ArrayList<>()));
		}

    private <T> Mono<T> handleResponseErrors ( final Mono<T> current ) {

        return current.onErrorMap(throwable -> {
		log.debug("inside handleResponseErrors::onErrorMap() - throwable {}",throwable.toString());
            // On a 401 we should clear the token before propagating the error.
            // Sierra returns 404 if a search returns no results ( :explodinghead: ) need to find a way to handle that gracefully
            if (HttpClientResponseException.class.isAssignableFrom(throwable.getClass())) {
		log.debug("and throwable is a HttpClientResponseException");
                HttpClientResponseException e = (HttpClientResponseException) throwable;
                int code = e.getStatus().getCode();
                switch (code) {
                    case 401:
                        log.debug("Clearing token to trigger reauthentication");
                        this.currentToken = null;
                        break;
                    default:
                        log.warn("response code: {} error {}",code,e.getStatus().toString());
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
        var response = Mono.from( client.retrieve(request, Argument.of(type), ERROR_TYPE) );
        return mapErrors ? handleResponseErrors( response ) : response;
    }

		private <T> Mono<HttpResponse<T>> doExchange( MutableHttpRequest<?> request, Class<T> type) {
			Mono<HttpResponse<T>> response = Mono.from(client.exchange(request, Argument.of(type), ERROR_TYPE));

			return response.onErrorMap(throwable -> {
				// On a 401 we should clear the token before propagating the error.
				// Sierra returns 404 if a search returns no results ( :explodinghead: ) need to find a way to handle that gracefully
				if (HttpClientResponseException.class.isAssignableFrom(throwable.getClass())) {
					HttpClientResponseException e = (HttpClientResponseException) throwable;
					int code = e.getStatus().getCode();

					switch (code) {
						case 401:
							log.debug("Clearing token to trigger reauthentication");
							this.currentToken = null;
							break;
						default:
							log.warn("response error {}",e.getStatus().toString());
							break;
					}
				}
				return throwable;
			});
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
                .switchIfEmpty(aquireAccessToken() )

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

    @SingleResult
    @Post("patrons/{id}/holds/requests")
    @Produces(value = APPLICATION_JSON)
    public Mono<Void> placeHoldRequest(@PathVariable String id, @Body PatronHoldPost body) {
        return createRequest(HttpMethod.POST, "patrons/" + id + "/holds/requests")
            .map(req -> req.body(body))
            .flatMap(this::ensureToken)
            .flatMap(req -> doExchange(req, Object.class))
            .then();
	}

        @SingleResult
        @Retryable
        @Get("patrons/holds")
        public Publisher<SierraPatronHoldResultSet> getAllPatronHolds(
                        final Integer limit,
                        final Integer offset) {
		// return Mono.empty();
	log.debug("getAllPatronHolds(limit:{},offset:{})",limit,offset);
        return getRequest("patrons/holds")
                .map(req -> req.uri(theUri -> {
                    theUri
                            .queryParam("limit", limit)
                            .queryParam("offset", offset)
                                        ;
                        }))
                .flatMap(this::ensureToken)
                .flatMap(req -> doRetrieve(req, SierraPatronHoldResultSet.class) );
	}


	@SingleResult
	@Get("/patrons/{id}")
	public Publisher<SierraPatronRecord> getPatron(@Nullable @PathVariable("id") final Long patronId) {
		// See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		return getRequest("patrons/" + patronId)
			.flatMap(this::ensureToken)
			.flatMap(req -> doRetrieve(req, SierraPatronRecord.class) )
			.onErrorReturn(new SierraPatronRecord());
	}

        @SingleResult
        @Get("/patrons/holds/{id}")
        public Publisher<SierraPatronHold> getHold(@Nullable @PathVariable("id") final Long holdId) {
                // See https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_the_holds_data_for_a_single_patron_record_get_30
		log.debug("getHold({})",holdId);
                return getRequest("patrons/holds/" + holdId)
                        .flatMap(this::ensureToken)
                        .flatMap(req -> doRetrieve(req, SierraPatronHold.class) );
        }
}
