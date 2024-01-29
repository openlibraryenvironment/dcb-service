package org.olf.dcb.stats;

import io.micronaut.serde.annotation.Serdeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Essentially this class is just three ring buffers.
 */
public class StatCounter {

	private static final Logger log = LoggerFactory.getLogger(StatCounter.class);

	private long[] lastHoursCounts = new long[60];
	private long[] lastDaysCounts = new long[24];
	private long[] lastMonthsCounts = new long[31];

	private int current_minute = -1;
	private int current_hour = -1;
	private int current_day = -1;

	public StatCounter(String id) {
		this.id = id;
	}
	String id;

	public void increment() {
		// Work out the current minute, hour and day of month

    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(System.currentTimeMillis());
    cal.setTimeZone(TimeZone.getTimeZone("UTC"));
		int minute = cal.get(Calendar.MINUTE);
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int dom = cal.get(Calendar.DAY_OF_MONTH);

		current_minute = incrementCounter(lastHoursCounts, minute, current_minute);
		current_hour = incrementCounter(lastDaysCounts, hour, current_hour);

		// Remember day of month starts at 1 not 0 :)
		current_day = incrementCounter(lastMonthsCounts, dom-1, current_day);
	}

	/**
	 * @param buffer is the ring buffer we are incrementing
	 *
	 * @param bucket_now is the bucket we want to increment - so if this is the minute bucket and the time is 13:27 then
	 * we want to log to buffer[27] so bucket_now is 27
	 *
	 * @param currently_logging_to_bucket is the bucket we are currently incrementing... so the clock was at 13:26 and we have
	 * been incrementing 26 previously, when the new signal for 27 comes in, we know we have moved on.
	 */
	private int incrementCounter(long[] buffer, int bucket_now, int currently_logging_to_bucket) {

		// log.info("incrementCounter {} {} {}", this.id, bucket_now, currently_logging_to_bucket);

		if ( bucket_now == currently_logging_to_bucket ) {
			// The easy case - this is just another value in a counter we are already incrementing
			buffer[currently_logging_to_bucket]++;
		}
		else {
			boolean initialising = ( currently_logging_to_bucket == -1 );
			// The bucket has rolled over, wind forward, clearing any counts between that cell and now
			while ( currently_logging_to_bucket != bucket_now ) {

				if ( !initialising )
					log.info("Bucket rollover {}[{}] = {}",id,currently_logging_to_bucket,buffer[currently_logging_to_bucket]);

				// log.debug("increment current bucket");
				currently_logging_to_bucket++;

				// log.debug("incremented current bucket to {}",currently_logging_to_bucket);
				if ( currently_logging_to_bucket == buffer.length ) {
					// We're winding over the end of the ring buffer, reset to 0
					// log.debug("Reset to 0");
					currently_logging_to_bucket = 0;
				}

				buffer[currently_logging_to_bucket] = 0;
				// log.debug("Wound forward one bucket now at {} target is {}",currently_logging_to_bucket,bucket_now);
			}

			// We have wound forward to the current moment
			buffer[currently_logging_to_bucket] = 1;
		}

		return currently_logging_to_bucket;
	}

	public StatCounterReport report() {
		// log.debug("stats counter report {} {}",current_minute,lastHoursCounts);
		return new StatCounterReport(current_minute, current_hour,current_day,lastHoursCounts,lastDaysCounts,lastMonthsCounts);
	}

	@Serdeable
	public record StatCounterReport(int minute, int hour, int day, long[] minutes, long[] hours, long[] days) {}

}
