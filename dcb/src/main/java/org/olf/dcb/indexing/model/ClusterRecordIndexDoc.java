package org.olf.dcb.indexing.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.ingest.model.Author;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.Serdeable;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Serdeable
@ExcludeFromGeneratedCoverageReport
public class ClusterRecordIndexDoc {
	
	private final ClusterRecord cluster;
	
	public ClusterRecordIndexDoc(@NonNull ClusterRecord cluster) {
		this.cluster = cluster;
	}
	
	public String getTitle() {
		return cluster.getTitle();
	}
	
	@SuppressWarnings("unchecked")
	private String getIdentifier( @NonNull final String namepace ) {
		return getSelectedBib()
			.map( BibRecord::getCanonicalMetadata )
			.map( map -> (Collection<Map<String,?>>)map.get("identifiers") )
			.stream()
			.flatMap( Collection::stream )
			.filter( idMap -> namepace.equalsIgnoreCase( (String) idMap.get("namespace")) )
			.findFirst()
			.map( idMap -> idMap.get("value") )
			.map( Objects::toString )
			.orElse( null );
	}
	
	private static final Pattern REGEX_PUB_YEAR = Pattern.compile("(?:19|20)[0-9]{2}(?:\\b|_)");
	
	public String getPlaceOfPublication() {
		return getSelectedBib()
			.map(BibRecord::getPlaceOfPublication)
			.orElse(null);
	}
	
  public String getPublisher() {
		return getSelectedBib()
			.map(BibRecord::getPublisher)
			.orElse(null);
  }
  
  public String getDateOfPublication() {
		return getSelectedBib()
			.map(BibRecord::getDateOfPublication)
			.orElse(null);
  }
  
  public String getDerivedType() {
  	return getSelectedBib()
			.map(BibRecord::getDerivedType)
			.orElse(null);
  		
  }
	
	public String getPrimaryAuthor() {
		return getSelectedBib()
			.map(BibRecord::getAuthor)
			.map(Author::getName)
			.orElse(null);
	}
	
	public Integer getYearOfPublication() {
		return Optional.ofNullable(this.getDateOfPublication())
			.map(REGEX_PUB_YEAR::matcher)
			.filter(Matcher::find)
			.map(Matcher::toMatchResult)
			.map(MatchResult::group)
			.map(Integer::parseInt)
			.orElse(null);
	}
	
	public UUID getBibClusterId() {
		return cluster.getId();
	}
	
	public String getIsbn() {
		return getIdentifier("ISBN");
	}
	
	public String getIssn() {
		return getIdentifier("ISSN");
	}
	
  public List<NestedBibIndexDoc> getMembers() {
  	return Stream.concat(
  	  getSelectedBib().stream().map( primaryBib -> new NestedBibIndexDoc(primaryBib, true)), // Add the selected bib first
	  	Stream.ofNullable(cluster.getBibs())
				.flatMap( Set::stream )
				.filter(bib ->
				  // Filter out primary bib this time if there is one.
					Optional.ofNullable(cluster.getSelectedBib())
						.map( id -> id != bib.getId() )
						.orElse(true))
	  		.map( NestedBibIndexDoc::new ))
  			.toList();
  }
  
  public Map<String, ?> getMetadata() {
		return getSelectedBib()
			.map(BibRecord::getCanonicalMetadata)
			.orElse(null);
  }
  
  
  private Optional<BibRecord> _selectedBib = null;
  
  @JsonIgnoreProperties({"contributesTo"})
  public Optional<BibRecord> getSelectedBib() {
  	
  	if (this._selectedBib == null) {
  		_selectedBib = Stream.ofNullable(cluster.getBibs())
	  		.flatMap( Set::stream )
	  		.filter(bib ->
	  			// Filter to only the primary bib if there is one.
	  			Optional.ofNullable(cluster.getSelectedBib())
						.map(bib.getId()::equals)
						.orElse(true))
	  		.findFirst();
  	}
  	
  	return this._selectedBib;
  }
}
