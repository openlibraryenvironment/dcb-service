package org.olf.dcb.core.interaction;

import java.util.List;

import lombok.Getter;

/**
 * An agency's auth profile that its Host LMS cannot authenticate with. A configuration error,
 * deliberately not the empty answer a wrong PIN gets.
 */
@Getter
public class UnsupportedAuthProfileException extends RuntimeException {
	private final String hostLmsCode;
	private final String authProfile;

	public UnsupportedAuthProfileException(String hostLmsCode, String authProfile, List<String> supported) {
		super("Host LMS \"%s\" does not support auth profile \"%s\". Supported: %s"
			.formatted(hostLmsCode, authProfile, String.join(", ", supported)));

		this.hostLmsCode = hostLmsCode;
		this.authProfile = authProfile;
	}
}
