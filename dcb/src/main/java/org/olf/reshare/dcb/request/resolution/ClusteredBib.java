package org.olf.reshare.dcb.request.resolution;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.UUID;
@Serdeable
public record ClusteredBib(UUID id, @Nullable List<Holdings> holdings) { }
