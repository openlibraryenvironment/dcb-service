package org.olf.dcb.test;

import java.util.Random;

public class IdentifierGenerator {
	public static Integer generateNumericLocalId() {
		return new Random().nextInt(1000000);
	}

	public static String generateBarcode() {
		return String.valueOf(new Random().nextLong(1000000000));
	}
}
