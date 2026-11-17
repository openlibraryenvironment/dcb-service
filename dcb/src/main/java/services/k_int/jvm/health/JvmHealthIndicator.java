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
import reactor.core.publisher.Mono;

@Singleton
@Requires(beans = HealthEndpoint.class)
public class JvmHealthIndicator implements HealthIndicator {

	private static final String NAME = "jvm";
	public JvmHealthIndicator() {
	}
	
	private HealthResult getJvmHealth() {
		RuntimeMXBean runTimeBean = ManagementFactory.getRuntimeMXBean();
		long uptime = runTimeBean.getUptime();
//		long millis = uptime % 1000;
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
		
		return HealthResult.builder(NAME)
				.status(UP)
				.details(details)
				.build();
	}

	@Override
	public Publisher<HealthResult> getResult() {
		return Mono.defer(() -> Mono.fromSupplier(this::getJvmHealth));
	}
}
