package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.List;

import org.olf.dcb.core.model.StatusCode;
import org.olf.dcb.storage.StatusCodeRepository;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;

@Singleton
public class StatusCodeFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final StatusCodeRepository statusCodeRepository;

	public StatusCodeFixture(StatusCodeRepository statusCodeRepository) {
		this.statusCodeRepository = statusCodeRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(statusCodeRepository.queryAll(),
			grant -> statusCodeRepository.delete(grant.getId()));
	}

	public List<? extends StatusCode> findAll() {
		return manyValuesFrom(statusCodeRepository.queryAll());
	}
}
