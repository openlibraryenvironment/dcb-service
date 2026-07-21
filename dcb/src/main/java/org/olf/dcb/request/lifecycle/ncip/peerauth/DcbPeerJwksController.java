package org.olf.dcb.request.lifecycle.ncip.peerauth;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import com.k_int.peerauth.PeerAuthContext;
import com.k_int.peerauth.service.PeerJwksService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import java.util.Map;

@Controller("/peer-auth")
@Secured(IS_ANONYMOUS)
@Requires(property = "dcb.peer-auth.enabled", value = "true")
public class DcbPeerJwksController {
	private final PeerJwksService peerJwksService;

	public DcbPeerJwksController(PeerJwksService peerJwksService) {
		this.peerJwksService = peerJwksService;
	}

	@Get("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return peerJwksService.publicJwks(
				PeerAuthContext.singleTenant(NcipPeerAuth.LOCAL_IDENTITY))
			.asMap();
	}
}
