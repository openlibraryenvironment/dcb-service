package org.olf.dcb.test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.graphql.validation.HostLmsConfigValidator;

import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Whether a client config that {@link HostLmsConfigValidator} accepts is one the adapter can
 * use. No database and no application context: the reads are the adapter's own.
 */
public final class ConfigCompleteness {
	private ConfigCompleteness() { }

	/**
	 * A Host LMS carrying the smallest part of {@code clientConfig} the validator still accepts:
	 * each key it does not demand is dropped, so what is left is what it requires.
	 */
	public static DataHostLms acceptedHostLms(String lmsClientClass, Map<String, Object> clientConfig) {
		final var validator = new HostLmsConfigValidator();
		validator.validate(lmsClientClass, clientConfig);

		final var minimal = new LinkedHashMap<>(clientConfig);
		dropUndemanded(minimal, () -> validator.validate(lmsClientClass, minimal));

		// One level down too: Polaris requires keys inside its nested objects
		for (Map.Entry<String, Object> entry : minimal.entrySet()) {
			if (entry.getValue() instanceof Map<?, ?> nested) {
				@SuppressWarnings("unchecked")
				final var copy = new LinkedHashMap<>((Map<String, Object>) nested);
				entry.setValue(copy);
				dropUndemanded(copy, () -> validator.validate(lmsClientClass, minimal));
			}
		}

		return DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("config-completeness")
			.name("Config completeness")
			.lmsClientClass(lmsClientClass)
			.clientConfig(minimal)
			.build();
	}

	private static void dropUndemanded(Map<String, Object> config, Runnable validate) {
		for (String key : List.copyOf(config.keySet())) {
			final var removed = config.remove(key);

			try {
				validate.run();
			} catch (HttpStatusException required) {
				config.put(key, removed);
			}
		}
	}

	/** Each named read that throws, mapped to what it threw. */
	public static Map<String, String> unreadable(Map<String, Callable<?>> reads) {
		final var failures = new LinkedHashMap<String, String>();

		reads.forEach((name, read) -> {
			try {
				read.call();
			} catch (Exception e) {
				failures.put(name, e.getClass().getSimpleName() + ": " + e.getMessage());
			}
		});

		return failures;
	}
}
