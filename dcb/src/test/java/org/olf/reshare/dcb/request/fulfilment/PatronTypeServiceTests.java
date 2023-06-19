package org.olf.reshare.dcb.request.fulfilment;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;
import org.olf.reshare.dcb.test.DataAccess;
import org.olf.reshare.dcb.test.DcbTest;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

@DcbTest
class PatronTypeServiceTests {
	@Inject
	private PatronTypeService patronTypeService;

	@Inject
	private ReferenceValueMappingRepository referenceValueMappingRepository;

	@BeforeEach
	public void beforeEach() {
		new DataAccess().deleteAll(referenceValueMappingRepository.findAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));
	}

	@Test
	void shouldDeterminePatronTypeBasedUponHostLms() {
		// Arrange

                // We set up a mapping HOSTA.1 -> DCB.DCB_UG -> 15
                final var mapping_a = ReferenceValueMapping.builder()
                        .id(randomUUID())
                        .fromCategory("patronType")
                        .fromContext("HOSTA")
                        .fromValue("1")
                        .toCategory("patronType")
                        .toContext("DCB")
                        .toValue("DCB_UG")
                        .reciprocal(true)
                        .build();
		saveMapping(mapping_a);

                // Mapping from DCB::DCB_UG to EXAMPLE-CODE:15
		final var mapping_b = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("patronType")
			.fromContext("DCB")
			.fromValue("DCB_UG")
			.toCategory("patronType")
			.toContext("EXAMPLE-CODE")
			.toValue("15")
			.reciprocal(true)
			.build();

		saveMapping(mapping_b);

		// Act
                // patronTypeService.determinePatronType(TARGET-CONTEXT,ORIGIN-CONTEXT,ORIGIN-VALUE)
		final var patronType = patronTypeService.determinePatronType("EXAMPLE-CODE","HOSTA","1").block();

		// Assert
		assertThat(patronType, is("15"));
	}

	@Test
	void shouldFallBackToDefaultValueWhenNoMappingFound() {
		// Act
		final var patronType = patronTypeService.determinePatronType("EXAMPLE-CODE","DCB","DCB_UG").block();

		// Assert
		assertThat(patronType, is("210"));
	}

	private void saveMapping(ReferenceValueMapping mapping) {
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}
}
