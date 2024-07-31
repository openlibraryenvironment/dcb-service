package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.List;

import org.olf.dcb.core.model.Grant;
import org.olf.dcb.storage.GrantRepository;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;

@Singleton
public class GrantFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final GrantRepository grantRepository;

	public GrantFixture(GrantRepository grantRepository) {
		this.grantRepository = grantRepository;
	}

	public void deleteAll() {
		dataAccess.deleteAll(grantRepository.queryAll(),
			grant -> grantRepository.delete(grant.getId()));
	}

	public List<Grant> findAll() {
		return manyValuesFrom(grantRepository.queryAll());
	}
}
