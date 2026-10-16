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

	// --- R-17d: one new accepted form, and every old rejection kept ----------------
	//
	// An uploaded asset is served from a path this service owns, which is exactly the
	// site-relative form every test above exists to reject. So the rule is widened by one
	// case and not relaxed: the prefix AND the shape of the key the store actually mints.

	@Test
	void shouldAcceptAPathUnderOurOwnAssetPrefix() {
		final var uploaded = "/discovery/brand-assets/"
			+ "0".repeat(64) + ".png";

		assertThat(validator.logoUrl(uploaded), is(uploaded));
	}

	@Test
	void shouldAcceptAJpegAssetPath() {
		final var uploaded = "/discovery/brand-assets/" + "a".repeat(64) + ".jpg";

		assertThat(validator.logoUrl(uploaded), is(uploaded));
	}

	/**
	 * The prefix check is not "starts with". A prefix test that can be walked out of is
	 * not a prefix test, and this is the string that proves it.
	 */
	@Test
	void shouldRejectATraversalOutOfTheAssetPrefix() {
		assertBadRequest(() -> validator.logoUrl("/discovery/brand-assets/../../etc/passwd"));
	}

	/**
	 * Under the prefix but not a key this service ever minted. Accepting it would make
	 * the prefix a namespace anyone could write into by guessing a path.
	 */
	@Test
	void shouldRejectSomethingUnderThePrefixThatIsNotAnAssetKey() {
		assertBadRequest(() -> validator.logoUrl("/discovery/brand-assets/logo.png"));
		assertBadRequest(() -> validator.logoUrl("/discovery/brand-assets/" + "0".repeat(64) + ".svg"));
		assertBadRequest(() -> validator.logoUrl("/discovery/brand-assets/" + "0".repeat(63) + ".png"));
	}

	/** Every other site-relative path is still refused, exactly as before. */
	@Test
	void shouldStillRejectAnyOtherSiteRelativePath() {
		assertBadRequest(() -> validator.logoUrl("/discovery/brand-assets-evil/x.png"));
		assertBadRequest(() -> validator.logoUrl("/uploads/" + "0".repeat(64) + ".png"));
	}

	/**
	 * These are the values MOBIUS actually holds today, which V9_0_004 carries into
	 * brand_header_icon_url and brand_logo_url. They were stored before this validator
	 * existed, so the migration moves unvalidated data into columns that are validated.
	 *
	 * If this test ever fails, the next consortium edit in production starts being rejected
	 * over a field the administrator did not touch.
	 */
	@Test
	void shouldAcceptTheBlobUrlsAlreadyStoredInProduction() {
		final var header = "https://djlwg7trj3cacjdl.public.blob.vercel-storage.com/"
			+ "consortiumMOBIUSuserCasey%20Henderson-gumS5rLc7TlM9wMI5xlXC167lurg5x.png";

		final var about = "https://djlwg7trj3cacjdl.public.blob.vercel-storage.com/"
			+ "consortiumMOBIUSuserCasey%20Henderson-B6LRGChh4s8v5bQnztEksQrYIJFAwF.png";

		assertThat("Percent-encoding in the path must survive untouched",
			validator.logoUrl(header), is(header));
		assertThat(validator.logoUrl(about), is(about));
	}

	/**
	 * Why the admin-chrome columns had to be validated before they could merge. Each of
	 * these was storable in header_image_url, survivable only because that column was
	 * rendered behind authentication - and the merged column is rendered to patrons on an
	 * anonymous route.
	 */
	@Test
	void shouldRefuseInAdminChromeWhatItRefusesInPatronBrand() {
		assertBadRequest(() -> validator.logoUrl("javascript:alert(1)"));
		assertBadRequest(() -> validator.logoUrl("data:image/png;base64,iVBORw0KGgo="));
		assertBadRequest(() -> validator.logoUrl("//evil.example.org/logo.png"));
	}

	/**
	 * patronWebsite, supportUrl and websiteUrl become the href of an anchor in the
	 * footer of every page of the patron app. An href is not a safer place for
	 * {@code javascript:} than an img src is, so the scheme rule is the same one.
	 */
	@Test
	void shouldAcceptAnAbsoluteLink() {
		assertThat(validator.linkUrl("https://library.example.org/opening-hours"),
			is("https://library.example.org/opening-hours"));
	}

	@Test
	void shouldClearALinkWhenItIsBlank() {
		// A library that typed the wrong address has to be able to remove it, and the
		// update fetchers have no other way to say "set this to null".
		assertThat(validator.linkUrl("   "), is(nullValue()));
		assertThat(validator.linkUrl(null), is(nullValue()));
	}

	@Test
	void shouldKeepALinkStoredBeforeTheCheckWhenItIsSentBackUnchanged() {
		assertThat(validator.changedLinkUrl("www.library.example.org", "www.library.example.org"),
			is("www.library.example.org"));
		assertThat(validator.changedLinkUrl(" www.library.example.org ", "www.library.example.org"),
			is("www.library.example.org"));
	}

	@Test
	void shouldCheckALinkThatChanges() {
		assertBadRequest(() -> validator.changedLinkUrl("www.other.example.org", "www.library.example.org"));
		assertThat(validator.changedLinkUrl("https://library.example.org", "www.library.example.org"),
			is("https://library.example.org"));
		assertThat(validator.changedLinkUrl("", "www.library.example.org"), is(nullValue()));
	}

	@Test
	void shouldRefuseInALinkWhatItRefusesInAnImage() {
		assertBadRequest(() -> validator.linkUrl("javascript:alert(1)"));
		assertBadRequest(() -> validator.linkUrl("data:text/html,<h1>hi"));
		assertBadRequest(() -> validator.linkUrl("//evil.example.org/phish"));
		assertBadRequest(() -> validator.linkUrl("mailto:librarian@example.org"));
	}

	/**
	 * The one place the two rules deliberately DIFFER. logoUrl accepts a path under this
	 * service's asset prefix because an uploaded mark really is served from there; a
	 * website is somewhere else entirely, and accepting the prefix here would let "report
	 * a problem" point at a stored PNG.
	 */
	@Test
	void shouldRefuseAnAssetPathAsALink() {
		final var asset = "/discovery/brand-assets/"
			+ "0".repeat(64) + ".png";

		assertThat("the same value is a valid IMAGE", validator.logoUrl(asset), is(asset));
		assertBadRequest(() -> validator.linkUrl(asset));
	}

	@Test
	void shouldRefuseALinkWithNoHost() {
		assertBadRequest(() -> validator.linkUrl("https:///opening-hours"));
		assertBadRequest(() -> validator.linkUrl("/opening-hours"));
	}

	private static BrandingValidator validatorAccepting(String... themeNames) {
		final var properties = new BrandingProperties();
		properties.setThemeNames(List.of(themeNames));

		return new BrandingValidator(properties, new BrandAssetProperties());
	}

	private static void assertBadRequest(Runnable action) {
		final var exception = assertThrows(HttpStatusException.class, action::run);

		assertThat(exception.getStatus(), is(HttpStatus.BAD_REQUEST));
	}
}
