package services.k_int.utils;

import java.util.Map;
import java.util.Optional;

public class MapUtils {
	
	public static <K> Optional<String> getAsOptionalString ( Map<K,?> map, K key ) {
		return Optional.ofNullable(map.get(key))
			.map(String::valueOf);
	}

	public static <T> void putNonNullValue(Map<String, T> auditData,
		String key, T value) {

		if (value != null) {
			auditData.put(key, value);
		}
	}
}
