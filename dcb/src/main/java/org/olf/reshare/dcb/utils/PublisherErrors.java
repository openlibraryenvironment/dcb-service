package org.olf.reshare.dcb.utils;

import java.util.Optional;
import java.util.function.Supplier;

public class PublisherErrors {
	public static <T> T failWhenEmpty(Optional<? extends T> optionalRecord,
		Supplier<? extends RuntimeException> exceptionSupplier) {

		if (optionalRecord.isEmpty()) {
			throw exceptionSupplier.get();
		} else {
			return optionalRecord.get();
		}
	}
}
