package org.olf.dcb.core.remediation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.remediation.RemediationCode.FOLIO_REQUEST_POLICY_REFUSED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.remediation.RemediationCatalogue.Entry;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;

class RemediationCatalogueTests {
	private static RemediationCatalogue shipped() {
		return new RemediationCatalogue(new ResourceResolver(), ObjectMapper.getDefault());
	}

	private static Entry entry(String summary, String... nextSteps) {
		return new Entry(Remediation.Audience.CONSORTIUM, List.of("hostLms"), summary, List.of(nextSteps), "docs");
	}

	@Test
	void everyCodeInTheShippedCatalogueRendersWithEveryPlaceholderFilled() {
		final var catalogue = shipped();

		for (final var code : RemediationCode.values()) {
			final var remediation = catalogue.render(code, Map.of());

			assertThat(code + " summary", remediation.summary(), not(containsString("{")));
			assertThat(code + " next steps", remediation.nextSteps(), everyItem(not(containsString("{"))));
			assertThat(code + " has next steps", remediation.nextSteps().isEmpty(), is(false));
		}
	}

	@Test
	void aRequestPolicyRefusalSaysWhichRequestTypeToAllowForWhichPatronGroup() {
		final var remediation = shipped().render(FOLIO_REQUEST_POLICY_REFUSED,
			Map.of("hostLms", "COLLEGE", "requestType", "Hold", "patronGroup", "DCB patrons"));

		assertThat(remediation.audience(), is(Remediation.Audience.SUPPLYING_LIBRARY));
		assertThat(remediation.summary(), is("COLLEGE's request policy does not allow a Hold request "
			+ "for patron group \"DCB patrons\" on this item"));
		assertThat(remediation.nextSteps().get(0), is("In COLLEGE's FOLIO circulation rules, allow Hold requests "
			+ "in the request policy that applies to patron group \"DCB patrons\" and this item."));
	}

	@Test
	void aValueThatIsNotGivenReadsUnknown() {
		final var remediation = shipped().render(FOLIO_REQUEST_POLICY_REFUSED, Map.of("hostLms", "COLLEGE"));

		assertThat(remediation.summary(), containsString("for patron group \"unknown\""));
	}

	@Test
	void aPlaceholderAnEntryDoesNotDeclareStopsStartup() {
		final var failure = assertThrows(IllegalStateException.class, () -> new RemediationCatalogue(
			Map.of(FOLIO_REQUEST_POLICY_REFUSED.name(), entry("{hostLms} refused", "Ask {library}"))));

		assertThat(failure.getMessage(), containsString("uses {library}, which it does not declare"));
	}

	@Test
	void aCodeWithoutAnEntryStopsStartup() {
		final var failure = assertThrows(IllegalStateException.class, () -> new RemediationCatalogue(Map.of()));

		assertThat(failure.getMessage(), containsString("FOLIO_REQUEST_POLICY_REFUSED has no entry"));
	}

	@Test
	void anEntryWithoutACodeStopsStartup() {
		final Map<String, Entry> entries = new HashMap<>(Stream.of(RemediationCode.values())
			.collect(Collectors.toMap(Enum::name, code -> entry("{hostLms} refused", "Fix it"))));
		entries.put("NOT_A_CODE", entry("{hostLms} refused", "Fix it"));

		final var failure = assertThrows(IllegalStateException.class, () -> new RemediationCatalogue(entries));

		assertThat(failure.getMessage(), containsString("NOT_A_CODE is not a RemediationCode"));
	}

	@Test
	void anEntryWithNoNextStepStopsStartup() {
		final var failure = assertThrows(IllegalStateException.class, () -> new RemediationCatalogue(
			Map.of(FOLIO_REQUEST_POLICY_REFUSED.name(), entry("{hostLms} refused"))));

		assertThat(failure.getMessage(), containsString("needs at least one next step"));
	}
}
