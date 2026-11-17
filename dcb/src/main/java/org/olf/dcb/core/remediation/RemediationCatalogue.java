package org.olf.dcb.core.remediation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * The maintained vocabulary of what to do about a failure DCB can name: one entry in
 * {@code remediation/catalogue.json} for each {@link RemediationCode}.
 */
@Slf4j
// Created at startup, so an entry that does not match RemediationCode stops the application
// starting rather than failing the first request that needs it
@Context
public class RemediationCatalogue {
	static final String LOCATION = "classpath:remediation/catalogue.json";

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
	private static final String UNKNOWN_VALUE = "unknown";

	@Serdeable
	public record Entry(Remediation.Audience audience, List<String> placeholders, String summary,
		List<String> nextSteps, List<String> retrySteps, String docs) { }

	private final Map<RemediationCode, Entry> entries;

	@Inject
	public RemediationCatalogue(ResourceResolver resourceResolver, ObjectMapper objectMapper) {
		this(read(resourceResolver, objectMapper));
	}

	RemediationCatalogue(Map<String, Entry> entries) {
		this.entries = validated(entries);

		log.info("Remediation catalogue loaded: {} entries", this.entries.size());
	}

	public Remediation render(RemediationCode code, @Nullable Map<String, String> values) {
		final var entry = entries.get(code);

		return new Remediation(code, entry.audience(), fill(entry.summary(), values),
			fillAll(entry.nextSteps(), values), fillAll(entry.retrySteps(), values), entry.docs());
	}

	private static List<String> fillAll(List<String> templates, @Nullable Map<String, String> values) {
		return templates.stream().map(step -> fill(step, values)).toList();
	}

	private static String fill(String template, @Nullable Map<String, String> values) {
		return PLACEHOLDER.matcher(template).replaceAll(match -> Matcher.quoteReplacement(
			values != null && values.get(match.group(1)) != null ? values.get(match.group(1)) : UNKNOWN_VALUE));
	}

	private static Map<String, Entry> read(ResourceResolver resourceResolver, ObjectMapper objectMapper) {
		try (InputStream catalogue = resourceResolver.getResourceAsStream(LOCATION)
			.orElseThrow(() -> new IllegalStateException("The remediation catalogue is missing: " + LOCATION))) {

			return objectMapper.readValue(catalogue, Argument.mapOf(String.class, Entry.class));
		}
		catch (IOException e) {
			throw new UncheckedIOException("The remediation catalogue could not be read: " + LOCATION, e);
		}
	}

	private static Map<RemediationCode, Entry> validated(Map<String, Entry> entries) {
		final var problems = new ArrayList<String>();
		final var byCode = new EnumMap<RemediationCode, Entry>(RemediationCode.class);

		entries.forEach((key, entry) -> {
			final var code = Stream.of(RemediationCode.values()).filter(c -> c.name().equals(key)).findFirst();

			if (code.isEmpty()) {
				problems.add(key + " is not a RemediationCode");
				return;
			}

			problems.addAll(problemsWith(key, entry));
			byCode.put(code.get(), entry);
		});

		Stream.of(RemediationCode.values())
			.filter(code -> !entries.containsKey(code.name()))
			.forEach(code -> problems.add(code.name() + " has no entry"));

		if (!problems.isEmpty()) {
			throw new IllegalStateException("The remediation catalogue (" + LOCATION + ") is invalid: "
				+ String.join("; ", problems));
		}

		return byCode;
	}

	private static List<String> problemsWith(String key, Entry entry) {
		if (entry == null) {
			return List.of(key + " is empty");
		}

		final var problems = new ArrayList<String>();

		if (entry.audience() == null) {
			problems.add(key + " has no audience");
		}
		if (entry.summary() == null || entry.summary().isBlank()) {
			problems.add(key + " has no summary");
		}
		if (!hasSteps(entry.nextSteps())) {
			problems.add(key + " needs at least one next step, none of them blank");
		}
		if (!hasSteps(entry.retrySteps())) {
			problems.add(key + " needs at least one retry step, none of them blank");
		}

		final Set<String> declared = entry.placeholders() != null ? Set.copyOf(entry.placeholders()) : Set.of();
		final var used = new HashSet<String>();

		Stream.of(Stream.ofNullable(entry.summary()), stepsOf(entry.nextSteps()), stepsOf(entry.retrySteps()))
			.flatMap(texts -> texts)
			.filter(text -> text != null)
			.forEach(text -> PLACEHOLDER.matcher(text).results().forEach(match -> used.add(match.group(1))));

		used.stream()
			.filter(name -> !declared.contains(name))
			.sorted()
			.forEach(name -> problems.add(key + " uses {" + name + "}, which it does not declare"));

		return problems;
	}

	private static boolean hasSteps(@Nullable List<String> steps) {
		return steps != null && !steps.isEmpty() && steps.stream().noneMatch(step -> step == null || step.isBlank());
	}

	private static Stream<String> stepsOf(@Nullable List<String> steps) {
		return steps != null ? steps.stream() : Stream.empty();
	}
}
