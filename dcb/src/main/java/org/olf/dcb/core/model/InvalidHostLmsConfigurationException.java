package org.olf.dcb.core.model;

import lombok.Getter;

@Getter
public class InvalidHostLmsConfigurationException extends RuntimeException {
	private final String hostLmsCode;

	public InvalidHostLmsConfigurationException(String hostLmsCode, String message) {
		super("Host LMS \"" + hostLmsCode + "\" has invalid configuration: " + message);

		this.hostLmsCode = hostLmsCode;
	}
}
