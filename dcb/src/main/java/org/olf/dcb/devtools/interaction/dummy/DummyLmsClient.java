package org.olf.dcb.devtools.interaction.dummy;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.*;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.devtools.interaction.dummy.responder.DummyResponse;
import org.olf.dcb.devtools.interaction.dummy.responder.LmsResponseResolver;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

import java.io.StringWriter;
import java.time.Instant;
import java.util.*;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.singletonList;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;


/**
 * This adapter exists to allow devs to run a fully functional local system
 * without configuring an external HostLMS.
 */
@Slf4j
@Singleton
public class DummyLmsClient implements HostLmsClient, IngestSource {
	private static final String UUID5_PREFIX = "ingest-source:dummy-lms";
	private final HostLms lms;
	private final ProcessStateService processStateService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final LmsResponseResolver lmsResponseResolver;
	private final static Map<String, DummySystemData> dummySystems = new HashMap<String, DummySystemData>();
	private String state;

	private static final String[] titleWords = { "Science", "Philosophy", "Music", "Art", "Nonsense", "Dialectic",
			"FlipDeBoop", "FlopLehoop", "Affdgerandunique", "Literacy" };

	public DummyLmsClient(@Parameter HostLms lms, ProcessStateService processStateService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		ReferenceValueMappingService referenceValueMappingService,
		LmsResponseResolver lmsResponseResolver) {

		this.lms = lms;
		this.processStateService = processStateService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.referenceValueMappingService = referenceValueMappingService;
		this.lmsResponseResolver = lmsResponseResolver;

		checkState();
	}

	/**
	 * A way to change the state of the Dummy LMS client based on configuration.
	 */
	void checkState() {
		String configState = (String) lms.getClientConfig().get("state");

		log.info("{}/{} state is {}", lms.getName(), lms.getCode(), configState);

		// By setting this value we allow methods to check the state before responding.
		if (configState != null) {
			state = configState;
		}
	}

