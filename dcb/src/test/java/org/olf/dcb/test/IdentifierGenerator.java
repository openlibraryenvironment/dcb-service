package org.olf.dcb.test;

import static services.k_int.utils.StringUtils.convertIntegerToString;

import java.util.Random;

public class IdentifierGenerator {
	public static Integer generateNumericLocalId() {
		return new Random().nextInt(1000000);
	}

	public static String generateBarcode() {
		return String.valueOf(new Random().nextLong(1000000000));
	}

	public static String generateNumericLocalIdAsString() {
		return convertIntegerToString(generateNumericLocalId());
	}
}
