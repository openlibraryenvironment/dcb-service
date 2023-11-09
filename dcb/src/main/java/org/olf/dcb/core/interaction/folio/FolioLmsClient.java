package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.interaction.oaipmh.OaiRecord;
import services.k_int.utils.UUIDUtils;

/**
 */
// @Prototype
public class FolioLmsClient implements HostLmsClient {

	private static final Logger log = LoggerFactory.getLogger(FolioLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:folio-lms";
	private final HostLms lms;
	// private final FolioApiClient client;

	public FolioLmsClient(@Parameter HostLms lms) {
		this.lms = lms;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(new HostLmsPropertyDefinition("base-url", "Base URL Of FOLIO System", Boolean.TRUE, "URL"));
	}

	@Override
	public Flux<Map<String, ?>> getAllBibData() {
		return Flux.empty();
	}

	@Override
	public Mono<List<Item>> getItems(String localBibId) {
		return Mono.empty();
	}

	// public Mono<String> createPatron(String uniqueId, String patronType) {
	// return Mono.empty();
	// }

	public Mono<Patron> patronFind(String tag, String content) {
		return Mono.empty();
	}

	// (localHoldId, localHoldStatus)
	@Override
	public Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType, String recordNumber,
			String pickupLocation, String note, String patronRequestId) {
		return Mono.empty();
	}

	@Override
	public boolean useTitleHold() {
		return false;
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return Mono.empty();
	}


	public UUID uuid5ForOAIResult(@NotNull final OaiRecord result) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + result.header().identifier();
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
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
