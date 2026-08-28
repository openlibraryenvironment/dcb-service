package org.olf.dcb.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.BrandAssetUploadController;
import org.olf.dcb.core.api.LocationController;
import org.olf.dcb.core.api.UploadedMappingsController;

import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

class UploadControllerExecutionBoundaryTests {
	@Test
	void brandImageUploadDispatchesSynchronousImageWork() throws NoSuchMethodException {
		assertBlocking(BrandAssetUploadController.class.getMethod(
			"upload", CompletedFileUpload.class));
	}

	@Test
	void locationUploadDispatchesSynchronousFileParsing() throws NoSuchMethodException {
		assertBlocking(LocationController.class.getMethod("importLocations",
			CompletedFileUpload.class, String.class, String.class, String.class,
			String.class, String.class));
	}

	@Test
	void mappingUploadDispatchesSynchronousFileParsing() throws NoSuchMethodException {
		assertBlocking(UploadedMappingsController.class.getMethod("post",
			CompletedFileUpload.class, String.class, String.class, String.class,
			String.class, String.class, String.class));
	}

	private static void assertBlocking(java.lang.reflect.Method route) {
		final var executeOn = route.getAnnotation(ExecuteOn.class);
		assertNotNull(executeOn, route.getName() + " must declare an execution boundary");
		assertEquals(TaskExecutors.BLOCKING, executeOn.value());
	}
}
