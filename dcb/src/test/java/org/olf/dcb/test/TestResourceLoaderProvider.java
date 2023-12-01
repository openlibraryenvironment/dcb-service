package org.olf.dcb.test;

import io.micronaut.context.BeanContext;
import jakarta.inject.Singleton;

@Singleton
public class TestResourceLoaderProvider {
	private final BeanContext context;

	public TestResourceLoaderProvider(BeanContext context) {
		this.context = context;
	}

	public TestResourceLoader forBasePath(String basePath) {
		return context.createBean(TestResourceLoader.class, basePath);
	}
}
