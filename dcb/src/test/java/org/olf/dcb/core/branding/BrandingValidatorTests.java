package org.olf.dcb.core.branding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Write-time validation of the brand fields (N-1.3). No context, no database: this is
 * arithmetic over strings and it should cost nothing to run on every build.
 *
 * The logo URL cases are the ones that matter. That value is rendered as the src of an
 * img in the chrome of every page of the patron app, served from an anonymous route, so
 * "is it a String" is not validation — javascript: and data: both pass that.
 */
class BrandingValidatorTests {

	private final BrandingValidator validator = validatorAccepting("openRS", "kInt");

	@Test
	void shouldAcceptAnAbsoluteHttpsUrl() {
		assertThat(validator.logoUrl("https://example.com/logo.svg"),
			is("https://example.com/logo.svg"));
	}

	@Test
	void shouldAcceptHttpForAnOnPremiseDeployment() {
		assertThat(validator.logoUrl("http://intranet.example/logo.png"),
			is("http://intranet.example/logo.png"));
	}

	@Test
	void shouldRejectAJavascriptUrl() {
		assertBadRequest(() -> validator.logoUrl("javascript:alert(1)"));
	}

	@Test
	void shouldRejectADataUrl() {
		assertBadRequest(() -> validator.logoUrl("data:image/svg+xml;base64,PHN2Zy8+"));
	}

	/**
	 * Protocol-relative. It looks like a path and leaves the origin entirely, which is
	 * exactly the case a "does it start with a slash" check waves through.
	 */
	@Test
	void shouldRejectAProtocolRelativeUrl() {
		assertBadRequest(() -> validator.logoUrl("//evil.example/logo.svg"));
	}

	/**
	 * A relative path cannot be resolved by the consumer: the app rendering this mark is
	 * served from a different origin from the one that stored it.
	 */
	@Test
	void shouldRejectARootRelativePath() {
		assertBadRequest(() -> validator.logoUrl("/assets/logo.svg"));
	}

	@Test
	void shouldRejectAUrlWithNoHost() {
		assertBadRequest(() -> validator.logoUrl("https:///logo.svg"));
	}

	/** An administrator who uploaded the wrong mark has to be able to remove it. */
	@Test
	void shouldTreatBlankAsClearingTheField() {
		assertThat(validator.logoUrl("   "), is(nullValue()));
		assertThat(validator.logoUrl(null), is(nullValue()));
		assertThat(validator.themeName(""), is(nullValue()));
	}

	@Test
	void shouldAcceptAKnownThemeName() {
		assertThat(validator.themeName("kInt"), is("kInt"));
	}

	@Test
	void shouldRejectAnUnknownThemeName() {
		assertBadRequest(() -> validator.themeName("midnight"));
	}

	/**
	 * The frontend registry lookup is case-sensitive, so "openrs" would be stored, pass
	 * review, and then silently render the default theme — the precise failure this
	 * check exists to catch, arriving through a friendlier-looking route.
	 */
	@Test
	void shouldRejectAThemeNameOfTheWrongCase() {
		assertBadRequest(() -> validator.themeName("openrs"));
	}

	/**
	 * A deployment running a discovery frontend we do not ship configures its own
	 * vocabulary. An empty one means "we cannot know", and refusing everything would
	 * make the field unusable rather than safe.
	 */
	@Test
	void shouldAcceptAnyThemeNameWhenNoVocabularyIsConfigured() {
		assertThat(validatorAccepting().themeName("somebody-elses-theme"),
			is("somebody-elses-theme"));
	}

	private static BrandingValidator validatorAccepting(String... themeNames) {
		final var properties = new BrandingProperties();
		properties.setThemeNames(List.of(themeNames));

		return new BrandingValidator(properties);
	}

	private static void assertBadRequest(Runnable action) {
		final var exception = assertThrows(HttpStatusException.class, action::run);

		assertThat(exception.getStatus(), is(HttpStatus.BAD_REQUEST));
	}
}
