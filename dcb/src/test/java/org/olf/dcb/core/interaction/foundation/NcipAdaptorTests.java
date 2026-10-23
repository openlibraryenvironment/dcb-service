package org.olf.dcb.core.interaction.foundation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.TEXT_XML;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.RegexBody;
import org.mockserver.verify.VerificationTimes;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * NCIP over the wire: what DCB sends and how it reads what comes back. The responder here is
 * shaped like ByWater's Koha NCIP plugin, which takes its token as the last path segment.
 */
@MockServerMicronautTest
class NcipAdaptorTests {
	private static final String HOST = "fake-ncip-responder";
	private static final String PATH = "/api/v1/contrib/ncip_server/ncip";
	private static final String TOKEN = "ncip-token-31415";

	@Inject
	private HttpClient httpClient;

	private MockServerClient mockServer;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		this.mockServer = mockServerClient;
	}

	@Test
	void shouldSendTheTokenAsThePathSegmentAndCheckTheItemIn() {
		answer(PATH + "/" + TOKEN, "<NCIPMessage><CheckInItemResponse><ItemId><ItemIdentifierValue>B1</ItemIdentifierValue></ItemId></CheckInItemResponse></NCIPMessage>");

		StepVerifier.create(adaptor(TOKEN).checkInItem("B1"))
			.expectNext("OK")
			.verifyComplete();

		mockServer.verify(request().withPath(PATH + "/" + TOKEN), VerificationTimes.once());
	}

	@Test
	void shouldEscapeTheBarcode() {
		answer(PATH, "<NCIPMessage><CheckInItemResponse/></NCIPMessage>");

		adaptor(null).checkInItem("A&B<1>").block();

		mockServer.verify(request().withPath(PATH)
			.withBody(RegexBody.regex("(?s).*<ItemIdentifierValue>A&amp;B&lt;1&gt;</ItemIdentifierValue>.*")));
	}

	@Test
	void shouldReadANamespacedProblemAsARefusal() {
		answer(PATH, """
			<ns1:NCIPMessage xmlns:ns1="http://www.niso.org/2008/ncip"><ns1:CheckInItemResponse><ns1:Problem>
			<ns1:ProblemType>Item Not Checked Out</ns1:ProblemType>
			<ns1:ProblemDetail>There is no record of the check out of the item.</ns1:ProblemDetail>
			</ns1:Problem></ns1:CheckInItemResponse></ns1:NCIPMessage>""");

		StepVerifier.create(adaptor(null).checkInItem("B1"))
			.expectErrorSatisfies(error -> {
				final var problem = (ThrowableProblem) error;
				assertThat(problem.getParameters().get(NcipAdaptor.PROBLEM_TYPE), is("Item Not Checked Out"));
				assertThat(problem.getDetail(), is("There is no record of the check out of the item."));
			})
			.verify();
	}

	@Test
	void shouldNotTakeAnyOtherPageForASuccess() {
		// A proxy's sign-in page, answered with 200
		answer(PATH, "<html><body>Please sign in</body></html>");

		StepVerifier.create(adaptor(null).checkInItem("B1"))
			.expectErrorSatisfies(error -> assertThat(
				((ThrowableProblem) error).getParameters().get(NcipAdaptor.PROBLEM_TYPE), is("Unexpected response")))
			.verify();
	}

	@Test
	void shouldNeverShowTheTokenWhenTheEndpointIsPrinted() {
		final var endpoint = new NcipEndpoint("https://" + HOST + PATH, TOKEN);

		assertThat(endpoint.toString(), not(containsString(TOKEN)));
		assertThat(endpoint.requestUri().getPath(), is(PATH + "/" + TOKEN));
	}

	@Test
	void shouldAnswerNothingRatherThanNullForAPatronByBarcode() {
		StepVerifier.create(adaptor(null).findPatronByBarcode("C1"))
			.verifyComplete();
	}

	private NcipAdaptor adaptor(String token) {
		return NcipAdaptor.forEndpoint(httpClient, new NcipEndpoint("https://" + HOST + PATH, token), "ncip-test");
	}

	private void answer(String path, String body) {
		mockServer.when(request().withMethod("POST").withPath(path).withHeader("host", HOST))
			.respond(response().withStatusCode(200).withBody(body, TEXT_XML));
	}
}
