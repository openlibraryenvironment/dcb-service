package org.olf.dcb.item.availability;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Value;

/**
 * Availability for several clusters, asked for once.
 *
 * <p>Two lists rather than one list of outcomes: {@code results} entries are exactly the
 * shape {@code /items/availability} returns, so a client reuses its parser, and a caller
 * that wants only what answered does not have to filter. A cluster appears in exactly one
 * of the two.
 */
@Value
@Serdeable
public class BatchAvailabilityView {
	/**
	 * One entry per cluster that could be checked, in the order they were asked for.
	 *
	 * <p>ALWAYS serialised, including when empty. Serde's default inclusion omits an empty
	 * collection rather than only a null one, so a batch that checked nothing would arrive
	 * with no {@code results} key at all and every client would have to defend against a
	 * field its own type declares as required.
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	List<AvailabilityResponseView> results;

	/**
	 * Clusters that could not be checked at all.
	 *
	 * <p>Separate from {@link AvailabilityResponseView#getErrors()}, which reports a host
	 * that failed WITHIN an otherwise successful check. This is the check itself failing,
	 * and a client has to say something different about it: "we could not look" rather than
	 * "we looked and found none".
	 */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	List<Unchecked> failures;

	@Value
	@Serdeable
	public static class Unchecked {
		UUID clusteredBibId;
		String message;
	}

	// The class only: this answers anonymous callers, and a message can carry a member system's URL
	public static Unchecked unchecked(UUID clusteredBibId, Throwable error) {
		return new Unchecked(clusteredBibId, error.getClass().getSimpleName());
	}
}
