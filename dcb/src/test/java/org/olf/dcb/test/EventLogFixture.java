package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.Collection;

import org.olf.dcb.core.model.Event;
import org.olf.dcb.storage.EventLogRepository;

import jakarta.inject.Singleton;

@Singleton
public class EventLogFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final EventLogRepository eventLogRepository;

	public EventLogFixture(EventLogRepository eventLogRepository) {
		this.eventLogRepository = eventLogRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(eventLogRepository.queryAll(),
			mapping -> eventLogRepository.delete(mapping.getId()));
	}

	public Collection<Event> findAll() {
		return manyValuesFrom(eventLogRepository.queryAll());
	}
}
