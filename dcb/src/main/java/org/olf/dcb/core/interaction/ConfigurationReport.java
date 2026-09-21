package org.olf.dcb.core.interaction;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * What a Host LMS can tell DCB about its own configuration.
 * <p>
 * Two questions in one answer, both asked while an implementer is building mappings: does the
 * configuration DCB was given actually exist in that system, and what values does the system
 * hold that mappings could be built from.
 * <p>
 * {@code NOT_SUPPORTED} is deliberately distinct from a report with nothing wrong in it. An
 * adapter that cannot ask must not look like one that asked and found everything present.
 */
@Serdeable
public record ConfigurationReport(
	String hostLmsCode,
	Status status,
	@Nullable String detail,
	List<Check> checks,
	List<Vocabulary> vocabularies) {

	public enum Status {
		/** The Host LMS was asked, and the checks and vocabularies below are its answers. */
		CHECKED,
		/** This adapter cannot read configuration from its Host LMS. */
		NOT_SUPPORTED,
		/** The adapter tried, and the Host LMS could not be read. */
		FAILED
	}

	public enum CheckResult {
		/** DCB's configured value exists in the Host LMS. */
		PRESENT,
		/** DCB has a value configured and the Host LMS does not have it. */
		MISSING,
		/** DCB has no value for this setting. */
		NOT_CONFIGURED,
		/** The list this would have been checked against could not be read. */
		UNKNOWN
	}

	/** One setting DCB holds, and whether the Host LMS agrees it exists. */
	@Serdeable
	public record Check(String setting, @Nullable String configuredValue, CheckResult result,
		@Nullable String detail) {}

	/**
	 * A list of values the Host LMS holds, for building mappings from.
	 * <p>
	 * {@code truncated} is set rather than the list silently cut: a tenant's locations have no
	 * documented ceiling, and a half-list presented as whole would send someone mapping
	 * against values they cannot see.
	 */
	@Serdeable
	public record Vocabulary(String name, List<Entry> entries, boolean truncated,
		@Nullable String detail) {}

	@Serdeable
	public record Entry(String code, @Nullable String description) {}

	public static ConfigurationReport notSupported(String hostLmsCode, String detail) {
		return new ConfigurationReport(hostLmsCode, Status.NOT_SUPPORTED, detail, List.of(), List.of());
	}

	public static ConfigurationReport failed(String hostLmsCode, String detail) {
		return new ConfigurationReport(hostLmsCode, Status.FAILED, detail, List.of(), List.of());
	}
}
