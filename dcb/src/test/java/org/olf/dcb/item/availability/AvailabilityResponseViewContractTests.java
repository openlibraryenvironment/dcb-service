package org.olf.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.AvailabilityReason;
import org.olf.dcb.core.model.Item;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;

class AvailabilityResponseViewContractTests {
	private final ObjectMapper objectMapper = ObjectMapper.getDefault();

	@Test
	@SuppressWarnings("unchecked")
	void legacyItemFieldSetIsUnchanged() throws IOException {
		final var report = AvailabilityReport.ofItems(List.of(electronicItem()));
		final var json = objectMapper.writeValueAsString(AvailabilityResponseView.from(report, randomUUID()));
		final Map<String, Object> response = objectMapper.readValue(
			json, Argument.mapOf(String.class, Object.class));
		final var item = (Map<String, Object>) ((List<Object>) response.get("itemList")).getFirst();

		assertThat(item.keySet(), containsInAnyOrder("id", "status", "location", "statusCorrectAsOf"));
	}

	@Test
	void v2AddsElectronicItemFields() {
		final var item = AvailabilityResponseViewV2.from(
			AvailabilityReport.ofItems(List.of(electronicItem())), randomUUID())
			.getItemList().getFirst();

		assertThat(item.getItemAccessType(), is("E"));
		assertThat(item.getElectronicResourceUrl(), is("https://catalogue.example/item"));
		assertThat(item.getAvailabilityReason().label(), is("Available to library members"));
	}

	private static Item electronicItem() {
		return Item.builder()
			.localId("item-1")
			.itemAccessType("E")
			.electronicResourceUrl("https://catalogue.example/item")
			.availabilityReason(new AvailabilityReason("LICENCE_RESTRICTED", "Available to library members"))
			.build();
	}
}
