package org.olf.dcb.request.lifecycle.ncip.peerauth;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.PeerAuthException;
import com.k_int.peerauth.PeerStatus;
import com.k_int.peerauth.TrustedPeer;
import com.k_int.peerauth.TrustedPeerBinding;
import com.k_int.peerauth.service.NimbusPeerTokenVerifier;
import com.k_int.peerauth.service.PeerJwksResolver;
import com.k_int.peerauth.store.TrustedPeerStore;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.olf.dcb.request.lifecycle.ncip.NcipInboundMessage;
import org.olf.dcb.request.lifecycle.ncip.NcipPeerHostLmsResolver;
import org.olf.dcb.request.lifecycle.ncip.NcipResponseBuilder;
import reactor.core.publisher.Mono;

@Singleton
public class NcipPeerAuthGuard {
	private final DcbPeerAuthProperties properties;
	private final NcipPeerHostLmsResolver hostLmsResolver;
	private final PeerJwksResolver peerJwksResolver;
	private final NcipResponseBuilder responseBuilder;

	public NcipPeerAuthGuard(
		DcbPeerAuthProperties properties,
		NcipPeerHostLmsResolver hostLmsResolver,
		PeerJwksResolver peerJwksResolver,
		NcipResponseBuilder responseBuilder) {

		this.properties = properties;
		this.hostLmsResolver = hostLmsResolver;
		this.peerJwksResolver = peerJwksResolver;
		this.responseBuilder = responseBuilder;
	}

	public Mono<Optional<MutableHttpResponse<String>>> problem(
		HttpRequest<?> request,
		NcipInboundMessage message) {

		return hostLmsResolver.findBySystemId(message.hostLmsCode())
			.map(hostLms -> authorize(request, message, hostLms))
			.onErrorResume(error -> Mono.just(Optional.of(problem(
				HttpResponse.unauthorized(), error.getMessage()))));
	}

	private Optional<MutableHttpResponse<String>> authorize(
		HttpRequest<?> request,
		NcipInboundMessage message,
		org.olf.dcb.core.model.HostLms hostLms) {

		final var profile = NcipPeerAuthProfile.from(hostLms);
		if (!profile.jwtRequired()) {
			return Optional.empty();
		}
		if (!properties.isNcipEnabled()) {
			return Optional.of(problem(HttpResponse.unauthorized(),
				"Peer authentication is disabled for JWT_REQUIRED HostLMS"));
		}
		final var bearer = bearerToken(request);
		if (bearer.isEmpty()) {
			return Optional.of(problem(HttpResponse.unauthorized(), "Missing peer bearer token"));
		}
		try {
			final var context = PeerAuthContext.singleTenant(NcipPeerAuth.LOCAL_IDENTITY);
			final var trustedPeer = new TrustedPeer(
				hostLms.getCode(),
				profile.issuer(),
				profile.jwksUri(),
				Set.of(properties.getLocalIdentity().getId()),
				Set.of(message.hostLmsCode()),
				PeerStatus.ACTIVE,
				Duration.ofMinutes(15),
				Map.of());
			final var principal = new NimbusPeerTokenVerifier(
				new SinglePeerStore(trustedPeer), peerJwksResolver).verify(context, bearer.get());
			if (!message.hostLmsCode().equals(principal.subject())) {
				throw new PeerAuthException("JWT subject does not match NCIP FromSystemId");
			}
			if (!NcipPeerAuth.PROTOCOL.equals(principal.claims().get("protocol"))) {
				throw new PeerAuthException("JWT protocol claim is not NCIP2");
			}
			return Optional.empty();
		} catch (PeerAuthException e) {
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

	private record SinglePeerStore(TrustedPeer peer) implements TrustedPeerStore {
		@Override
		public Optional<TrustedPeer> findByIssuer(PeerAuthContext context, String issuer) {
			return peer.issuer().equals(issuer) ? Optional.of(peer) : Optional.empty();
		}

		@Override
		public List<TrustedPeerBinding> findBindings(PeerAuthContext context, String peerId) {
			return List.of();
		}
	}
}
