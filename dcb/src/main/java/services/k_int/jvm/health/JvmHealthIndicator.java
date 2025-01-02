package services.k_int.jvm.health;

import static io.micronaut.health.HealthStatus.UP;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;

@Requires(beans = HealthEndpoint.class)
@Singleton
public class JvmHealthIndicator implements HealthIndicator {

	private static final String NAME = "jvm";

	/**
	 * Constructor.
	 *
	 * @param client The OpenSearch high level REST client.
	 */
	public JvmHealthIndicator() {
	}

	@Override
	public Publisher<HealthResult> getResult() {
		RuntimeMXBean runTimeBean = ManagementFactory.getRuntimeMXBean();
		long uptime = runTimeBean.getUptime();
		long millis = uptime % 1000;
		long second = (uptime / 1000) % 60;
		long minute = (uptime / (1000 * 60)) % 60;
		long hour = (uptime / (1000 * 60 * 60)) % 24;
		long days = (uptime /(1000*60*60*24));
		String upTimeFormatted = String.format("%d days %02d hours %02d minutes %02d seconds", days, hour, minute, second);

		String details = new StringBuilder()
			.append("{")
			.append("\"specVersion\":")
			.append("\"" + runTimeBean.getSpecVersion() + "\",")
			.append("\"vmName\":")
			.append("\"" + runTimeBean.getVmName() + "\",")
			.append("\"vmVersion\":")
			.append("\"" + runTimeBean.getVmVersion() + "\",")
			.append("\"upTime\":")
			.append(uptime)
			.append(",")
			.append("\"upTimeFormatted\":")
			.append("\"" + upTimeFormatted + "\",")
			.append("\"startTime\":")
			.append(runTimeBean.getStartTime())
			.append("}")
			.toString();
				
		return(subscriber -> {
			final HealthResult.Builder resultBuilder = HealthResult.builder(NAME);
			subscriber.onNext(resultBuilder.status(UP).details(details).build());
			subscriber.onComplete();
		});
	}
}
