package org.olf.dcb.info;

import java.util.HashMap;
import java.util.Map;

import org.olf.dcb.core.AppConfig;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.IngestSourcesProvider;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.MapPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Member;
import com.hazelcast.map.IMap;
import org.olf.dcb.stats.StatCounter;

@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.config.enabled", notEquals = "false")
public class DcbInfoSource implements InfoSource {

 private final IngestSourcesProvider[] ingestSourcesProviders;
 private final AppConfig appConfig; 
 private final HazelcastInstance hazelcastInstance; 
 

	public DcbInfoSource(
		IngestSourcesProvider[] ingestSourcesProviders, 
		AppConfig appConfig,
		HazelcastInstance hazelcastInstance) {

		this.ingestSourcesProviders = ingestSourcesProviders;
		this.appConfig = appConfig;
		this.hazelcastInstance = hazelcastInstance;
	}

 /**
  * Return dcb info
  *
  * @return DCB information we wish to expose
  */
	@Override
	public Publisher<PropertySource> getSource() {
		return Flux.concat(
			getIngestSourceProperties(), 
			getAppConfigProperties(),
			getDCBNodes())
			.reduceWith(HashMap<String,Object>::new, (combined, single) -> {
				combined.putAll( single );
				return combined;
			})
			.map( vals -> MapPropertySource.of("dcb-info-source", vals));
 }

 
	private Map<String, ?> ingestSourceProviderInfo( IngestSource source ) {
		final String prefix = "dcb.ingest.sources." + source.getName() ;
		Map<String, Object> map = new HashMap<>();
		// Add any info for this source we wish to expose
		map.put(prefix + ".enabled", source.isEnabled());
		return map;
	}
 
	private Flux<Map<String, ?>> getIngestSourceProperties() {
	 
		return Flux.fromArray( ingestSourcesProviders )
			.map( IngestSourcesProvider::getIngestSources )
			.flatMap( Flux::from )
			.map( this::ingestSourceProviderInfo );
	}
 
	private Mono<Map<String, ?>> getAppConfigProperties() {
		Map<String, Object> props = new HashMap<>();
		props.put("dcb.sheduled-tasks.enabled", appConfig.getScheduledTasks().isEnabled());
		return Mono.just( props );
	}

	private Mono<Map<String, Object>> getDCBNodes() {
		Map<String, Object> props = new HashMap<>();

		if ( hazelcastInstance != null ) {
			IMap<String,Map<String,String>> dcbNodeInfo = hazelcastInstance.getMap("DCBNodes");
			// log.debug("Got dcbNode info {}",dcbNodeInfo);

			Map<String, Map<String,String>> nodeinfo = new HashMap<>();

			for ( Map.Entry n : dcbNodeInfo ) {
				Map<String, String> m = (Map<String, String>) n.getValue();
				nodeinfo.put(n.getKey().toString(), m);
			}

		  IMap<String, StatCounter> stat_counters =  hazelcastInstance.getMap("stats");

			props.put("dcbNodes", nodeinfo);
			props.put("stats", stat_counters);
		}

		return Mono.just( props );
	}

}
