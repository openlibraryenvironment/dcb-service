package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.holdings.AlmaHoldings;

class AlmaVirtualBibDeletionTests {
	private static final String BIB = "99100200300";

	private AlmaApiClient almaApi;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

		almaApi = mock(AlmaApiClient.class);
		when(almaApi.deleteBibRecord(any())).thenReturn(Mono.just("Bib deleted"));

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		client = new AlmaHostLmsClient(hostLms, clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldDeleteABibThatIsStillDcbsVirtualRecord() {
		given(virtualBib(), dcbHolding());

		assertThat(client.deleteBib(BIB).block(), is("OK"));

		verify(almaApi).deleteBibRecord(BIB);
	}

	@Test
	void shouldDeleteAVirtualBibWhoseHoldingIsAlreadyGone() {
		given(virtualBib());

		assertThat(client.deleteBib(BIB).block(), is("OK"));
	}

	@Test
	void shouldLeaveABibThatNoLongerCarriesDcbsNote() {
		given(bib("true", "<record><datafield tag=\"245\"/></record>"), dcbHolding());

		assertRefused("does not carry DCB's note");
	}

	@Test
	void shouldDeleteAVirtualBibMadeBeforeTheNoteExisted() {
		given(bib("true", preNoteVirtualBib()), dcbHolding());

		assertThat(client.deleteBib(BIB).block(), is("OK"));
	}

	@Test
	void shouldLeaveABibCarryingOnlyOneOfTheOldPlaceholders() {
		given(bib("true", "<record><datafield tag=\"260\"><subfield code=\"b\">DCB Publisher</subfield>"
			+ "</datafield></record>"), dcbHolding());

		assertRefused("does not carry DCB's note");
	}

	@Test
	void shouldLeaveABibThatHasBeenPublished() {
		given(bib("false", marcWithNote()), dcbHolding());

		assertRefused("not suppressed from publishing");
	}

	@Test
	void shouldLeaveABibWithAHoldingDcbDidNotCreate() {
		given(virtualBib(), dcbHolding(), AlmaHolding.builder().holdingId("h2").call_number("QA76 .S5").build());

		assertRefused("holdings DCB did not create");
	}

	private void assertRefused(String reason) {
		final var error = assertThrows(IllegalStateException.class, () -> client.deleteBib(BIB).block());

		assertThat(error.getMessage(), containsString("left in place"));
		assertThat(error.getMessage(), containsString(reason));
		verify(almaApi, never()).deleteBibRecord(any());
	}

	private void given(AlmaBib bib, AlmaHolding... holdings) {
		when(almaApi.retrieveBib(BIB)).thenReturn(Mono.just(bib));
		when(almaApi.retrieveHoldings(BIB)).thenReturn(Mono.just(AlmaHoldings.builder()
			.totalRecordCount(holdings.length)
			.holdings(List.of(holdings))
			.build()));
	}

	private static AlmaBib virtualBib() {
		return bib("true", marcWithNote());
	}

	private static AlmaBib bib(String suppressed, String marc) {
		return AlmaBib.builder()
			.mmsId(BIB)
			.suppressFromPublishing(suppressed)
			.anies(List.of(marc))
			.build();
	}

	private static String marcWithNote() {
		return AlmaXmlGenerator.createBibXml("A title", "An author");
	}

	// The MARC that released versions of DCB wrote, before the 500 note
	private static String preNoteVirtualBib() {
		return "<record><controlfield tag=\"001\">DCB1758900000000</controlfield>"
			+ "<datafield tag=\"020\"><subfield code=\"a\">978-0-DCB-1758900000000</subfield></datafield>"
			+ "<datafield tag=\"245\"><subfield code=\"a\">A title</subfield></datafield>"
			+ "<datafield tag=\"260\"><subfield code=\"a\">DCB City</subfield>"
			+ "<subfield code=\"b\">DCB Publisher</subfield></datafield></record>";
	}

	private static AlmaHolding dcbHolding() {
		return AlmaHolding.builder()
			.holdingId("h1")
			.call_number(AlmaHostLmsClient.DCB_VIRTUAL_COLLECTION)
			.build();
	}
}
