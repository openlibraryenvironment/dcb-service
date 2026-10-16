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
		consortiumFixture.createConsortiumWithBrand("NORTHFIELD",
			"https://example.com/northfield.svg", "NORTHFIELD consortium",
			"Search every NORTHFIELD library at once.", "kInt");

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.name(), is("NORTHFIELD"));
		assertThat(brand.logoUrl(), is("https://example.com/northfield.svg"));
		assertThat(brand.logoAlt(), is("NORTHFIELD consortium"));
		assertThat(brand.welcome(), is("Search every NORTHFIELD library at once."));
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
		consortiumFixture.createConsortiumWithBrand("NORTHFIELD", null, null, null, null);

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.name(), is("NORTHFIELD"));
		assertThat(brand.logoUrl(), is(nullValue()));
		assertThat(brand.logoAlt(), is(nullValue()));
		assertThat(brand.welcome(), is(nullValue()));
		assertThat(brand.themeName(), is(nullValue()));
		assertThat(brand.headerIconUrl(), is(nullValue()));
		assertThat(brand.backgroundImageUrl(), is(nullValue()));
	}

	/**
	 * R-17d. The header icon is a separate field from the logo rather than a size of it:
	 * a lockup put in a 32px box is a squashed lockup, and a consumer that only has the
	 * logo should render nothing in the icon slot rather than the wrong thing.
	 *
	 * The background is decorative and carries no alt text, deliberately — there is no
	 * backgroundAlt here and there should not be. A mark identifies an organisation; a
	 * canvas does not, and labelling one gives a screen reader something to read out that
	 * means nothing.
	 */
	@Test
	void shouldReturnTheHeaderIconAndBackgroundSeparatelyFromTheLogo() {
		// Arrange
		consortiumFixture.createConsortiumWithBrandImages("NORTHFIELD",
			"https://example.com/northfield-lockup.png",
			"https://example.com/northfield-icon.png",
			"/discovery/brand-assets/" + "c".repeat(64) + ".jpg");

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.logoUrl(), is("https://example.com/northfield-lockup.png"));
		assertThat(brand.headerIconUrl(), is("https://example.com/northfield-icon.png"));
		assertThat(brand.backgroundImageUrl(),
			is("/discovery/brand-assets/" + "c".repeat(64) + ".jpg"));
	}

	/**
	 * An <img> with no alt text is an unlabelled image to a screen reader, and the
	 * consortium's name is always available to label it with. An administrator who
	 * uploads a mark and leaves the alt field blank must not be able to ship that.
	 */
	@Test
	void shouldNameTheLogoByTheConsortiumWhenNoAltTextIsConfigured() {
		// Arrange
		consortiumFixture.createConsortiumWithBrand("NORTHFIELD",
			"https://example.com/northfield.svg", null, null, null);

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.logoAlt(), is("NORTHFIELD"));
	}

	/**
	 * A standalone DCB has no consortium, and "there is no consortium here" is a
	 * different fact from "the consortium has filled nothing in". A consumer falls back
	 * to its own configuration on a 404 and renders a one-level chain, which is complete
	 * and correct rather than degraded.
	 */
	/**
	 * Discovery is not the consortium's website and must not pretend to be. A patron
	 * who arrived from a search engine needs a route back to opening hours, branches and
	 * joining — none of which this software holds.
	 */
	@Test
	void shouldReturnTheConsortiumWebsiteWhenItHasOne() {
		// Arrange
		consortiumFixture.createConsortiumWithWebsite("NORTHFIELD", "https://northfield.example.org",
			"https://support.northfield.example.org");

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.websiteUrl(), is("https://northfield.example.org"));
		assertThat(brand.supportUrl(), is("https://support.northfield.example.org"));
	}

	@Test
	void shouldReturnNoWebsiteOrSupportDeskWhenTheConsortiumHasNotSetThem() {
		// Arrange
		consortiumFixture.createConsortiumWithBrand("NORTHFIELD", null, null, null, null);

		// Act
		final var brand = getBrand();

		// Assert
		assertThat(brand.websiteUrl(), is(nullValue()));
		assertThat(brand.supportUrl(), is(nullValue()));
	}

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
