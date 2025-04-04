package org.olf.dcb.core.interaction.alma;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.http.client.HttpClient;


import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.interaction.RelativeUriResolver;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import services.k_int.interaction.alma.*;
import services.k_int.interaction.alma.types.*;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.HttpMethod.GET;


@Slf4j
@Secondary
@Prototype
public class AlmaApiClientImpl implements AlmaApiClient {
  private static final String APIKEY_KEY = "apikey";
  private static final String CLIENT_BASE_URL = "base-url";

  private final URI rootUri;
  private final HostLms lms;
  private final HttpClient client;
  private final String apikey;
  private final ConversionService conversionService;

  public AlmaApiClientImpl() {
    // No args constructor needed for Micronaut bean
    // context to not freak out when deciding which bean of the interface type
    // implemented it should use. Even though this one is "Secondary" the
    // constructor
    // args are still found to not exist without this constructor.
    throw new IllegalStateException();
  }

  @Creator
  public AlmaApiClientImpl(@Parameter("hostLms") HostLms hostLms, @Parameter("client") HttpClient client, ConversionService conversionService) {

    log.debug("Creating Alma HostLms client for HostLms {}", hostLms);

    URI hostUri = UriBuilder.of((String) hostLms.getClientConfig().get(CLIENT_BASE_URL)).build();
		apikey = (String) hostLms.getClientConfig().get(APIKEY_KEY);
    URI relativeURI = UriBuilder.of("/almaws/v1/").build();
    rootUri = RelativeUriResolver.resolve(hostUri, relativeURI);

    lms = hostLms;
    this.client = client;
    this.conversionService = conversionService;
  }

	public Publisher<UserList> users() {
		log.debug("Get users - apikey={}",apikey);
		return get("/users", Argument.of(UserList.class),
			uri -> uri.queryParam("apikey",apikey)
				.queryParam("limit","10")
				.queryParam("offset","0"));
	}

	// See https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
	public Publisher<User> user(String id) {
		log.debug("Get user id={}, apikey={}",id,apikey);
		return get("/users/"+id, Argument.of(User.class),
			uri -> uri.queryParam("apikey",apikey));
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

	private <T> Mono<T> get(String path, Argument<T> argumentType, Consumer<UriBuilder> uriBuilderConsumer) {
		return createRequest(GET, path)
			.map(req -> req.uri(uriBuilderConsumer))
			.flatMap(req -> Mono.from(doRetrieve(req, argumentType)));
	}

	private <T> Mono<MutableHttpRequest<T>> createRequest(HttpMethod method, String path) {
		log.debug("Creating request for {}",APPLICATION_JSON);
		return Mono.just(UriBuilder.of(path).build())
			.map(this::resolve)
			.map(resolvedUri -> HttpRequest.<T>create(method, resolvedUri.toString())
				.accept(APPLICATION_JSON));
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}

	private <T> Mono<HttpResponse<T>> doExchange(MutableHttpRequest<?> request, Class<T> type) {
		return Mono.from(client.exchange(request, Argument.of(type)));
	}

	private <T> Mono<T> doRetrieve(MutableHttpRequest<?> request, Argument<T> argumentType) {
		return Mono.from(client.retrieve(request, argumentType));
	}

  @Override
  @NotNull
  public URI getRootUri() {
    return this.rootUri;
  }

}
