package org.olf.dcb.statemodel;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * Used to describe the conditions that guard a state transition. Initially used to
 * generate descriptions of the state model rather than functionally to actually guard the transition
 */

@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
public class DCBGuardCondition {

	String description;
	@Override
	public String toString() {
		return description;
	}
}
