package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.Patron;

public class PatronMatchers {
	public static Matcher<Patron> hasLocalId(String... localIds) {
		return hasProperty("localId", containsInAnyOrder(localIds));
	}
}
