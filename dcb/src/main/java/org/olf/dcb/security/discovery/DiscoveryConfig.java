package org.olf.dcb.security.discovery;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Discovery services DCB will accept patron assertions from: docs/discovery-service-approach.md.
 *
 * <p>Present only when discovery is on. With it off there is no bean, so nothing here is read or
 * checked, and the routes that need it do not exist.
 */
@Context
@Requires(property = DiscoveryConfig.ENABLED, value = "true")
@ConfigurationProperties(DiscoveryConfig.ROOT)
public interface DiscoveryConfig {
	String ROOT = "dcb.discovery";
	String ENABLED = ROOT + ".enabled";

	/** The audience a patron assertion must name. This DCB, and not some other one. */
	@NotBlank
	@NonNull
	@Bindable(defaultValue = "dcb")
	String getAudience();

	/** DCB's own cap on an assertion's validity, whatever the issuer put in {@code exp}. */
	@NotNull
	@NonNull
	@Bindable(defaultValue = "PT2M")
	Duration getMaxAssertionLifetime();

	/** How many loans one patron may be told about: each is one live item read at their library. */
	@Min(1)
	@Bindable(defaultValue = "25")
	int getMaxLoans();

	/** How many of those reads may be in flight at once, all against the patron's own library. */
	@Min(1)
	@Bindable(defaultValue = "2")
	int getLoanLookupConcurrency();

	@NotNull
	@NonNull
	@Bindable(defaultValue = "8s")
	Duration getLoanLookupTimeout();

	/** How many loans and how many charges one account call may return. */
	@Min(1)
	@Bindable(defaultValue = "25")
	int getMaxAccountRows();

	@NotNull
	@NonNull
	@Bindable(defaultValue = "8s")
	Duration getAccountLookupTimeout();

	@NotNull
	@NonNull
	@Bindable(defaultValue = "8s")
	Duration getPatronResolveTimeout();

	/** How many institution sign-ins one trusted service may resolve in a minute. */
	@Min(1)
	@Bindable(defaultValue = "600")
	int getPatronResolvesPerMinute();

	/**
	 * The trusted services as one JSON array, the one form a deployment with only environment
	 * variables can express: {@link DiscoveryTrustedServices#parse(String)}.
	 */
	@Nullable
	String getTrustedServicesJson();
}
