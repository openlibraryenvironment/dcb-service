package org.olf.dcb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.clients.LoginClient;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;

import jakarta.inject.Inject;
import lombok.SneakyThrows;

@DcbTest
class DeclarativeHttpClientWithJwtTest {

	@Inject
	AppClient appClient;

	@Inject
	LoginClient loginClient;

	@SneakyThrows
	@Test
	void verifyJwtAuthenticationWorksWithDeclarativeClient() {
		final var loginRsp = loginClient.login("user", "password");

		assertNotNull(loginRsp);
		assertNotNull(loginRsp.getAccessToken());
		assertTrue(JWTParser.parse(loginRsp.getAccessToken()) instanceof SignedJWT);

		String msg = appClient.home("Bearer " + loginRsp.getAccessToken());
		assertEquals("user", msg);
	}
}
