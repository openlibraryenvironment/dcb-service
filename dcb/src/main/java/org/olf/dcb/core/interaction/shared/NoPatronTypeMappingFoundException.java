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
}
