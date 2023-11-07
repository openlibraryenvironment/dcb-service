package org.olf.dcb.core.interaction.shared;

import lombok.Getter;

@Getter
public class UnableToConvertLocalPatronTypeException extends RuntimeException {
	private final String localId;
	private final String localSystemCode;
	private final String localPatronTypeCode;

	public UnableToConvertLocalPatronTypeException(String message,
		String localId, String localSystemCode, String localPatronTypeCode) {

		super(message);
		this.localId = localId;
		this.localSystemCode = localSystemCode;
		this.localPatronTypeCode = localPatronTypeCode;
	}
}
