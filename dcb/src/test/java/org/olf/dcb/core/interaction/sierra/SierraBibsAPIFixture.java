package org.olf.dcb.core.interaction.sierra;

import static org.olf.dcb.test.MockServerCommonResponses.noContent;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;

import org.olf.dcb.test.MockServer;
import org.olf.dcb.test.MockServerCommonRequests;

import lombok.AllArgsConstructor;
import services.k_int.interaction.sierra.LinkResult;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResult;
import services.k_int.interaction.sierra.bibs.BibResultSet;

@AllArgsConstructor
public class SierraBibsAPIFixture {
	private final MockServer mockServer;
	private final MockServerCommonRequests mockServerCommonRequests;

	public void createGetBibsMockWithQueryStringParameters(BibResult... bibs) {
		createGetBibsMockWithQueryStringParameters(List.of(bibs));
	}

	public void createGetBibsMockWithQueryStringParameters(List<BibResult> bibs) {
		mockServer.mock(mockServerCommonRequests.get(bibsPath())
				.withQueryStringParameter("updatedDate", "null")
				.withQueryStringParameter("suppressed", "false")
				.withQueryStringParameter("offset", "1")
				.withQueryStringParameter("locations", "a")
				.withQueryStringParameter("limit", "3")
				.withQueryStringParameter("deleted", "false")
				.withQueryStringParameter("createdDate", "null"),
			BibResultSet.builder()
				.entries(bibs)
				.start(0)
				.total(getValue(bibs, List::size, 0))
				.build());
	}

	public void createPostBibsMock(BibPatch bibPatch, Integer returnId) {
		mockServer.mockPost(bibsPath(), bibPatch,
			LinkResult.builder()
				.link("https://sandbox.iii.com" + getBibPath(returnId))
				.build());
	}

	public void mockDeleteBib(String bibId) {
		mockServer.replaceMock(mockServerCommonRequests.delete(getBibPath(bibId)), noContent());
	}

	public void verifyDeleteBibRequestMade(String bibId) {
		mockServer.verify(mockServerCommonRequests.delete(getBibPath(bibId)));
	}

	public void verifyDeleteBibRequestNeverMade(String bibId) {
		mockServer.verifyNever(mockServerCommonRequests.delete(getBibPath(bibId)));
	}

	private static String bibsPath() {
		return "/iii/sierra-api/v6/bibs";
	}

	private static String getBibPath(Object bibId) {
		return bibsPath() + "/" + bibId;
	}
}
