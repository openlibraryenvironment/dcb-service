package org.olf.dcb.core.interaction.folio;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

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
import reactor.core.publisher.Mono;

@Prototype
public class FolioLmsClient implements HostLmsClient {
	private final HostLms lms;

	public FolioLmsClient(@Parameter HostLms lms) {
		this.lms = lms;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(urlPropertyDefinition("base-url", "Base URL Of FOLIO System", TRUE));
	}

	@Override
	public Mono<List<Item>> getItems(String localBibId) {
		return Mono.empty();
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequest(PlaceHoldRequestParameters parameters) {
		return Mono.empty();
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
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
}
