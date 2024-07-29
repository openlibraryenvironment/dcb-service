package org.olf.dcb.core.audit;
import java.util.UUID;

import io.micronaut.security.annotation.UpdatedBy;
public interface Auditable {
	
	UUID getId();
	
	String getLastEditedBy();
	
	String getReason();
	
}
