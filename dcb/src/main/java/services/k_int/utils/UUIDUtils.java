package services.k_int.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

import javax.validation.constraints.NotNull;

public class UUIDUtils {

	private static final Charset UTF8 = Charset.forName("UTF-8");
	public static final UUID NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
	public static final UUID NAMESPACE_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

	/**
	 * Create a UUID V5 from the supplied namespace UUID and a string
	 * 
	 * @param namespace UUID namespace
	 * @param name      The string data
	 * @return A Version 5 compliant UUID
	 */
	public static UUID nameUUIDFromNamespaceAndString(@NotNull UUID namespace, @NotNull String name) {
		return nameUUIDFromNamespaceAndBytes(namespace, name.getBytes(UTF8));
	}

	/**
	 * Create a UUID V5 from the supplied namespace UUID and an array of bytes
	 * 
	 * @param namespace UUID namespace
	 * @param nameBytes The byte data
	 * @return A Version 5 compliant UUID
	 */
	public static UUID nameUUIDFromNamespaceAndBytes(@NotNull UUID namespace, @NotNull byte[] nameBytes) {
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new InternalError("SHA-1 not supported");
		}

		// Create a bytebuffer.
		final ByteBuffer buffer = ByteBuffer.wrap(new byte[16 + nameBytes.length]);
		buffer.putLong(Objects.requireNonNull(namespace, "Namespace is null").getMostSignificantBits());
		buffer.putLong(namespace.getLeastSignificantBits());
		buffer.put(nameBytes);

		md.update(buffer.array());

		// Tweak the bytes to add variant information
		final byte[] sha1Bytes = md.digest();
		sha1Bytes[6] &= 0x0f; /* clear version */
		sha1Bytes[6] |= 0x50; /* set to version 5 */
		sha1Bytes[8] &= 0x3f; /* clear variant */
		sha1Bytes[8] |= 0x80; /* set to IETF variant */

		return fromBytes(sha1Bytes);
	}

	/**
	 * Construct UUID directly from Bytes.
	 * 
	 * @param data The byte data
	 * @return A UUID
	 */
	public static UUID fromBytes(final byte[] data) {
		// Based on the private UUID(bytes[]) constructor
		long msb = 0;
		long lsb = 0;
		assert data.length >= 16;

		// First 8 bytes (64 bits)
		for (int i = 0; i < 8; i++)
			msb = (msb << 8) | (data[i] & 0xff);

		// Next 8 bytes (64 bits). Truncating if longer than 128 bits total
		for (int i = 8; i < 16; i++)
			lsb = (lsb << 8) | (data[i] & 0xff);

		return new UUID(msb, lsb);
	}
}
