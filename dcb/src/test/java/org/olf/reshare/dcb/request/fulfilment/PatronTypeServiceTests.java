package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.ReferenceValueMapping;
import org.olf.reshare.dcb.storage.ReferenceValueMappingRepository;
import org.olf.reshare.dcb.test.DataAccess;
import org.olf.reshare.dcb.test.DcbTest;

import jakarta.inject.Inject;

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
		final var mapping = ReferenceValueMapping.builder()
			.id(randomUUID())
			.fromCategory("patronType")
			.fromContext("DCB")
			.fromValue("DCB_UG")
			.toCategory("patronType")
			.toContext("EXAMPLE-CODE")
			.toValue("15")
			.reciprocal(true)
			.build();

		saveMapping(mapping);

		// Act
		final var patronType = patronTypeService
			.determinePatronType("EXAMPLE-CODE").block();

		// Assert
		assertThat(patronType, is("15"));
	}

	@Test
	void shouldFallBackToDefaultValueWhenNoMappingFound() {
		// Act
		final var patronType = patronTypeService
			.determinePatronType("EXAMPLE-CODE").block();

		// Assert
		assertThat(patronType, is("210"));
	}

	private void saveMapping(ReferenceValueMapping mapping) {
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}
}
