package services.k_int.integration.opensearch.health;

import static io.micronaut.health.HealthStatus.DOWN;
import static io.micronaut.health.HealthStatus.UP;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;

import java.time.Duration;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Requires(beans = HealthEndpoint.class)
@Requires(property = HealthEndpoint.PREFIX + ".opensearch.enabled", notEquals = "false")
@Singleton
@Slf4j
public class OpenSearchClientHealthIndicator implements HealthIndicator {

	private static final String NAME = "opensearchclient";

	private final OpenSearchAsyncClient client;

	/**
	 * Constructor.
	 *
	 * @param client The OpenSearch high level REST client.
	 */
	public OpenSearchClientHealthIndicator(OpenSearchAsyncClient client) {
		this.client = client;
	}

	/**
	 * Tries to call the cluster info API on OpenSearch to obtain information about
	 * the cluster. If the call succeeds, the OpenSearch cluster health status
	 * (GREEN / YELLOW / RED) will be included in the health indicator details.
	 *
	 * @return A positive health result UP if the cluster can be communicated with
	 *         and is in either GREEN or YELLOW status. A negative health result
	 *         DOWN if the cluster cannot be communicated with or is in RED status.
	 */
	@Override
	public Publisher<HealthResult> getResult() {
    return Mono.from(internalGetResult())
			.timeout(Duration.ofSeconds(5));
  }

	public Publisher<HealthResult> internalGetResult() {

    log.info("Get opensearch health...");

		return (subscriber -> {
			final HealthResult.Builder resultBuilder = HealthResult.builder(NAME);
			try {
				client.cluster().health().handle((health, exception) -> {
					if (exception != null) {
						subscriber.onNext(resultBuilder.status(DOWN).exception(exception).build());
						subscriber.onComplete();
					} else {
						HealthStatus status = health.status() == org.opensearch.client.opensearch._types.HealthStatus.Red ? DOWN
								: UP;
						subscriber.onNext(resultBuilder.status(status).details(healthResultDetails(health)).build());
						subscriber.onComplete();
					}
					return health;
				});
			} catch (IOException ex) {
        log.error("Problem getting open search status",ex);
				subscriber.onNext(resultBuilder.status(DOWN).exception(ex).build());
				subscriber.onComplete();
			}
      finally {
      }
		});
	}

	private String healthResultDetails(HealthResponse response) {
		return "{" + "\"cluster_name\":\"" + response.clusterName() + "\"," + "\"status\":\""
				+ response.status().name().toLowerCase(Locale.ENGLISH) + "\"," + "\"timed_out\":" + response.timedOut() + ","
				+ "\"number_of_nodes\":" + response.numberOfNodes() + "," + "\"number_of_data_nodes\":"
				+ response.numberOfDataNodes() + "," + "\"number_of_pending_tasks\":" + response.numberOfPendingTasks() + ","
				+ "\"number_of_in_flight_fetch\":" + response.numberOfInFlightFetch() + "," + "\"task_max_waiting_in_queue\":\""
				+ response.taskMaxWaitingInQueueMillis() + "\"," + "\"task_max_waiting_in_queue_millis\":"
				+ response.taskMaxWaitingInQueueMillis() + "," + "\"active_shards_percent_as_number\":\""
				+ response.activeShardsPercentAsNumber() + "\"," + "\"active_primary_shards\":" + response.activePrimaryShards()
				+ "," + "\"active_shards\":" + response.activeShards() + "," + "\"relocating_shards\":"
				+ response.relocatingShards() + "," + "\"initializing_shards\":" + response.initializingShards() + ","
				+ "\"unassigned_shards\":" + response.unassignedShards() + "," + "\"delayed_unassigned_shards\":"
				+ response.delayedUnassignedShards() + "}";
	}
}
