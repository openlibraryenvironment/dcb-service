package org.olf.dcb.utils;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import io.micronaut.core.annotation.Introspected;

import io.micronaut.context.env.ActiveEnvironment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class GitPropertiesLoader implements PropertySourceLoader {

	private static final String FILE_NAME = "git.properties";
	private static final String SOURCE_NAME = "git";


	@Override
	public Optional<PropertySource> loadEnv(String name, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
		return loadGitProperties(resourceLoader);
	}

	@Override
	public Optional<PropertySource> load(String name, ResourceLoader resourceLoader) {
		// Delegate to the loadEnv method for compatibility
		return loadGitProperties(resourceLoader);
	}

	private Optional<PropertySource> loadGitProperties(ResourceLoader resourceLoader) {

		return resourceLoader.getResource(FILE_NAME)
			.flatMap(url -> {
				try (InputStream is = url.openStream()) {
					Properties props = new Properties();
					props.load(is);
					Map<String, Object> map = new HashMap<>();

					for (String key : props.stringPropertyNames()) {
						map.put(key, props.getProperty(key));
					}

					return Optional.of(PropertySource.of(SOURCE_NAME, map));
				} catch (IOException e) {
					log.error("Problem",e);
					return Optional.empty();
				}
			});
	}

	@Override
	public Map<String, Object> read(String name, InputStream input) {
		Properties props = new Properties();
		try {
			props.load(input);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read git.properties", e);
		}
		Map<String, Object> map = new HashMap<>();
		for (String key : props.stringPropertyNames()) {
			map.put(key, props.getProperty(key));
		}
		return map;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
