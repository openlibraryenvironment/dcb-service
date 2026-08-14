package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.DiscoveryConsortiumController.ConsortiumBrand;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/**
 * The consortium level of the brand chain (N-1.1).
 *
 * DCB is the system of record for it, so that a discovery deployment does not keep a
 * second hand-edited copy of the consortium's name that diverges from this one.
 */
@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryConsortiumApiTests {

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private ConsortiumFixture consortiumFixture;

	@BeforeEach
	void beforeEach() {
		consortiumFixture.deleteAll();
	}

	@Test
	void shouldReturnTheConsortiumBrand() {
		// Arrange
		consortiumFixture.createConsortiumWithBrand("MOBIUS",
			"https://example.com/mobius.svg", "MOBIUS consortium",
			"Search every MOBIUS library at once.", "kInt");

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.name(), is("MOBIUS"));
		assertThat(brand.logoUrl(), is("https://example.com/mobius.svg"));
		assertThat(brand.logoAlt(), is("MOBIUS consortium"));
		assertThat(brand.welcome(), is("Search every MOBIUS library at once."));
		assertThat(brand.themeName(), is("kInt"));
	}

	/**
	 * A consortium that has uploaded nothing is not an error and must not be reported as
	 * one: its NAME is a complete brand level on its own, and a consumer renders that
	 * rather than substituting a logo belonging to somebody else.
	 */
	@Test
	void shouldReturnTheNameAloneWhenNothingElseIsConfigured() {
		// Arrange
		consortiumFixture.createConsortiumWithBrand("MOBIUS", null, null, null, null);

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.name(), is("MOBIUS"));
		assertThat(brand.logoUrl(), is(nullValue()));
		assertThat(brand.logoAlt(), is(nullValue()));
		assertThat(brand.welcome(), is(nullValue()));
		assertThat(brand.themeName(), is(nullValue()));
	}

	/**
	 * An <img> with no alt text is an unlabelled image to a screen reader, and the
	 * consortium's name is always available to label it with. An administrator who
	 * uploads a mark and leaves the alt field blank must not be able to ship that.
	 */
	@Test
	void shouldNameTheLogoByTheConsortiumWhenNoAltTextIsConfigured() {
		// Arrange
		consortiumFixture.createConsortiumWithBrand("MOBIUS",
			"https://example.com/mobius.svg", null, null, null);

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.logoAlt(), is("MOBIUS"));
	}

	/**
	 * A standalone DCB has no consortium, and "there is no consortium here" is a
	 * different fact from "the consortium has filled nothing in". A consumer falls back
	 * to its own configuration on a 404 and renders a one-level chain, which is complete
	 * and correct rather than degraded.
	 */
	@Test
	void shouldReturnNotFoundWhenThereIsNoConsortium() {
		// Act
		final var exception = assertThrows(HttpClientResponseException.class, this::getBrand);

		// Assert
		assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	private ConsortiumBrand getBrand() {
		return client.toBlocking().retrieve(
			HttpRequest.GET("/discovery/consortium"), ConsortiumBrand.class);
	}
}
