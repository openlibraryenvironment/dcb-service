package org.olf.dcb.core.interaction.folio;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.List;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriBuilder;
import reactor.core.publisher.Mono;

@Prototype
public class ConsortialFolioHostLmsClient implements HostLmsClient {
	private final HostLms hostLms;

	private final HttpClient httpClient;

	public ConsortialFolioHostLmsClient(@Parameter HostLms hostLms, @Parameter("client") HttpClient httpClient) {
		this.hostLms = hostLms;
		this.httpClient = httpClient;
	}

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(urlPropertyDefinition("base-url", "Base URL Of FOLIO System", TRUE));
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return Mono.empty();
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

	Mono<OuterHoldings> getHoldings() {
		final var uri = UriBuilder.of("https://fake-folio/rtac")
			.queryParam("instanceIds", "d68dfc67-a947-4b7a-9833-b71155d67579")
			// Full periodicals refers to items, without this parameter holdings will be returned instead of items
			.queryParam("fullPeriodicals", true)
			.build();

		final var request = HttpRequest.create(HttpMethod.GET, uri.toString())
			// Base 64 encoded API key
			.header("Authorization", "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9")
			// MUST explicitly accept JSON otherwise XML will be returned
			.accept(APPLICATION_JSON);

		return Mono.from(this.httpClient.retrieve(request, Argument.of(OuterHoldings.class)));
	}
}
