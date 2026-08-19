package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.branding.BrandAssetCleanup;
import org.olf.dcb.core.branding.BrandingValidator;
import org.olf.dcb.core.model.Consortium;
import org.olf.dcb.storage.ConsortiumRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateConsortiumDataFetcher implements DataFetcher<CompletableFuture<Consortium>> {

	private final ConsortiumRepository consortiumRepository;

	private final R2dbcOperations r2dbcOperations;

	private final BrandingValidator brandingValidator;

	private final BrandAssetCleanup brandAssetCleanup;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateConsortiumDataFetcher(ConsortiumRepository consortiumRepository,
		R2dbcOperations r2dbcOperations, BrandingValidator brandingValidator,
		BrandAssetCleanup brandAssetCleanup) {

		this.consortiumRepository = consortiumRepository;
		this.r2dbcOperations = r2dbcOperations;
		this.brandingValidator = brandingValidator;
		this.brandAssetCleanup = brandAssetCleanup;
	}

	@Override
	public CompletableFuture<Consortium> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateConsortiumDataFetcher {}", input_map);

		UUID id = input_map.get("id") != null ? UUID.fromString(input_map.get("id").toString()) : null;

		String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");
		Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
			.map(Object::toString);
		Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
			.map(Object::toString);
		Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
			.map(Object::toString);
		String displayName = input_map.containsKey("displayName") ?
			input_map.get("displayName").toString() : null;
		String description = input_map.containsKey("description") ?
			input_map.get("description").toString() : null;
		String websiteUrl = input_map.containsKey("websiteUrl") ?
			input_map.get("websiteUrl").toString() : null;
		String catalogueSearchUrl = input_map.containsKey("catalogueSearchUrl") ?
			input_map.get("catalogueSearchUrl").toString() : null;
		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role to edit consortium information
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
			log.warn("updateConsortiumDataFetcher: Access denied for user {}: user does not have the required role to update a consortium.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		// The brand (N-1.3). Read and validated BEFORE the transaction opens, so a bad logo
		// URL costs no database round trip and cannot leave a half-applied form.
		//
		// These follow a different absent/blank rule from the fields above, and deliberately:
		// a key that is present but blank CLEARS the field, because an administrator who has
		// uploaded the wrong mark has to be able to remove it. Everything else here treats
		// absent and blank alike and can therefore only ever be overwritten, never unset.
		boolean brandLogoUrlSupplied = input_map.containsKey("brandLogoUrl");
		String brandLogoUrl = brandLogoUrlSupplied
			? brandingValidator.logoUrl(asString(input_map.get("brandLogoUrl")))
			: null;

		// R-17d. Same absent/blank rule as the logo, and the same validator: an uploaded
		// asset path and a CDN URL are both acceptable and everything else is not.
		boolean brandHeaderIconUrlSupplied = input_map.containsKey("brandHeaderIconUrl");
		String brandHeaderIconUrl = brandHeaderIconUrlSupplied
			? brandingValidator.logoUrl(asString(input_map.get("brandHeaderIconUrl")))
			: null;

		boolean brandBackgroundImageUrlSupplied = input_map.containsKey("brandBackgroundImageUrl");
		String brandBackgroundImageUrl = brandBackgroundImageUrlSupplied
			? brandingValidator.logoUrl(asString(input_map.get("brandBackgroundImageUrl")))
			: null;

		boolean brandLogoAltSupplied = input_map.containsKey("brandLogoAlt");
		String brandLogoAlt = brandLogoAltSupplied
			? brandingValidator.text(asString(input_map.get("brandLogoAlt")))
			: null;

		boolean patronWelcomeSupplied = input_map.containsKey("patronWelcome");
		String patronWelcome = patronWelcomeSupplied
			? brandingValidator.text(asString(input_map.get("patronWelcome")))
			: null;

		boolean defaultThemeNameSupplied = input_map.containsKey("defaultThemeName");
		String defaultThemeName = defaultThemeNameSupplied
			? brandingValidator.themeName(asString(input_map.get("defaultThemeName")))
			: null;

		// What each brand image pointed at before this edit, so the objects it stops
		// referencing can be removed. Collected inside the transaction, acted on after it
		// commits: deleting an object for an update that then rolls back would leave a
		// row pointing at a 404.
		final var replacedAssets = new java.util.ArrayList<BrandAssetCleanup.Change>();

		Mono<Consortium> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(consortiumRepository.findById(id))
				.flatMap(consortium -> {
					if (displayName != null) {
						consortium.setDisplayName(displayName);
					}
					if (description != null) {
						consortium.setDescription(description);
					}
					if (catalogueSearchUrl != null) {
						consortium.setCatalogueSearchUrl(catalogueSearchUrl);
					}
					if (websiteUrl != null) {
						consortium.setWebsiteUrl(websiteUrl);
					}
					// Who changed a brand image is NOT recorded on this row. setLastEditedBy
					// below plus the audit trigger on `consortium` puts the actor and the
					// before/after in data_change_log, which is behind a role check and is
					// where provenance for every other config change already lives. The
					// uploader columns that used to sit here held a member of staff's name
					// and email address on a row any authenticated principal could read.
					if (brandLogoUrlSupplied) {
						replacedAssets.add(new BrandAssetCleanup.Change(
							consortium.getBrandLogoUrl(), brandLogoUrl));
						consortium.setBrandLogoUrl(brandLogoUrl);
					}
					if (brandLogoAltSupplied) {
						consortium.setBrandLogoAlt(brandLogoAlt);
					}
					if (brandHeaderIconUrlSupplied) {
						replacedAssets.add(new BrandAssetCleanup.Change(
							consortium.getBrandHeaderIconUrl(), brandHeaderIconUrl));
						consortium.setBrandHeaderIconUrl(brandHeaderIconUrl);
					}
					if (brandBackgroundImageUrlSupplied) {
						replacedAssets.add(new BrandAssetCleanup.Change(
							consortium.getBrandBackgroundImageUrl(), brandBackgroundImageUrl));
						consortium.setBrandBackgroundImageUrl(brandBackgroundImageUrl);
					}
					if (patronWelcomeSupplied) {
						consortium.setPatronWelcome(patronWelcome);
					}
					if (defaultThemeNameSupplied) {
						consortium.setDefaultThemeName(defaultThemeName);
					}
					consortium.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(consortium::setChangeReferenceUrl);
					changeCategory.ifPresent(consortium::setChangeCategory);
					reason.ifPresent(consortium::setReason);
					return Mono.from(consortiumRepository.update(consortium));
				})
		));

		// Cleanup runs after the commit and can never fail the edit: BrandAssetCleanup
		// swallows a failed delete deliberately, and a leaked object costs one image.
		return transactionMono
			.flatMap(consortium -> brandAssetCleanup.removeReplaced(replacedAssets)
				.thenReturn(consortium))
			.toFuture();
	}

	/** GraphQL sends an explicit null as a present key with a null value. */
	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}
}
