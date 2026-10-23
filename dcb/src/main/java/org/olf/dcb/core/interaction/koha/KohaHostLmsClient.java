package org.olf.dcb.core.interaction.koha;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.HttpStatus;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.*;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.*;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.*;


import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.events.RulesetCacheInvalidator;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.rules.AnnotatedObject;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.rules.ObjectRulesService;
import org.olf.dcb.core.interaction.foundation.NcipAdaptor;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.ThrowableProblem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.common.util.StringUtils.isBlank;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

/** <p> This is the client for the Koha library management system, and a crucial part of the Koha adapter.
 * </p><br>
 * <p> A useful first point of call for developers interested in this area is the Koha API docs site: <a href="https://api.koha-community.org/">Koha API documentation</a> </p>
 * <p>
 * You can also find OpenRS-specific developer documentation here (TBC).
 * The methods in this class are divided into groups based on what they do, and then alphabetically ordered,
 * to make this code easier to scan.
 * Please respect the ordering if adding new methods, as it makes maintaining this a lot easier.
 * If you are unsure of where a new method belongs, put it under 'General'
 * */

@Slf4j
@Prototype
public class KohaHostLmsClient implements HostLmsClient {

	private final HostLms hostLms;
	private final KohaApiClient client;
	private final ReferenceValueMappingService referenceValueMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final ObjectRulesService objectRulesService;
	private final RulesetCacheInvalidator cacheInvalidator;
	private final HostLmsService hostLmsService;
	private final KohaClientConfig config;
	// Null unless ncip-checkin-fallback is on with its address and token
	private final NcipAdaptor ncipCheckIn;



	// Static defaults for virtual patron creation
	private static final String DEFAULT_FIRST_NAME = "DCB";
	private static final String DEFAULT_LAST_NAME = "VPATRON";

	// How a DCB created item is recognised in Koha, both when creating one and before writing to
	// one - the two have to agree, or renewal prevention refuses every item DCB made
	private static final String DCB_VIRTUAL_ITEM_CALL_NUMBER = "DCB_VIRTUAL_COLLECTION";

	// PIN and PASSWORD are one thing to Koha: the OPAC password, validated by Koha itself
	private static final List<String> AUTH_PROFILES = List.of(
		"BASIC/BARCODE+PASSWORD", "BASIC/BARCODE+PIN", "BASIC/BARCODE+NAME");


	/**
	 * How many items getItems enriches at once.
	 * <p>
	 * mapKohaItemToDcbItem issues a getActiveHoldsForItem per item, so an unbounded
	 * flatMap turns one availability check into one API call per holding. A title
	 * held at sixty branches of a shared Koha is then sixty concurrent calls to a
	 * single server, per check, per user - and co-tenancy is exactly what makes the
	 * holding count large while the server count stays at one.
	 */
	private static final int ITEM_ENRICHMENT_CONCURRENCY = 4;

	// Koha's list answers RESTdefaultPageSize rows (20 by default) unless asked for a page
	// size; a title on a shared Koha has more copies than that. The cap bounds one check.
	private static final int ITEMS_PAGE_SIZE = 100;
	private static final int MAX_ITEMS_PER_BIBLIO = 500;

	/**
	 * Item suppression ruleset used when the Host LMS names none of its own.
	 * <p>
	 * Mirrors the bib side, where KohaOaiPmhIngestSource defaults to "koha-default": a
	 * Koha created without a ruleset name would otherwise contribute every item it has.
	 * The shipped ruleset encodes the not_for_loan_status 42 convention that used to be
	 * hardcoded here, so an existing deployment sees no change in behaviour - only a
	 * value it can now edit without a release.
	 */
	private static final String DEFAULT_ITEM_SUPPRESSION_RULESET_NAME = "koha-item-default";

	public KohaHostLmsClient(
		@Parameter HostLms hostLms,
		ReferenceValueMappingService referenceValueMappingService,
		KohaClientFactory kohaClientFactory,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		ObjectRulesService objectRulesService,
		RulesetCacheInvalidator cacheInvalidator,
		HostLmsService hostLmsService) {
		this.hostLms = hostLms;
		this.client = kohaClientFactory.createClientFor(hostLms);
		this.referenceValueMappingService = referenceValueMappingService;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.objectRulesService = objectRulesService;
		this.cacheInvalidator = cacheInvalidator;
		this.hostLmsService = hostLmsService;
		// Built here rather than injected, as Alma does. KohaClientConfig has no bean
		// definition and its only constructor takes the HostLms, so it could never have
		// been satisfied as an injection point.
		this.config = new KohaClientConfig(hostLms);

		final var ncipEndpoint = config.isNcipCheckInFallbackEnabled() ? config.getNcipCheckInEndpoint() : null;
		this.ncipCheckIn = ncipEndpoint == null ? null
			: kohaClientFactory.createNcipClientFor(ncipEndpoint, hostLms.getCode());
		}

	/*** General operations - are we missing any? A version check would be useful. possibly implementer also***/

	@Override
	public HostLms getHostLms() {
		return hostLms;
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		// To be implemented using a KohaClientConfig class
		return config.getSettings();
	}

	@Override
	public String getClientId() {
		// The Koha server, not the Host LMS code: several DCB Host LMS records can
		// point at one Koha, and returning the code would hide that from the
		// same-server checks entirely.
		return qualifySystemIdentity(KohaApiClientImpl.resolveBelow(config.getApiUrl(), URI.create("/")).toString());
	}

	private String getDcbSharingLibraryCode() {
		final var sharingLibraryCode = config.getDcbSharingLibraryCode();
		if (isBlank(sharingLibraryCode)) throw new IllegalStateException("Missing DCB sharing library code in config. Please check the client configuration of this Koha LMS.");
		return sharingLibraryCode;
	}

	/**
	 * The Koha branch a hold is placed at, as its library_id.
	 * <p>
	 * A pickup location's localId is the only thing that carries a Koha branch code.
	 * Notably it is <em>not</em> pickupLocationCode, which despite the name carries
	 * whatever the caller put there: the pickup Location's UUID from
	 * BorrowingAgencyService, and the pickup <em>agency</em> code from the supplying and
	 * pickup agency transitions. Koha's library_id is a branchcode of at most 10
	 * characters, so neither can name a branch, and sending them only ever produced a
	 * hold Koha rejected. A pickup location with no localId is a configuration error,
	 * which LocationInputValidator now refuses to create.
	 * <p>
	 * External pickup falls back to the sharing library, which is the branch that
	 * stands for "a borrower outside this Koha".
	 */
	private String resolveLibraryCode(PlaceHoldRequestParameters p) {

		final var localId = getValueOrNull(p,
			PlaceHoldRequestParameters::getPickupLocation, Location::getLocalId);

		if (!isBlank(localId)) {
			return localId;
		}

		log.warn("Pickup location \"{}\" on request {} has no localId, so the hold goes to sharing library \"{}\" "
				+ "rather than the branch the patron expects. Set the Koha branch code as the location's localId.",
			getValueOrNull(p, PlaceHoldRequestParameters::getPickupLocation, Location::getCode),
			p.getPatronRequestId(), getDcbSharingLibraryCode());

		return getDcbSharingLibraryCode();
	}

	/*** Patron operations ***/

	// We're going to need a way of getting the library
	// Think AspenCat
	// Can we use agencies for this
	// 1 system
	@Override
	public Mono<String> createPatron(Patron patron) {
		// For creating a virtual patron
		log.info("Creating a virtual patron in {}", getHostLmsCode());

		// Extract names
		String firstName = (patron.getLocalNames() != null && !patron.getLocalNames().isEmpty())
			? patron.getLocalNames().get(0) : DEFAULT_FIRST_NAME;
		String lastName = (patron.getLocalNames() != null && patron.getLocalNames().size() > 1)
			? patron.getLocalNames().get(patron.getLocalNames().size() - 1) : DEFAULT_LAST_NAME;

		// Koha POST /api/v1/patrons strictly requires surname, library_id, and category_id.
		// A virtual patron stands for a borrower outside this Koha, which is precisely
		// what the sharing library represents - so one scalar is the right shape here
		// even on a shared server. The default agency code is not: it names one of the
		// co-tenant libraries, and reading it from config directly also bypassed the
		// shared-system guard on getDefaultAgencyCode.
		String libraryId = getDcbSharingLibraryCode();
		String categoryId = patron.getLocalPatronType() != null
			? patron.getLocalPatronType() : config.getVirtualPatronCategory();

		// The unique id is the borrower's id and agency code, which overruns Koha's 32-character
		// cardnumber; userid takes 75. The virtual patron has no card
		final var kohaPatron = KohaPatron.builder()
			.userid(patron.getUniqueIds() != null && !patron.getUniqueIds().isEmpty()
				? patron.getUniqueIds().get(0) : null)
			.surname(lastName)
			.firstname(firstName)
			.email(config.getVirtualPatronEmail())
			.libraryId(libraryId)
			.categoryId(categoryId)
			.build();

		return client.createPatron(kohaPatron)
			.map(returnedPatron -> String.valueOf(returnedPatron.getPatronId()))
			.doOnSuccess(id -> log.info("Created Koha patron with ID: {}", id));
	}

	@Override
	public Mono<String> deletePatron(String id) {
		log.info("Deleting Koha patron: {}", id);
		return client.deletePatron(id)
			.thenReturn("OK")
			.doOnError(e -> log.error("Failed to delete Koha patron {}: {}", id, e.getMessage()));
	}

