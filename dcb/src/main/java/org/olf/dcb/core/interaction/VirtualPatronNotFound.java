package org.olf.dcb.core.interaction;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.zalando.problem.*;

import java.net.URI;
import java.util.Map;

public class VirtualPatronNotFound extends AbstractThrowableProblem {

	private VirtualPatronNotFound(URI type,
		String title,
		Status status,
		String detail,
		URI instance,
		ThrowableProblem cause,
		Map<String, Object> parameters) {

		super(type, title, status, detail, instance, cause, parameters);
	}

	public static VirtualPatronNotFoundBuilder builder() {
		return new VirtualPatronNotFoundBuilder();
	}

	// Custom Builder class extending ProblemBuilder
	public static class VirtualPatronNotFoundBuilder {
		private final ProblemBuilder problemBuilder;

		public VirtualPatronNotFoundBuilder() {
			this.problemBuilder = Problem.builder()
				.withTitle("Virtual Patron Not Found");
		}

		public VirtualPatronNotFoundBuilder withDetail(String detail) {
			this.problemBuilder.withDetail(detail);
			return this;
		}

		public VirtualPatronNotFoundBuilder with(final String key, final @Nullable Object value) {
			this.problemBuilder.with(key,value);
			return this;
		}

		public VirtualPatronNotFound build() {
			Problem problem = this.problemBuilder.build();

			URI type = (problem.getType() != null) ? problem.getType() : null;
			String title = (problem.getTitle() != null) ? problem.getTitle() : "Virtual Patron Not Found";
			Status status = (problem.getStatus() != null) ? (Status) problem.getStatus() : null;
			String detail = (problem.getDetail() != null) ? problem.getDetail() : null;
			URI instance = (problem.getInstance() != null) ? problem.getInstance() : null;
			Map<String, Object> map = (problem.getParameters() != null) ? problem.getParameters() : null;

			return new VirtualPatronNotFound(type, title, status, detail, instance, null, map);
		}
	}
}
