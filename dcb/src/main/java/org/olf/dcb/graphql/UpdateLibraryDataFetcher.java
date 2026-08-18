package org.olf.dcb.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import org.olf.dcb.core.branding.BrandAssetCleanup;
import org.olf.dcb.core.branding.BrandingValidator;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.storage.LibraryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Singleton
public class UpdateLibraryDataFetcher implements DataFetcher<CompletableFuture<Library>> {

	private final LibraryRepository libraryRepository;

	private final R2dbcOperations r2dbcOperations;

	private final BrandingValidator brandingValidator;

	private final BrandAssetCleanup brandAssetCleanup;

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);


	public UpdateLibraryDataFetcher(LibraryRepository libraryRepository,
		R2dbcOperations r2dbcOperations, BrandingValidator brandingValidator,
		BrandAssetCleanup brandAssetCleanup) {

		this.libraryRepository = libraryRepository;
		this.r2dbcOperations = r2dbcOperations;
		this.brandingValidator = brandingValidator;
		this.brandAssetCleanup = brandAssetCleanup;
	}

	@Override
	public CompletableFuture<Library> get(DataFetchingEnvironment env) {
		Map<String, Object> input_map = env.getArgument("input");
		log.debug("updateLibraryDataFetcher {}", input_map);

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

		Collection<String> roles = env.getGraphQlContext().get("roles");

		// Check if the user has the required role to edit library information
		if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN") && !roles.contains("LIBRARY_ADMIN"))) {
			log.warn("updateLibraryDataFetcher: Access denied for user {}: user does not have the required role to update a library.", userString);
			throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");		}

		String backupDowntimeSchedule = input_map.containsKey("backupDowntimeSchedule") ?
			(input_map.get("backupDowntimeSchedule").toString()) : null;
		String supportHours = input_map.containsKey("supportHours") ?
			input_map.get("supportHours").toString() : null;
		String fullName = input_map.containsKey("fullName") ?
			input_map.get("fullName").toString() : null;
		String shortName = input_map.containsKey("shortName") ?
			input_map.get("shortName").toString() : null;
		String abbreviatedName = input_map.containsKey("abbreviatedName") ?
			input_map.get("abbreviatedName").toString() : null;
		String targetLoanToBorrowRatio = input_map.containsKey("targetLoanToBorrowRatio") ?
			input_map.get("targetLoanToBorrowRatio").toString() : null;
		String secretLabel = input_map.containsKey("secretLabel") ?
			input_map.get("secretLabel").toString() : null;
		String principalLabel = input_map.containsKey("principalLabel") ?
			input_map.get("principalLabel").toString() : null;
		String address = input_map.containsKey("address") ?
			input_map.get("address").toString() : null;
		String discoverySystem = input_map.containsKey("discoverySystem") ?
			input_map.get("discoverySystem").toString() : null;
		String patronWebsite = input_map.containsKey("patronWebsite") ?
			input_map.get("patronWebsite").toString() : null;
		String type = input_map.containsKey("type") ?
			input_map.get("type").toString() : null;


		Float latitude = input_map.containsKey("latitude") ?
			((Number) input_map.get("latitude")).floatValue() : null;
		Float longitude = input_map.containsKey("longitude") ?
			((Number) input_map.get("longitude")).floatValue() : null;

		// Patron-facing brand (N-1.3). Validated before the transaction opens; a key that
		// is present but blank CLEARS the field, so a library that uploaded the wrong mark
		// can remove it. See UpdateConsortiumDataFetcher for the full reasoning.
		boolean brandLogoUrlSupplied = input_map.containsKey("brandLogoUrl");
		String brandLogoUrl = brandLogoUrlSupplied
			? brandingValidator.logoUrl(asString(input_map.get("brandLogoUrl")))
			: null;

		boolean brandLogoAltSupplied = input_map.containsKey("brandLogoAlt");
		String brandLogoAlt = brandLogoAltSupplied
			? brandingValidator.text(asString(input_map.get("brandLogoAlt")))
			: null;

		boolean defaultThemeNameSupplied = input_map.containsKey("defaultThemeName");
		String defaultThemeName = defaultThemeNameSupplied
			? brandingValidator.themeName(asString(input_map.get("defaultThemeName")))
			: null;


		// Collected inside the transaction, acted on after it commits — see the same
		// comment in UpdateConsortiumDataFetcher.
		final var replacedAssets = new java.util.ArrayList<BrandAssetCleanup.Change>();

		Mono<Library> transactionMono = Mono.from(r2dbcOperations.withTransaction(status ->
			Mono.from(libraryRepository.findById(id))
				.flatMap(library -> {
					if (backupDowntimeSchedule != null) {
						library.setBackupDowntimeSchedule(backupDowntimeSchedule);
					}
					if (supportHours != null) {
						library.setSupportHours(supportHours);
					}
					if (latitude != null) {
						library.setLatitude(latitude);
					}
					if (longitude != null) {
						library.setLongitude(longitude);
					}
					if (shortName != null) {
						library.setShortName(shortName);
					}
					if (fullName != null) {
						library.setFullName(fullName);
					}
					if (abbreviatedName != null) {
						library.setAbbreviatedName(abbreviatedName);
					}
					if (targetLoanToBorrowRatio != null) {
						library.setTargetLoanToBorrowRatio(targetLoanToBorrowRatio);
					}
					if (secretLabel != null) {
						library.setSecretLabel(secretLabel);
					}
					if (principalLabel != null) {
						library.setPrincipalLabel(principalLabel);
					}
					if (address != null) {
						library.setAddress(address);
					}
					if (discoverySystem != null) {
						library.setDiscoverySystem(discoverySystem);
					}
					if (patronWebsite != null) {
						library.setPatronWebsite(patronWebsite);
					}
					if (type != null) {
						library.setType(type);
					}
					if (brandLogoUrlSupplied) {
						replacedAssets.add(new BrandAssetCleanup.Change(
							library.getBrandLogoUrl(), brandLogoUrl));
						library.setBrandLogoUrl(brandLogoUrl);
					}
					if (brandLogoAltSupplied) {
						library.setBrandLogoAlt(brandLogoAlt);
					}
					if (defaultThemeNameSupplied) {
						library.setDefaultThemeName(defaultThemeName);
					}
					library.setLastEditedBy(userString);
					changeReferenceUrl.ifPresent(library::setChangeReferenceUrl);
					changeCategory.ifPresent(library::setChangeCategory);
					reason.ifPresent(library::setReason);
					// And continue as you go - possibly with switch statements
					return Mono.from(libraryRepository.update(library));
				})
		));

		return transactionMono
			.flatMap(library -> brandAssetCleanup.removeReplaced(replacedAssets)
				.thenReturn(library))
			.toFuture();
	}

	/** GraphQL sends an explicit null as a present key with a null value. */
	private static String asString(Object value) {
		return value == null ? null : value.toString();
	}
}
