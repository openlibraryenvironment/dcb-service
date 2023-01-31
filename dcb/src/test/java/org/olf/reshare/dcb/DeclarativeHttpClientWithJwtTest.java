package org.olf.reshare.dcb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.ParseException;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.test.DcbTest;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken;
import jakarta.inject.Inject;

@DcbTest
class DeclarativeHttpClientWithJwtTest {

	@Inject
	AppClient appClient;

	@Test
	void verifyJwtAuthenticationWorksWithDeclarativeClient ()
	    throws ParseException {
		UsernamePasswordCredentials creds = new UsernamePasswordCredentials("user",
		    "password");
		BearerAccessRefreshToken loginRsp = appClient.login(creds);

		assertNotNull(loginRsp);
		assertNotNull(loginRsp.getAccessToken());
		assertTrue(JWTParser.parse(loginRsp.getAccessToken()) instanceof SignedJWT);

		String msg = appClient.home("Bearer " + loginRsp.getAccessToken());
		assertEquals("user", msg);
	}
}