package org.olf.dcb.test;

import static org.mockserver.model.JsonBody.json;

import java.io.InputStream;

import org.mockserver.model.JsonBody;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.io.ResourceLoader;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;

@Singleton
public class TestResourceLoader {
	private final String basePath;
	private final ResourceLoader loader;

	public TestResourceLoader(@Parameter String basePath, ResourceLoader loader) {
		this.loader = loader;
		this.basePath = basePath;
	}

	public String getResource(String responseBodySubPath) {
		return getResourceAsString(basePath + responseBodySubPath);
	}

	public JsonBody getJsonResource(String subPath) {
		return json(getResource(subPath));
	}

	@SneakyThrows
	private static String resourceToString(InputStream resource) {
		return new String(resource.readAllBytes());
	}

	private String getResourceAsString(String resourcePath) {
		return loader.getResourceAsStream(resourcePath)
			.map(TestResourceLoader::resourceToString)
			.orElseThrow(() -> new RuntimeException("Resource could not be found: " + resourcePath));
	}
}
