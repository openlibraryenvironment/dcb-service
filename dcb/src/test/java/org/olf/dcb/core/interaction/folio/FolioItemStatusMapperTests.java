package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient.folioFallback;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;

@DcbTest
class FolioItemStatusMapperTests {
	/**
	 * The codes used here come from
	 * <a href="ttps://github.com/folio-org/mod-inventory-storage/blob/294f8c42b48d9099b49f37fc7c193bb5e36bf918/ramls/item.json#L253>this documentation</a>
	 */

	private static final String HOST_LMS_CODE = "folio-host-lms";

	@Inject
	private ItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void statusIsAvailableWhenCodeIsAvailable() {
		final var mappedStatus = mapFolioStatus("Available");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsCheckedOut() {
		final var mappedStatus = mapFolioStatus("Checked out");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"Aged to lost",
		"Awaiting pickup",
		"Awaiting delivery",
		"Claimed returned",
		"Declared lost",
		"In process",
		"In process (non-requestable)",
		"In transit",
		"Intellectual item",
		"Long missing",
		"Lost and paid",
		"Missing",
		"On order",
		"Paged",
		"Restricted",
		"Order closed",
		"Unavailable",
		"Unknown",
		"Withdrawn"
	})
	void statusIsUnavailableWhenCodeAnythingElse(String statusCode) {
		final var mappedStatus = mapFolioStatus(statusCode);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsNull() {
		final var mappedStatus = mapFolioStatus(null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmptyString() {
		final var mappedStatus = mapFolioStatus("");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Nullable
	private ItemStatus mapFolioStatus(String statusCode) {
		return mapper.mapStatus(statusCode, null, HOST_LMS_CODE, false, folioFallback())
			.block();
	}
}
