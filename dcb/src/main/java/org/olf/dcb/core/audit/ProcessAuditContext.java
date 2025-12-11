package org.olf.dcb.core.audit;

import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Introspected
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessAuditContext {

	@NonNull
	@NotNull
	final UUID processId;

	@NonNull
	@NotNull
	@Size(min = 1, max = 15)
	final String processType;

	@NonNull
	@NotNull
	final UUID processSubject;
	
	public static class ProcessAuditContextBuilder {
		UUID processId = UUID.randomUUID();
		ProcessAuditContextBuilder processId(UUID processId) {
			this.processId = processId;
			return this;
		}
	}
}
