package org.olf.dcb.preferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What stored preference content may contain. Allow-lists, so a column added to a grid is not
 * remembered until it is added here: the safe direction for patron data.
 */
public final class PreferenceVocabulary {
	public static final String ADMIN_APP = "admin";
	public static final String LIBRARIES_APP = "libraries";
	public static final Set<String> APPS = Set.of(ADMIN_APP, LIBRARIES_APP);

	public static final Set<String> THEME_MODES = Set.of("light", "dark", "highContrast");
	public static final Set<String> TEXT_SIZES = Set.of("small", "normal", "large", "largest");
	public static final Set<String> DENSITIES = Set.of("comfortable", "compact");
	public static final Set<String> FONT_NAMES = Set.of("roboto", "system", "atkinsonHyperlegible", "inter", "lexend");
	public static final Set<String> MOTIONS = Set.of("system", "full", "reduced");
	public static final Set<String> LANGUAGES = Set.of("en-US", "en-GB");

	/** A grid entry in an app slice holds these and nothing else; the page number stays in the session. */
	public static final Set<String> GRID_KEYS = Set.of("filterModel", "sortModel", "columnVisibilityModel", "pageSize");
	public static final int MIN_PAGE_SIZE = 5;
	public static final int MAX_PAGE_SIZE = 100;

	/** Free text over every column, so no rule can say what it holds: never stored. */
	public static final String QUICK_FILTER_KEY = "quickFilterValues";

	/** A patron filter is stored as the patrons' internal ids, never a barcode. */
	public static final String PATRON_FIELD = "patron";
	public static final int MAX_PATRONS_IN_FILTER = 10;

	public static final String FIELD_KEY = "field";

	private static final Set<String> REQUEST_FIELDS = Set.of(
		"activeWorkflow", "canonicalItemType", "canonicalPtype", "clusterRecordTitle", "dateCreated",
		"dateUpdated", "description", "elapsedTimeInCurrentStatus", "errorMessage", "id", "isExpeditedCheckout",
		"isManuallySelectedItem", "itemBarcode", "localItemId", "localItemStatus", "localItemType",
		"localRequestId", "localRequestStatus", "nextExpectedStatus", "outOfSequenceFlag", "patronHostlmsCode",
		"pickupItemId", "pickupLibrary", "pickupLocationCode", "pickupRequestId", "pickupRequestStatus",
		"placedByStaff", "pollCountForCurrentStatus", "previousStatus", "rawLocalItemStatus",
		"rawLocalRequestStatus", "renewalCount", "resolutionCount", "status", "supplyingAgencyCode", "tagCode",
		PATRON_FIELD);

	/** Saved views are offered on the request grids only. */
	public static final Map<String, Set<String>> VIEW_FIELDS = Map.of(
		"admin.patronRequests", REQUEST_FIELDS,
		"admin.supplierRequests", REQUEST_FIELDS,
		"libraries.borrowing", REQUEST_FIELDS,
		"libraries.supplying", REQUEST_FIELDS);

	/**
	 * Every field an app's grids may remember a filter or sort on. Not here, deliberately:
	 * patronBarcode, requesterNote and noteText (text people wrote), fullName and lastEditedBy (people).
	 */
	public static final Map<String, Set<String>> GRID_FIELDS = Map.of(
		ADMIN_APP, union(REQUEST_FIELDS, Set.of(
			"abbreviatedName", "action", "actionInfo", "address", "agencyCode", "authProfile", "availabilityDate",
			"changeCategory", "changeReferenceUrl", "clientConfigIngest", "clusterRecordId", "code", "context",
			"domain", "dueDate", "entityId", "entityType", "environment", "fromCategory", "fromContext", "fromValue",
			"healthStatus", "holdCount", "hostLmsCatalogue", "hostLmsCirculation", "hostSystemName", "ils",
			"isBorrowingAgency", "isEnabledForPickupAnywhere", "isPickup", "isRequestable", "isSupplyingAgency",
			"isSuppressed", "lastImported", "latestVersion", "localId", "lowerBound", "mappedValue", "name",
			"printLabel", "processVersion", "reason", "releaseStatus", "serviceName", "sourceRecordId",
			"sourceSystemId", "stageName", "targetContext", "timestampLogged", "title", "toCategory", "toContext",
			"toValue", "translationTemplate", "upperBound", "version")),
		LIBRARIES_APP, union(REQUEST_FIELDS, Set.of(
			"agencyCode", "auditDate", "availabilityDate", "briefDescription", "clusterRecordId", "code", "context",
			"domain", "dueDate", "fromCategory", "fromContext", "fromStatus", "fromValue", "holdCount",
			"hostSystemName", "isPickup", "isPickupAnywhere", "isRequestable", "isSuppressed", "lastImported",
			"localId", "lowerBound", "mappedValue", "name", "patronRequestTitle", "printLabel", "processVersion",
			"sourceRecordId", "sourceSystemId", "targetContext", "title", "toCategory", "toContext", "toStatus",
			"toValue", "upperBound")));

	private PreferenceVocabulary() {
	}

	public static String describe(Set<String> allowed) {
		return String.join(", ", allowed.stream().sorted().toList());
	}

	private static Set<String> union(Set<String> first, Set<String> second) {
		final Set<String> all = new HashSet<>(first);
		all.addAll(second);
		return Set.copyOf(all);
	}
}
