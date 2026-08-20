package org.olf.dcb.core.api.serde;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record DashboardMetrics(
	TurnaroundStat turnaroundToLoaned,
	TurnaroundStat turnaroundToFinalised,
	List<PartnerStat> topSuppliers,
	List<PartnerStat> topBorrowers
) {}
