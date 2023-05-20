package org.olf.reshare.dcb.stats;

import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.BibRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

/**
 * The purpose of this class is to collect statistics across this instance of DCB. Devs should be
 * mindful that eventually DCB will be running in cluster mode with many instances so stats may need to be
 * aggregated downstream. This class should be a common point to collect/aggregate stats and metrics about the
 * running instance.
 *
 * Because many instances of DCB may be running, we store the stats in a distributed map.
 */
@Singleton
public class StatsService {

	private static final Logger log = LoggerFactory.getLogger(StatsService.class);

  private HazelcastInstance instance = null;
  private IMap<String, StatCounter> stat_counters = null;

	// This needs to be a hazelcast map ideally
	// Map<String, StatCounter> stat_counters = new HashMap<String, StatCounter>();


  @javax.annotation.PostConstruct
  public void init() {
    Config cfg = new Config("DCB");
    instance = Hazelcast.getOrCreateHazelcastInstance(cfg);
    stat_counters = instance.getMap("stats");
  }

	private StatCounter getCounter(String id) {
		StatCounter result = stat_counters.get(id);
		if ( result == null ) {
			result = new StatCounter(id);
			stat_counters.put(id,result);
		}
		return result;
	}
	/**
	 * StatsService offers a general mechanism for maintaining a histogram of event counts. Initially, the
	 * aim is to provide per-minute counts for the last hour, per hour counts for the last day and per day counts
	 * for the past 30 days.
	 * An example event BibRecordInserted, Context DCB (In case we want to be more granular later)
	 */
	public void notifyEvent(String event, String context) {
		// log.debug("notifyEvent {} {}",event,context); Check
		StatCounter sc = getCounter(event+":"+context);
		sc.increment();
	}

	public Report getReport() {
		// log.debug("report {}",stat_counters);
		return new Report(stat_counters);
	}

	public void notifyTimedEvent(String event, String context, long elapsed) {
		// log.debug("notifyTimedEvent({},{},{})",event,context,elapsed);
	}

	@Serdeable
	public class Report {

		public Map<String, StatCounter.StatCounterReport> counters;

		public Report(Map<String, StatCounter> internal_counters) {
			// this.counters = counters;
			this.counters = new HashMap<String, StatCounter.StatCounterReport>();
			for (var entry : internal_counters.entrySet()) {
				// log.debug("Adding report for {}",entry.getKey());
				counters.put(entry.getKey(), entry.getValue().report());
			}
		}

		public Map<String, StatCounter.StatCounterReport> getCounters() {
			return counters;
		}
	}
}