	Mono<DummyResponse> getDummyResponse() {
		String role = (String) lms.getClientConfig().get("role");
		String responseType = (String) lms.getClientConfig().get("response-type");

		Optional<DummyResponse> response = lmsResponseResolver.resolve(state, role, responseType);
		if (response.isPresent()) {
			return Mono.just(response.get());
		} else {
			log.error("No dummy response found.");

			// This will cause tracking to fail with audit
			return Mono.empty();
		}
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		log.warn("Renewal is not currently implemented for {}", getHostLms().getName());

		return Mono.just(hostLmsRenewal);
	}

	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		log.warn("Update patron request is not currently implemented for {}", getHostLms().getName());
		return null;
	}

    @Override
	public HostLms getHostLms() {
		return lms;
	}

	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(urlPropertyDefinition("base-url", "Base URL Of Dummy System", TRUE));
	}

	@Override
	public Mono<List<Item>> getItems(BibRecord bib) {
		log.debug("getItems({})", bib);

		String localBibId = bib.getSourceRecordId();

		// All Dummy systems return holdings for each shelving location
		String shelvingLocations = (String) lms.getClientConfig().get("shelving-locations");

		if (shelvingLocations != null) {
			int n = 0;
			List<Item> result_items = new ArrayList<>();
			String[] locs = shelvingLocations.split(",");
			for (String s : locs) {
				result_items.add(
					Item.builder()
						.localId(localBibId + "-i" + n)
						.localBibId(localBibId)
						.status(new ItemStatus(ItemStatusCode.AVAILABLE))
						.location(org.olf.dcb.core.model.Location.builder()
							.code(s)
							.name(s)
							.build())
						.barcode(localBibId + "-i" + n)
						.callNumber("CN-" + localBibId)
						.holdCount(0)
						.localItemType("Books/Monographs")
						.localItemTypeCode("BKM")
						// Real host lms adapters use mappings to convert item types into canonical codes CIRC, CIRCAV and NONCIRC
						.canonicalItemType("CIRC")
						.deleted(false)
						.suppressed(false)
						.build());
				n++;
			}

			return Flux.fromIterable(result_items)
				.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()))
				.collectList()
				.doOnSuccess(items -> log.debug("Found {} items for bib {}", items, localBibId));
		}

		return Mono.empty();
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		return referenceValueMappingService.findMapping("patronType", "DCB",
				canonicalPatronType, getHostLmsCode())
			.map(ReferenceValueMapping::getToValue);
	}

	@Override
	public Mono<String> findCanonicalPatronType(String localPatronType, String localId) {
    String hostLmsCode = getHostLmsCode();
    if (localPatronType == null) {
      return Mono.empty();
    }
    
    return referenceValueMappingService.findMapping("patronType",
        hostLmsCode, localPatronType, "patronType", "DCB")
      .map(ReferenceValueMapping::getToValue)
      .switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
        "Unable to map patron type \"" + localPatronType + "\" on Host LMS: \"" + hostLmsCode + "\" to canonical value",
        hostLmsCode, localPatronType)));
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		log.info("getPatronByLocalId({})", localPatronId);
		Patron result = Patron.builder().localId(List.of(localPatronId)).localNames(List.of("Dummy Name"))
				.localBarcodes(List.of("66635556635")).uniqueIds(List.of("994746466")).localPatronType("STD")
				.localHomeLibraryCode("TR").isBlocked(false).build();

		return Mono.just(result);
	}

	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		log.info("getPatronByIdentifier({})", id);
		Patron result = Patron.builder().localId(List.of(id)).localNames(List.of("Dummy Name"))
			.localBarcodes(List.of("66635556635")).uniqueIds(List.of("994746466")).localPatronType("STD")
			.localHomeLibraryCode("TR").build();
		return Mono.just(result);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {
		log.info("getPatronByUsername({})", username);
		Patron result = Patron.builder().localId(List.of(username)).localNames(List.of("Dummy Name"))
				.localBarcodes(List.of("66635556635")).uniqueIds(List.of("994746466")).localPatronType("STD")
				.localHomeLibraryCode("TR").build();
		return Mono.just(result);
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(TRUE);
	}

	public UUID uuid5ForDummyRecord(@NotNull final String record_id) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + record_id;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		log.info("findVirtualPatron({})", patron);

		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);
		final var barcode = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineHomeIdentityBarcode);

		// Pretend that we already know everything about all patrons, this will skip the
		// patron create step
		// when placing a request - at least for now.
		return Mono.just(Patron.builder()
			.localId(List.of(uniqueId))
			.localNames(List.of("Dummy Name"))
			.localBarcodes(List.of(barcode))
			.uniqueIds(List.of(uniqueId))
			.localPatronType("STD")
			.localHomeLibraryCode("TR")
			.build());
	}

	public Mono<String> createPatron(Patron patron) {
		log.info("Create patron {}", patron);
		String newPatronUUID = UUID.randomUUID().toString();
		return Mono.just(newPatronUUID);
	}

	public Mono<String> createBib(Bib bib) {
		log.info("Create bib {}", bib);
		String newBibUUID = UUID.randomUUID().toString();
		return Mono.just(newBibUUID);
	}

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		log.info("Cancel hold request {}", parameters);
		return Mono.just(parameters.getLocalRequestId());
	}

	public Mono<Patron> updatePatron(String localId, String patronType) {
		log.info("Update patron {},{}", localId, patronType);
		return Mono.just(Patron.builder()
			.localId(singletonList(localId))
			.localPatronType(patronType)
			.build());
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		// TODO: needs implementing
		log.info("patronAuth({},{},...", authProfile, patronPrinciple);

		// Pretend that we already know everything about all patrons, this will skip the
		// patron create step
		// when placing a request - at least for now.
		return Mono.just(Patron.builder().localId(List.of(patronPrinciple)).localNames(List.of("Dummy Name"))
				.localBarcodes(List.of(patronPrinciple)).uniqueIds(List.of(patronPrinciple)).localPatronType("STD")
				.localHomeLibraryCode("TR").build());
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		log.debug("createItem({})", cic);
		return Mono.just(HostLmsItem.builder().localId(cic.getBibId()).status(HostLmsItem.ITEM_AVAILABLE)
				.barcode(cic.getBarcode()).build());
	}

	public Mono<HostLmsRequest> getRequest(String localRequestId) {
		log.debug("getRequest({})", localRequestId);

		if (state != null) {
			return getDummyResponse()
				.map(dummyResponse -> {
					final var responseData = dummyResponse.getData();
					final var requestStatus = (String) responseData.get("requestStatus");
					final var rawRequestStatus = (String) responseData.get("rawRequestStatus");
					return HostLmsRequest.builder()
						.localId(localRequestId)
						.status(requestStatus)
						.rawStatus(rawRequestStatus)
						.build();
				});
		}

		DummyRequestData drd = getDummyRequest(localRequestId);

    if ( drd != null ) {
      log.debug("Looked up request {}",drd);
			return Mono.just( HostLmsRequest.builder()
				.localId(localRequestId)
				.status("CONFIRMED")
				.requestedItemId(drd.getInitialParameters().getLocalItemId())
				.requestedItemBarcode(drd.getInitialParameters().getLocalItemBarcode())
				.build());
    }

    log.warn("unable to locate request {}",localRequestId);
		return Mono.empty();

	}

	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {

		final var localItemId = hostLmsItem.getLocalId();
		final var localRequestId = hostLmsItem.getLocalRequestId();

		log.debug("getItem(localItemId:{}, localRequestId:{})", localItemId, localRequestId);

		if (state != null) {
			return getDummyResponse()
				.map(dummyResponse -> {
					final var responseData = dummyResponse.getData();
					final var itemStatus = (String) responseData.get("itemStatus");
					final var rawItemStatus = (String) responseData.get("rawItemStatus");

					return HostLmsItem.builder()
						.localId(localItemId)
						.status(itemStatus)
						.rawStatus(rawItemStatus)
						.build();
				});
		}

		DummyRequestData drd = getDummyRequest(localRequestId);

		if ( drd != null ) {
			log.debug("Looked up request {}",drd);
		}
		else {
			log.warn("unable to locate request {}",localRequestId);
		}

		return Mono.empty();
	}

	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs, String localRequestId) {
		log.info("updateItemStatus({},{})", itemId, crs);
		return Mono.just("Dummy");
	}

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkout) {
		final var itemId = checkout.getItemId();
		final var patronBarcode = checkout.getPatronBarcode();

		log.info("checkOutItemToPatron({},{})", itemId, patronBarcode);
		return Mono.just("DUMMY");
	}

	public Mono<String> deleteItem(String id) {
		return Mono.just("DUMMY");
	}

	public Mono<String> deleteBib(String id) {
		return Mono.just("DUMMY");
	}

  public Mono<String> deletePatron(String id) {
    log.info("Delete patron is not currently implemented");
    return Mono.empty();
  }

	@Override
	public Publisher<IngestRecord> apply(@Nullable Instant changedSince, Publisher<String> terminator) {

		int pageSize = 100;
		return getInitialState(lms.getId(), "ingest")
				.flatMap(state -> Mono.zip(Mono.just(state.toBuilder().build()), fetchPage(state, pageSize)))
				.expand(TupleUtils.function((state, bibs) -> {

					int target_record_count = ((Integer) (lms.getClientConfig().get("num-records-to-generate"))).intValue();
					int records_generated_so_far = Integer.valueOf(state.storred_state.get("num_generated").toString())
							.intValue();
					records_generated_so_far += bibs.size();
					state.storred_state.put("num_generated", "" + records_generated_so_far);

					state.possiblyMore = records_generated_so_far < target_record_count;

					// Increment the offset for the next fetch
					state.offset += bibs.size();

					// If we have exhausted the currently cached page, and we are at the end,
					// terminate.
					if (!state.possiblyMore) {
						log.info("{} ingest Terminating cleanly - run out of bib results - new timestamp is {}", lms.getName(),
								state.request_start_time);
						return Mono.empty();

					} else {
						log.info("Exhausted current page from {} , prep next", lms.getName());
					}

					// Create a new mono that first saves the state and then uses it to fetch
					// another page.
					return Mono.just(state.toBuilder().build()) // toBuilder().build() should copy the object.
							.zipWhen(updatedState -> fetchPage(updatedState, pageSize));
				}))
				
				.takeUntilOther( Mono.from(terminator)
					.doOnNext( reason -> log.info("Ejecting from collect sequence. Reason: {}", reason) ))
				
				.concatMap(TupleUtils.function((state, page) -> {
					return Flux.fromIterable(page)
							// Concatenate with the state so we can propagate signals from the save
							// operation.
							.concatWith(Mono.defer(() -> saveState(state)).flatMap(_s -> {
								log.debug("Updating state...");
								return Mono.empty();
							}))

							.doOnComplete(() -> log.debug("Consumed {} items", page.size()));
				}));

	}

	private Mono<List<IngestRecord>> fetchPage(PublisherState state, int limit) {

		log.debug("fetchPage... {},{},{}", state, limit);
		log.debug("fetchPage... config={}", lms.getClientConfig());

		int target = ((Integer) (lms.getClientConfig().get("num-records-to-generate"))).intValue();
		int records_generated_so_far = Integer.valueOf(state.storred_state.get("num_generated").toString()).intValue();

		log.debug("target: {}, current:{}", target, records_generated_so_far);

		List<IngestRecord> result = new ArrayList<>();

		if (records_generated_so_far == 0) {
			log.debug("Bootstrap a dummy collection with some reasonable records");
			generateRealRecords(result);
		}

		// Then bulk out the collection with generated records
		for (int n = result.size(); ((n < limit) && ((records_generated_so_far + n) < target)); n++) {
			String str_record_id = "" + (1000000 + (n + records_generated_so_far));
			result.add(createDummyBookRecord(str_record_id, str_record_id, generateTitle(str_record_id)));
		}

		return Mono.just(result);
	}

	private void generateRealRecords(List<IngestRecord> result) {
		log.debug("Adding in real records");
		result.add(createDummyBookRecord("0000001", "978-0471948391", "Brain of the Firm 2e: 10 (Classic Beer Series)"));
	}

	private IngestRecord createDummyBookRecord(String str_record_id, String isbn13, String title) {
		UUID rec_uuid = uuid5ForDummyRecord(str_record_id);

		Map<String, Object> canonicalMetadata = new HashMap<>();
		canonicalMetadata.put("title", title);
		var subjects = List.of(Map.of("label", "Cybernetics"), Map.of("label", "General Systems Theory"));
		canonicalMetadata.put("subjects", subjects);
		var agents = List.of(Map.of("label", "Beers, Stafford", "subtype", "author"));
		canonicalMetadata.put("agents", agents);

		Set<Identifier> identifiers = new HashSet<>();
		identifiers.add(Identifier.builder().namespace("isbn").value(isbn13).build());

		return IngestRecord.builder().uuid(rec_uuid).sourceSystem(lms).sourceRecordId(str_record_id)
				.identifiers(identifiers).canonicalMetadata(canonicalMetadata).derivedType("Books").build();
	}

	@Override
	public Publisher<ConfigurationRecord> getConfigStream() {
		return Mono.empty();
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtSupplyingAgency({})", parameters);
		return placeHoldRequest(parameters)
			.map(localRequest -> LocalRequest.builder()
				.localId(localRequest.getLocalId())
				// to help trigger HandleSupplierRequestConfirmed without tracking
				.localStatus(HostLmsRequest.HOLD_CONFIRMED)
				.build());
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtPickupAgency({})", parameters);
		return placeHoldRequest(parameters);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtLocalAgency({})", parameters);
		return placeHoldRequest(parameters);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		log.debug("placeHoldRequestAtBorrowingAgency({})", parameters);
		return placeHoldRequest(parameters);
	}

	private Mono<LocalRequest> placeHoldRequest(
		PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequest({})", parameters);

		UUID generated_request_id = UUID.randomUUID();
		String id_as_string = generated_request_id.toString();

		DummyRequestData drd = DummyRequestData.builder()
			.initialParameters(parameters)
			.requestId(id_as_string)
			.requestStatus("HELD")
			.build();

		DummyItemData did = DummyItemData.builder()
    	.itemId(parameters.getLocalItemId())
    	.itemStatus("AVAILABLE")
    	.itemBarcode(parameters.getLocalItemBarcode())
			.build();

		log.debug("Store {} as {} in dummyRequestStore",drd,id_as_string);
		putDummyRequest(id_as_string, drd);
		putDummyItem(parameters.getLocalItemId(), did);

		return Mono.just(LocalRequest.builder()
			.localId(id_as_string)
			.localStatus("HELD")
			.build());
	}

	private String generateTitle(String recordId) {
		StringWriter sw = new StringWriter();

		for (int i = 0; i < recordId.length(); i++) {
			if (i > 0)
				sw.write(" ");
			// conver the char at position i into an integer of that value 0-9
			int digit = recordId.charAt(i) - 48;
			sw.write(titleWords[digit]);
		}
		return sw.toString();
	}

	/**
	 * Use the ProcessStateRepository to get the current state for
	 * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
	 * just create a new empty map transform that data into the PublisherState class
	 * above ^^ THIS SHOULD REALLY MOVE TO A SHARED SUPERCLASS
	 */
	public Mono<PublisherState> getInitialState(UUID context, String process) {
		return processStateService.getStateMap(context, process).defaultIfEmpty(new HashMap<>()).map(current_state -> {
			PublisherState generator_state = new PublisherState(current_state);
			if (current_state.get("num_generated") == null) {
				current_state.put("num_generated", Long.valueOf(0));
			}
			generator_state.request_start_time = System.currentTimeMillis();
			return generator_state;
		});
	}

	@Transactional(value = TxType.REQUIRES_NEW)
	protected Mono<PublisherState> saveState(PublisherState state) {
		log.debug("Update state {} - {}", state, lms.getName());

		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state))
			.thenReturn(state);
	}

	@Override
	public ProcessStateService getProcessStateService() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PublisherState mapToPublisherState(Map<String, Object> mapData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Publisher<PublisherState> saveState(UUID context, String process, PublisherState state) {
		// TODO Auto-generated method stub
		return null;
	}

  public Mono<Boolean> supplierPreflight(String borrowingAgencyCode, String supplyingAgencyCode, String canonicalItemType, String canonicalPatronType) {
    return Mono.just(Boolean.TRUE);
  }

  @Override
  public Mono<String> deleteHold(String id) {
    log.info("Delete hold is not currently implemented for Dummy");
    return Mono.just("OK");
  }

	public DummySystemData getDummySystem() {
    DummySystemData dsd = dummySystems.get(getHostLmsCode());
    if ( dsd == null ) {
      dsd = new DummySystemData();
      dummySystems.put(getHostLmsCode(), dsd);
    }
		return dsd;
	}

	public DummyRequestData getDummyRequest(String requestId) {
		DummySystemData dsd = getDummySystem();
		return dsd.getRequests().get(requestId);
	}

  public DummyItemData getDummyItem(String itemId) {
		DummySystemData dsd = getDummySystem();
    return dsd.getItems().get(itemId);
  }

	public void putDummyRequest(String request_id, DummyRequestData drd) {
		DummySystemData dsd = getDummySystem();
		dsd.getRequests().put(request_id,drd);
	}

	public void putDummyItem(String item_id, DummyItemData did) {
		DummySystemData dsd = getDummySystem();
		dsd.getItems().put(item_id,did);
	}

	@Data
	@AllArgsConstructor
	@Serdeable
	@NoArgsConstructor
  static class DummySystemData {
  	public Map<String, DummyRequestData> requests = new HashMap<String, DummyRequestData>();
  	public Map<String, DummyItemData> items = new HashMap<String, DummyItemData>();
  }

  @Data
  @Builder
  @Serdeable
  static class DummyRequestData {
	  public PlaceHoldRequestParameters initialParameters;
	  public String requestId;
	  public String requestStatus;
  }

  @Data
  @Builder
  @Serdeable
  static class DummyItemData {
    public String itemId;
    public String itemStatus;
    public String itemBarcode;
  }

	@Override
	public @NonNull String getClientId() {
		return "DUMMY_DEV_CLIENT";
	} 

	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		return Mono.empty();
	}

}
