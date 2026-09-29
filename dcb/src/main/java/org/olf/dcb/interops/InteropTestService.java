package org.olf.dcb.interops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import java.time.Duration;
import java.time.Instant;

import io.micronaut.data.model.Pageable;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.api.ImplementationToolsController;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.ReferenceValueMapping;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service to test interoperability with host library management systems.
 * Handles creating, testing, and cleaning up test data in integrated systems.
 */
@Slf4j
@Singleton
public class InteropTestService {

	private static final String TEST_ITEM_LOCATION_CODE = "GTLSC";
	private static final String TEST_PICKUP_LOCATION_CODE = "GTMAIN";
	private static final String TEST_LOCAL_PATRON_TYPE = "UNDERGRAD";
	private static final String TEST_UPDATE_LOCAL_PATRON_TYPE = "LOCATE";
	private static final Duration SYNC_DELAY = Duration.ofSeconds(10);

	private final HostLmsService hostLmsService;
	private final BibRecordService bibRecordService;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;

	public InteropTestService(HostLmsService hostLmsService,
			BibRecordService bibRecordService,
			ReferenceValueMappingRepository referenceValueMappingRepository) {
		this.hostLmsService = hostLmsService;
		this.bibRecordService = bibRecordService;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	/**
	 * Ping a host LMS system to check connectivity.
	 *
	 * @param code The host LMS code
	 * @return the ping response
	 */
	public Mono<PingResponse> ping(String code) {
		return hostLmsService.getClientFor(code)
			.flatMap(HostLmsClient::ping);
	}

	public Mono<List<Item>> findFirstMatchingItemList(String systemCode,
		ImplementationToolsController.ItemQuery q,
		int pageSize,
		Duration timeout) {

		return hostLmsService.getClientFor(systemCode)
			.flatMap(client ->
				bibRecordService.getPageOfBibs(Pageable.from(0, pageSize))
					.flatMap(first -> {
						long total = first.getTotalSize();
						if (total == 0) return Mono.error(new IllegalStateException("No bib records available"));
						long totalPages = (total + pageSize - 1) / pageSize;
						int start = ThreadLocalRandom.current().nextInt(Math.toIntExact(totalPages));

						Flux<Integer> pageOrder = Flux.concat(
							Flux.range(start, (int) (totalPages - start)),
							Flux.range(0, start)
						);

						return pageOrder
							// iterate bibs once, sequentially
							.concatMap(pageIdx ->
									bibRecordService.getPageOfBibs(Pageable.from(pageIdx, pageSize))
										.flatMapMany(p -> Flux.fromIterable(p.getContent())),
								1)
							// for each bib, fetch its items and KEEP the whole list if any item matches
							.concatMap(bib ->
								client.getItems(bib)	// Mono<List<Item>>
										.onErrorResume(e -> {
											log.warn("getItems failed for bib {}: {}", bib.getId(), e.toString());
											return Mono.just(List.<Item>of());
										})
										.filter(items -> items.stream().anyMatch(item -> matches(item, q))), // emit list only if a match exists
								1)
							.next(); // take the first list whose bib had a matching item
					})
			)
			.timeout(timeout, Mono.error(new TimeoutException("Timed out after " + timeout.toSeconds() + "s")));
	}

	private boolean matches(Item it, ImplementationToolsController.ItemQuery q) {
		if (q.available()      != null && it.isAvailable() != q.available()) return false;
		if (q.requestable()    != null && !q.requestable().equals(Boolean.TRUE.equals(it.getIsRequestable()))) return false;
		if (q.locationCode()   != null && !q.locationCode().equalsIgnoreCase(it.getLocationCode())) return false;
		if (q.canonicalItemType()!= null && !q.canonicalItemType().equalsIgnoreCase(it.getCanonicalItemType())) return false;
		if (q.itemTypeCode()   != null && !q.itemTypeCode().equalsIgnoreCase(it.getLocalItemTypeCode())) return false;
		if (q.minHoldCount()   != null && (it.getHoldCount() == null || it.getHoldCount() < q.minHoldCount())) return false;
		if (q.maxHoldCount()   != null && (it.getHoldCount() != null && it.getHoldCount() > q.maxHoldCount())) return false;
		if (q.notSuppressed()  != null && (q.notSuppressed() ? !it.notSuppressed() : it.notSuppressed())) return false;
		if (q.notDeleted()     != null && (q.notDeleted() ? !it.notDeleted() : it.notDeleted())) return false;
		if (q.agencyCode()     != null && (it.getAgencyCode() == null || !q.agencyCode().equalsIgnoreCase(it.getAgencyCode()))) return false;

		// DCB-2005 - to confirm that we are mapping call numbers correctly
		if (q.callNumberExists() != null) {
			boolean present = !isBlank(it.getCallNumber());
			if (q.callNumberExists() != present) return false;
		}

		return true;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/**
	 * Run a comprehensive test suite against the specified host LMS.
	 * Tests include patron management, bibliographic record operations,
	 * item operations, and hold placement.
	 *
	 * @param code The host LMS code to test
	 * @param forceCleanup If true, cleanup steps will run even after test failures
	 * @return test results for each step
	 */
	public Flux<InteropTestResult> testIls(String code, boolean forceCleanup) {
		log.debug("Testing ILS with code: {}, forceCleanup: {}", code, forceCleanup);

		List<Function<InteropTestContext, Mono<InteropTestResult>>> testSteps = List.of(
			this::patronCreateTest,
			this::patronValidateTest,
//			this::findVirtualPatronTest,
//			this::updatePatronTest,
			this::createBibTest,
			this::createItemTest,
			this::getItemsTest,
			this::getItemTest,
			this::placeHoldTest,
//			this::checkoutTest,
			this::getHoldTest
		);

		List<Function<InteropTestContext, Mono<InteropTestResult>>> cleanupSteps = List.of(
			this::deleteHoldTest,
			this::deleteItemTest,
			this::deleteBibTest,
			this::patronDeleteTest
		);

		return hostLmsService.getClientFor(code)
			.map(hostLms -> InteropTestContext.builder()
				.hostLms(hostLms)
				.values(new HashMap<>())
				.success(true)
				.build())
			.flatMapMany(testCtx -> {
				Flux<InteropTestResult> testFlux = Flux.fromIterable(testSteps)
					.concatMap(step -> executeTestStep(step, testCtx))
					.takeUntil(result -> !testCtx.isSuccess() && !forceCleanup);

				if (forceCleanup) {
					return testFlux.concatWith(Flux.fromIterable(cleanupSteps)
						.concatMap(step -> executeCleanupStep(step, testCtx)));
				} else {
					return testFlux;
				}
			});
	}

	/**
	 * Execute a test step with error handling.
	 * If a step fails, mark the context as failed but continue with the test.
	 */
	private Mono<InteropTestResult> executeTestStep(
		Function<InteropTestContext, Mono<InteropTestResult>> step,
		InteropTestContext testCtx) {

		return step.apply(testCtx)
			.onErrorResume(error -> {
				testCtx.setSuccess(false);
				String stepName = step.getClass().getSimpleName().toString();
				log.error("Error in test step {}: {}", stepName, error.getMessage(), error);

				return createErrorResult("setup", stepName, error);
			});
	}

	/**
	 * Execute a cleanup step with error handling.
	 * Cleanup steps always run regardless of previous test success.
	 */
	private Mono<InteropTestResult> executeCleanupStep(
		Function<InteropTestContext, Mono<InteropTestResult>> step,
		InteropTestContext testCtx) {


		return step.apply(testCtx)
			.onErrorResume(error -> {
				String stepName = step.getClass().getSimpleName();
				log.error("Error in cleanup step {}: {}", stepName, error.getMessage(), error);

				return createErrorResult("cleanup", stepName, error);
			});
	}

	private Mono<InteropTestResult> createErrorResult(String stage, String step, Throwable error) {
		return Mono.just(InteropTestResult.builder()
			.stage(stage)
			.step(step)
			.result("ERROR")
			.note("Failed with error: " + "[" + error.getClass().getSimpleName() + "] " + error.getMessage())
			.build());
	}

	/**
	 * Create a success result with standard format.
	 */
	private InteropTestResult createSuccessResult(String stage, String step, String message, HostLmsClient hostLms) {
		return InteropTestResult.builder()
			.stage(stage)
			.step(step)
			.result("OK")
			.note(message + " from " + hostLms.getHostLmsCode() + " at " + Instant.now())
			.build();
	}

	private InteropTestResult createNotRunResult(String stage, String step, String message, HostLmsClient hostLms) {
		return InteropTestResult.builder()
			.stage(stage)
			.step(step)
			.result("NOT-RUN")
			.note(message + " for " + hostLms.getHostLmsCode() + " at " + Instant.now())
			.build();
	}

	private Mono<InteropTestResult> placeHoldTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		String bibId = (String) testCtx.getValues().get("mms_id");
		String itemId = (String) testCtx.getValues().get("item_id");
		String holdingId = (String) testCtx.getValues().get("holding_id");
		String patronId = (String) testCtx.getValues().get("remotePatronId");
		String itemBarcode = (String) testCtx.getValues().get("item_barcode");

		if (anyNull(bibId, itemId, holdingId, patronId, itemBarcode)) {
			return Mono.just(createNotRunResult("setup", "place hold",
				"Missing required values for hold placement", hostLms));
		}

		Location pickupLocation = Location.builder().code(TEST_PICKUP_LOCATION_CODE).build();

		final var hold = PlaceHoldRequestParameters.builder()
			.localBibId(bibId)
			.localItemId(itemId)
			.localHoldingId(holdingId)
			.localPatronId(patronId)
			.pickupLocation(pickupLocation)
			.localItemBarcode(itemBarcode)
			.build();

		return hostLms.placeHoldRequestAtBorrowingAgency(hold)
			.map(localRequest -> {
				testCtx.getValues().put("hold_id", localRequest.getLocalId());
				return createSuccessResult("setup", "place hold",
					"placeHold returned " + localRequest, hostLms);
			});
	}

	private Mono<InteropTestResult> getItemsTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String bibId = (String) testCtx.getValues().get("mms_id");

		if (bibId == null) {
			return Mono.just(createNotRunResult("setup", "live availability",
				"Missing bibliographic ID", hostLms));
		}

		return Mono.delay(SYNC_DELAY)
			.flatMap(tick -> hostLms.getItems(BibRecord.builder().sourceRecordId(bibId).build()))
			.map(items -> createSuccessResult("setup", "live availability",
				"getItems returned " + items, hostLms));
	}

