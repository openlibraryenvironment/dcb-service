package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrackingBoundaryArchitectureTests {
	@Test
	void trackingServiceV4MustUseLifecycleEvidenceBoundary() throws IOException {
		final var source = Files.readString(Path.of(
			trackingServiceV4Source()));

		assertThat(source, containsString("LifecycleEvidenceTrackingEventSink"));
		assertThat(source, not(containsString("HostLmsReactions")));
	}

	@Test
	void trackingImplementationsMustNotRegisterScheduledTasks()
		throws IOException {

		final var v3Source = Files.readString(Path.of(
			sourcePath("src/main/java/org/olf/dcb/tracking/TrackingServiceV3.java")));
		final var v4Source = Files.readString(Path.of(
			trackingServiceV4Source()));

		assertThat(v3Source, not(containsString("@Scheduled")));
		assertThat(v3Source, not(containsString("@AppTask")));
		assertThat(v4Source, not(containsString("@Scheduled")));
		assertThat(v4Source, not(containsString("@AppTask")));
	}

	@Test
	void trackingSchedulerMustOwnScheduledTaskRegistration()
		throws IOException {

		final var source = Files.readString(Path.of(
			sourcePath("src/main/java/org/olf/dcb/tracking/TrackingScheduler.java")));

		assertThat(source, containsString("@Scheduled"));
		assertThat(source, containsString("@AppTask"));
		assertThat(source, containsString("TrackingService trackingService"));
		assertThat(source, containsString("trackingService.run()"));
	}

	@Test
	void appTaskMustRemainMarkerOnly()
		throws IOException {

		final var source = Files.readString(Path.of(
			sourcePath("src/main/java/services/k_int/micronaut/scheduling/processor/AppTask.java")));

		assertThat(source, not(containsString("@Executable")));
		assertThat(source, not(containsString("processOnStartup")));
	}

	private static String trackingServiceV4Source() {
		return sourcePath("src/main/java/org/olf/dcb/tracking/TrackingServiceV4.java");
	}

	private static String sourcePath(String relativePath) {
		final var projectPath = Path.of(relativePath);

		if (Files.exists(projectPath)) {
			return projectPath.toString();
		}

		return "dcb/" + relativePath;
	}
}
