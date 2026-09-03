package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Every paged GraphQL fetcher either narrows its results to the caller's own libraries, or
 * says in this file why it does not.
 *
 * <h2>Why this exists</h2>
 *
 * {@code /graphql} is behind {@code isAuthenticated()} and nothing else, so each fetcher
 * decides its own authorisation. Reference value mappings were scoped; numeric range
 * mappings, sitting twelve lines above them and holding the same class of data, were not.
 * Patron requests were scoped; the audits of those same requests were not, and DCB Admin
 * for Libraries reaches an audit by id straight from a URL.
 *
 * Nothing failed when they drifted. That is the whole argument for a structural test rather
 * than a paragraph in a review checklist: the defect is an <em>absence</em>, and an absence
 * is exactly what a reviewer reading a diff does not see.
 *
 * <h2>The allow-list is the important half</h2>
 *
 * A test with no exemption mechanism gets deleted the first time it is right about something
 * inconvenient. Several of these fetchers genuinely must stay open - DCB Admin for Libraries
 * needs the full library roster to render its filter dropdowns, and pickup-anywhere makes
 * restricting locations actively harmful. Each such fetcher is named below with the reason,
 * so the decision is recorded where it can be argued with rather than inferred from silence.
 *
 * Adding a fetcher therefore forces a choice: scope it, gate it to consortium roles, or
 * write down why neither. What it can no longer do is default to open by omission.
 */
class AgencyScopeArchitectureTests {

	private static final String FETCHERS =
		"org/olf/dcb/graphql/DataFetchers.java";

	/** A paged fetcher: the shape that returns a list a caller can filter. */
	private static final Pattern PAGED_FETCHER = Pattern.compile(
		"DataFetcher<CompletableFuture<Page<.*?>>>\\s+(\\w+)\\(\\)");

	/**
	 * A floor, not an exact count. Exact trains everyone to bump a number without reading
	 * why it moved.
	 */
	private static final int KNOWN_MINIMUM_FETCHERS = 18;

	/**
	 * Fetchers that return data no library owns, with the reason each one is open.
	 *
	 * These are answers, not deferrals. Anything genuinely unresolved belongs in the
	 * failing list, not here.
	 */
	private static final Map<String, String> DELIBERATELY_UNSCOPED = unscoped();

	private static Map<String, String> unscoped() {
		final var reasons = new LinkedHashMap<String, String>();

		reasons.put("getAgenciesDataFetcher",
			"Directory data. An agency roster is not confidential and DAFL resolves codes against it.");
		reasons.put("getPaginatedAgencyGroupsDataFetcher",
			"Directory data, as agencies are.");
		reasons.put("getLocationsDataFetcher",
			"Deliberately shared - see AgencyAccessScope. Pickup-anywhere makes hiding another "
				+ "library's pickup locations actively harmful.");
		reasons.put("getHostLMSDataFetcher",
			"The roster is directory data; the sensitive part is clientConfig, which is redacted "
				+ "for restricted callers inside the fetcher.");
		reasons.put("getLibrariesDataFetcher",
			"DAFL needs the full library list to render filter dropdowns. A library roster is not "
				+ "confidential.");
		reasons.put("getLibraryGroupsDataFetcher",
			"Directory data, as libraries are.");
		reasons.put("getConsortiaDataFetcher",
			"One consortium, and its branding is served anonymously to discovery apps anyway. "
				+ "Gated to ADMINISTRATIVE.");
		reasons.put("getRolesDataFetcher",
			"The role vocabulary itself, not anybody's holding of a role.");
		reasons.put("getFunctionalSettingsDataFetcher",
			"Consortium-wide switches that govern every library equally. A library needs to read "
				+ "the ones that constrain it.");

		// The two below are operational internals rather than library data, so no agency
		// predicate applies - but neither carries a role check either, which means
		// DISCOVERY_SERVICE and INTERNAL_API principals reach them. That is a narrower
		// question than this feature's, and it is recorded here rather than left silent.
		reasons.put("getProcessStateDataFetcher",
			"Ingest process state - operational, not library-owned. NOTE: no role check; a "
				+ "candidate for GraphQLRoles.CONSORTIUM in its own change.");
		reasons.put("getAlarmsDataFetcher",
			"System alarms - operational, not library-owned. NOTE: no role check; a candidate "
				+ "for GraphQLRoles.CONSORTIUM in its own change.");

		return Map.copyOf(reasons);
	}

