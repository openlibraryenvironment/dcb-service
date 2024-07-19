package org.olf.dcb.core.audit;
import java.util.UUID;
import java.util.Map;
public interface Auditable {




	UUID getId();

	void setLastEditedBy(String lastEditedBy);

	void setReason(String reason);

	String getLastEditedBy();
}
