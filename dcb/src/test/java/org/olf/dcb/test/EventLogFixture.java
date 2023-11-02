package org.olf.dcb.test;

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
}