	private Mono<InteropTestResult> getItemTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		String bibId = (String) testCtx.getValues().get("mms_id");
		String itemId = (String) testCtx.getValues().get("item_id");
		String holdingId = (String) testCtx.getValues().get("holding_id");

		if (anyNull(bibId, itemId, holdingId)) {
			return Mono.just(createNotRunResult("setup", "item tracking",
				"Missing required item identifiers", hostLms));
		}

		final var item = HostLmsItem.builder()
			.localId(itemId)
			.holdingId(holdingId)
			.bibId(bibId)
			.build();

		return hostLms.getItem(item)
			.map(hostLmsItem -> {
				testCtx.getValues().put("item_barcode", hostLmsItem.getBarcode());
				return createSuccessResult("setup", "item tracking",
					"getItem returned " + hostLmsItem, hostLms);
			});
	}

	private Mono<InteropTestResult> deleteHoldTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		String patronId = (String) testCtx.getValues().get("remotePatronId");
		String holdId = (String) testCtx.getValues().get("hold_id");

		if (anyNull(patronId, holdId)) {
			return Mono.just(createNotRunResult("cleanup", "hold",
				"Missing required hold identifiers. Unable to test hold delete", hostLms));
		}

		final var deleteCommand = DeleteCommand.builder()
			.patronId(patronId)
			.requestId(holdId)
			.build();

		return hostLms.deleteHold(deleteCommand)
			.map(result -> createSuccessResult("cleanup", "hold",
				"delete hold returned " + result, hostLms));
	}

	private Mono<InteropTestResult> getHoldTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String patronId = (String) testCtx.getValues().get("remotePatronId");
		String holdId = (String) testCtx.getValues().get("hold_id");

		if (anyNull(patronId, holdId)) {
			return Mono.just(createNotRunResult("tracking", "hold",
				"Missing required hold identifiers. Unable to test hold tracking", hostLms));
		}

		final var request = HostLmsRequest.builder().localPatronId(patronId).localId(holdId).build();

		return Mono.delay(SYNC_DELAY)
			.flatMap(tick -> hostLms.getRequest(request))
			.map(result -> createSuccessResult("tracking", "hold",
				"track hold returned " + result, hostLms));
	}

	private Mono<InteropTestResult> deleteItemTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		String itemId = (String) testCtx.getValues().get("item_id");
		String bibId = (String) testCtx.getValues().get("mms_id");
		String holdingId = (String) testCtx.getValues().get("holding_id");

		if (anyNull(itemId, bibId, holdingId)) {
			return Mono.just(createNotRunResult("cleanup", "item",
				"Missing required item identifiers. Unable to test item delete", hostLms));
		}

		final var deleteCommand = DeleteCommand.builder()
			.itemId(itemId)
			.holdingsId(holdingId)
			.bibId(bibId)
			.build();

		return hostLms.deleteItem(deleteCommand)
			.map(result -> createSuccessResult("cleanup", "item",
				"delete item returned " + result, hostLms));
	}

	private Mono<InteropTestResult> createItemTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String bibId = (String) testCtx.getValues().get("mms_id");

		if (bibId == null) {
			return Mono.just(createNotRunResult("setup", "create item",
				"Missing bibliographic ID required for item creation", hostLms));
		}

		String barcode = "TEST-" + Instant.now();

		return hostLms.createItem(CreateItemCommand.builder()
				.bibId(bibId)
				.locationCode(TEST_ITEM_LOCATION_CODE)
				.barcode(barcode)
				.build())
			.map(item -> {
				testCtx.getValues().put("item_id", item.getLocalId());
				testCtx.getValues().put("holding_id", item.getHoldingId());
				return createSuccessResult("setup", "create item",
					"Created item with ID " + item.getLocalId(), hostLms);
			});
	}

	private Mono<InteropTestResult> deleteBibTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String bibId = (String) testCtx.getValues().get("mms_id");

		if (bibId == null) {
			return Mono.just(createNotRunResult("cleanup", "bib",
				"There was no mms_id in context. Unable to test bib delete", hostLms));
		}

		return hostLms.deleteBib(bibId)
			.map(result -> createSuccessResult("cleanup", "bib",
				"delete bib returned " + result, hostLms));
	}

	private Mono<InteropTestResult> createBibTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		return hostLms.createBib(Bib.builder()
				.author("testAuthor")
				.title("testTitle")
				.build())
			.map(bibId -> {
				testCtx.getValues().put("mms_id", bibId);
				return createSuccessResult("setup", "create bib",
					"Created bib with ID " + bibId, hostLms);
			});
	}

	private Mono<InteropTestResult> patronValidateTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String remotePatronId = (String) testCtx.getValues().get("remotePatronId");

		if (remotePatronId == null) {
			return Mono.just(createNotRunResult("setup", "patron validation",
				"Missing remote patron ID", hostLms));
		}

		return hostLms.getPatronByIdentifier(remotePatronId)
			.map(patron -> createSuccessResult("setup", "patron validation",
				"Validated patron with remotePatronId " + remotePatronId, hostLms));
	}

	private Mono<InteropTestResult> findVirtualPatronTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String externalPatronId = (String) testCtx.getValues().get("external_patron_id");

		if (externalPatronId == null) {
			return Mono.just(createNotRunResult("setup", "find virtual patron",
				"Missing external patron ID", hostLms));
		}


		return hostLms.findVirtualPatron(org.olf.dcb.core.model.Patron.builder().homeLibraryCode(externalPatronId).build())
			.map(patron -> createSuccessResult("setup", "find virtual patron",
				"Found virtual patron with externalPatronId " + externalPatronId, hostLms));
	}

	private Mono<InteropTestResult> updatePatronTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String remotePatronId = (String) testCtx.getValues().get("remotePatronId");

		if (remotePatronId == null) {
			return Mono.just(createNotRunResult("setup", "update patron",
				"Missing remote patron ID", hostLms));
		}

		return hostLms.updatePatron(remotePatronId, TEST_UPDATE_LOCAL_PATRON_TYPE)
			.map(patron -> createSuccessResult("setup", "patron update",
				"Updated patron with remotePatronId " + remotePatronId, hostLms));
	}

	private Mono<InteropTestResult> patronCreateTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

