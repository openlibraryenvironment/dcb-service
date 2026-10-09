package org.olf.dcb.core.interaction.foundation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.PingFailure;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.model.HostLms;

import io.micronaut.context.BeanContext;
import reactor.core.publisher.Mono;

class FoundationPingTests {
	@Test
	void shouldBeOkWhenTheNcipEndpointAnswersLookupVersion() {
		final var ncip = mock(NcipAdaptor.class);
		when(ncip.isAvailable()).thenReturn(Mono.just(true));

		final var response = clientSpeaking("NCIP", ncip).ping().block();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("NCIP 2.02"));
	}

	@Test
	void shouldBeUnreachableWhenTheNcipEndpointDoesNotAnswer() {
		final var ncip = mock(NcipAdaptor.class);
		when(ncip.isAvailable()).thenReturn(Mono.just(false));

		final var response = clientSpeaking("NCIP", ncip).ping().block();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getFailure(), is(PingFailure.UNREACHABLE));
	}

	@Test
	void shouldNotCheckASip2HostWhoseTransportDoesNotExist() {
		final var sip2 = mock(Sip2Adaptor.class);

		final var response = clientSpeaking("SIP2", sip2).ping().block();

		assertThat(response.getStatus(), is(PingResponse.NOT_IMPLEMENTED));
		assertThat(response.getAdditional(), containsString("SIP2 transport"));
		verify(sip2, never()).isAvailable();
	}

	private static FoundationClient clientSpeaking(String protocol, ProtocolAdaptor adaptor) {
		final var lms = mock(HostLms.class);
		when(lms.getCode()).thenReturn("FOUNDATION");
		when(lms.getClientConfig()).thenReturn(Map.of("base-protocol", protocol));

		final var beanContext = mock(BeanContext.class);
		if (adaptor instanceof Sip2Adaptor sip2) {
			when(beanContext.createBean(eq(Sip2Adaptor.class), eq(lms))).thenReturn(sip2);
		}
		else {
			when(beanContext.createBean(eq(NcipAdaptor.class), eq(lms))).thenReturn((NcipAdaptor) adaptor);
		}

		return new FoundationClient(lms, beanContext);
	}
}
