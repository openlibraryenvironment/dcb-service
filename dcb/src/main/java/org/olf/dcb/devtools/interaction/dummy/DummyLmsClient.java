package org.olf.dcb.devtools.interaction.dummy;

import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsHold;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.utils.MapUtils;
import services.k_int.utils.UUIDUtils;

/**
 * This adapter exists to allow devs to run a fully functional local system
 * without configuring an external HostLMS.
 */
// public class DummyLmsClient implements HostLmsClient, MarcIngestSource<OaiListRecordsMarcXML> {
@Prototype
public class DummyLmsClient implements HostLmsClient, IngestSource {

	private static final Logger log = LoggerFactory.getLogger(DummyLmsClient.class);

	private static final String UUID5_PREFIX = "ingest-source:dummy-lms";
	private final HostLms lms;
	private final ProcessStateService processStateService;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final AgencyRepository agencyRepository;

	private static final String[] titleWords = { "Science", "Philosophy", "Music", "Art", "Nonsense", "Dialectic",
			"Curiosity", "Reading", "Numeracy", "Literacy" };

	public DummyLmsClient(@Parameter HostLms lms,
			ProcessStateService processStateService,
			ReferenceValueMappingRepository referenceValueMappingRepository, AgencyRepository agencyRepository) {
		this.lms = lms;
		this.processStateService = processStateService;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.agencyRepository = agencyRepository;
	}

	@Override
	public HostLms getHostLms() {
		return lms;
	}

	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(new HostLmsPropertyDefinition("base-url", "Base URL Of Dummy System", Boolean.TRUE, "URL"));
	}

	public Flux<Map<String, ?>> getAllBibData() {
		return Flux.empty();
	}

	public Mono<List<Item>> getItems(String localBibId) {
		// All Dummy systems return holdings for each shelving location
		log.debug("getItemsByBibId({},{})", localBibId, lms.getCode());
		String shelvingLocations = (String) lms.getClientConfig().get("shelving-locations");
		if (shelvingLocations != null) {
			int n = 0;
			List<Item> result_items = new ArrayList<>();
			String[] locs = shelvingLocations.split(",");
			for (String s : locs) {
				result_items.add(Item.builder().id(localBibId + "-i" + n).localBibId(localBibId)
						.status(new ItemStatus(ItemStatusCode.AVAILABLE)).hostLmsCode(lms.getCode())
						// .dueDate(parsedDueDate)
						.location(org.olf.dcb.core.model.Location.builder().code(s).name(s).build()).barcode(localBibId + "-i" + n)
						.callNumber("CN-" + localBibId).holdCount(0).localItemType("Books/Monographs").localItemTypeCode("BKM")
						.deleted(false).suppressed(false).build());
				n++;
			}

			return Flux.fromIterable(result_items)
					.flatMap(
							item -> enrichItemAgencyFromShelvingLocation(item, item.getHostLmsCode(), item.getLocation().getCode()))
					.collectList();
		}

		return Mono.empty();
	}

	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		log.info("getPatronByLocalId({})", localPatronId);
		Patron result = Patron.builder().localId(List.of(localPatronId)).localNames(List.of("Dummy Name"))
				.localBarcodes(List.of("66635556635")).uniqueIds(List.of("994746466")).localPatronType("STD")
				.localHomeLibraryCode("TR").build();

		return Mono.just(result);
		// return Mono.empty();
	}

	@Override
	public String getName() {
		return lms.getName();
	}

	@Override
	public boolean isEnabled() {
		return MapUtils.getAsOptionalString(lms.getClientConfig(), "ingest").map(StringUtils::isTrue).orElse(Boolean.TRUE);
	}

	public UUID uuid5ForDummyRecord(@NotNull final String record_id) {
		final String concat = UUID5_PREFIX + ":" + lms.getCode() + ":" + record_id;
		return UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, concat);
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

	public Mono<Patron> updatePatron(String localId, String patronType) {
		log.info("Update patron {},{}", localId, patronType);
		return Mono.empty();
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

	public Mono<HostLmsHold> getHold(String holdId) {
		log.debug("getHold({})", holdId);
		return Mono.empty();
	}

	public Mono<HostLmsItem> getItem(String itemId) {
		log.debug("getItem({})", itemId);
		return Mono.empty();
	}

	public Mono<String> updateItemStatus(String itemId, CanonicalItemState crs) {
		log.info("updateItemStatus({},{})", itemId, crs);
		return Mono.just("Dummy");
	}

	// WARNING We might need to make this accept a patronIdentity - as different
	// systems might take different ways to identify the patron
	public Mono<String> checkOutItemToPatron(String itemId, String patronBarcode) {
		log.info("checkOutItemToPatron({},{})", itemId, patronBarcode);
		return Mono.just("DUMMY");
	}

	public Mono<String> deleteItem(String id) {
		return Mono.just("DUMMY");
	}

	public Mono<String> deleteBib(String id) {
		return Mono.just("DUMMY");
	}

	@Override
	public Publisher<IngestRecord> apply(@Nullable Instant changedSince) {

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
				})).concatMap(TupleUtils.function((state, page) -> {
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
	public Mono<Tuple2<String, String>> placeHoldRequest(String id, String recordType, String recordNumber,
			String pickupLocation, String note, String patronRequestId) {
		log.info("placeHoldRequest({},{},{},{},{})", id, recordType, recordNumber, pickupLocation, note, patronRequestId);

		// (localHoldId, localHoldStatus)
		// return Mono.empty();
		return Mono.just(Tuples.of(UUID.randomUUID().toString(), "HELD"));
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

		return Mono.from(processStateService.updateState(lms.getId(), "ingest", state.storred_state)).thenReturn(state);
	}

	Mono<org.olf.dcb.core.model.Item> enrichItemAgencyFromShelvingLocation(org.olf.dcb.core.model.Item item,
			String hostSystem, String itemShelvingLocation) {
		// log.debug("map shelving location to agency
		// {}:\"{}\"",hostSystem,itemShelvingLocation);
		return Mono
				.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
						"ShelvingLocation", hostSystem, itemShelvingLocation, "AGENCY", "DCB"))
				.flatMap(rvm -> Mono.from(agencyRepository.findOneByCode(rvm.getToValue()))).map(dataAgency -> {
					item.setAgencyCode(dataAgency.getCode());
					item.setAgencyDescription(dataAgency.getName());
					return item;
				}).defaultIfEmpty(item);
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

}
