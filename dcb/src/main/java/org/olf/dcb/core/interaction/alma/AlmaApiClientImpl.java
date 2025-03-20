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

import services.k_int.interaction.alma.*;

@Slf4j
@Secondary
@Prototype
public class AlmaApiClientImpl implements AlmaApiClient {
  private static final String CLIENT_SECRET = "secret";
  private static final String CLIENT_KEY = "key";
  private static final String CLIENT_BASE_URL = "base-url";

  private final URI rootUri;
  private final HostLms lms;
  private final HttpClient client;
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
    URI relativeURI = UriBuilder.of("/almaws/v1/").build();
    rootUri = RelativeUriResolver.resolve(hostUri, relativeURI);

    lms = hostLms;
    this.client = client;
    this.conversionService = conversionService;
  }

  @Override
  @NotNull
  public URI getRootUri() {
    return this.rootUri;
  }

}
