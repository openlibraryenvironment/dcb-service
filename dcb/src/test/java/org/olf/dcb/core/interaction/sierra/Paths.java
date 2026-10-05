package org.olf.dcb.core.interaction.sierra;

import org.jspecify.annotations.NonNull;

public class Paths {
	public static @NonNull String itemPath(String itemId) {
		return toSierraApiPath("/items/%s".formatted(itemId));
	}

	public static @NonNull String itemCheckoutPath(String itemId) {
		return itemPath(itemId) + "/checkouts";
	}

	public static @NonNull String patronsPath() {
		return toSierraApiPath("/patrons");
	}

	public static @NonNull String patronPath(Object patronId) {
		return patronsPath() + "/" + patronId;
	}

	public static @NonNull String patronHoldsPath(String patronId) {
		return patronPath(patronId) + "/holds";
	}

	public static @NonNull String patronHoldRequestsPath(String patronId) {
		return patronHoldsPath(patronId) + "/requests";
	}

	public static @NonNull String patronCheckoutPath(String checkoutId) {
		return patronPath("checkouts/%s".formatted(checkoutId));
	}

	public static @NonNull String renewalPath(String checkoutId) {
		return patronCheckoutPath(checkoutId) + "/renewal";
	}

	private static @NonNull String toSierraApiPath(String subPath) {
		return "/iii/sierra-api/v6" + subPath;
	}
}
