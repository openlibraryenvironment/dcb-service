package org.olf.dcb.interops;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MappingValueCheck;
import org.olf.dcb.core.interaction.MappingVocabulary;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Judging the mappings that are already saved, rather than one someone is about to type.
 * <p>
 * A live sandbox carried ten patron type mappings targeting "Consortial Express Patron", the
 * description of user group CONSORTIAL. Nothing failed until a hold was placed and Alma refused
 * it with an error that named neither the mapping nor the value.
 */
@TestInstance(PER_CLASS)
class MappingAuditTests {
	private static final String HOST_LMS = "ALMA";

	@Test
	void shouldReportAMappingBuiltFromADescriptionRatherThanACode() {
		final var audit = auditOf(
			List.of(mapping("patronType", "UNDERGRADUATE", "Consortial Express Patron"),
				mapping("patronType", "GRADUATE", "gradstudent")),
			List.of(new ConfigurationReport.Entry("CONSORTIAL", "Consortial Express Patron"),
				new ConfigurationReport.Entry("gradstudent", "Graduate Student")));

		assertThat(audit.checked(), is(2));
		assertThat(audit.missing(), is(1));

		assertThat(audit.rows().stream()
			.filter(row -> row.result() == MappingValueCheck.Result.MISSING)
			.map(MappingAuditRowValue::of)
			.toList(), contains("Consortial Express Patron"));
	}

	@Test
	void shouldNotCallAnythingMissingWhenTheVocabularyCouldNotBeRead() {
		// Saying a value is absent when we could not look sends an implementer to create one
		// that already exists
		final var audit = auditOf(
			List.of(mapping("patronType", "UNDERGRADUATE", "Consortial Express Patron")),
			List.of());

		assertThat(audit.missing(), is(0));
		assertThat(audit.rows().stream().map(row -> row.result()).toList(),
			everyItem(is(MappingValueCheck.Result.UNKNOWN)));
	}

	@Test
	void shouldNotReportNothingWrongForASystemThatCannotBeAsked() {
		// fetchVocabulary's empty Mono means "this adapter cannot read it". Dropping the rows made
		// a system that was never asked read as CHECKED with nothing missing
		final var audit = auditOf(
			List.of(mapping("patronType", "UNDERGRADUATE", "undergrad")), null);

		assertThat(audit.checked(), is(1));
		assertThat(audit.rows().stream().map(row -> row.result()).toList(),
			everyItem(is(MappingValueCheck.Result.NOT_SUPPORTED)));
	}

	private interface MappingAuditRowValue {
		static String of(org.olf.dcb.core.interaction.MappingAudit.Row row) {
			return row.toValue();
		}
	}

	private org.olf.dcb.core.interaction.MappingAudit auditOf(
		List<ReferenceValueMapping> mappings, List<ConfigurationReport.Entry> vocabulary) {

		final var repository = mock(ReferenceValueMappingRepository.class);
		when(repository.findAllTargeting(anyString())).thenReturn(Flux.fromIterable(mappings));

		final var client = mock(HostLmsClient.class);
		when(client.fetchVocabulary(any(MappingVocabulary.class)))
			.thenReturn(vocabulary != null ? Mono.just(vocabulary) : Mono.empty());

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(HOST_LMS)).thenReturn(Mono.just(client));

		return new InteropTestService(hostLmsService, mock(BibRecordService.class), repository)
			.auditMappings(HOST_LMS)
			.block();
	}

	private static ReferenceValueMapping mapping(String category, String fromValue, String toValue) {
		return ReferenceValueMapping.builder()
			.fromCategory(category)
			.fromContext("DCB")
			.fromValue(fromValue)
			.toCategory(category)
			.toContext(HOST_LMS)
			.toValue(toValue)
			.build();
	}
}
