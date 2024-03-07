package org.olf.dcb;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;

public class SystemInformationSource extends MapPropertySource {

	public static SystemInformationSource get() {
		return new SystemInformationSource();
	}

	private static Map<String, ?> getData() {

		Map<String, ?> jvm = Map.ofEntries(
				new SimpleEntry<>("available-processors", Runtime.getRuntime().availableProcessors()),
				new SimpleEntry<>("max-memory", Runtime.getRuntime().maxMemory()),
				new SimpleEntry<>("free-memory", Runtime.getRuntime().freeMemory()),
				new SimpleEntry<>("version", Runtime.version()),
				new SimpleEntry<>("vendor", System.getProperty("java.vendor")));

		Map<String, ?> root = Map.of("jvm", jvm);
		return root;
	}

	private SystemInformationSource() {
		super(Environment.DEFAULT_NAME, getData());
	}
}
