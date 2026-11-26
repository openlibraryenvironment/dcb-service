package services.k_int.interaction.oaipmh;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every OAI-PMH reply is read into {@link Response} by reflection, and a native image can only
 * reflect on the classes {@code reflect-config.json} names: a type missing there fails every
 * harvest at its first page, on the native image alone.
 */
class OaiPmhNativeImageTests {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String REFLECT_CONFIG = "/META-INF/native-image/org.olf.dcb/dcb/reflect-config.json";

	@Test
	void everyTypeAnOaiPmhReplyIsReadIntoIsRegisteredForNativeReflection() throws Exception {
		final Map<String, Map<String, Object>> registered = registeredTypes();

		final List<String> unregistered = typesReachableFrom(Response.class).stream()
			.filter(type -> !isRegistered(type, registered.get(type.getName())))
			.map(Class::getName)
			.toList();

		assertThat("types missing from " + REFLECT_CONFIG, unregistered, empty());
	}

	private static boolean isRegistered(Class<?> type, Map<String, Object> entry) {
		if (entry == null || !Boolean.TRUE.equals(entry.get("allDeclaredMethods"))) {
			return false;
		}

		// A record is built through its canonical constructor; an enum is not constructed
		return type.isEnum() || Boolean.TRUE.equals(entry.get("allDeclaredConstructors"));
	}

	private Map<String, Map<String, Object>> registeredTypes() throws Exception {
		try (var input = getClass().getResourceAsStream(REFLECT_CONFIG)) {
			final List<Map<String, Object>> entries = JSON.readValue(
				new String(input.readAllBytes(), StandardCharsets.UTF_8), new TypeReference<>() { });

			return entries.stream()
				.collect(Collectors.toMap(entry -> (String) entry.get("name"), Function.identity(),
					(first, second) -> first));
		}
	}

	private static Set<Class<?>> typesReachableFrom(Class<?> root) {
		final Set<Class<?>> found = new LinkedHashSet<>();
		final Deque<Type> pending = new ArrayDeque<>(List.of(root));

		while (!pending.isEmpty()) {
			final Type next = pending.pop();

			if (next instanceof ParameterizedType parameterized) {
				pending.addAll(List.of(parameterized.getActualTypeArguments()));
			}
			else if (next instanceof Class<?> type && isOaiPmhType(type) && found.add(type)) {
				if (type.isRecord()) {
					for (RecordComponent component : type.getRecordComponents()) {
						pending.push(component.getGenericType());
					}
				}
			}
		}

		return found;
	}

	private static boolean isOaiPmhType(Class<?> type) {
		return type.getPackageName().equals(Response.class.getPackageName())
			&& (type.isRecord() || type.isEnum());
	}
}
