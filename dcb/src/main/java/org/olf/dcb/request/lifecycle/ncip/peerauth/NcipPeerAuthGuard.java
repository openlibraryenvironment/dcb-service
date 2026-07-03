package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerAuthException;
import com.k_int.peerauth.PeerBindingException;
import com.k_int.peerauth.service.PeerBindingValidator;
import com.k_int.peerauth.service.PeerTokenVerifier;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.olf.dcb.request.lifecycle.ncip.NcipInboundMessage;
import org.olf.dcb.request.lifecycle.ncip.NcipResponseBuilder;

@Singleton
public class NcipPeerAuthGuard {
	private final DcbPeerAuthProperties properties;
	private final PeerTokenVerifier peerTokenVerifier;
	private final PeerBindingValidator peerBindingValidator;
	private final NcipResponseBuilder responseBuilder;

	public NcipPeerAuthGuard(
		DcbPeerAuthProperties properties,
		PeerTokenVerifier peerTokenVerifier,
		PeerBindingValidator peerBindingValidator,
		NcipResponseBuilder responseBuilder) {

		this.properties = properties;
		this.peerTokenVerifier = peerTokenVerifier;
		this.peerBindingValidator = peerBindingValidator;
		this.responseBuilder = responseBuilder;
	}

	public Optional<MutableHttpResponse<String>> problem(
		HttpRequest<?> request,
		NcipInboundMessage message) {

		if (!properties.isNcipEnabled()) {
			return Optional.empty();
		}

		final var bearer = bearerToken(request);
		if (bearer.isEmpty()) {
			return Optional.of(problem(HttpResponse.unauthorized(), "Missing peer bearer token"));
		}

		try {
			final var context = PeerAuthContext.singleTenant(NcipPeerAuth.LOCAL_IDENTITY);
			final var principal = peerTokenVerifier.verify(context, bearer.get());
			peerBindingValidator.requireAllowedSystemId(
				context,
				principal,
				NcipPeerAuth.PROTOCOL,
				message.hostLmsCode());
			return Optional.empty();
		}
		catch (PeerBindingException e) {
			return Optional.of(problem(HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN),
				e.getMessage()));
		}
		catch (PeerAuthException e) {
			return Optional.of(problem(HttpResponse.unauthorized(), e.getMessage()));
		}
	}

	private MutableHttpResponse<String> problem(
		MutableHttpResponse<?> response,
		String message) {

		return response.body(responseBuilder.problem(message))
			.contentType(MediaType.APPLICATION_XML_TYPE);
	}

	private static Optional<String> bearerToken(HttpRequest<?> request) {
		return request.getHeaders()
			.getAuthorization()
			.filter(value -> value.regionMatches(true, 0, "Bearer ", 0, 7))
			.map(value -> value.substring(7).trim())
			.filter(value -> !value.isBlank());
	}
}
