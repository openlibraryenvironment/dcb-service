package org.olf.dcb.core.audit.model;

import java.time.Instant;
import java.util.UUID;

import org.olf.dcb.core.audit.ProcessAuditContext;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Value
@Serdeable
@MappedEntity
@Builder(toBuilder = true)
@ExcludeFromGeneratedCoverageReport
@AllArgsConstructor(onConstructor_ = @Creator(), access = AccessLevel.PACKAGE)
public class ProcessAuditLogEntry {
	
	@Id
	@NotNull
	private final UUID id;
	
	@NotNull
	private final String processType;
	
	@NotNull
	private final UUID processId;

	@NotNull
	private final UUID subjectId;
	
	@NotNull
	private final String message;
	
	@NotNull
	private final Instant timestamp;
	
	public static class ProcessAuditLogEntryBuilder {
		private ProcessAuditLogEntryBuilder() {};
		private ProcessAuditLogEntryBuilder id( UUID id ) {
			this.id = id;
			return this;
		}
		
		private ProcessAuditLogEntryBuilder timestamp( Instant timestamp ) {
			this.timestamp = timestamp;
			return this;
		}
		
		private ProcessAuditLogEntryBuilder processId( UUID processId ) {
			this.processId = processId;
			return this;
		}

		private ProcessAuditLogEntryBuilder subjectId( UUID subjectId ) {
			this.subjectId = subjectId;
			return this;
		}
		
		private ProcessAuditLogEntryBuilder processType( String processType ) {
			this.processType = processType;
			return this;
		}
	}
	
	public static ProcessAuditLogEntryBuilder builder(ProcessAuditContext context) {
		return new ProcessAuditLogEntryBuilder()
			.id( UUID.randomUUID() )
			.timestamp( Instant.now() )
			.processId( context.getProcessId() )
			.processType( context.getProcessType() )
			.subjectId( context.getProcessSubject() );
	}
}