	// Get the canonical patron type
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
				"Unable to map patron type \"" + localPatronType + "\" for Koha Host LMS: \"" + hostLmsCode + "\" to canonical value",
				hostLmsCode, localPatronType)));
	}

	@Override
	public Mono<String> findLocalPatronType(String canonicalPatronType) {
		if (canonicalPatronType == null) {
			return Mono.empty();
		}

		return referenceValueMappingService.findMapping("patronType", "DCB", canonicalPatronType, "patronType", getHostLmsCode())
			.map(ReferenceValueMapping::getToValue)
			.switchIfEmpty(Mono.error(new NoPatronTypeMappingFoundException(
				"Unable to map canonical patron type \"" + canonicalPatronType + "\" to a patron type on Koha Host LMS: \"" + getHostLmsCode() + "\"",
				getHostLmsCode(), canonicalPatronType)));
	}

	@Override
	public Mono<Patron> findVirtualPatron(org.olf.dcb.core.model.Patron patron) {
		log.info("Finding virtual patron for DCB patron {} in {}", getValueOrNull(patron, org.olf.dcb.core.model.Patron::getId), getHostLmsCode());

		final var uniqueId = getValueOrNull(patron, org.olf.dcb.core.model.Patron::determineUniqueId);
		if (uniqueId == null) {
			return Mono.error(new IllegalArgumentException("Unable to find uniqueId for virtual patron"));
		}

		return client.getPatronByUserId(uniqueId)
			.filter(patrons -> patrons.length > 0)
			.map(patrons -> mapKohaPatronToDcbPatron(patrons[0]))
			.switchIfEmpty(Mono.error(VirtualPatronNotFound.builder()
				.withDetail("No Koha patron found")
				.with("uniqueId", uniqueId)
				.build()));
	}

	/**
	 * The identifier DCB holds for a patron is Koha's patron_id, which is what
	 * ResolvePatronPreflightCheck and ValidatePatronTransition pass here, as Polaris and FOLIO
	 * read it. Searching it as a card number found the wrong patron or none.
	 */
	@Override
	public Mono<Patron> getPatronByIdentifier(String id) {
		return getPatronByLocalId(id);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {
		log.debug("Fetching patron by username (userid) at {}", getHostLmsCode());
		return client.getPatronByUserId(username)
			.filter(patrons -> patrons.length > 0)
			.map(patrons -> mapKohaPatronToDcbPatron(patrons[0]))
			.flatMap(this::enrichWithCanonicalPatronType)
			.doOnError(e -> log.error("Failed to fetch patron by username: {}", e.getClass().getSimpleName()));
	}

	@Override
	public Mono<Patron> getPatronByLocalId(String localPatronId) {
		return client.getPatron(localPatronId)
			.map(this::mapKohaPatronToDcbPatron)
			// Not found is an answer the callers handle; any other failure is theirs to see
			.onErrorResume(KohaHostLmsClient::isNotFound, notFound -> Mono.empty())
			.flatMap(this::enrichWithCanonicalPatronType);
	}

	// As Sierra and Polaris do: a category with no mapping leaves the patron as read, and
	// the request fails later with the mapping named, rather than refusing sign-in
	private Mono<Patron> enrichWithCanonicalPatronType(Patron patron) {
		return findCanonicalPatronType(patron.getLocalPatronType(), patron.getFirstLocalId())
			.onErrorResume(NoPatronTypeMappingFoundException.class, unmapped -> Mono.empty())
			.map(patron::setCanonicalPatronType)
			.defaultIfEmpty(patron);
	}


	@Override
	public List<String> supportedAuthProfiles() {
		return AUTH_PROFILES;
	}

	@Override
	public Mono<Patron> patronAuth(String authProfile, String patronPrinciple, String secret) {
		log.debug("{} patronAuth({})", getHostLmsCode(), authProfile);

		return switch (authProfile) {
			case "BASIC/BARCODE+PASSWORD", "BASIC/BARCODE+PIN" -> validatePassword(patronPrinciple, secret);
			case "BASIC/BARCODE+NAME" -> validateByName(patronPrinciple, secret);
			case null, default -> Mono.error(
				new UnsupportedAuthProfileException(getHostLmsCode(), authProfile, AUTH_PROFILES));
		};
	}

	/**
	 * Koha validates the OPAC password: 401 for a wrong one, 400 for a patron it does not
	 * know. Both are the empty answer a wrong credential gets everywhere in DCB; an outage
	 * or a rate limit is not, and stays an error.
	 */
	private Mono<Patron> validatePassword(String cardnumber, String password) {
		return client.authenticatePatron(cardnumber, password)
			.flatMap(response -> response.getPatronId() != null
				? getPatronByLocalId(String.valueOf(response.getPatronId()))
				: Mono.empty())
			.onErrorResume(KohaHostLmsClient::isRefusal, refused -> Mono.empty());
	}

	// The same rule as Sierra: the name given starts one of the names on record
	private Mono<Patron> validateByName(String cardnumber, String name) {
		if (isBlank(name)) {
			return Mono.empty();
		}

		return client.getPatronByCardnumber(cardnumber)
			.filter(patrons -> patrons.length > 0)
			.map(patrons -> mapKohaPatronToDcbPatron(patrons[0]))
			.flatMap(this::enrichWithCanonicalPatronType)
			.filter(patron -> patron.getLocalNames().stream()
				.filter(Objects::nonNull)
				.anyMatch(known -> known.toLowerCase().trim().startsWith(name.toLowerCase().trim())));
	}

	private static boolean isRefusal(Throwable error) {
		final var status = statusOf(error);

		return status == HttpStatus.BAD_REQUEST.getCode()
			|| status == HttpStatus.UNAUTHORIZED.getCode()
			|| status == HttpStatus.FORBIDDEN.getCode();
	}

	@Override
	public Mono<Patron> updatePatron(String localId, String patronType) {
		log.debug("Updating Koha patron - localID {} and patron type {}", localId, patronType);

		return client.getPatron(localId)
			.flatMap(existingPatron -> {
				existingPatron.setCategoryId(patronType);
				return client.updatePatron(localId, existingPatron);
			})
			.map(this::mapKohaPatronToDcbPatron)
			.doOnError(e -> log.error("Failed to update Koha patron {}: {}", localId, e.getMessage()));
	}

	@Override
	public Mono<Integer> countHoldsForPatron(String localPatronId) {
		log.debug("countHoldsForPatron({})", localPatronId);

		return client.getActiveHoldsForPatron(localPatronId)
			.map(holds -> holds != null ? holds.length : 0)
			// An error here must not be reported as a count of zero
			.doOnError(error -> log.warn("Could not count holds for patron {} at {}",
				localPatronId, getHostLmsCode(), error))
			.onErrorResume(error -> Mono.empty());
	}

	/**
	 * Loans and charges for the discovery account page. The balance needs updatecharges,
	 * which a library may not grant the API patron: the loans are still answered and the
	 * charges are then unknown rather than zero.
	 */
	@Override
	public Mono<LocalPatronAccount> getLocalPatronAccount(String localPatronId, int maxRows) {
		final var loans = client.getPatronCheckouts(localPatronId, maxRows)
			.map(checkouts -> Arrays.stream(checkouts).map(KohaHostLmsClient::toLocalLoan).toList())
			.defaultIfEmpty(List.of());

		final var balance = client.getPatronBalance(localPatronId)
			.map(Optional::of)
			.onErrorResume(KohaHostLmsClient::isRefusal, forbidden -> {
				log.warn("The API patron for {} may not read patron balances; charges are not reported",
					getHostLmsCode());
				return Mono.just(Optional.<KohaPatronBalance>empty());
			})
			.defaultIfEmpty(Optional.empty());

		return Mono.zip(loans, balance)
			.map(found -> toLocalPatronAccount(found.getT1(), found.getT2().orElse(null)));
	}

	private static LocalPatronAccount toLocalPatronAccount(List<LocalPatronAccount.LocalLoan> loans,
		KohaPatronBalance balance) {

		final var charges = balance == null ? null : Optional.ofNullable(balance.getOutstandingDebits())
			.map(KohaPatronBalance.Outstanding::getLines)
			.orElse(List.of())
			.stream()
			.map(KohaHostLmsClient::toLocalCharge)
			.toList();

		return LocalPatronAccount.builder()
			.loans(loans)
			.totalLoans(loans.size())
			.charges(charges)
			.totalCharges(charges == null ? null : charges.size())
			.amountOwed(balance == null ? null : balance.getBalance())
			.build();
	}

	private static LocalPatronAccount.LocalLoan toLocalLoan(KohaCheckout checkout) {
		final var biblio = getValueOrNull(checkout, KohaCheckout::getItem, KohaItem::getBiblio);
		final var dueDate = DueDates.parse(checkout.getDueDate());

		return LocalPatronAccount.LocalLoan.builder()
			.title(getValueOrNull(biblio, KohaBiblio::getTitle))
			.author(getValueOrNull(biblio, KohaBiblio::getAuthor))
			.localBibId(biblio != null && biblio.getBiblioId() != null ? String.valueOf(biblio.getBiblioId()) : null)
			.localItemId(checkout.getItemId() != null ? String.valueOf(checkout.getItemId()) : null)
			.loanDate(DueDates.parse(checkout.getCheckoutDate()))
			.dueDate(dueDate)
			.overdue(dueDate != null && dueDate.isBefore(Instant.now()))
			.build();
	}

	private static LocalPatronAccount.LocalCharge toLocalCharge(KohaAccountLine line) {
		return LocalPatronAccount.LocalCharge.builder()
			.description(line.getDescription())
			.reason(line.getDebitType())
			.state(line.getStatus())
			.amount(line.getAmountOutstanding())
			.accrualDate(DueDates.parse(line.getDate()))
			.build();
	}

	private Patron mapKohaPatronToDcbPatron(KohaPatron kohaPatron) {
		return Patron.builder()
			.localId(List.of(String.valueOf(kohaPatron.getPatronId())))
			// Both names are nullable in Koha's schema
			.localNames(Stream.of(kohaPatron.getFirstname(), kohaPatron.getSurname()).filter(Objects::nonNull).toList())
			.localBarcodes(kohaPatron.getCardnumber() != null ? List.of(kohaPatron.getCardnumber()) : List.of())
			.localPatronType(kohaPatron.getCategoryId())
			// The Koha branch is the only thing distinguishing co-tenant libraries on a
			// shared server, and it is what location-to-agency mappings are keyed on.
			// Without it every patron falls through to the default agency, which on a
			// 60-library Koha means every patron belongs to whichever library was
			// configured there.
			.localHomeLibraryCode(kohaPatron.getLibraryId())
			// Koha's own blocks: a restriction (debarment) and a lost card. Debt is not read
			// here: Koha refuses the hold itself and that refusal is what the patron sees
			.isBlocked(TRUE.equals(kohaPatron.getRestricted()) || TRUE.equals(kohaPatron.getPatronCardLost()))
			.expiryDate(expiryDate(kohaPatron.getExpiryDate()))
			.isActive(true)
			.build();
	}

	// Koha's expiry_date is a plain date in the library's calendar; the end of that day is
	// when the account stops working, so a patron is not expired during it
	private static Date expiryDate(String kohaExpiryDate) {
		if (isBlank(kohaExpiryDate)) {
			return null;
		}

		try {
			return Date.from(LocalDate.parse(kohaExpiryDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
		}
		catch (DateTimeParseException unrecognised) {
			log.warn("Koha expiry date '{}' is not a date; expiry is not checked", kohaExpiryDate);
			return null;
		}
	}

	/*** Bib and item operations ***/

	@Override
	public Mono<String> createBib(Bib bib) {
		log.debug("Koha: Creating virtual bib record for title: {}", bib.getTitle());

		final var author = (bib.getAuthor() != null) ? bib.getAuthor() : "";
		final var title = (bib.getTitle() != null) ? bib.getTitle() : "DCB Virtual Record";

		final String kohaBibXml = KohaMarcXmlGenerator.createBibXml(title, author);

		return client.createBibRecord(kohaBibXml)
			.map(kohaBiblio -> String.valueOf(kohaBiblio.getBiblioId()))
			.doOnSuccess(id -> log.info("Successfully created Koha virtual bib with ID: {}", id))
			.doOnError(e -> log.error("Failed to create Koha virtual bib: {}", e.getMessage()));
	}

	@Override
	public Mono<HostLmsItem> getItem(HostLmsItem hostLmsItem) {
		final String itemId = getValueOrNull(hostLmsItem, HostLmsItem::getLocalId);

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local item ID is required to fetch a Koha item"));
		}

		log.debug("Fetching Koha item by local ID: {}", itemId);

		return biblioIdFor(getValueOrNull(hostLmsItem, HostLmsItem::getBibId), itemId)
			.flatMap(biblioId -> client.getItemCirculation(biblioId, itemId))
			.filter(items -> items.length > 0)
			.map(items -> mapKohaItemToHostLmsItem(items[0]))
			.defaultIfEmpty(HostLmsItem.builder()
				.localId(itemId)
				.status(HostLmsItem.ITEM_MISSING)
				.build())
			.doOnError(e -> log.error("Failed to retrieve Koha item {}: {}", itemId, e.getMessage()));
	}

	/**
	 * Only the per-biblio item list embeds an item's checkout, transfer and first hold; the
	 * single-item read offers no such embeds (25.11 spec), so the item is read through its
	 * biblio. The caller usually knows the biblio; when it does not, the plain read supplies it.
	 */
	private Mono<String> biblioIdFor(String knownBiblioId, String itemId) {
		if (!isBlank(knownBiblioId)) {
			return Mono.just(knownBiblioId);
		}

		return client.getItem(itemId)
			.mapNotNull(KohaItem::getBiblioId)
			.map(String::valueOf);
	}

	/**
	 * Helper method to fetch a Koha item by barcode and map it to a DCB HostLmsItem.
	 * To be replicated across all LMS to provide "lookup by barcode". Not used just yet.
	 */
	@Override
	public boolean canSeeVirtualItemsByBarcode() {
		return true;
	}

	public Mono<HostLmsItem> getItemByBarcode(String barcode) {
		log.info("Fetching Koha item by barcode: {}", barcode);

		return client.getItemByBarcode(barcode)
			.filter(items -> items.length > 0)
			.map(items -> mapKohaItemToHostLmsItem(items[0]))
			.doOnError(e -> log.error("Failed to fetch item by barcode {} from Koha: {}", barcode, e.getMessage()));
	}

	private HostLmsItem mapKohaItemToHostLmsItem(KohaItem kohaItem) {
		final var checkout = kohaItem.getCheckout();
		final var circulation = KohaItemCirculation.of(kohaItem);

		return HostLmsItem.builder()
			.localId(String.valueOf(kohaItem.getItemId()))
			.barcode(kohaItem.getExternalId())
			.bibId(String.valueOf(kohaItem.getBiblioId()))
			.status(circulation.hostLmsStatus())
			.rawStatus(circulation.name())
			// The loan's own count: items.renewals was dropped in Koha 23.11, so the item field
			// is empty on every target version
			.renewalCount(checkout != null && checkout.getRenewalsCount() != null
				? checkout.getRenewalsCount()
				: kohaItem.getRenewalsCount())
			.dueDate(checkout == null ? null : DueDates.parse(checkout.getDueDate()))
			// holds_count is the lifetime tally, which the virtual patron's own hold is part of;
			// a first hold on a lent item is someone waiting for it now
			.holdCount(kohaItem.getFirstHold() != null ? 1 : 0)
			.holdingId(kohaItem.getHoldingLibraryId())
			.build();
	}

	/**
	 * Koha has no item status column. An item is lost or withdrawn by its own flags, on
	 * loan because a checkout exists, in transit because a transfer has been sent and not
	 * received, and on the hold shelf because its first hold is waiting - which is why the
	 * read embeds checkout, transfer and first_hold, and why the order below is the order
	 * the flags override one another in Koha's own displays.
	 */
	enum KohaItemCirculation {
		WITHDRAWN(HostLmsItem.ITEM_MISSING),
		LOST(HostLmsItem.ITEM_MISSING),
		CHECKED_OUT(HostLmsItem.ITEM_LOANED),
		IN_TRANSIT(HostLmsItem.ITEM_TRANSIT),
		WAITING(HostLmsItem.ITEM_ON_HOLDSHELF),
		NOT_FOR_LOAN(HostLmsItem.LIBRARY_USE_ONLY),
		AVAILABLE(HostLmsItem.ITEM_AVAILABLE);

		private final String hostLmsStatus;

		KohaItemCirculation(String hostLmsStatus) {
			this.hostLmsStatus = hostLmsStatus;
		}

		String hostLmsStatus() {
			return hostLmsStatus;
		}

		static KohaItemCirculation of(KohaItem item) {
			if (isSet(item.getWithdrawn())) {
				return WITHDRAWN;
			}
			if (isSet(item.getLostStatus())) {
				return LOST;
			}
			if (item.getCheckout() != null || item.getCheckedOutDate() != null) {
				return CHECKED_OUT;
			}
			if (isOpenTransfer(item.getTransfer())) {
				return IN_TRANSIT;
			}
			if (isWaiting(item.getFirstHold())) {
				return WAITING;
			}
			if (isSet(item.getEffectiveNotForLoanStatus()) || isSet(item.getNotForLoanStatus())) {
				return NOT_FOR_LOAN;
			}
			return AVAILABLE;
		}

		// A transfer that has been requested but not sent leaves the item where it is
		private static boolean isOpenTransfer(KohaTransfer transfer) {
			return transfer != null
				&& transfer.getDateSent() != null
				&& transfer.getDateArrived() == null;
		}

		private static boolean isWaiting(KohaHoldResponse firstHold) {
			return firstHold != null && "W".equalsIgnoreCase(firstHold.getStatus());
		}

		private static boolean isSet(Integer flag) {
			return flag != null && flag != 0;
		}
	}

	// The tracking read's view of the item, so that availability and tracking agree. An item in
	// transit or waiting for another patron is not offered: a hold on it queues behind theirs
	private ItemStatusCode deriveItemStatus(KohaItem item) {
		return switch (KohaItemCirculation.of(item)) {
			case CHECKED_OUT -> ItemStatusCode.CHECKED_OUT;
			// Koha refuses a checkout for restricted 1 only; other values are the site's own
			case AVAILABLE -> KohaItemCirculation.isSet(item.getDamagedStatus())
				|| Integer.valueOf(1).equals(item.getRestrictedStatus())
				? ItemStatusCode.UNAVAILABLE
				: ItemStatusCode.AVAILABLE;
			default -> ItemStatusCode.UNAVAILABLE;
		};
	}

	/**
	 * Fetches all items for a given bibliographic record.
	 * <p>
	 * NOTE: Pagination depends on local Koha system preferences. The GET /api/v1/biblios/{biblio_id}/items
	 * endpoint respects the `_per_page` setting. If a biblio has a massive number of items, this array
	 * may be truncated unless you loop pages or set a sufficiently high `_per_page` value.
	 * </p>
	 */
	@Override
	public Mono<List<Item>> getItems(BibRecord bibRecord) {
		final String bibId = bibRecord.getSourceRecordId();
		log.debug("Fetching items for Koha bib: {}", bibId);

		if (bibId == null || bibId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Bib ID is required to fetch Koha items"));
		}

		// Resolved once per call rather than per item. The accessor caches, so per item
		// would also be cheap, but the ruleset cannot change midway through one bib's
		// items and reading it once says so.
		return Mono.zip(getLmsItemSuppressionRuleset(), opacVisibleItemIds(bibId))
			.flatMap(rulesAndVisible -> allItemsForBiblio(bibId)
				.flatMap(kohaItem -> mapKohaItemToDcbItem(kohaItem, rulesAndVisible.getT1(), rulesAndVisible.getT2()),
					ITEM_ENRICHMENT_CONCURRENCY)
				.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, getHostLmsCode()),
					ITEM_ENRICHMENT_CONCURRENCY)
				.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType,
					ITEM_ENRICHMENT_CONCURRENCY)
				.onErrorContinue((throwable, item) -> log.warn("Mapping error for Koha item {}: {}", item, throwable.getMessage()))
				.collectList());
	}

	private Flux<KohaItem> allItemsForBiblio(String bibId) {
		return pagedItems(bibId, client::getItemsForBiblio);
	}

	/**
	 * The ids of the biblio's items Koha's OPAC shows, empty when this Host LMS does not honour
	 * OpacHiddenItems. Same pages and cap as the staff read, so an item past the cap is treated as
	 * hidden. A refused read fails the availability check rather than offer what the library hides.
	 */
	private Mono<Optional<Set<Long>>> opacVisibleItemIds(String bibId) {
		if (!config.honoursOpacHiddenItems()) {
			return Mono.just(Optional.empty());
		}

		return pagedItems(bibId, client::getOpacVisibleItemsForBiblio)
			.map(KohaItem::getItemId)
			.filter(Objects::nonNull)
			.collect(Collectors.toUnmodifiableSet())
			.map(Optional::of)
			// The biblio is gone: the staff read finds nothing either
			.onErrorResume(KohaHostLmsClient::isNotFound, gone -> Mono.just(Optional.of(Set.of())))
			.onErrorMap(error -> !isNotFound(error), this::opacVisibilityUnreadable);
	}

	private ThrowableProblem opacVisibilityUnreadable(Throwable error) {
		log.error("Koha at {} would not say which copies its OPAC hides", getHostLmsCode(), error);

		return Problem.builder()
			.withTitle("Koha would not say which copies its OPAC hides")
			.withStatus(Status.BAD_GATEWAY)
			.withDetail("The anonymous read of %s's public items failed: %s"
				.formatted(getHostLmsCode(), kohaErrorMessage(error)))
			.with("remedy", "Turn on RESTPublicAPI and RESTPublicAnonymousRequests, or set "
				+ "honour-opac-hidden-items to false and mirror OpacHiddenItems in the item ruleset")
			.with("hostLmsCode", getHostLmsCode())
			.build();
	}

	@FunctionalInterface
	private interface ItemPageReader {
		Mono<KohaItem[]> read(String biblioId, int page, int perPage);
	}

	// Pages until a short page or the cap; a title past the cap is reported, not read on
	private Flux<KohaItem> pagedItems(String bibId, ItemPageReader readPage) {
		return Flux.range(1, MAX_ITEMS_PER_BIBLIO / ITEMS_PAGE_SIZE)
			.concatMap(page -> readPage.read(bibId, page, ITEMS_PAGE_SIZE)
				.defaultIfEmpty(new KohaItem[0]))
			.takeUntil(page -> page.length < ITEMS_PAGE_SIZE)
			.flatMapIterable(Arrays::asList)
			.take(MAX_ITEMS_PER_BIBLIO)
			.doOnDiscard(KohaItem.class, discarded -> log.warn(
				"Koha biblio {} in {} has more than {} items; the rest are not read",
				bibId, getHostLmsCode(), MAX_ITEMS_PER_BIBLIO));
	}

	/**
	 * Deliberately NOT cached here, unlike the Sierra and Polaris equivalents.
	 * <p>
	 * Its only caller caches the ruleset it derives, against the same invalidator, so a
	 * second cache layer buys nothing. It also cannot be empty-safe: cacheInvalidateWhen
	 * raises "expects a value, source completed empty" rather than completing empty, so a
	 * Host LMS that cannot be re-read would fail getItems outright instead of falling
	 * through to the default ruleset.
	 */
	private Mono<DataHostLms> refetchLms() {
		return Mono.fromSupplier(hostLms::getId)
			.flatMap(hostLmsService::findById);
	}

	private Mono<Optional<ObjectRuleset>> _lmsItemSuppressionRuleset = null;

	/**
	 * Guarded on its own field, deliberately. SierraLmsClient tests the BIB field before
	 * populating the ITEM one, so once the bib ruleset has been read this returns a null
	 * Mono rather than an empty one - do not copy that shape.
	 */
	private synchronized Mono<Optional<ObjectRuleset>> getLmsItemSuppressionRuleset() {

		if (_lmsItemSuppressionRuleset == null) {

			_lmsItemSuppressionRuleset = refetchLms()
				.mapNotNull(HostLms::getItemSuppressionRulesetName)
				.defaultIfEmpty(DEFAULT_ITEM_SUPPRESSION_RULESET_NAME)
				.flatMap(name -> objectRulesService.findByName(name)
					.doOnSuccess(val -> {
						if (val == null) {
							log.warn("Host LMS [{}] uses ruleset [{}] for item suppression, but no ruleset with that name could be found - no items will be suppressed by rules",
								hostLms.getCode(), name);
							return;
						}

						log.debug("Found item suppression ruleset [{}] for Host LMS [{}]", name, hostLms.getCode());
					}))
				.singleOptional()
				.cacheInvalidateWhen(cacheInvalidator::getInvalidator);
		}

		return _lmsItemSuppressionRuleset;
	}

	/**
	 * Koha's own withdrawn flag suppresses unconditionally - a withdrawn item is not on a
	 * shelf anywhere, so no ruleset should be able to contribute it. Everything else is
	 * the operator's call, expressed as the item suppression ruleset.
	 * <p>
	 * Rulesets evaluate true for INCLUSION, hence the negate(). No ruleset means no
	 * rule-driven suppression, which is why the default is FALSE rather than TRUE: an
	 * unreadable or missing ruleset must not silently empty a library's availability.
	 */
	private boolean deriveItemSuppressedFlag(KohaItem kohaItem,
		Optional<ObjectRuleset> itemSuppressionRules, Optional<Set<Long>> opacVisible) {

		if (kohaItem.getWithdrawn() != null && kohaItem.getWithdrawn() > 0) {
			return true;
		}

		if (opacVisible.isPresent() && !opacVisible.get().contains(kohaItem.getItemId())) {
			log.debug("Koha item {} is hidden from the OPAC, so not offered", kohaItem.getItemId());
			return true;
		}

		final List<String> decisionLog = new ArrayList<>();

		final boolean suppressed = itemSuppressionRules
			.map(rules -> rules.negate().test(new AnnotatedObject(kohaItem, decisionLog)))
			.orElse(FALSE);

		if (suppressed && log.isDebugEnabled()) {
			log.debug("Koha item {} suppressed by ruleset, decisions: {}", kohaItem.getItemId(), decisionLog);
		}

		return suppressed;
	}

	@Override
	public Mono<HostLmsItem> createItem(CreateItemCommand cic) {
		final String bibId = getValueOrNull(cic, CreateItemCommand::getBibId);
		final String barcode = getValueOrNull(cic, CreateItemCommand::getBarcode);

		if (bibId == null || barcode == null) {
			return Mono.error(new IllegalArgumentException("Bib ID and Barcode are required to create a Koha item"));
		}

		log.info("Koha: Creating virtual item for bibId: {}, barcode: {}", bibId, barcode);

		final String targetLibraryCode = virtualItemLibraryFor(cic);
//		final Integer virtualNotForLoanStatus = -1; // We might need to make this configurable

		return getMappedItemType(cic.getCanonicalItemType())
			.switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown canonical item type: " + cic.getCanonicalItemType())))
			.flatMap(itemType -> {

				KohaItem virtualItem = KohaItem.builder()
					.externalId(barcode) // Maps to Koha's barcode field
					.itemTypeId(itemType)
					.homeLibraryId(targetLibraryCode)
					.holdingLibraryId(targetLibraryCode)
					// Can we use an item type or a status to make it clear these aren't requestable
					// Koha admins might need to do this for us and then we can use a "DCB_VIRTUAL" item type to hide them from OPAC
//					.notForLoanStatus(virtualNotForLoanStatus) // This could also make it so that virtual items are NOT requestable by local patrons.
					// But we need to check it won't break checkouts (or that we can override) Override can be done via confirmation token so one to experiment with
					.callnumber(DCB_VIRTUAL_ITEM_CALL_NUMBER)
					.internalNotes("Virtual item = created by DCB")
					.publicNotes("Virtual item = created by DCB")
					.build();

				// POST /api/v1/biblios/{biblio_id}/items
				return client.createItem(bibId, virtualItem);
			})
			.map(this::mapKohaItemToHostLmsItem)
			.doOnSuccess(item -> log.info("Successfully created Koha virtual item: {}", item.getLocalId()))
			.doOnError(e -> log.error("Failed to create Koha virtual item for bib {}: {}", bibId, e.getMessage()));
	}

	/**
	 * Which branch a virtual item is created at.
	 * <p>
	 * The borrowing patron's <em>home</em> branch, not the pickup branch.
	 * BorrowingAgencyService builds CreateItemCommand from the borrowing patron
	 * identity's localHomeLibraryCode, so that is what patronHomeLocation carries and
	 * that is what this returns. Under RET-PUA the two differ and the virtual item is
	 * created at the patron's home branch rather than where they will collect it;
	 * putting it at the pickup branch would mean threading the pickup location through
	 * CreateItemCommand, which is a change to the shared command shape.
	 * <p>
	 * virtual-item-library-code remains the fallback for when the caller does not know
	 * the branch. It is a single value on the Host LMS, which is fine for a Koha
	 * serving one library and wrong for one serving sixty - every library's incoming
	 * loans would otherwise materialise at whichever branch was configured.
	 */
	private String virtualItemLibraryFor(CreateItemCommand cic) {
		final var patronHomeLocation = getValueOrNull(cic, CreateItemCommand::getPatronHomeLocation);

		if (isBlank(patronHomeLocation)) {
			log.debug("No patron home location on {}, falling back to virtual-item-library-code",
				cic.getPatronRequestId());

			return config.getVirtualItemLibraryCode();
		}

		return patronHomeLocation;
	}

	Mono<String> getMappedItemType(String itemTypeCode) {

		final var hostlmsCode = getHostLmsCode();

		if (hostlmsCode != null && itemTypeCode != null) {
			return referenceValueMappingService.findMapping("ItemType", "DCB",
					itemTypeCode, "ItemType", hostlmsCode)
				.map(ReferenceValueMapping::getToValue)
				.switchIfEmpty(raiseError(Problem.builder()
					.withTitle("Unable to find item type mapping from DCB to " + hostlmsCode)
					.withDetail("Attempt to find item type mapping returned empty")
					.with("Source category", "ItemType")
					.with("Source context", "DCB")
					.with("DCB item type code", itemTypeCode)
					.with("Target category", "ItemType")
					.with("Target context", hostlmsCode)
					.build())
				);
		}

		log.error(String.format("Request to map item type was missing required parameters %s/%s", hostlmsCode, itemTypeCode));
		return raiseError(Problem.builder()
			.withTitle("Request to map item type was missing required parameters")
			.withDetail(String.format("itemTypeCode=%s, hostLmsCode=%s", itemTypeCode, hostlmsCode))
			.with("Source category", "ItemType")
			.with("Source context", "DCB")
			.with("DCB item type code", itemTypeCode)
			.with("Target category", "ItemType")
			.with("Target context", hostlmsCode)
			.build());
	}

	private Mono<Item> mapKohaItemToDcbItem(KohaItem kohaItem,
		Optional<ObjectRuleset> itemSuppressionRules, Optional<Set<Long>> opacVisible) {
		final String itemId = String.valueOf(kohaItem.getItemId());
		final String bibId = String.valueOf(kohaItem.getBiblioId());

		// First we need the active holds.
		return client.getActiveHoldsForItem(itemId)
			.map(holds -> holds != null ? holds.length : 0)
			.onErrorResume(e -> {
				log.warn("Failed to fetch active holds for Koha item {}: {}", itemId, e.getMessage());
				return Mono.just(0);
			})
			.map(activeHoldCount -> {
				ItemStatusCode dcbStatusCode = deriveItemStatus(kohaItem);
				Boolean isRequestable = ItemStatusCode.AVAILABLE.equals(dcbStatusCode); // CHECK THAT THIS IS THE SAME THING IN KOHA


				final Instant dueDate = kohaItem.getCheckout() == null
					? null
					: DueDates.parse(kohaItem.getCheckout().getDueDate());

				// Koha's "location" is a shelving classifier - REF, STACKS, JUV - and every
				// branch on a shared server uses the same set of them, so it can never
				// identify which library owns an item. The branch is home_library_id, with
				// holding_library_id as the fallback for an item currently away from home.
				// See Item, which documents the two as distinct concepts.
				final var owningBranch = kohaItem.getHomeLibraryId() != null
					? kohaItem.getHomeLibraryId()
					: kohaItem.getHoldingLibraryId();

				Location derivedLocation = owningBranch != null
					? Location.builder()
					.code(owningBranch)
					.name(owningBranch)
					.build()
					: null;

				// Withdrawn is native and unconditional; everything else is the operator's
				// ruleset. The not_for_loan_status 42 convention that used to be hardcoded
				// here now lives in the koha-item-default ruleset, so a library that uses a
				// different value can say so in config instead of asking for a release.
				Boolean isSuppressed = deriveItemSuppressedFlag(kohaItem, itemSuppressionRules, opacVisible);

				return Item.builder()
					.localId(itemId)
					.status(new ItemStatus(dcbStatusCode))
					.dueDate(dueDate)
					.location(derivedLocation)
					.shelvingLocation(kohaItem.getLocation())
					.barcode(kohaItem.getExternalId())
					.callNumber(kohaItem.getCallnumber())
					.isRequestable(isRequestable)
					.holdCount(activeHoldCount) // Uses active holds, not the historical hold_count tally
					.localBibId(bibId)
					.localItemType(kohaItem.getEffectiveItemTypeId() != null ? kohaItem.getEffectiveItemTypeId() : kohaItem.getItemTypeId())
					.localItemTypeCode(kohaItem.getEffectiveItemTypeId() != null ? kohaItem.getEffectiveItemTypeId() : kohaItem.getItemTypeId())
					.suppressed(isSuppressed)
					.rawVolumeStatement(kohaItem.getSerialIssueNumber())
					.parsedVolumeStatement(VolumeDesignation.normalise(kohaItem.getSerialIssueNumber()))
					// The system the item came from, as opposed to owningContext, which is
					// overwritten with the agency's Host LMS once the location resolves. When
					// the location does not resolve there is no agency and so no owning
					// context, and this is then the only record of where the item came from -
					// which is exactly the case an operator has to diagnose on a shared system.
					.sourceHostLmsCode(getHostLmsCode())
					.owningContext(getHostLms().getCode())
					.build();
			});
	}


	@Override
	public Mono<String> deleteItem(DeleteCommand deleteCommand) {
		final String itemId = getValueOrNull(deleteCommand, DeleteCommand::getItemId);

		if (itemId == null || itemId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Item ID is required to delete a Koha item"));
		}

		log.info("Koha: Deleting item: {}", itemId);
		return client.deleteItem(itemId)
			.thenReturn("OK")
			// Gone already, as an NCIP responder with delete_item_on_checkin leaves it
			.onErrorResume(KohaHostLmsClient::isNotFound, gone -> {
				log.info("Koha item {} is already deleted", itemId);
				return Mono.just("OK");
			})
			.doOnError(e -> log.error("Failed to delete Koha item {}: {}", itemId, e.getMessage()));
	}

	@Override
	public Mono<List<ConfigurationReport.Entry>> fetchVocabulary(MappingVocabulary vocabulary) {
		return switch (vocabulary) {
			case ITEM_TYPE -> entries(client.getItemTypes(),
				type -> new ConfigurationReport.Entry(type.getItemTypeId(), type.getDescription()));

			case PATRON_TYPE -> entries(client.getPatronCategories(),
				category -> new ConfigurationReport.Entry(category.getPatronCategoryId(), category.getName()));

			// Koha's branches are its locations
			case LOCATION -> entries(client.getLibraries(),
				library -> new ConfigurationReport.Entry(library.getLibraryId(), library.getName()));
		};
	}

	// A Koha older than the 25.11 spec lacks some of these lists, and says so with a 404.
	// An empty body is empty too: as an empty Mono it would empty checkConfiguration's zip
	private <T> Mono<List<ConfigurationReport.Entry>> entries(Mono<T[]> source,
		Function<T, ConfigurationReport.Entry> toEntry) {

		return source.map(values -> Arrays.stream(values).map(toEntry).toList())
			.defaultIfEmpty(List.of())
			.onErrorResume(KohaHostLmsClient::isNotFound, notFound -> Mono.just(List.of()));
	}

	private static boolean isNotFound(Throwable error) {
		return statusOf(error) == HttpStatus.NOT_FOUND.getCode();
	}

	// KohaApiClientImpl raises a Problem carrying the status when the body fits Koha's error
	// schema, and the raw exception otherwise, so a status arrives in either shape
	private static int statusOf(Throwable error) {
		if (error instanceof HttpClientResponseException response) {
			return response.getStatus().getCode();
		}

		if (error instanceof ThrowableProblem problem && problem.getStatus() != null) {
			return problem.getStatus().getStatusCode();
		}

		return -1;
	}

	// An unreadable list is an empty one, not a failure: the report says so per vocabulary
	private Mono<List<ConfigurationReport.Entry>> emptyWhenUnreadable(MappingVocabulary vocabulary) {
		return fetchVocabulary(vocabulary)
			.onErrorResume(error -> {
				log.warn("Could not read {} from Koha at {}", vocabulary, getHostLmsCode(), error);

				return Mono.just(List.of());
			});
	}

	/**
	 * Three list calls. Triggered by an implementer from the tools API, never on a request path.
	 */
	/**
	 * One library read: it needs a token from the configured client id and secret, and the
	 * catalogue permission DCB's reads rely on. Koha publishes no software version in its API.
	 */
	@Override
	public Mono<PingResponse> ping() {
		final var start = Instant.now();
		final var versionInfo = getHostSystemType() + " REST API v1";

		return client.getOneLibrary()
			.then(Mono.fromSupplier(() -> PingResponse.ok(getHostLmsCode(), versionInfo,
				Duration.between(start, Instant.now()))))
			.onErrorResume(error -> Mono.just(PingResponse.error(getHostLmsCode(), versionInfo,
				error.getMessage(), error, Duration.between(start, Instant.now()))));
	}

	@Override
	public String getHostSystemType() {
		return "KOHA";
	}

	@Override
	public Mono<ConfigurationReport> checkConfiguration() {
		return Mono.zip(
				emptyWhenUnreadable(MappingVocabulary.ITEM_TYPE),
				emptyWhenUnreadable(MappingVocabulary.PATRON_TYPE),
				librariesOrEmpty(),
				systemPreferences(),
				opacVisibilityCheck(),
				bundleHostCheck(),
				ncipFallbackCheck())
			.map(answers -> buildConfigurationReport(answers.getT1(), answers.getT2(), answers.getT3())
				.withChecks(preferenceChecks(answers.getT4()))
				.withChecks(List.of(answers.getT5(), answers.getT6(), answers.getT7())))
			.onErrorResume(error -> Mono.just(ConfigurationReport.failed(getHostLmsCode(),
				"Could not read configuration from Koha: " + error.getMessage())));
	}

	private Mono<ConfigurationReport.Check> ncipFallbackCheck() {
		final var setting = "ncip-checkin-fallback";

		if (!config.isNcipCheckInFallbackEnabled()) {
			return Mono.just(new ConfigurationReport.Check(setting, "false",
				ConfigurationReport.CheckResult.NOT_CONFIGURED, "Off: the bundle route is the only check-in"));
		}

		if (ncipCheckIn == null) {
			return Mono.just(new ConfigurationReport.Check(setting, "true", ConfigurationReport.CheckResult.MISSING,
				"On without ncip-checkin-url and ncip-checkin-token"));
		}

		return ncipCheckIn.isAvailable()
			.map(answered -> answered
				? new ConfigurationReport.Check(setting, "true", ConfigurationReport.CheckResult.PRESENT, null)
				: new ConfigurationReport.Check(setting, "true", ConfigurationReport.CheckResult.MISSING,
					"The NCIP responder did not answer LookupVersion, or refused the token"));
	}

	private Mono<ConfigurationReport.Check> bundleHostCheck() {
		final var setting = "bundle-host-barcode";
		final var hostBarcode = config.getBundleHostBarcode();

		if (hostBarcode == null) {
			return Mono.just(new ConfigurationReport.Check(setting, null,
				ConfigurationReport.CheckResult.NOT_CONFIGURED,
				"Not set: DCB cannot check a virtual item in, so pickup anywhere and walk-up with this Koha as the borrower stop at the return"));
		}

		return client.getItemByBarcode(hostBarcode)
			.map(found -> found.length > 0
				? new ConfigurationReport.Check(setting, hostBarcode, ConfigurationReport.CheckResult.PRESENT, null)
				: new ConfigurationReport.Check(setting, hostBarcode, ConfigurationReport.CheckResult.MISSING,
					"No item in Koha has this barcode"))
			.defaultIfEmpty(new ConfigurationReport.Check(setting, hostBarcode,
				ConfigurationReport.CheckResult.MISSING, "No item in Koha has this barcode"))
			.onErrorResume(error -> Mono.just(new ConfigurationReport.Check(setting, hostBarcode,
				ConfigurationReport.CheckResult.UNKNOWN, "Could not read the item: " + kohaErrorMessage(error))));
	}

	private Mono<ConfigurationReport.Check> opacVisibilityCheck() {
		final var setting = "honour-opac-hidden-items";

		if (!config.honoursOpacHiddenItems()) {
			return Mono.just(new ConfigurationReport.Check(setting, "false",
				ConfigurationReport.CheckResult.NOT_CONFIGURED,
				"Off: copies OpacHiddenItems hides are offered unless the item ruleset mirrors it"));
		}

		final var reachable = new ConfigurationReport.Check(setting, "true",
			ConfigurationReport.CheckResult.PRESENT, null);

		return client.getOnePublicLibrary()
			.map(answered -> reachable)
			.defaultIfEmpty(reachable)
			.onErrorResume(error -> Mono.just(new ConfigurationReport.Check(setting, "true",
				ConfigurationReport.CheckResult.MISSING,
				"The anonymous public API is refused (RESTPublicAPI or RESTPublicAnonymousRequests off), so every availability check for this Koha fails")));
	}

	// Read on Koha 26.05 and later; an older Koha answers 404 and the API patron may lack
	// manage_sysprefs, so an unreadable preference is simply absent from the map
	private static final List<String> PREFERENCES_DCB_DEPENDS_ON = List.of(
		"AllowHoldPolicyOverride", "IndependentBranches", "canreservefromotherbranches",
		"ItemsDeniedRenewal", "OpacSuppression", "ReservesNeedReturns");

	private Mono<Map<String, String>> systemPreferences() {
		return Flux.fromIterable(PREFERENCES_DCB_DEPENDS_ON)
			.concatMap(name -> Mono.defer(() -> client.getSystemPreference(name))
				.filter(found -> found.length > 0 && found[0].getValue() != null)
				.map(found -> Map.entry(name, found[0].getValue()))
				.onErrorResume(error -> Mono.empty()))
			.collectMap(Map.Entry::getKey, Map.Entry::getValue);
	}

	/**
	 * What DCB needs each preference to say, and what it costs when it does not. PRESENT means
	 * "as DCB needs it", MISSING "not as DCB needs it", UNKNOWN "this Koha did not say".
	 */
	private List<ConfigurationReport.Check> preferenceChecks(Map<String, String> preferences) {
		final var independentBranches = "1".equals(preferences.get("IndependentBranches"));

		return List.of(
			preferenceCheck(preferences, "AllowHoldPolicyOverride", value -> "1".equals(value),
				"Off: DCB's override of its own virtual patron's blocks on supplier and pickup holds has no effect"),
			preferenceCheck(preferences, "IndependentBranches", value -> !"1".equals(value),
				"On: the API patron must act across branches (superlibrarian), or holds and item edits for other branches are refused"),
			preferenceCheck(preferences, "canreservefromotherbranches",
				value -> !independentBranches || "1".equals(value),
				"Off with IndependentBranches on: a patron may hold only their own branch's items, so supplier holds and co-tenant local requests are refused"),
			preferenceCheck(preferences, "ItemsDeniedRenewal",
				value -> value.contains("ccode") && value.contains(config.getNoRenewCollectionCode()),
				"Does not list ccode: [" + config.getNoRenewCollectionCode() + "], so renewal prevention fails on first use"),
			preferenceCheck(preferences, "OpacSuppression", value -> "1".equals(value),
				"Off: hidden records and DCB's virtual bibs show in the OPAC"),
			preferenceCheck(preferences, "ReservesNeedReturns", value -> "1".equals(value),
				"Automatic: a hold on a shelved item is waiting at placement and never transits to the sharing branch"));
	}

	private static ConfigurationReport.Check preferenceCheck(Map<String, String> preferences,
		String name, java.util.function.Predicate<String> asDcbNeedsIt, String whenNot) {

		final var value = preferences.get(name);

		if (value == null) {
			return new ConfigurationReport.Check(name, null, ConfigurationReport.CheckResult.UNKNOWN,
				"Not readable: Koha before 26.05 has no system preference route, or the API patron lacks manage_sysprefs");
		}

		return asDcbNeedsIt.test(value)
			? new ConfigurationReport.Check(name, value, ConfigurationReport.CheckResult.PRESENT, null)
			: new ConfigurationReport.Check(name, value, ConfigurationReport.CheckResult.MISSING, whenNot);
	}

	// Read once: the branch list and which of them may be collected from come from the same call
	private Mono<List<KohaLibrary>> librariesOrEmpty() {
		return client.getLibraries()
			.map(Arrays::asList)
			.defaultIfEmpty(List.of())
			.onErrorResume(error -> {
				log.warn("Could not read libraries from Koha at {}", getHostLmsCode(), error);

				return Mono.just(List.of());
			});
	}

	private ConfigurationReport buildConfigurationReport(List<ConfigurationReport.Entry> itemTypes,
		List<ConfigurationReport.Entry> patronTypes, List<KohaLibrary> kohaLibraries) {

		final var libraries = kohaLibraries.stream().map(KohaHostLmsClient::libraryEntry).toList();

		// A pickup location's localId must be one of these: Koha refuses a hold at any other
		final var pickupLibraries = kohaLibraries.stream()
			.filter(library -> TRUE.equals(library.getPickupLocation()))
			.map(KohaHostLmsClient::libraryEntry)
			.toList();

		final var checks = List.of(
			checkSetting("sharing-library-code", rawConfigValue("sharing-library-code"), pickupLibraries),
			checkSetting("virtual-item-library-code", rawConfigValue("virtual-item-library-code"), libraries),
			checkSetting("virtual-patron-category", config.getVirtualPatronCategory(), patronTypes));

		final var vocabularies = List.of(
			vocabulary("Item types", itemTypes),
			vocabulary("Patron categories", patronTypes),
			vocabulary("Libraries", libraries),
			vocabulary(ConfigurationReport.PICKUP_VOCABULARY, pickupLibraries));

		return new ConfigurationReport(getHostLmsCode(), ConfigurationReport.Status.CHECKED,
			null, checks, vocabularies);
	}

	private static ConfigurationReport.Entry libraryEntry(KohaLibrary library) {
		return new ConfigurationReport.Entry(library.getLibraryId(), library.getName());
	}

	private static ConfigurationReport.Check checkSetting(String setting, String configuredValue,
		List<ConfigurationReport.Entry> knownValues) {

		return ConfigurationReport.check(setting, configuredValue, knownValues, "Koha");
	}

	private static ConfigurationReport.Vocabulary vocabulary(String name,
		List<ConfigurationReport.Entry> entries) {

		return ConfigurationReport.vocabulary(name, entries, "Koha");
	}

	// Read from the raw config: the typed accessors throw on a missing required setting, and a
	// report has to name what is absent rather than die reading it
	private String rawConfigValue(String key) {
		final var value = getConfig().get(key);

		return value != null ? value.toString() : null;
	}

	@Override
	public Mono<String> deleteBib(String id) {
		if (id == null || id.isBlank()) {
			return Mono.error(new IllegalArgumentException("Bib ID is required to delete a Koha bib"));
		}

		log.info("Koha: Deleting bib record: {}", id);
		return client.deleteBib(id)
			.thenReturn("OK")
			.onErrorResume(KohaHostLmsClient::isNotFound, gone -> {
				log.info("Koha bib {} is already deleted", id);
				return Mono.just("OK");
			})
			.doOnError(e -> log.error("Failed to delete Koha bib {}: {}", id, e.getMessage()));
	}

	/**
	 * Acknowledged without a call. Koha has no item status to write: an item is in transit,
	 * waiting or on loan because a transfer, a hold or a checkout exists, and the circulation
	 * operations create those themselves. FOLIO and Polaris answer OK the same way for the
	 * states they cannot express.
	 */
	@Override
	public Mono<String> updateItemStatus(HostLmsItem hostLmsItem, CanonicalItemState crs) {
		log.info("Koha has no item status write; {} on item {} in {} is acknowledged",
			crs, getValueOrNull(hostLmsItem, HostLmsItem::getLocalId), getHostLmsCode());

		return Mono.just("OK");
	}

	/*** Hold request and checkout operations ***/

	@Override
	public Mono<String> cancelHoldRequest(CancelHoldRequestParameters parameters) {
		final String localRequestId = parameters.getLocalRequestId();

		// Can we add a cancel reason
		if (localRequestId == null || localRequestId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local request ID is required to cancel a Koha hold"));
		}

		log.info("Koha: Cancelling hold request: {}", localRequestId);
		return deleteHoldUnlessGone(localRequestId)
			.doOnError(e -> log.error("Failed to cancel Koha hold {}: {}", localRequestId, e.getMessage()));
	}

	@Override
	public Mono<String> checkOutItemToPatron(CheckoutItemCommand checkoutItemCommand) {
		final String patronId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getPatronId);
		final String itemId = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemId);
		final String itemBarcode = getValueOrNull(checkoutItemCommand, CheckoutItemCommand::getItemBarcode);

		if (isBlank(patronId) || (isBlank(itemId) && isBlank(itemBarcode))) {
			return Mono.error(new IllegalArgumentException(
				"Patron ID and an item id or barcode are required for Koha checkout"));
		}

		log.info("Koha: Checking out item {} (barcode {}) to patron {}", itemId, itemBarcode, patronId);

		// item_id names the item outright; the barcode is the fallback for a caller that has
		// nothing else
		final var request = new KohaCheckoutRequest(Long.valueOf(patronId),
			isBlank(itemId) ? null : Long.valueOf(itemId),
			isBlank(itemId) ? itemBarcode : null);

		return client.checkoutItem(request)
			.onErrorResume(KohaHostLmsClient::needsConfirmation,
				needsConfirmation -> confirmCheckout(request, patronId, itemId))
			.thenReturn("OK")
			.doOnError(e -> log.error("Koha checkout of item {} to patron {} failed: {}",
				itemId, patronId, e.getMessage()));
	}

	/**
	 * The confirmations Koha asks staff for that a consortium loan always answers yes to:
	 * the item is in transit to, reserved for, or waiting at the sharing branch for the very
	 * patron it is being lent to. Any other confirmation, and any blocker, is a refusal.
	 */
	private static final Set<String> ACCEPTED_CHECKOUT_CONFIRMATIONS = Set.of(
		"TRANSFERRED", "RESERVED", "RESERVE_WAITING");

	// Koha answers 412 when staff would be asked to confirm; the availability read says what,
	// and issues the token the confirmed checkout carries
	private Mono<KohaCheckout> confirmCheckout(KohaCheckoutRequest request, String patronId, String itemId) {
		if (isBlank(itemId)) {
			return Mono.error(Problem.builder()
				.withTitle("Koha wants the checkout confirmed")
				.withStatus(Status.PRECONDITION_FAILED)
				.withDetail("Koha asked for a confirmation before lending to patron %s in %s, and without the item id DCB cannot ask what"
					.formatted(patronId, getHostLmsCode()))
				.build());
		}

		return client.getCheckoutAvailability(patronId, itemId)
			.flatMap(availability -> {
				final var blockers = keysOf(availability.getBlockers());

				if (!blockers.isEmpty()) {
					return Mono.error(checkoutRefused("Koha refused the checkout", patronId, itemId,
						"blocked", blockers));
				}

				final var confirms = keysOf(availability.getConfirms());
				final var unaccepted = confirms.stream()
					.filter(confirm -> !ACCEPTED_CHECKOUT_CONFIRMATIONS.contains(confirm))
					.toList();

				if (!unaccepted.isEmpty() || isBlank(availability.getConfirmationToken())) {
					return Mono.error(checkoutRefused("Koha wants a confirmation DCB does not give",
						patronId, itemId, "confirmations", unaccepted.isEmpty() ? confirms : unaccepted));
				}

				log.info("Koha: confirming checkout of item {} to patron {} over {}", itemId, patronId, confirms);

				return client.checkoutItem(request, availability.getConfirmationToken());
			});
	}

	private ThrowableProblem checkoutRefused(String title, String patronId, String itemId,
		String parameter, List<String> reasons) {

		return Problem.builder()
			.withTitle(title)
			.withStatus(Status.PRECONDITION_FAILED)
			.withDetail("Koha did not lend item %s to patron %s in %s: %s"
				.formatted(itemId, patronId, getHostLmsCode(), String.join(", ", reasons)))
			.with(parameter, reasons)
			.with("hostLmsCode", getHostLmsCode())
			.build();
	}

	private static List<String> keysOf(Map<String, Object> answers) {
		return answers == null ? List.of() : List.copyOf(answers.keySet());
	}

	private static boolean needsConfirmation(Throwable error) {
		return statusOf(error) == HttpStatus.PRECONDITION_FAILED.getCode();
	}

	/**
	 * Koha's REST API has no check-in. Bundling an item with force_checkin returns it first, so
	 * the virtual item is linked into the DCB bundle host item and unlinked again (ADR 0002).
	 * Called on a borrowing Koha in RET-PUA and RET-EXP only, for the virtual item DCB deletes
	 * at finalisation.
	 */
	@Override
	public Mono<String> checkInItem(CheckInItemCommand checkInItemCommand) {
		final String itemId = getValueOrNull(checkInItemCommand, CheckInItemCommand::getItemId);

		if (isBlank(itemId)) {
			return Mono.error(new IllegalArgumentException("Item ID is required to check a Koha item in"));
		}

		return bundleCheckIn(itemId)
			.onErrorResume(bundleFailure -> ncipCheckIn != null,
				bundleFailure -> ncipFallbackCheckIn(itemId, checkInItemCommand, bundleFailure));
	}

	private Mono<String> bundleCheckIn(String itemId) {
		final var hostBarcode = config.getBundleHostBarcode();

		if (hostBarcode == null) {
			return Mono.error(checkInProblem("Koha check-in is not configured", itemId,
				"no bundle-host-barcode", "Set bundle-host-barcode to a never-lent DCB item at the sharing branch"));
		}

		return Mono.zip(bundleHost(hostBarcode), client.getItem(itemId))
			.flatMap(hostAndItem -> {
				final var hostItemId = String.valueOf(hostAndItem.getT1().getItemId());

				return client.addToBundle(hostItemId, hostAndItem.getT2().getExternalId())
					.then()
					// An earlier attempt bundled it, so the return happened; only the unlink is left
					.onErrorResume(error -> "already_bundled".equals(kohaErrorCode(error)), bundled -> Mono.empty())
					.then(client.removeFromBundle(hostItemId, itemId)
						.onErrorResume(KohaHostLmsClient::isNotFound, unlinked -> Mono.empty()))
					.thenReturn("OK");
			})
			.doOnSuccess(ok -> log.info("Koha item {} checked in at {} through bundle host {}",
				itemId, getHostLmsCode(), hostBarcode))
			.onErrorMap(error -> !(error instanceof ThrowableProblem problem
					&& problem.getParameters().containsKey("checkInRemedy")),
				error -> refusedCheckIn(itemId, hostBarcode, error));
	}

	/**
	 * Only with ncip-checkin-fallback on, and only once the bundle route has failed, including
	 * for want of a bundle host (ADR 0002). The address and token never reach a log or a problem.
	 */
	private Mono<String> ncipFallbackCheckIn(String itemId, CheckInItemCommand command, Throwable bundleFailure) {
		log.warn("Bundle check-in of Koha item {} at {} failed ({}); trying NCIP",
			itemId, getHostLmsCode(), checkInReason(bundleFailure));

		return virtualItemBarcode(itemId, command)
			.flatMap(ncipCheckIn::checkInItem)
			// Koha says the item is not on loan: it is already back, which is what was asked
			.onErrorResume(error -> "Item Not Checked Out".equals(ncipProblemType(error)), back -> Mono.just("OK"))
			.doOnSuccess(ok -> log.info("Koha item {} checked in at {} over NCIP", itemId, getHostLmsCode()))
			.onErrorMap(ncipFailure -> checkInProblem("Koha would not check the item in by either route", itemId,
				"bundle: " + checkInReason(bundleFailure) + "; NCIP: " + checkInReason(ncipFailure),
				isRefusedCredential(ncipFailure)
					? "The NCIP responder refused ncip-checkin-token; check it against the plugin's auth_token"
					: "Check the item in by hand in Koha"));
	}

	// The command carries the lender's barcode, which the virtual item was created with
	private Mono<String> virtualItemBarcode(String itemId, CheckInItemCommand command) {
		final var barcode = getValueOrNull(command, CheckInItemCommand::getItemBarcode);

		return !isBlank(barcode)
			? Mono.just(barcode)
			: client.getItem(itemId).mapNotNull(KohaItem::getExternalId);
	}

	private static String ncipProblemType(Throwable error) {
		return error instanceof ThrowableProblem problem
			? Objects.toString(problem.getParameters().get(NcipAdaptor.PROBLEM_TYPE), null)
			: null;
	}

	private static boolean isRefusedCredential(Throwable error) {
		return error instanceof HttpClientResponseException response
			&& (response.getStatus().getCode() == 401 || response.getStatus().getCode() == 403);
	}

	// What a failure was, in words that cannot carry a request's address
	private static String checkInReason(Throwable error) {
		if (error instanceof ThrowableProblem problem) {
			final var reason = problem.getParameters().get("reason");
			final var ncipType = problem.getParameters().get(NcipAdaptor.PROBLEM_TYPE);

			return reason != null ? String.valueOf(reason)
				: ncipType != null ? ncipType + ": " + problem.getDetail()
				: String.valueOf(problem.getTitle());
		}

		if (error instanceof HttpClientResponseException response) {
			return "HTTP " + response.getStatus().getCode();
		}

		return error.getClass().getSimpleName();
	}

	private Mono<KohaItem> bundleHost(String hostBarcode) {
		return client.getItemByBarcode(hostBarcode)
			.filter(items -> items.length > 0)
			.map(items -> items[0])
			.switchIfEmpty(Mono.error(() -> checkInProblem("Koha bundle host item not found", null,
				"no item has barcode " + hostBarcode,
				"Set bundle-host-barcode to the barcode of the DCB bundle host item at the sharing branch")));
	}

	private ThrowableProblem refusedCheckIn(String itemId, String hostBarcode, Throwable error) {
		final var code = kohaErrorCode(error);

		final var remedy = switch (code == null ? "" : code) {
			case "bundle_checked_out" -> "The bundle host item " + hostBarcode
				+ " is on loan; check it in and keep it out of circulation";
			case "failed_checkin" -> "Koha's AddReturn refused the item; check it in by hand";
			case "failed_nesting" -> "The bundle host item " + hostBarcode
				+ " is itself in a bundle, or the item is a bundle host; use a plain item";
			default -> "Check the item in by hand in Koha";
		};

		return checkInProblem("Koha would not check the item in", itemId,
			code != null ? code : kohaErrorMessage(error), remedy);
	}

	private ThrowableProblem checkInProblem(String title, String itemId, String reason, String remedy) {
		return Problem.builder()
			.withTitle(title)
			.withStatus(Status.BAD_GATEWAY)
			.withDetail("%s: item %s in %s".formatted(reason, itemId, getHostLmsCode()))
			.with("reason", reason)
			.with("checkInRemedy", remedy)
			.with("hostLmsCode", getHostLmsCode())
			.build();
	}

	// The error_code KohaApiClientImpl kept from Koha's error body, where there was one
	private static String kohaErrorCode(Throwable error) {
		if (error instanceof ThrowableProblem problem) {
			final var kept = problem.getParameters().get("Koha Error Code");

			return kept == null ? null : String.valueOf(kept);
		}

		return null;
	}

	@Override
	public Mono<String> deleteHold(DeleteCommand deleteCommand) {
		final String holdId = getValueOrNull(deleteCommand, DeleteCommand::getRequestId);

		if (holdId == null || holdId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Hold ID is required to delete a Koha hold"));
		}

		log.info("Koha: Deleting hold: {}", holdId);
		return deleteHoldUnlessGone(holdId)
			.doOnError(e -> log.error("Failed to delete Koha hold {}: {}", holdId, e.getMessage()));
	}

	// A filled or cancelled hold is already in old_reserves: there is nothing left to delete,
	// which is the outcome both a cancellation and a cleanup wanted
	private Mono<String> deleteHoldUnlessGone(String holdId) {
		return client.deleteHold(holdId)
			.thenReturn("OK")
			.onErrorResume(KohaHostLmsClient::isNotFound, gone -> {
				log.info("Koha hold {} is already filled or cancelled; nothing to delete", holdId);
				return Mono.just("OK");
			});
	}
	@Override
	public Mono<HostLmsRequest> getRequest(HostLmsRequest request) {
		final String localRequestId = getValueOrNull(request, HostLmsRequest::getLocalId);

		if (localRequestId == null || localRequestId.isBlank()) {
			return Mono.error(new IllegalArgumentException("Local request ID is required to fetch a Koha hold"));
		}

		log.debug("Fetching Koha hold request by ID: {}", localRequestId);

		return client.getHold(localRequestId)
			.map(this::toHostLmsRequest)
			.onErrorResume(KohaHostLmsClient::isNotFound, notFound -> oldHold(localRequestId))
			.doOnError(e -> log.error("Failed to retrieve Koha hold request {}: {}", localRequestId, e.getMessage()));
	}

	/**
	 * Koha moves a hold to old_reserves the moment it is filled or cancelled; GET /holds/{id}
	 * then answers 404 and only the list with old=true still knows it. Its cancellation date
	 * says which of the two happened. A hold found in neither place is gone, which the
	 * workflow treats as it treats filled.
	 */
	private Mono<HostLmsRequest> oldHold(String localRequestId) {
		return client.getOldHold(localRequestId)
			.filter(oldHolds -> oldHolds.length > 0)
			.map(oldHolds -> oldHoldToHostLmsRequest(oldHolds[0]))
			.defaultIfEmpty(HostLmsRequest.builder()
				.localId(localRequestId)
				.status(HostLmsRequest.HOLD_MISSING)
				.build());
	}

	private HostLmsRequest toHostLmsRequest(KohaHoldResponse hold) {
		return HostLmsRequest.builder()
			.localId(String.valueOf(hold.getHoldId()))
			.status(checkHoldStatus(hold.getStatus(), hold.getItemId()))
			.rawStatus(hold.getStatus())
			.requestedItemId(itemIdOrNull(hold))
			.build();
	}

	private HostLmsRequest oldHoldToHostLmsRequest(KohaHoldResponse oldHold) {
		final var cancelled = oldHold.getCancellationDate() != null;

		return HostLmsRequest.builder()
			.localId(String.valueOf(oldHold.getHoldId()))
			.status(cancelled ? HostLmsRequest.HOLD_CANCELLED : HostLmsRequest.HOLD_MISSING)
			.rawStatus(oldHold.getStatus())
			.requestedItemId(itemIdOrNull(oldHold))
			.build();
	}

	private static String itemIdOrNull(KohaHoldResponse hold) {
		return hold.getItemId() != null ? String.valueOf(hold.getItemId()) : null;
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(PlaceHoldRequestParameters parameters) {
		// For an item level hold, we must provide patron ID, bib ID, item ID,  and pickup library ID (for borrowing, this corresponds to the pickup location in Koha)
		// We can provide notes, a priority flag, a hold date, and an item group id
		// Construct Koha hold request
		// Deal with it if the values aren't integers
		log.info("placeHoldRequestAtBorrowingAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		// In pickup anywhere and walk-up the patron collects at another library, whose branch
		// does not exist in this Koha
		return Mono.just(parameters)
			.map(p -> WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW.equals(p.getActiveWorkflow())
				|| WorkflowConstants.EXPEDITED_WORKFLOW.equals(p.getActiveWorkflow())
				? getDcbSharingLibraryCode() : resolveLibraryCode(p))
			.flatMap(lib -> submitKohaHold(parameters, lib, false));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtSupplyingAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		// If we are dealing with the expedited checkout workflow, the pickup is NOT external.
		// Otherwise, the pickup is external
		return Mono.just(parameters)
			.map(p -> WorkflowConstants.EXPEDITED_WORKFLOW.equals(p.getActiveWorkflow())
				? resolveLibraryCode(p) : getDcbSharingLibraryCode())
			.flatMap(lib -> submitKohaHold(parameters, lib, true));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtPickupAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtPickupAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		return Mono.just(parameters)
			.map(this::resolveLibraryCode)
			.flatMap(lib -> submitKohaHold(parameters, lib, true));
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtLocalAgency(PlaceHoldRequestParameters parameters) {
		log.info("placeHoldRequestAtLocalAgency patron={} item={}", parameters.getLocalPatronId(), parameters.getLocalItemId());
		return Mono.just(parameters)
			.map(this::resolveLibraryCode)
			.flatMap(lib -> submitKohaHold(parameters, lib, false));
	}

	/**
	 * The supplier and pickup holds are for DCB's own virtual patron, whose expiry or debt
	 * must not stop a consortium loan, so they override patron blocks. The borrower and
	 * local holds are for a real patron, and the library's blocks stand.
	 */
	private Mono<LocalRequest> submitKohaHold(PlaceHoldRequestParameters parameters,
		String pickupLibraryId, boolean overridePatronBlocks) {

		return Mono.defer(() -> {
			KohaHoldRequest holdRequest = KohaHoldRequest.builder()
				.itemId(Integer.valueOf(parameters.getLocalItemId()))
				.biblioId(Integer.valueOf(parameters.getLocalBibId()))
				.patronId(Integer.valueOf(parameters.getLocalPatronId()))
				.pickupLibraryId(pickupLibraryId)
				.notes(parameters.getNote())
//				.expirationDate()
//				.nonPriority()
//				.itemTypeId()
//				.holdDate()
				.build();

			final var placement = overridePatronBlocks
				? client.placeHoldRequestOverridingPatronBlocks(holdRequest)
				: client.placeHoldRequest(holdRequest);

			return placement
				.onErrorMap(KohaHostLmsClient::isHoldRefusal,
					refused -> holdRefused(refused, parameters, pickupLibraryId))
				.map(response -> mapKohaHoldToLocalRequest(response, parameters))
				.doOnSubscribe(s -> log.info("Submitting Koha HOLD patron={} item={} pickupLibrary={}",
					parameters.getLocalPatronId(), parameters.getLocalItemId(), pickupLibraryId));
		});
	}

	/**
	 * Koha's 403 carries "Hold cannot be placed. Reason: <status>" from CanItemBeReserved and its
	 * 409 the patron block from can_place_holds. The reason is the administrator's action, so the
	 * problem names it and what to do; the request audit is where they read it.
	 */
	private static final Map<String, String> HOLD_REFUSAL_REMEDIES = Map.ofEntries(
		Map.entry("cannotReserveFromOtherBranches",
			"IndependentBranches is on and canreservefromotherbranches is off, so this patron may hold only their own branch's items. Allow holds from other branches, or turn IndependentBranches off; for DCB's own virtual patron, AllowHoldPolicyOverride lets DCB's override apply."),
		Map.entry("libraryNotPickupLocation",
			"The branch is not flagged as a pickup location in Koha. Flag it, or point the DCB pickup location at a branch that is."),
		Map.entry("tooManyReserves",
			"The patron is at the hold limit the circulation rule sets for this branch, category and item type."),
		Map.entry("noReservesAllowed",
			"The circulation rule for this branch, patron category and item type allows no holds."),
		Map.entry("notReservable",
			"The circulation rule for this branch, patron category and item type allows no holds, or no on-shelf holds."),
		Map.entry("itemAlreadyOnHold", "The patron already holds this item."),
		Map.entry("alreadypossession", "The patron already has this item on loan."),
		Map.entry("ageRestricted", "The item is age-restricted for this patron."),
		Map.entry("pickupNotInHoldGroup", "The pickup branch is outside the item's hold group."),
		Map.entry("branchNotInHoldGroup", "The patron's branch is outside the item's hold group."));

	private static final Map<String, String> PATRON_BLOCK_REMEDIES = Map.of(
		"debt_limit", "The patron owes more than Koha allows for holds.",
		"expired", "The patron's account has expired.",
		"restricted", "The patron is restricted.",
		"hold_limit", "The patron is at their hold limit.",
		"card_lost", "The patron's card is marked lost.",
		"bad_address", "The patron's address is flagged as incorrect.");

	// 403 is a policy refusal, 409 a block on the patron; both carry Koha's reason
	private static boolean isHoldRefusal(Throwable error) {
		final var status = statusOf(error);

		return status == HttpStatus.FORBIDDEN.getCode() || status == HttpStatus.CONFLICT.getCode();
	}

	private static final java.util.regex.Pattern REFUSAL_REASON =
		java.util.regex.Pattern.compile("Reason:\\s*(\\w+)");

	private Throwable holdRefused(Throwable refused, PlaceHoldRequestParameters parameters,
		String pickupLibraryId) {

		final var message = kohaErrorMessage(refused);
		final var matcher = REFUSAL_REASON.matcher(message);
		final var reason = matcher.find() ? matcher.group(1) : message;
		final var remedy = HOLD_REFUSAL_REMEDIES.containsKey(reason)
			? HOLD_REFUSAL_REMEDIES.get(reason)
			: PATRON_BLOCK_REMEDIES.getOrDefault(reason,
				"See Koha's circulation rules and hold policies for this branch, patron category and item type.");

		return Problem.builder()
			.withTitle("Koha refused the hold")
			.withStatus(Status.valueOf(statusOf(refused)))
			.withDetail("Koha did not allow a hold for patron %s on item %s at branch %s in %s: %s"
				.formatted(parameters.getLocalPatronId(), parameters.getLocalItemId(), pickupLibraryId,
					getHostLmsCode(), reason))
			.with("reason", reason)
			.with("remedy", remedy)
			.with("hostLmsCode", getHostLmsCode())
			.build();
	}

	// The message KohaApiClientImpl kept from Koha's error body, where there was one
	private static String kohaErrorMessage(Throwable error) {
		if (error instanceof ThrowableProblem problem) {
			final var kept = problem.getParameters().get("Koha Error Message");

			if (kept != null) {
				return String.valueOf(kept);
			}
		}

		return String.valueOf(error.getMessage());
	}

	private LocalRequest mapKohaHoldToLocalRequest(KohaHoldResponse response,
		PlaceHoldRequestParameters parameters) {

		return LocalRequest.builder()
			.localId(String.valueOf(response.getHoldId()))
			.localStatus(checkHoldStatus(response.getStatus(), response.getItemId()))
			.rawLocalStatus(response.getStatus())
			.requestedItemId(itemIdOrNull(response))
			.requestedItemBarcode(requestedItemBarcode(response, parameters))
			.build();
	}

	/**
	 * The barcode of the item the hold was placed on.
	 * <p>
	 * Koha's POST /api/v1/holds answers with a bare hold. The operation takes no
	 * x-koha-embed - only listHolds does - so the item is never embedded and
	 * response.getItem() is always null. Reading straight through it failed every Koha
	 * hold placement with a NullPointerException, whatever the pickup location was.
	 * <p>
	 * The caller already knows the barcode of the item it asked to hold, so prefer that,
	 * and tolerate its absence: the pickup agency path does not set one at all.
	 */
	private static String requestedItemBarcode(KohaHoldResponse response,
		PlaceHoldRequestParameters parameters) {

		final var embeddedBarcode = getValueOrNull(response,
			KohaHoldResponse::getItem, KohaItem::getExternalId);

		return embeddedBarcode != null
			? embeddedBarcode
			: getValueOrNull(parameters, PlaceHoldRequestParameters::getLocalItemBarcode);
	}

	/**
	 * Koha's status is the reserves.found column: null until the hold is filled, T in
	 * transit to the pickup branch, P in processing there, W waiting on the shelf. An
	 * item-level hold is confirmed from the moment it exists, as Sierra reports "0" with
	 * an item, which is what HandleSupplierRequestConfirmed waits for.
	 */
	private static String checkHoldStatus(String kohaStatus, Long itemId) {
		if (kohaStatus == null) {
			return itemId != null ? HostLmsRequest.HOLD_CONFIRMED : HostLmsRequest.HOLD_PLACED;
		}

		return switch (kohaStatus.toUpperCase()) {
			case "W" -> HostLmsRequest.HOLD_READY;
			case "T", "P" -> HostLmsRequest.HOLD_TRANSIT;
			default -> HostLmsRequest.HOLD_PLACED;
		};
	}

	/**
	 * Re-resolution: the virtual item now stands for a different supplier's copy, so it
	 * takes that copy's barcode (and item type, where one is given). Only the changed
	 * fields are sent, as denyRenewalOfItem does. The hold is on the item, not on the
	 * barcode, so it needs no change.
	 */
	@Override
	public Mono<LocalRequest> updateHoldRequest(LocalRequest localRequest) {
		final var itemId = getValueOrNull(localRequest, LocalRequest::getRequestedItemId);
		final var barcode = getValueOrNull(localRequest, LocalRequest::getRequestedItemBarcode);

		if (isBlank(itemId) || isBlank(barcode)) {
			return Mono.error(new IllegalArgumentException(
				"Item ID and barcode are required to update a Koha virtual item"));
		}

		final var canonicalItemType = getValueOrNull(localRequest, LocalRequest::getCanonicalItemType);
		final var itemType = canonicalItemType == null
			? Mono.<Optional<String>>just(Optional.empty())
			: getMappedItemType(canonicalItemType).map(Optional::of);

		return Mono.zip(biblioIdFor(localRequest.getBibId(), itemId), itemType)
			.flatMap(found -> client.updateItem(found.getT1(), itemId, KohaItem.builder()
				.externalId(barcode)
				.itemTypeId(found.getT2().orElse(null))
				.build()))
			.thenReturn(localRequest);
	}

	/*** Renewal operations ***/

	/**
	 * Stops the borrowing patron renewing a virtual item, because the owning library now has a
	 * hold on the real one.
	 *
	 * <h2>Onboarding requirement - every participating Koha must be configured for this</h2>
	 *
	 * Koha has no per-item renewal limit to lower. Renewal is decided by
	 * {@code C4::Circulation::CanBookBeRenewed}, and its only item level hook is the
	 * <strong>{@code ItemsDeniedRenewal}</strong> system preference: a YAML map of item columns to
	 * values that may not be renewed. DCB stamps a collection code on the item; the Koha
	 * administrator must list it, or nothing is prevented:
	 *
	 * <pre>
	 * ItemsDeniedRenewal:
	 *   ccode: [DCB_NO_RENEW]
	 * </pre>
	 *
	 * The value is the {@code no-renew-collection-code} Host LMS property (default
	 * {@code DCB_NO_RENEW}) and it must match the system preference exactly. It should also exist
	 * as a CCODE authorised value, so staff see a meaningful collection rather than a bare code.
	 *
	 * <h2>Why a collection code and not the not-for-loan status</h2>
	 *
	 * <ul>
	 *   <li>{@code notforloan} blocks <em>checkout</em>, not renewal. It only ever denies a
	 *       renewal when the library happens to have listed it in {@code ItemsDeniedRenewal},
	 *       so relying on it means relying on configuration we did not ask for.</li>
	 *   <li>DCB reads any non-zero not-for-loan status as {@link HostLmsItem#ITEM_MISSING}
	 *       (see {@code deriveItemStatusForHostLmsItem}), and nothing clears the flag. The
	 *       borrower's item would never read AVAILABLE or TRANSIT again, so
	 *       {@code HandleBorrowerRequestReturnTransit} could not fire and the request would be
	 *       stranded in LOANED after the patron returned the book.</li>
	 *   <li>{@code ccode} is inert for DCB's own status derivation, and unused by this adapter
	 *       otherwise, so marking it changes renewability and nothing else.</li>
	 * </ul>
	 *
	 * The marker cannot be a permanent property of virtual items: DCB renews through
	 * {@code POST /checkouts/{id}/renewals}, which goes through the same eligibility check, so a
	 * blanket rule would block legitimate renewals too. It is applied only when a hold is detected.
	 *
	 * <p>Because the system preference is deployment state DCB cannot see, the outcome is verified
	 * against Koha rather than assumed - see {@link #confirmRenewalIsDenied}.
	 */
	@Override
	public Mono<Void> preventRenewalOnLoan(PreventRenewalCommand prc) {
		log.info("Koha: preventRenewalOnLoan({})", prc);

		final String itemId = prc.getItemId();

		if (isBlank(itemId)) {
			return Mono.error(new IllegalArgumentException("Item ID is required to prevent renewal in Koha"));
		}

		return client.getItem(itemId)
			.flatMap(kohaItem -> denyRenewalOfItem(itemId, kohaItem))
			// Deferred so that nothing is asked of Koha until the item has actually been marked
			.then(Mono.defer(() -> confirmRenewalIsDenied(itemId)));
	}

	/**
	 * Marks the item as denied renewal, and tells staff why in the same call.
	 * <p>
	 * Only the changed fields are sent. Koha has no item PATCH, so an update is a PUT of the whole
	 * item, and echoing back the item as read would both overwrite anything changed in Koha since
	 * the read and send computed fields (effective_item_type_id and its siblings) that the update
	 * rejects because they are not item columns.
	 */
	private Mono<KohaItem> denyRenewalOfItem(String itemId, KohaItem currentItem) {
		if (!DCB_VIRTUAL_ITEM_CALL_NUMBER.equals(currentItem.getCallnumber())) {
			return raiseError(Problem.builder()
				.withTitle("Refusing to prevent renewal on an item DCB did not create")
				.withDetail("Item %s in %s does not carry the %s call number"
					.formatted(itemId, getHostLmsCode(), DCB_VIRTUAL_ITEM_CALL_NUMBER))
				.with("itemId", itemId)
				.with("hostLmsCode", getHostLmsCode())
				.build());
		}

		final var biblioId = getValueOrNull(currentItem, KohaItem::getBiblioId);

		if (biblioId == null) {
			return raiseError(Problem.builder()
				.withTitle("Koha item cannot be updated without its biblio")
				.withDetail("Item %s in %s was returned without a biblio_id"
					.formatted(itemId, getHostLmsCode()))
				.with("itemId", itemId)
				.build());
		}

		final var update = KohaItem.builder()
			.collectionCode(config.getNoRenewCollectionCode())
			.internalNotes(withDoNotRenewNote(currentItem.getInternalNotes(), config.getDoNotRenewNote()))
			.build();

		return client.updateItem(String.valueOf(biblioId), itemId, update);
	}

	/**
	 * Renewal prevention can fire more than once for the same request, so the note is added only
	 * when it is not already there - otherwise it grows without bound.
	 */
	static String withDoNotRenewNote(String existingNotes, String note) {
		if (isBlank(existingNotes)) {
			return note;
		}

		if (existingNotes.contains(note)) {
			return existingNotes;
		}

		return existingNotes + " | " + note;
	}

	/**
	 * Asks Koha whether the loan can still be renewed, and fails when it can.
	 * <p>
	 * Setting the marker is not the same as preventing the renewal: it only works if this Koha
	 * lists the collection code in ItemsDeniedRenewal. Reporting prevention we did not achieve is
	 * worse than failing, because the failure is audited against the request and the borrowing
	 * library is told the loan cannot be renewed here.
	 * <p>
	 * An item with no current checkout is not a failure - there is no renewal to prevent - but it
	 * leaves the marker unverified, so it is logged as such.
	 */
	private Mono<Void> confirmRenewalIsDenied(String itemId) {
		return client.getCheckoutsForItem(itemId)
			.flatMap(checkouts -> {
				if (checkouts == null || checkouts.length == 0) {
					log.warn("Koha item {} in {} has no current checkout, so renewal prevention could not be confirmed",
						itemId, getHostLmsCode());

					return Mono.<Void>empty();
				}

				return client.allowsRenewal(String.valueOf(checkouts[0].getCheckoutId()))
					.flatMap(renewability -> renewalStillAllowed(renewability)
						? renewalNotPrevented(itemId, renewability)
						: confirmed(itemId, renewability));
			});
	}

	private static boolean renewalStillAllowed(KohaRenewability renewability) {
		// A missing answer is not a denial: fail closed rather than assume the loan is protected
		return !Boolean.FALSE.equals(getValueOrNull(renewability, KohaRenewability::getAllowsRenewal));
	}

	private Mono<Void> renewalNotPrevented(String itemId, KohaRenewability renewability) {
		return raiseError(Problem.builder()
			.withTitle("Koha still allows this loan to be renewed")
			.withDetail(("Item %s in %s was marked with collection code %s, but Koha reports the loan as "
				+ "renewable. Check that this Koha's ItemsDeniedRenewal system preference contains "
				+ "ccode: [%s]")
				.formatted(itemId, getHostLmsCode(), config.getNoRenewCollectionCode(),
					config.getNoRenewCollectionCode()))
			.with("itemId", itemId)
			.with("hostLmsCode", getHostLmsCode())
			.with("renewability", String.valueOf(renewability))
			.build());
	}

	private Mono<Void> confirmed(String itemId, KohaRenewability renewability) {
		log.info("Koha confirmed renewal is denied for item {} in {}, reason: {}",
			itemId, getHostLmsCode(), renewability.getError());

		return Mono.empty();
	}

	@Override
	public Mono<HostLmsRenewal> renew(HostLmsRenewal hostLmsRenewal) {
		final String itemId = getValueOrNull(hostLmsRenewal, HostLmsRenewal::getLocalItemId);

		if (isBlank(itemId)) {
			return Mono.error(new IllegalArgumentException("Item ID is required to renew a Koha loan"));
		}

		// The workflow's request id is the hold; Koha renews a checkout, and only the item's
		// current loan knows that id
		return client.getCheckoutsForItem(itemId)
			.filter(checkouts -> checkouts.length > 0)
			.map(checkouts -> String.valueOf(checkouts[0].getCheckoutId()))
			.switchIfEmpty(Mono.error(() -> Problem.builder()
				.withTitle("Koha has no loan to renew")
				.withStatus(Status.CONFLICT)
				.withDetail("Item %s in %s is not on loan, so there is no checkout to renew"
					.formatted(itemId, getHostLmsCode()))
				.with("hostLmsCode", getHostLmsCode())
				.build()))
			.flatMap(checkoutId -> client.renewCheckout(checkoutId)
				.doOnNext(renewed -> log.info("Koha: renewed checkout {} of item {}, due {}",
					checkoutId, itemId, renewed.getDueDate())))
			.thenReturn(hostLmsRenewal)
			.doOnError(e -> log.error("Koha renewal of item {} failed: {}", itemId, e.getMessage()));
	}
}
