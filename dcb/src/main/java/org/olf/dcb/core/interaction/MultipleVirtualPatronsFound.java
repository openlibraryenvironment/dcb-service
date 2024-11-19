package org.olf.dcb.core.interaction;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.zalando.problem.*;

import java.net.URI;
import java.util.Map;

public class MultipleVirtualPatronsFound extends AbstractThrowableProblem {

	private MultipleVirtualPatronsFound(URI type, String title, Status status, String detail, URI instance, ThrowableProblem cause, Map<String, Object> parameters) {
		super(type, title, status, detail, instance, cause, parameters);
	}

	public static MultipleVirtualPatronsFound.MultipleVirtualPatronsFoundBuilder builder() {
		return new MultipleVirtualPatronsFound.MultipleVirtualPatronsFoundBuilder();
	}

	// Custom Builder class extending ProblemBuilder
	public static class MultipleVirtualPatronsFoundBuilder {
		private final ProblemBuilder problemBuilder;

		public MultipleVirtualPatronsFoundBuilder() {
			this.problemBuilder = Problem.builder()
				.withTitle("Multiple Virtual Patrons Found");
		}

		public MultipleVirtualPatronsFound.MultipleVirtualPatronsFoundBuilder withDetail(String detail) {
			this.problemBuilder.withDetail(detail);
			return this;
		}

		public MultipleVirtualPatronsFound.MultipleVirtualPatronsFoundBuilder with(final String key, final @Nullable Object value) {
			this.problemBuilder.with(key,value);
			return this;
		}

		public MultipleVirtualPatronsFound build() {
			Problem problem = this.problemBuilder.build();

			URI type = (problem.getType() != null) ? problem.getType() : null;
			String title = (problem.getTitle() != null) ? problem.getTitle() : "Multiple Virtual Patrons Found";
			Status status = (problem.getStatus() != null) ? (Status) problem.getStatus() : null;
			String detail = (problem.getDetail() != null) ? problem.getDetail() : null;
			URI instance = (problem.getInstance() != null) ? problem.getInstance() : null;
			Map<String, Object> map = (problem.getParameters() != null) ? problem.getParameters() : null;

			return new MultipleVirtualPatronsFound(type, title, status, detail, instance, null, map);
		}
	}
}
