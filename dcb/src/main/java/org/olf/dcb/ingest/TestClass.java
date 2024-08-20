package org.olf.dcb.ingest;

import io.micronaut.context.annotation.Context;
import lombok.extern.slf4j.Slf4j;
import services.k_int.stability.FeatureGate;
import services.k_int.stability.StabilityLevel.Alpha;

@Context
@Alpha
@FeatureGate("batch-api")
@Slf4j
public class TestClass {
	public TestClass() {
		log.info("Test class loaded");
	}
}
