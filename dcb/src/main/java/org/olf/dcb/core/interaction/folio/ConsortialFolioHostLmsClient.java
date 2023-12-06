package org.olf.dcb.core.interaction.folio;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.FailedToGetItemsException;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class ConsortialFolioHostLmsClient implements HostLmsClient {
	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("base-url", "Base URL of the FOLIO system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this FOLIO tenant", TRUE);

	private static final List<String> itemStatuses = List.of(
		"Aged to lost",
		"Available",
		"Awaiting pickup",
		"Awaiting delivery",
		"Checked out",
		"Claimed returned",
		"Declared lost",
		"In process",
		"In process (non-requestable)",
		"In transit",
		"Intellectual item",
		"Long missing",
		"Lost and paid",
		"Missing",
		"On order",
		"Paged",
		"Restricted",
		"Order closed",
		"Unavailable",
		"Unknown",
		"Withdrawn");

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final ItemStatusMapper itemStatusMapper;

	private final String apiKey;
	private final URI rootUri;

	public static ItemStatusMapper.FallbackMapper folioFallback() {
		final var statusCodeMap = Map.of(
			"Available", AVAILABLE,
			"Checked out", CHECKED_OUT);

		return statusCode -> (statusCode == null || (statusCode.isEmpty()))
			? UNKNOWN
			: statusCodeMap.getOrDefault(statusCode, UNAVAILABLE);
	}

	public ConsortialFolioHostLmsClient(@Parameter HostLms hostLms,
		@Parameter("client") HttpClient httpClient, ItemStatusMapper itemStatusMapper) {

		this.hostLms = hostLms;
		this.httpClient = httpClient;

		this.apiKey = API_KEY_SETTING.getRequiredConfigValue(hostLms);
		this.rootUri = UriBuilder.of(BASE_URL_SETTING.getRequiredConfigValue(hostLms)).build();
		this.itemStatusMapper = itemStatusMapper;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			BASE_URL_SETTING,
			API_KEY_SETTING
		);
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return getHoldings(bib.getSourceRecordId())
			.flatMap(outerHoldings -> checkResponse(outerHoldings, bib.getSourceRecordId()))
			.mapNotNull(OuterHoldings::getHoldings)
			.flatMapMany(Flux::fromIterable)
			.flatMap(this::mapHoldingsToItems)
			.collectList();
	}

	private Mono<OuterHoldings> getHoldings(String instanceId) {
		final var relativeUri = UriBuilder.of("/rtac").build();

		final var request = HttpRequest.create(HttpMethod.GET, resolve(relativeUri).toString())
			.uri(uriBuilder -> uriBuilder
				.queryParam("instanceIds", instanceId)
				// Full periodicals refers to items, without this parameter holdings will be returned instead of items
				.queryParam("fullPeriodicals", true)
			)
			// Base 64 encoded API key
			.header("Authorization", apiKey)
			// MUST explicitly accept JSON otherwise XML will be returned
			.accept(APPLICATION_JSON);

		return Mono.from(httpClient.retrieve(request, Argument.of(OuterHoldings.class)));
	}

	private static Mono<OuterHoldings> checkResponse(OuterHoldings outerHoldings, String instanceId) {
		if (hasNoErrors(outerHoldings)) {
			if (hasNoOuterHoldings(outerHoldings)) {
				log.error("No errors or outer holdings returned from RTAC for instance ID: {}, response: {}, likely to be invalid API key",
					instanceId, outerHoldings);

				// RTAC returns no outer holdings (instances) when the API key is invalid
				return Mono.error(new LikelyInvalidApiKeyException(instanceId));
			} else {
				if (hasMultipleOuterHoldings(outerHoldings)) {
					log.error("Unexpected outer holdings (instances) received from RTAC for instance ID: {}, response: {}",
						instanceId, outerHoldings);

					// DCB only asks for holdings for a single instance at a time
					// RTAC should never respond with multiple outer holdings (instances)
					return Mono.error(new UnexpectedOuterHoldingException(instanceId));
				}
				else {
					return Mono.just(outerHoldings);
				}
			}
		} else {
			log.debug("Errors received from RTAC: {}", outerHoldings.getErrors());

			// DCB cannot know in advance whether an instance has any associated holdings / items
			// Holdings not being found for an instance is a false negative
			if (allErrorsAreHoldingsNotFound(outerHoldings)) {
				return Mono.just(outerHoldings);
			}
			else {
				log.error("Failed to get items for instance ID: {}, errors: {}",
					instanceId, outerHoldings.getErrors());

				return Mono.error(new FailedToGetItemsException(instanceId));
			}
		}
	}

	private static boolean hasNoErrors(OuterHoldings outerHoldings) {
		return outerHoldings.getErrors() == null || outerHoldings.getErrors().isEmpty();
	}

	private static boolean hasNoOuterHoldings(OuterHoldings outerHoldings) {
		return outerHoldings.getHoldings() == null || outerHoldings.getHoldings().isEmpty();
	}

	private static boolean hasMultipleOuterHoldings(OuterHoldings outerHoldings) {
		return outerHoldings.getHoldings().size() > 1;
	}

	private static boolean allErrorsAreHoldingsNotFound(OuterHoldings outerHoldings) {
		return outerHoldings.getErrors()
			.stream()
			.filter(error -> isNotEmpty(error.getMessage()))
			.allMatch(error -> error.getMessage().contains("Holdings not found for instance"));
	}

	private Flux<Item> mapHoldingsToItems(OuterHolding outerHoldings) {
		if (outerHoldings.getHoldings() == null) {
			return Flux.empty();
		}

		return Flux.fromStream(outerHoldings.getHoldings().stream())
			// When RTAC encounters a holdings record without any items
			// it includes the holdings record in the response
			// DCB is only interested in items and thus needs to differentiate between
			// holdings in the response that are items and those that are only holdings
			// For more information on the flow inside RTAC - https://github.com/folio-org/mod-rtac/blob/3e7f25445ff79b60690fa2025f3a426d9e57fd21/src/main/java/org/folio/mappers/FolioToRtacMapper.java#L112
			.filter(holdings -> itemStatuses.contains(holdings.getStatus()))
			.flatMap(holding -> mapHoldingToItem(holding, outerHoldings.getInstanceId()));
	}

	private Mono<Item> mapHoldingToItem(Holding holding, String instanceId) {
		return Mono.justOrEmpty(holding.getStatus())
			.flatMap(status -> itemStatusMapper.mapStatus(status, null,
				hostLms.getCode(), false, folioFallback()))
			.map(status -> Item.builder()
				.localId(holding.getId())
				.localBibId(instanceId)
				.barcode(holding.getBarcode())
				.callNumber(holding.getCallNumber())
				.status(status)
				.dueDate(holding.getDueDate())
				.localItemType(getValue(holding.getMaterialType(), MaterialType::getName))
				.location(Location.builder()
					.name(holding.getLocation())
					.code(holding.getLocationCode())
					.build())
				.deleted(false)
				.build());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByUsername(String localUsername) {
		return Mono.empty();
	}

	@Override
	public Mono<String> createPatron(Patron patron) {
		return Mono.empty();
	}

	@Override
	public Mono<String> createBib(Bib bib) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String barcode, String secret) {
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsHold> getHold(String holdId) {
		return Mono.empty();
	}

	@Override
	public Mono<HostLmsItem> getItem(String itemId) {
		return Mono.empty();
	}

	@Override
	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		return Mono.just("Dummy");
	}

	// WARNING We might need to make this accept a patronIdentity - as different

	// systems might take different ways to identify the patron

	@Override
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode) {
		return Mono.just("DUMMY");
	}

	@Override
	public Mono<String> deleteItem(String id) {
		return Mono.just("DUMMY");
	}

	@Override
	public Mono<String> deleteBib(String id) {
		return Mono.just("DUMMY");
	}

	private URI resolve(URI relativeURI) {
		return RelativeUriResolver.resolve(rootUri, relativeURI);
	}
}