	@Test
	void everyPagedFetcherIsScopedOrDeliberatelyOpen() throws IOException {
		final var offenders = new ArrayList<String>();

		for (final var fetcher : pagedFetchers()) {
			if (isProtected(fetcher.body()) || DELIBERATELY_UNSCOPED.containsKey(fetcher.name())) {
				continue;
			}

			offenders.add(fetcher.name());
		}

		assertTrue(offenders.isEmpty(),
			() -> "A paged fetcher runs whatever filter the client sent, so one that adds no "
				+ "scope of its own returns the whole consortium to anybody who drops their "
				+ "filter. Narrow it with AgencyAccessScope / MappingAccessService, gate it "
				+ "with GraphQLRoles.CONSORTIUM, or name it in DELIBERATELY_UNSCOPED with the "
				+ "reason: " + offenders);
	}

	@Test
	void nothingIsExemptedThatIsNoLongerThere() throws IOException {
		final var names = pagedFetchers().stream().map(PagedFetcher::name).toList();

		final var stale = DELIBERATELY_UNSCOPED.keySet().stream()
			.filter(exempt -> !names.contains(exempt))
			.toList();

		// An exemption for a fetcher that has been renamed or deleted is worse than none:
		// it reads as a decision somebody took about code that no longer exists, and the
		// renamed fetcher is silently unexamined.
		assertTrue(stale.isEmpty(),
			() -> "DELIBERATELY_UNSCOPED names fetchers that no longer exist: " + stale);
	}

	@Test
	void everyExemptionStatesAReason() {
		final var silent = DELIBERATELY_UNSCOPED.entrySet().stream()
			.filter(entry -> entry.getValue().isBlank())
			.map(Map.Entry::getKey)
			.toList();

		assertTrue(silent.isEmpty(),
			() -> "An exemption with no reason is an oversight wearing a decision's clothes: "
				+ silent);
	}

	@Test
	void theSurfaceIsNotEmpty() throws IOException {
		// Guard the guard: renaming the class or changing the fetcher signature would make
		// every assertion above pass over an empty list.
		final var found = pagedFetchers().size();

		assertTrue(found >= KNOWN_MINIMUM_FETCHERS,
			() -> "Found " + found + " paged fetchers, expected at least "
				+ KNOWN_MINIMUM_FETCHERS + ". Has DataFetchers moved or the signature changed?");
	}

	/**
	 * Scoped by an agency predicate, by the Host LMS predicate the mapping tables use, or
	 * gated to consortium roles because the entity has no agency to narrow on.
	 */
	private static boolean isProtected(String body) {
		return body.contains("AgencyAccessScope.restrict")
			|| body.contains("mappingAccessService.restrict")
			|| body.contains("GraphQLRoles.CONSORTIUM");
	}

	private record PagedFetcher(String name, String body) {
	}

	/**
	 * Each fetcher's text runs to the start of the next one. Crude, and correct for what is
	 * being asked: whether a call appears inside a method, not what the method computes.
	 */
	private static List<PagedFetcher> pagedFetchers() throws IOException {
		final var source = Files.readString(sourceFile());
		final var matcher = PAGED_FETCHER.matcher(source);

		final var names = new ArrayList<String>();
		final var starts = new ArrayList<Integer>();

		while (matcher.find()) {
			names.add(matcher.group(1));
			starts.add(matcher.start());
		}

		final var fetchers = new ArrayList<PagedFetcher>(names.size());

		for (int i = 0; i < names.size(); i++) {
			final int end = (i + 1 < starts.size()) ? starts.get(i + 1) : source.length();
			fetchers.add(new PagedFetcher(names.get(i), source.substring(starts.get(i), end)));
		}

		return fetchers;
	}

	/**
	 * Resolved from the working directory rather than the classpath: this reads the SOURCE,
	 * because the property being asserted is what the next author will read, and a compiled
	 * class cannot answer that.
	 */
	private static Path sourceFile() {
		final var fromModuleRoot = Paths.get("src/main/java").resolve(FETCHERS);

		return Files.exists(fromModuleRoot)
			? fromModuleRoot
			: Paths.get("dcb/src/main/java").resolve(FETCHERS);
	}
}
