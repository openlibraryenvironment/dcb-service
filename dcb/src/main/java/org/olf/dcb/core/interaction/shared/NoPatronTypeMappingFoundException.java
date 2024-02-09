package org.olf.dcb.core.interaction.shared;

import lombok.Getter;

@Getter
public class NoPatronTypeMappingFoundException extends RuntimeException {
	private final String hostLmsCode;
	private final String localPatronType;

	public NoPatronTypeMappingFoundException(String message, String hostLmsCode,
		String localPatronType) {

		super(message);
		this.hostLmsCode = hostLmsCode;
		this.localPatronType = localPatronType;
	}

	// Constructor overload for a single message string
	public NoPatronTypeMappingFoundException(String message) {
		super(message);
		this.hostLmsCode = null;
		this.localPatronType = null;
	}
}
