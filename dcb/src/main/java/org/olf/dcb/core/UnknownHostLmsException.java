package org.olf.dcb.core;

public class UnknownHostLmsException extends RuntimeException {
	UnknownHostLmsException(String propertyName, Object value) {
		super(String.format("No Host LMS found for %s: %s", propertyName, value));
	}
}
