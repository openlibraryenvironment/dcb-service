package org.olf.dcb.core.interaction;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Every mapping pointing at one Host LMS, judged against what that system actually holds.
 * <p>
 * {@link MappingValueCheck} answers for one value someone is about to map. This answers for
 * the ones already saved, which is where the faults have been: an Alma sandbox carried ten
 * patron type mappings targeting {@code Consortial Express Patron}, the description of user
 * group {@code CONSORTIAL}, and nothing failed until a hold was placed and Alma refused it.
 */
@Serdeable
public record MappingAudit(
	String hostLmsCode,
	Status status,
	@Nullable String detail,
	int checked,
	int missing,
	List<Row> rows,
	List<String> notChecked) {

	public enum Status {
		/** The Host LMS was asked and the rows below are the result. */
		CHECKED,
		/** This adapter cannot read vocabularies from its Host LMS. */
		NOT_SUPPORTED,
		/** The adapter tried and the Host LMS could not be read. */
		FAILED
	}

	/** Which side of a mapping was checked against the system's list. */
	public enum Direction {
		/** A value DCB sends to the system: the mapping's to_value. */
		SENT_TO_SYSTEM,
		/** A value DCB reads from the system: the mapping's from_value. */
		READ_FROM_SYSTEM
	}

	/**
	 * One saved mapping. {@code result} carries {@link MappingValueCheck}'s four outcomes, so a
	 * vocabulary that could not be read reports UNKNOWN and never MISSING.
	 */
	@Serdeable
	public record Row(String category, Direction direction, String fromContext, String fromValue,
		String toValue, MappingValueCheck.Result result, @Nullable String detail) {}

	public static MappingAudit notSupported(String hostLmsCode, String detail) {
		return new MappingAudit(hostLmsCode, Status.NOT_SUPPORTED, detail, 0, 0, List.of(), List.of());
	}

	public static MappingAudit failed(String hostLmsCode, String detail) {
		return new MappingAudit(hostLmsCode, Status.FAILED, detail, 0, 0, List.of(), List.of());
	}

	public static MappingAudit of(String hostLmsCode, List<Row> rows, List<String> notChecked) {
		final var missing = (int) rows.stream()
			.filter(row -> row.result() == MappingValueCheck.Result.MISSING)
			.count();

		return new MappingAudit(hostLmsCode, Status.CHECKED, null, rows.size(), missing, rows, notChecked);
	}
}
