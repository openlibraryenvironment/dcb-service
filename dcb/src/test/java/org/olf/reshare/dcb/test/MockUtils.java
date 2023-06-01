package org.olf.reshare.dcb.test;

import org.mockito.stubbing.Answer;

import reactor.core.publisher.Mono;

public class MockUtils {
	private MockUtils() { }

	public static Answer<Object> withFirstArgument() {
		return invocation -> Mono.just(invocation.getArgument(0));
	}
}
