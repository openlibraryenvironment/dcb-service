package org.olf.dcb.core.clustering.matching;

import static services.k_int.utils.TupleUtils.curry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.olf.dcb.core.clustering.ImprovedRecordClusteringService;
import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.core.model.BibIdentifier;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.svc.BibRecordService;
import org.olf.dcb.storage.MatchPointRepository;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Requires(bean = ImprovedRecordClusteringService.class)
public class MatchpointGenerator {
	public static final String MATCHPOINT_ID = "id";
	private static final Pattern PATTERN_WILCARD = Pattern.compile("\\*");
	
	public static final Predicate<String> HIGH_CERTAINTY_IDS = Stream.of(
			"GOLDRUSH", "ONLY-ISBN-13", "ISSN-N", "LCCN", "OCOLC", "STRN" )
			
		.map( MatchpointGenerator::toPredicate )
		.reduce( Predicate::or )
		.get();
	
	// These are secondary matches.
	public static final Predicate<String> OTHER_IDS = Stream.of(
			"BLOCKING_TITLE", "GOLDRUSH::TITLE", "ISBN-N") 
			
		.map( MatchpointGenerator::toPredicate )
		.reduce( Predicate::or )
		.get();
	
	private static Predicate<String> toPredicate ( String thePattern ) {
		return Optional.of( thePattern )
			.map( pattern -> PATTERN_WILCARD.matcher(pattern).replaceAll("\\\\E\\\\w+\\\\Q") )
			.map( pattern -> "^\\Q" + pattern + "\\E$" )
			.map( pattern -> pattern.replace("\\Q\\E", "") )
			.map(str -> {
				log.info("Using match [{}]", str);
				return str;
			})
			.map( Pattern::compile )
			.map( Pattern::asMatchPredicate )
			.get();
	}
	
	private static final Predicate<? super String> validIdNamespacePatterns = HIGH_CERTAINTY_IDS.or(OTHER_IDS);
	
	private final BibRecordService bibRecords;
	private final MatchPointRepository matchPointRepository;
	
	public MatchpointGenerator(MatchPointRepository matchPointRepository, BibRecordService bibRecords) {
		this.bibRecords = bibRecords;
		this.matchPointRepository = matchPointRepository;
	}
	
	private boolean completeIdentifiersPredicate ( BibIdentifier bibId ) {
		
		boolean value = Stream.of( bibId.getNamespace(), bibId.getValue())
			.filter( StringUtils::hasText )
			.count() == 2;

		if ( !value && log.isInfoEnabled() ) {
			log.info("Skipping bibId matchpoint generation, as blank match point would be created. Bib: [{}]", bibId.getOwner().getId());
		}

		return value;
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Flux<MatchPoint> getAllCandidateMatchpointHits(BibRecord bib, Collection<UUID> pointValues) {
		return Flux.from( matchPointRepository.findAllByBibIdNotAndValueIn(bib.getId(), pointValues) );
	}
	
	private boolean usableForClusteringIdentifiersPredicate ( BibIdentifier bibId ) {

		String ns = bibId.getNamespace();
		
		var validNS = Optional.ofNullable( ns )
			.map( String::toUpperCase )
			.filter( validIdNamespacePatterns );
		
		if (validNS.isEmpty()) {
			log.debug("BibIdentifier Namespace [{}] did not match any of the accepted namespace patterns", ns);
			return false;
		}

		return true;
//		var uncertainValue = Optional.ofNullable(bibId.getConfidence())
//			.filter(i -> i > 0);
//		
//		// Include only certain value or ones without a certainty level.
//		return uncertainValue.isEmpty();
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Flux<MatchPoint> generateIdMatchPoints( BibRecord bib ) {
		return bibRecords.findAllIdentifiersForBib( bib )
			.filter( this::completeIdentifiersPredicate )
			.filter( this::usableForClusteringIdentifiersPredicate )
			.map( id -> {
				String s = String.format("%s:%s:%s", MATCHPOINT_ID, id.getNamespace(), id.getValue());
				MatchPoint mp = MatchPoint.buildFromString(s, id.getNamespace());
				return mp;
      });
	}
	
	private Flux<MatchPoint> recordMatchPoints ( BibRecord bib ) {
    return Flux.empty();
	}
	
	public Flux<MatchPoint> generateMatchPoints ( final BibRecord bib ) {
		return Flux.concat(
			generateIdMatchPoints(bib),
			recordMatchPoints(bib))
				.map( mp -> mp.setBibId(bib.getId()));
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	public Mono<Collection<MatchPoint>> reconcileMatchPoints( Collection<MatchPoint> currentMatchPoints, BibRecord bib ) {

		return Flux.fromIterable( currentMatchPoints )
			.map( MatchPoint::getValue )
			.collectList()
			.map( curry(bib.getId(), matchPointRepository::deleteAllByBibIdAndValueNotIn) )
			.flatMap(Mono::from)
			.doOnNext( del -> {
				if (del > 0) log.info("Deleted {} existing matchpoints that are no longer valid from {}", del, bib.getId());
			})
			.then( symmetricDifference(bib.getId(), currentMatchPoints) )
			.flatMapMany( matchPointRepository::saveAll )
			.count()
			.map( added -> {
				// if (added > 0) log.trace("Added {} new matchpoints for {}", added, bib.getId());
				return currentMatchPoints;
			});
	}
	
	@Transactional(propagation = Propagation.MANDATORY)
	protected Mono<List<MatchPoint>> symmetricDifference ( UUID bibId, Collection<MatchPoint> currentMatchPoints ) {
		return Flux.from( matchPointRepository.findAllByBibId( bibId ) )
			.map( MatchPoint::getValue )
			.collectList()
			.map( current_bib_match_points -> currentMatchPoints.stream()
          // Include and value not already present
					.filter( mp -> !current_bib_match_points.contains( mp.getValue() ) )
					.toList());
	}
}
