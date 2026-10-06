package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;

class PresentableItemTests {
	@Test
	void shouldCarryTheRawValuesAnAdapterReportedIntoTheAudit() {
		final var item = Item.builder()
			.localId("23789")
			.rawDataValue("baseStatus", "0")
			.rawDataValue("processType", "LOAN")
			.build();

		final var presented = PresentableItem.toPresentableItem(item);

		assertThat(presented.getRawDataValues(), hasEntry("processType", "LOAN"));
	}
}
