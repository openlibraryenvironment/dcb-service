package org.olf.dcb.core.interaction;

import java.util.regex.Pattern;

/**
 * An operational mailbox set on every virtual patron, so the member system's hold and pickup
 * notices go somewhere the consortium reads. Never a patron's own address.
 */
public final class VirtualPatronEmail {
	public static final String SETTING = "virtual-patron-email";

	// Shape only; whether anyone reads the mailbox is the consortium's business
	private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public static boolean isAddress(Object value) {
		return value instanceof String text && ADDRESS.matcher(text).matches();
	}

	// The validator refuses a malformed value, but REST and the import save without it, and a member
	// system may refuse the whole patron over one
	public static String addressOrNull(Object configured) {
		return isAddress(configured) ? (String) configured : null;
	}

	private VirtualPatronEmail() { }
}
