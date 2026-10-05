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

	private VirtualPatronEmail() { }
}