//		String temporaryPatronId = UUID.randomUUID().toString();
		String temporaryPatronId = "test_user_"+Instant.now().toString();
		testCtx.getValues().put("external_patron_id", temporaryPatronId);

		log.info("Starting patron tests with context: {}", testCtx.getValues());

		return hostLms.createPatron(
				Patron.builder()
					.localId(List.of(temporaryPatronId))
					.localPatronType(TEST_LOCAL_PATRON_TYPE)
					.localNames(List.of(temporaryPatronId + "-fn", temporaryPatronId + ".sn"))
					.localBarcodes(List.of(temporaryPatronId))
					.uniqueIds(List.of(temporaryPatronId))
					.canonicalPatronType("CIRC")
					.build()
			)
			.map(newPatronId -> {
				testCtx.getValues().put("remotePatronId", newPatronId);
				return createSuccessResult("setup", "patron creation",
					"Created patron with ID " + newPatronId, hostLms);
			});
	}

	private Mono<InteropTestResult> checkoutTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		return hostLms.checkOutItemToPatron(
				CheckoutItemCommand.builder()
					.itemId((String) testCtx.getValues().get("item_id"))
					.patronId((String) testCtx.getValues().get("remotePatronId"))
					.localRequestId((String) testCtx.getValues().get("hold_id"))
					.libraryCode(TEST_PICKUP_LOCATION_CODE)
					.build()
			)
			.map(checkout -> createSuccessResult("setup", "patron checkout",
				"Checkout returned: " + checkout, hostLms));
	}

	private Mono<InteropTestResult> patronDeleteTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String remotePatronId = (String) testCtx.getValues().get("remotePatronId");

		if (remotePatronId == null) {
			return Mono.just(createNotRunResult("cleanup", "patron",
				"There was no patron-id in context. Unable to test patron delete", hostLms));
		}

		return hostLms.deletePatron(remotePatronId)
			.map(result -> createSuccessResult("cleanup", "patron",
				"deletePatron returned " + result, hostLms));
	}

	/**
	 * Utility method to check if any values are null.
	 */
	private boolean anyNull(Object... values) {
		for (Object value : values) {
			if (value == null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Host LMS that cannot be reached reports FAILED; one that cannot be asked reports
	 * NOT_SUPPORTED from its own adapter. The two must not collapse into one outcome.
	 */
	public Mono<ConfigurationReport> checkConfiguration(String systemCode) {
		return hostLmsService.getClientFor(systemCode)
			.flatMap(client -> client.checkConfiguration())
			.onErrorResume(error -> Mono.just(ConfigurationReport.failed(systemCode,
				"Could not reach " + systemCode + ": " + error.getMessage())));
	}

	public Mono<MappingValueCheck> checkMappingValue(String systemCode, MappingVocabulary vocabulary,
		String value) {

		return hostLmsService.getClientFor(systemCode)
			.flatMap(client -> client.checkMappingValue(vocabulary, value))
			.onErrorResume(error -> Mono.just(MappingValueCheck.unknown(systemCode, vocabulary, value,
				"Could not reach " + systemCode + ": " + error.getMessage())));
	}

	/** The from_category values reference value mappings are stored under, and their vocabulary. */
	private static final List<Map.Entry<String, MappingVocabulary>> AUDITED_CATEGORIES = List.of(
		Map.entry("patronType", MappingVocabulary.PATRON_TYPE),
		Map.entry("ItemType", MappingVocabulary.ITEM_TYPE),
		Map.entry("Location", MappingVocabulary.LOCATION));

	/**
	 * Judges every saved mapping targeting one Host LMS against what that system holds.
	 * <p>
	 * Each vocabulary is read once and every row checked against it in memory: a call per row
	 * would be one outbound request per mapping, against a system we do not own.
	 */
	public Mono<MappingAudit> auditMappings(String systemCode) {
		return hostLmsService.getClientFor(systemCode)
			// One system's mappings, both directions: bounded by the consortium's configuration
			.flatMap(client -> Flux.from(referenceValueMappingRepository.findByContexts(List.of(systemCode)))
				.filter(mapping -> !Boolean.TRUE.equals(mapping.getDeleted()))
				.collectList()
				.flatMap(mappings -> Flux.fromIterable(AUDITED_CATEGORIES)
					.concatMap(category -> auditCategory(client, systemCode, category.getKey(),
						category.getValue(), targeting(mappings, systemCode, category.getKey())))
					.collectList()
					.map(rows -> MappingAudit.of(systemCode, rows.stream().flatMap(List::stream).toList(),
						notChecked(systemCode, mappings)))))
			.onErrorResume(error -> Mono.just(MappingAudit.failed(systemCode,
				"Could not read " + systemCode + ": " + error.getMessage())));
	}

	private static List<ReferenceValueMapping> targeting(List<ReferenceValueMapping> mappings,
		String systemCode, String category) {

		return mappings.stream()
			.filter(mapping -> systemCode.equals(mapping.getToContext()))
			.filter(mapping -> category.equals(mapping.getFromCategory()))
			.toList();
	}

	// A clean audit must not read as a clean configuration: say what it did not look at
	private static List<String> notChecked(String systemCode, List<ReferenceValueMapping> mappings) {
		final var readFromSystem = mappings.stream()
			.filter(mapping -> systemCode.equals(mapping.getFromContext()))
			.filter(mapping -> !systemCode.equals(mapping.getToContext()))
			.count();

		final var notChecked = new ArrayList<String>();

		if (readFromSystem > 0) {
			notChecked.add("%d mappings from %s's own values into DCB: whether those values exist in %s"
				.formatted(readFromSystem, systemCode, systemCode));
		}

		notChecked.add("Mappings under a shared context that %s reads through a context hierarchy"
			.formatted(systemCode));
		notChecked.add("Pickup locations: they are DCB location records, not mappings");

		return notChecked;
	}

	private Mono<List<MappingAudit.Row>> auditCategory(HostLmsClient client, String systemCode,
		String category, MappingVocabulary vocabulary, List<ReferenceValueMapping> mappings) {

		if (mappings.isEmpty()) {
			return Mono.just(List.of());
		}

		return client.fetchVocabulary(vocabulary)
			// An unreadable vocabulary reports UNKNOWN per row, never MISSING: telling an
			// implementer a value is absent when we could not look sends them to create
			// one that already exists
			.onErrorResume(error -> {
				log.warn("Could not read {} from {}", vocabulary, systemCode, error);
				return Mono.just(List.of());
			})
			.map(entries -> mappings.stream()
				.map(mapping -> auditRow(systemCode, category, vocabulary, mapping, entries))
				.toList())
			// An empty Mono is an adapter that cannot read this vocabulary. Dropping the rows
			// made a system that could not be asked read as one with nothing wrong
			.switchIfEmpty(Mono.fromSupplier(() -> mappings.stream()
				.map(mapping -> notSupportedRow(systemCode, category, vocabulary, mapping))
				.toList()));
	}

	private static MappingAudit.Row notSupportedRow(String systemCode, String category,
		MappingVocabulary vocabulary, ReferenceValueMapping mapping) {

		final var check = MappingValueCheck.notSupported(systemCode, vocabulary, mapping.getToValue());

		return new MappingAudit.Row(category, mapping.getFromContext(), mapping.getFromValue(),
			mapping.getToValue(), check.result(), check.detail());
	}

	private static MappingAudit.Row auditRow(String systemCode, String category,
		MappingVocabulary vocabulary, ReferenceValueMapping mapping,
		List<ConfigurationReport.Entry> entries) {

		final var check = MappingValueCheck.against(systemCode, vocabulary,
			mapping.getToValue(), entries);

		return new MappingAudit.Row(category, mapping.getFromContext(), mapping.getFromValue(),
			mapping.getToValue(), check.result(), check.detail());
	}

	public Mono<InteropTestResult> retrieveConfiguration(String systemCode, ConfigType validatedType) {
		return hostLmsService.getClientFor(systemCode)
			.flatMap(hostLmsClient -> hostLmsClient.fetchConfigurationFromAPI(validatedType))
			.map(config -> InteropTestResult.builder().result("OK").response(config).build())
			.onErrorResume(e -> Mono.just(InteropTestResult.builder()
				.result("ERROR")
				.stage("Fetching configuration for " + systemCode + " with type " + validatedType.getValue())
				.notes(List.of(e.getMessage()))
				.build()));
	}

}
