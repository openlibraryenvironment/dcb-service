package org.olf.dcb.core.interaction.shared;

import lombok.Getter;

@Getter
public class NoPatronTypeMappingFoundException extends RuntimeException {
	private final String hostLmsCode;
	private final String localPatronTypeCode;

	public NoPatronTypeMappingFoundException(String message, String hostLmsCode,
		String localPatronTypeCode) {

		super(message);
		this.hostLmsCode = hostLmsCode;
		this.localPatronTypeCode = localPatronTypeCode;
	}
}
