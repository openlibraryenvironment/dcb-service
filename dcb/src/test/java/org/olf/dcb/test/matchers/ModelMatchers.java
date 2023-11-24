package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import java.util.UUID;

import org.hamcrest.Matcher;

public class ModelMatchers {
	public static Matcher<Object> hasId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}
}
