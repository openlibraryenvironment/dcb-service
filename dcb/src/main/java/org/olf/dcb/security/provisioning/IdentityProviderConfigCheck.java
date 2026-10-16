package org.olf.dcb.security.provisioning;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;

/**
 * A provider type nobody implements is a typo, not a decision. Eager, so it fails the
 * deploy rather than the first account somebody tries to create.
 *
 * <p>Takes the property, not {@link IdentityProviderConfig}: injecting that validates it,
 * and its {@code @NotBlank} secret then makes every deployment with provisioning switched
 * off fail to start. Empty turns provisioning off, and stays silent.
 */
@Context
public class IdentityProviderConfigCheck {

	// Keep these lower kebab-case
	private static final List<String> SUPPORTED = List.of(
		"keycloak"
	);

	public IdentityProviderConfigCheck(ApplicationContext app) {
		
		final Map<String, Object> definedTypes = app.getProperties(IdentityProviderConfig.CONFIG_ROOT);
		var firstInvalid = definedTypes.keySet().stream()
			.filter( Objects::nonNull )
			
			// Roots only.
			.map( val -> val.split("\\.")[0] )
			.map( String::toLowerCase )
			.filter( Predicate.not(SUPPORTED::contains) )
			.findAny();
			
		// Error?
		if ( firstInvalid.isPresent() )
			throw new IllegalStateException(String.format(
				"dcb.identity-provider \"%s\" is not supported. " +
				"Supported values are: [\"%s\"]",
			
				firstInvalid.get(),
				SUPPORTED.stream().collect(Collectors.joining("\", \""))));
	}
}
