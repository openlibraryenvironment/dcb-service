package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Most ILS cannot be asked about their own configuration, so the default has to be a readable
 * answer rather than an error. A report that said nothing was wrong would be worse: an
 * implementer would take silence for a clean bill of health.
 */
@TestInstance(PER_CLASS)
class HostLmsClientConfigurationDefaultTests {

	@Test
	void shouldReportNotSupportedRatherThanFailingForAnAdapterThatCannotBeAsked() {
		final var client = mock(HostLmsClient.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

		// doReturn, because stubbing getHostLmsCode() the usual way would invoke the real
		// default method against a mock with no Host LMS behind it
		doReturn("SOME-ILS").when(client).getHostLmsCode();

		final var report = client.checkConfiguration().block();

		assertThat(report.hostLmsCode(), is("SOME-ILS"));
		assertThat(report.status(), is(ConfigurationReport.Status.NOT_SUPPORTED));
		assertThat(report.detail(), containsString("cannot read configuration"));

		// Not an empty CHECKED report: nothing here may be read as "checked and fine"
		assertThat(report.checks(), is(empty()));
		assertThat(report.vocabularies(), is(empty()));
	}
}
