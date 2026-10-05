package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.URI;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KohaApiPathPrefixTests {
	@ParameterizedTest
	@CsvSource({
		"https://host,                  https://host/api/v1/patrons?cardnumber=1",
		"https://host/,                 https://host/api/v1/patrons?cardnumber=1",
		"https://host/api/v1,           https://host/api/v1/patrons?cardnumber=1",
		"https://host/api/v1/,          https://host/api/v1/patrons?cardnumber=1",
		"https://host/koha,             https://host/koha/api/v1/patrons?cardnumber=1",
		"https://host/koha/api/v1,      https://host/koha/api/v1/patrons?cardnumber=1",
		"https://host:8443/koha/api/v1/, https://host:8443/koha/api/v1/patrons?cardnumber=1"
	})
	void shouldKeepThePathTheApiIsServedBelow(String apiUrl, String expected) {
		assertThat(KohaApiClientImpl.resolveBelow(URI.create(apiUrl),
			URI.create("/api/v1/patrons?cardnumber=1")), is(URI.create(expected)));
	}
}
