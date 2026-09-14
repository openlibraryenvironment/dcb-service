package org.olf.dcb.core.api.serde;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Introspected
@Serdeable
public class RequestedTitleStat {
	/**
	 * The work, so a client can link the row to the requests behind it. Selected rather than
	 * derived: the query already groups by it.
	 */
	UUID clusterId;
	String title;
	Integer requestCount;
}
