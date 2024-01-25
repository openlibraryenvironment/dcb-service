package org.olf.dcb.core.interaction.shared;

import lombok.Getter;
import org.olf.dcb.core.error.DcbError;

@Getter
public class NoItemTypeMappingFoundException extends DcbError {
	private final String hostLmsCode;
	private final String localItemType;

	public NoItemTypeMappingFoundException(String message, String hostLmsCode,
		String localItemType) {

		super(message);
		this.hostLmsCode = hostLmsCode;
		this.localItemType = localItemType;
	}
}
