package org.olf.dcb.core.interaction.folio;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static services.k_int.utils.MapUtils.getAsOptionalString;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.interaction.RelativeUriResolver;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.Location;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class ConsortialFolioHostLmsClient implements HostLmsClient {
	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("base-url", "Base URL of the FOLIO system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this FOLIO tenant", TRUE);

	private final HostLms hostLms;

	private final HttpClient httpClient;

	private final String apiKey;
	private final URI rootUri;

	public ConsortialFolioHostLmsClient(@Parameter HostLms hostLms, @Parameter("client") HttpClient httpClient) {
		this.hostLms = hostLms;
		this.httpClient = httpClient;

		this.apiKey = getRequiredConfigValue(hostLms, API_KEY_SETTING);
		this.rootUri = UriBuilder.of(getRequiredConfigValue(hostLms, BASE_URL_SETTING)).build();
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
			.flatMapIterable(OuterHoldings::getHoldings)
			.flatMap(this::mapHoldingToItem)
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

		return Mono.from(this.httpClient.retrieve(request, Argument.of(OuterHoldings.class)));
	}

	private Flux<Item> mapHoldingToItem(OuterHolding outerHoldings) {
		return Flux.fromStream(outerHoldings.getHoldings().stream()
			.map(holding -> Item.builder()
				.localId(holding.getId())
				.localBibId(outerHoldings.getInstanceId())
				.callNumber(holding.getCallNumber())
				.status(new ItemStatus(AVAILABLE))
				.localItemType(holding.getPermanentLoanType())
				.location(Location.builder()
					.name(holding.getLocation())
					.build())
			.build()));
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

	private static String getRequiredConfigValue(HostLms hostLms, HostLmsPropertyDefinition setting) {
		return getRequiredConfigValue(hostLms.getClientConfig(), setting.getName());
	}

	private static String getRequiredConfigValue(Map<String, Object> clientConfig, String key) {
		final var optionalConfigValue = getAsOptionalString(clientConfig, key);

		if (optionalConfigValue.isEmpty()) {
			throw new IllegalArgumentException("Missing required configuration property: \"" + key + "\"");
		}

		return optionalConfigValue.get();
	}
}
