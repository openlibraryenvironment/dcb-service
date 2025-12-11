package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.clustering.model.MatchPoint;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Vetoed;
import jakarta.validation.constraints.NotNull;

public interface MatchPointRepository {

	<M extends MatchPoint> Publisher<MatchPoint> saveAll(@NotNull Collection<MatchPoint> matchPoints);
	
	Publisher<MatchPoint> queryAll();
	
	Publisher<MatchPoint> findAllByBibId(@NotNull UUID bibId);
	
	Publisher<Long> deleteAllByBibId(@NotNull UUID bib);
	
	Publisher<Long> deleteAllByBibIdAndValueNotIn(@NotNull UUID bibId, @NotNull Collection<UUID> values);
	
	Publisher<MatchPoint> findAllByBibIdNotAndValueIn( @NotNull UUID bibId, @NotNull Collection<UUID> values );
	
	Publisher<Void> delete (@NotNull UUID id);

	@NonNull
	@Vetoed
	Publisher<MatchPoint> getMatchesByDerrivedType(String derivedType, Collection<UUID> points);
}
