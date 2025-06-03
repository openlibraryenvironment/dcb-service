package org.olf.dcb.ingest.marc;

import static services.k_int.integration.marc4j.Marc4jRecordUtils.concatSubfieldData;
import static services.k_int.integration.marc4j.Marc4jRecordUtils.extractOrderedSubfields;
import static services.k_int.integration.marc4j.Marc4jRecordUtils.interpretLanguages;
import static services.k_int.integration.marc4j.Marc4jRecordUtils.typeFromLeader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.olf.dcb.core.error.DcbError;
import org.olf.dcb.core.error.DcbException;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.ingest.IngestSource;
import org.olf.dcb.ingest.conversion.SourceToIngestRecordConverter;
import org.olf.dcb.ingest.model.Identifier;
import org.olf.dcb.ingest.model.IngestRecord;
import org.olf.dcb.ingest.model.IngestRecord.IngestRecordBuilder;
import org.olf.dcb.ingest.model.RawSource;
import org.olf.dcb.processing.matching.goldrush.GoldrushKey;
import org.olf.dcb.storage.RawSourceRepository;
import org.olf.dcb.utils.DCBStringUtilities;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.util.StringUtils;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.validation.constraints.NotEmpty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import services.k_int.integration.marc4j.Marc4jRecordUtils;

import org.olf.dcb.utils.EditionNormalizer;

public interface MarcIngestSource<T> extends IngestSource, SourceToIngestRecordConverter {

	public static final String NS_GOLDRUSH = "GOLDRUSH";
	final static Pattern REGEX_NAMESPACE_ID_PAIR = Pattern.compile("^((\\(([^)]+)\\))|(([^:]+):))(.*)$");
	final static Pattern REGEX_LINKAGE_245_880 = Pattern.compile("^880-(\\d+)");
	final static Pattern REGEX_REMOVE_PUNCTUATION = Pattern.compile("\\p{Punct}");

  // \b((?:\d[\-\s]?){9}[\dXx]|(?:\d[\-\s]?){13}|\d{4}-\d{3}[\dXx])\b(?:\s+(.*))?
  final static Pattern REGEX_ISXN_VALUE = Pattern.compile("\\b((?:\\d[\\-\\s]?){9}[\\dXx]|(?:\\d[\\-\\s]?){13}|\\d{4}-\\d{3}[\\dXx])\\b(?:\\s+(.*))?");

	static Logger log = LoggerFactory.getLogger(MarcIngestSource.class);

//	protected ConversionService getConversionService();

	default IngestRecordBuilder populateRecordFromMarc(final IngestRecordBuilder ingestRecord, final Record marcRecord) {

		// Leader fields
		enrichWithLeaderInformation(ingestRecord, marcRecord);

		enrichWithFormOfItemInformation(ingestRecord, marcRecord);

		// Title(s)
		enrichWithTitleInformation(ingestRecord, marcRecord);

		// Identifiers
		enrichWithIdentifiers(ingestRecord, marcRecord);

		// Author(s)
		enrichWithAuthorInformation(ingestRecord, marcRecord);

		enrichWithGoldrush(ingestRecord, marcRecord);

		enrichWithCanonicalRecord(ingestRecord, marcRecord);
		
		enrichWithMetadataScore(ingestRecord, marcRecord);

		return ingestRecord;
	}

	@NonNull
	String getDefaultControlIdNamespace();

	default IngestRecordBuilder enrichWithLeaderInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {

		// This page has useful info for how to convert leader character position 06
		// into a value that can be used to interpret 008 fields
		// https://www.itsmarc.com/crs/mergedprojects/helptop1/helptop1/directory_and_leader/idh_leader_06_bib.htm

//		var typeCode = marcRecord.getLeader().getTypeOfRecord();

		ingestRecord.recordStatus(String.valueOf(marcRecord.getLeader().getRecordStatus()));
		ingestRecord.typeOfRecord(String.valueOf(marcRecord.getLeader().getTypeOfRecord()));
		// marcRecord.getLeader.getImplDefined1() will give us 2 characters needed to
		// work out kind of resource - Marc variant specific
		// In marc21 07 is Bibliographic Level
		ingestRecord.derivedType(typeFromLeader(marcRecord.getLeader()));
		return ingestRecord;
	}

	default IngestRecordBuilder enrichWithFormOfItemInformation(final IngestRecordBuilder ir, final Record record) {

		ControlField field008 = (ControlField) record.getVariableField("008");
		if ( field008 != null ) {
			String data008 = field008.getData();
			if ( ( data008 != null ) && ( data008.length() >= 23 ) ) {
				char formOfRecord = data008.charAt(23);
				switch ( formOfRecord ) {
					case 'a':
						ir.formOfItem("Microfilm");
						ir.derivedFormOfItem("Physical");
						break;
					case 'b':
						ir.formOfItem("Microfiche");
						ir.derivedFormOfItem("Physical");
						break;
					case 'c':
						ir.formOfItem("Micropaque");
						ir.derivedFormOfItem("Physical");
						break;
					case 'd':
						ir.formOfItem("Large Print");
						ir.derivedFormOfItem("Physical");
						break;
					case 'f':
						ir.formOfItem("Braille");
						ir.derivedFormOfItem("Physical");
						break;
					case 'o':
						ir.formOfItem("Online");
						ir.derivedFormOfItem("Electronic");
						break;
					case 'q':
						ir.formOfItem("Direct Electronic");
						ir.derivedFormOfItem("Electronic");
						break;
					case 'r':
						ir.formOfItem("Regular Print");
						ir.derivedFormOfItem("Physical");
						break;
					case 's':
						ir.formOfItem("Electronic");
						ir.derivedFormOfItem("Electronic");
						break;
					case '|':
						ir.formOfItem("Not Encoded");
						ir.derivedFormOfItem("Not Encoded");
						break;
					default:
						ir.formOfItem("Unknown");
						ir.derivedFormOfItem("Unknown");
						break;
				}
			}
		}
		return ir;
	}

	default IngestRecordBuilder enrichWithAuthorInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {

		// II: This block was adding author names as identifiers. Whilst we do want to extract author names,
		// I don't think we want them in identifiers, so commenting out for now.
		/*
		Stream.of("100", "110", "700", "710").map(tag -> marcRecord.getVariableField(tag)).filter(Objects::nonNull)
				.map(DataField.class::cast).map(df -> {

					String authorName = switch (df.getTag()) {
					case "100", "700" -> concatSubfieldData(marcRecord, df.getTag(), "abc", ", ").findFirst().orElse(null);
					default -> df.getSubfieldsAsString("a");
					};

					// Build an ID
					return StringUtils.isEmpty(authorName) ? null : Identifier.build(id -> {
						id.value(authorName).namespace(Objects.requireNonNullElse(df.getSubfieldsAsString("0"), "M" + df.getTag()));
					});
				}).filter(Objects::nonNull).forEach(ingestRecord::addIdentifiers);
		 */

		return ingestRecord;
	}

	default IngestRecordBuilder enrichWithTitleInformation(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {
		// Initial title.
		final String title = Stream.of("245", "243", "240", "246", "222", "210", "240", "247", "130")
				.filter(Objects::nonNull)
				.flatMap(tag -> concatSubfieldData(marcRecord, tag, "abc"))
				.filter(StringUtils::isNotEmpty)
				.reduce(ingestRecord.build().getTitle(), (current, item) -> {
					if (StringUtils.isEmpty(current)) {

						ingestRecord.title(item);

						ingestRecord.addIdentifier(id -> {
							// This allows us to add in important discriminators into the blocking title - edition being
							// the most obvious one for now. Ideally we would normalised this tho into a canonical string
//							List<String> qualifiers = new ArrayList();
//							String edition = null;
//							DataField edition_field = (DataField) marcRecord.getVariableField("250");
//							if ( edition_field != null ) {
//								qualifiers.add(edition_field.getSubfieldsAsString("a"));
//							}
							
              // 250 Is edition statement
							List<String> qualifiers = Marc4jRecordUtils
                                           .concatSubfieldData(marcRecord, "250", "a")
                                           .map(ed -> EditionNormalizer.normalizeEdition(ed) )
			                                     .map( StringUtils::trimToNull )
                                           .toList();
							
							// The old style blocking titles arranged words alphabetically, removed duplicates and didn't
							// suffer with double spacing, so using that here as it provides cleaner matching.
							id.namespace("BLOCKING_TITLE").value(
								DCBStringUtilities.generateBlockingString(item, qualifiers)
							).confidence(Integer.valueOf(0));
						});

						return item;
					}

					ingestRecord.otherTitle(item);


					// Keep returning the first title that was set.
					return current;
				});

		// log.trace("Title used: {}", title);

		return ingestRecord;
	}

	default IngestRecordBuilder handleControlNumber(final IngestRecordBuilder ingestRecord, final Record marcRecord) {
		// Grab the pair of 001 and 003. These contain the identifier value and
		// namespace respectively

		Optional.ofNullable(marcRecord.getControlNumber()).filter(StringUtils::isNotEmpty).flatMap(cn -> {
			final String cnAuthority = extractControlData(marcRecord, "003").findFirst()
					.orElse(getDefaultControlIdNamespace());

      // log.info("Consider control number {} {}",cnAuthority,cn);

			return Optional.ofNullable(StringUtils.isEmpty(cnAuthority) ? null : Identifier.build(id -> {
				id.namespace(cnAuthority).value(cn);
			}));

		}).ifPresent(ingestRecord::addIdentifiers);

		return ingestRecord;
	}

	default IngestRecordBuilder handleSystemControlNumber(final IngestRecordBuilder ingestRecord,
			final Record marcRecord) {

    // We are seeing duplicated 035$a fields causing failure to store record identifiers. Need to remove duplicated values
    List<String> seen_identifiers = new ArrayList<>();

		concatSubfieldData(marcRecord, "035", "a").forEach(val -> {
			final Matcher matcher = REGEX_NAMESPACE_ID_PAIR.matcher(val);

			if (matcher.matches()) {
				// Common to get lots of spaces here for some reason - so trim
				final String idns = Objects.requireNonNullElse(matcher.group(3), matcher.group(5)).trim();

				final Integer confidence = switch ( idns ) {
					case "OCoLC" -> Integer.valueOf(0);
					default -> Integer.valueOf(10);
				};

        String value = matcher.group(6).trim();
        String duplicate_detection_str = (idns+":"+value).toUpperCase();

        if ( seen_identifiers.contains(duplicate_detection_str) == false ) {

          // log.info("Adding system control number \"{}\"",duplicate_detection_str);

  				ingestRecord.addIdentifier(id -> {
	  				id
		  				.namespace(idns.equalsIgnoreCase("OCoLC") ? "OCoLC" : idns)  // Fix weird capitialisation variants of OCoLC number
			  			.value(value)
				  		.confidence(confidence);
  				});
          seen_identifiers.add(duplicate_detection_str);
        }
        else {
          // log.info("Skip duplicate system control number \"{}\"",duplicate_detection_str);
        }
			}
		});

		return ingestRecord;
	}

	static final Map<String, String> IDENTIFIER_FIELD_NAMESPACE = Map.of("010", "LCCN", "020", "ISBN", "022", "ISSN",
			"027", "STRN");

	default IngestRecordBuilder enrichWithIdentifiers(final IngestRecordBuilder ingestRecord, final Record marcRecord) {

		handleControlNumber(ingestRecord, marcRecord);
		handleSystemControlNumber(ingestRecord, marcRecord);

		// It turns out that in some conventional cataloguing standards, the FIRST 020 or 022 has substantially more standing
		// than subsequent values. Rather than using an indicator or a subfield this convention is positional. We use this list
		// to be able to track the first occurrence of an identifier type. /sigh.
		List<String> seen_identifier_types = new ArrayList<>();

    // We're encountering copies of the same identifier in a record - short hand list of identifiers so we can drop duplicates
    List <String>seen_identifiers = new ArrayList<>();

		// Make a unique list of normalised ISBN13s (that contains converted normalised ISBN10 values) in order to
		// determine if this record contains just 1 useful ISBN13.
		Set<String> unqique_normalised_isbn13_set = new java.util.HashSet<String>();

		IDENTIFIER_FIELD_NAMESPACE.keySet().stream().flatMap(tag -> marcRecord.getVariableFields(tag).stream())
				.filter(Objects::nonNull).map(DataField.class::cast).forEach(df -> {
					Optional.ofNullable(df.getSubfieldsAsString("a")).filter(StringUtils::isNotEmpty).ifPresent(sfs -> {

            String idns = IDENTIFIER_FIELD_NAMESPACE.get(df.getTag());

            String duplicate_detection_key = idns+":"+sfs;

            // Don't add duplicates
            if ( seen_identifiers.contains(duplicate_detection_key) == false ) {
						  final boolean first_occurrence_of_type = !(seen_identifier_types.contains( idns ));

  						if ( first_occurrence_of_type )
	  						seen_identifier_types.add(idns);

		  				final Integer confidence = switch ( idns ) {
			  				case "LCCN" -> Integer.valueOf(10);
				  			case "ISBN" -> Integer.valueOf(11); // All ISBNs are untrustworthy at this stage - unless there is only 1
					  		case "ISSN" -> first_occurrence_of_type ? Integer.valueOf(0) : Integer.valueOf(11);
						  	default -> Integer.valueOf(12);
  						};

	  					ingestRecord.addIdentifier(id -> {
		  					id.namespace(idns).value(sfs).confidence(confidence);
			  			});

              seen_identifiers.add(duplicate_detection_key);

              // Add normalised versions of ISSN and ISBN
              if ( "ISBN".equals(idns) || "ISSN".equals(idns) ) {

                String cleaned_isxn = cleanIsxn(sfs);

                String norm_id_key = idns+"-n:" + cleaned_isxn;

                if ( seen_identifiers.contains(norm_id_key) == false ) {
                  // IN the case where an ISBN value is repeated, but with extra punc - e.g. "1234567890" vs "1234567890 :"
                  // the actual identifier value is different BUT the normalised value is not - so we have to check that we don't add
                  // duplicate normalised values. Sad but true
  				  	  	ingestRecord.addIdentifier(id -> {
				  			    id
			  					  	.namespace(idns+"-n")
		  							  .value(cleaned_isxn)
    									.confidence(confidence);
                  });

                  seen_identifiers.add(norm_id_key);
					
									// Extra processing for ISBNs - 
									if ( "ISBN".equals(idns) ) {
										
										int isbn_length = cleaned_isxn.length();
										String isbn_to_consider = cleaned_isxn;
										switch ( isbn_length ) {
										
											case 10:
												// Convert to ISBN 13 and drop-through
												isbn_to_consider = isbn10to13(isbn_to_consider);
													
												log.trace("Converted ISBN 10 [{}] to ISBN 13: [{}]", isbn_to_consider);
												
											case 13:
												// ISBN 13.. Lets add for consideration
												log.trace("Adding ISBN 13: [{}]", isbn_to_consider);
												unqique_normalised_isbn13_set.add(isbn_to_consider);
												break;
											
											default:
												// Invalid length of ISBN after cleaning. DO not consider.
												log.error("ISBN [{}] is of invalid length [{}], should be 10 or 13. Skipping ISBN for comparison", cleaned_isxn, isbn_length);
										}
										
//										String isbn_to_consider = cleaned_isxn.length() == 13 ? cleaned_isxn : isbn10to13(cleaned_isxn);
//										unqique_normalised_isbn13_set.add(isbn_to_consider);
									}
                }
              }
            }
            else {
            }
					});
				});

		// IF the record carries ONLY A UNIQUE ISBN - then we can consider it useful for clustering.
		// Unhelpful practices like listing analytical item ISBNs in series level records, or (worse) omnibus records that only carry the 
		// ISBN for the sub-items and not the ISBN of the parent item itself mean the dangers of ISBN match when a record carries multiple
		// distinct values mean we're now forced to be more selective
		if ( unqique_normalised_isbn13_set.size() == 1 ) {
  		ingestRecord.addIdentifier(id -> {
				id
			  	.namespace("ONLY-ISBN-13")
		  		.value(unqique_normalised_isbn13_set.iterator().next())
    			.confidence(0);
      });
		}
		else {
			log.info("No unqiue ISBN13 in record: "+unqique_normalised_isbn13_set+" ISBN matching skipped seen="+seen_identifiers);
		}
		
		return ingestRecord;
	}

	
	public static String isbn10to13( String isbn10 ) {

		if (isbn10 == null || isbn10.length() != 10) {
			throw new DcbError("Input must be exactly 10 digits but value was \"" + isbn10 + "\"");
		}

		// Start with the "978" prefix and the first 9 digits of the ISBN-10
		String isbn13Body = "978" + isbn10.substring(0, 9);

		// Calculate the check digit
		int sum = 0;
		for (int i = 0; i < isbn13Body.length(); i++) {
			int digit = Character.getNumericValue(isbn13Body.charAt(i));
			sum += (i % 2 == 0) ? digit : digit * 3;
		}
		int checkDigit = (10 - (sum % 10)) % 10;

		// Return the full ISBN-13
		return isbn13Body + checkDigit;
	}


  private static String cleanIsxn(String v) {
    Matcher m = REGEX_ISXN_VALUE.matcher(v);
    if ( m.matches() ) {
      return m.group(1).replaceAll("[^\\dXx]","");
    }
    return v;
  }

	default IngestRecordBuilder enrichWithGoldrush(final IngestRecordBuilder ingestRecord, final Record marcRecord) {
		final GoldrushKey grk = getGoldrushKey(marcRecord);
		
		return Optional.of(grk.getText())
			.map( StringUtils::trimToNull )
			.map( grVal -> Identifier.build(id -> id.namespace(NS_GOLDRUSH).value(grk.getText())))
			.map( ingestRecord::addIdentifiers )
			.orElse( ingestRecord )
		;
	}

	public static Stream<String> extractControlData(final Record marcRecord, @NotEmpty final String tag) {

		return marcRecord.getVariableFields(tag).stream().filter(Objects::nonNull).map(ControlField.class::cast)
				.map(field -> field.getData());
	}

	Publisher<T> getResources(Instant since, Publisher<String> terminator);

	IngestRecordBuilder initIngestRecordBuilder(T resource);

	Record resourceToMarc(T resource);
	
	@SingleResult
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	default Publisher<IngestRecord> processSingleResource( T resource ) {
		return Mono.just(resource) // Reactor will blow up above if resource is null.						
				.flatMap( r -> saveRawAndContinue(r)
						.onErrorComplete()) // Complete the mono (Empty) and terminate for this resource. Let the upstream log any messages.

				.publishOn(Schedulers.boundedElastic())
				.subscribeOn(Schedulers.boundedElastic())
				.map(this::initIngestRecordBuilder)
				.zipWith(Mono.just( resourceToMarc(resource) ) )
						// .map( this::createMatchKey ))
				.map(TupleUtils.function(this::populateRecordFromMarc))
				.map( IngestRecordBuilder::build )
				.onErrorResume(err -> {
					log.error("Could not marshal resource [{}] into IngestRecord. \n{}", resource, err);
					return Mono.empty();
				});
	}
	
	T convertSourceToInternalType( SourceRecord source );
	
	
	@Override
	default IngestRecord convertSourceToIngestRecord( @NonNull SourceRecord source ) throws DcbException {
		
		try {
			// This is less than ideal. The whole flow of this needs work, and properly moving to
			// the internal Micronaut Marshaling/Conversion suite of utilities.
			
			// Convert to internal source type.
			T internalRecord = convertSourceToInternalType(source);
			
			// Initialize the ingestRecordBuilder from data that isn't necessarily contained
			// in the Marc-based section of this record.
			IngestRecordBuilder irBuilder = initIngestRecordBuilder(internalRecord);
			
			// Grab a standardised Marc view. This is possibly null if we have a record
			// representing a deleted resource as the marc record portion is often not
			// available in the remote system.
			Record marc = resourceToMarc(internalRecord);
			if ( marc == null ) {
				log.debug("Couldn't get Marc portion of source [{}], default empty", source);
			} else {
				irBuilder = populateRecordFromMarc(irBuilder, marc);
			}		
			
			// Build the record
			IngestRecord ir = irBuilder.build();
			
			return ir;
		} catch (Exception e) {
			throw new DcbError("Error converting MARC source record to IngestRecord", e);
		}
	}
	

	@Override
	public default Publisher<IngestRecord> apply(Instant since, Publisher<String> terminator) {

		log.info("Read from the marc source and publish a stream of IngestRecords");
		
		return Flux.defer(() -> getResources(since, terminator))
			.publishOn(Schedulers.boundedElastic())
			.subscribeOn(Schedulers.boundedElastic())
			.concatMap(this::processSingleResource);
	}

	@NonNull
	RawSourceRepository getRawSourceRepository();

	@Transactional(propagation = Propagation.MANDATORY)
	public default Mono<T> saveRawAndContinue(final T resource) {
		log.debug("Save raw {}", resource);
		
		return Mono.from( getRawSourceRepository().saveOrUpdate(
				resourceToRawSource(resource)))
				.doOnError( t -> log.debug("Error saving Raw", t) )
				.then(Mono.just(resource));
	}

	RawSource resourceToRawSource(T resource);

	private static void parseFromSingleSubfield(Record marcRecord, String fieldTag, char subField,
			Consumer<String> consumer) {		
		extractOrderedSubfields((DataField) marcRecord.getVariableField(fieldTag), "" + subField)
			.filter( Objects::nonNull ) // FindFirst non null, or degrade to empty if no data or only nulls.
			.findFirst()
			.ifPresent(consumer);
	}

	public default GoldrushKey getGoldrushKey(final Record marcRecord) {
		GoldrushKey grk = new GoldrushKey();

		// Start with 245. If there is a linkage to a 880, then swap to that instead.
		final DataField field245 = (DataField) marcRecord.getVariableField("245");
		final DataField fieldForTitle = extractOrderedSubfields(field245, "6")
			.map( REGEX_LINKAGE_245_880::matcher )
			.filter( Matcher::matches )
			.findFirst()
			.map( match -> match.group(1) )
			.flatMap( occNum -> {
				final Pattern linked880 = Pattern.compile("^245-" + occNum);
				return marcRecord.getVariableFields("880")
						.stream()
						.map( DataField.class::cast )
						.filter(field880 ->
							extractOrderedSubfields(field880, "6")
								.anyMatch(linked880.asMatchPredicate()))
						.findFirst();
			})
			.orElseGet(() -> field245);
		
		// Goldrush does not normally include $c but that seems to create very odd clusters for things like 
		// "Greatest Hits". It seems that $c can be a bit of a dumping ground - so whilst
		// "$a Greatest Hits - $c Some Artist" is useful, "$a Greatest Hits - $s Some Artist, with a foreward by X and some other stuff by Y"
		// is likely less helpful. It may be that taking the first 2 words of $c will yeild better results.
		List<String> fields = extractOrderedSubfields(fieldForTitle, "abc").limit(3)
				.toList();

		if (fields.size() > 0) {
			grk.parseTitle(fields.get(0), 
				fields.size() > 1 ? fields.get(1) : null,
				fields.size() > 2 ? fields.get(2) : null);
		}

		parseFromSingleSubfield(marcRecord, "245", 'h', grk::parseMediaDesignation);
		parseFromSingleSubfield(marcRecord, "260", 'c', grk::parsePubYear);
		parseFromSingleSubfield(marcRecord, "300", 'a', grk::parsePagination);
		parseFromSingleSubfield(marcRecord, "250", 'a', grk::parseEdition);
		parseFromSingleSubfield(marcRecord, "260", 'b', grk::parsePublisher);
		parseFromSingleSubfield(marcRecord, "245", 'p', grk::parseTitlePart);
		parseFromSingleSubfield(marcRecord, "245", 'n', grk::parseTitleNumber);

		char type = marcRecord.getLeader().getTypeOfRecord();
		grk.setRecordType(type);
//		System.out.println( grk.toString() );
//		System.out.println( grk.getText() );
		return grk;
	}

	public default IngestRecordBuilder enrichWithCanonicalRecord(final IngestRecordBuilder irb, final Record marcRecord) {
		Map<String, Object> canonical_metadata = new HashMap<>();
		IngestRecord ir = irb.build();
		canonical_metadata.put("sourceRecordId", ir.getSourceRecordId());
		canonical_metadata.put("title", ir.getTitle());
		canonical_metadata.put("identifiers", ir.getIdentifiers());
		canonical_metadata.put("derivedType", ir.getDerivedType());
		canonical_metadata.put("formOfItem", ir.getFormOfItem());
		canonical_metadata.put("derivedFormOfItem", ir.getDerivedFormOfItem());
		canonical_metadata.put("recordStatus", ir.getRecordStatus());
		// canonical_metadata.put("typeOfRecord",ir.getTypeOfRecord());
		// canonical_metadata.put("bibLevel",ir.getBibLevel());
		// canonical_metadata.put("materialType",ir.getMaterialType());
		canonical_metadata.put("author", ir.getAuthor());
		canonical_metadata.put("otherAuthors", ir.getOtherAuthors());

    // Allow ourselves a way to track which version of marc ingest source created a source record.
    // Increment this seq number on significant changes to see if a record needs to be boosted
		canonical_metadata.put("dcbMarcIngestSeq", "1");

		DataField seriesStatement = (DataField) marcRecord.getVariableField("490");
		if ( seriesStatement != null ) {
			setIfSubfieldPresent(seriesStatement, 'a', canonical_metadata, "seriesStatement");
		}

		DataField seriesAddedEntry = (DataField) marcRecord.getVariableField("830");
		if ( seriesAddedEntry != null ) {
			setIfSubfieldPresent(seriesAddedEntry, 'a', canonical_metadata, "serUniformTitle");
			setIfSubfieldPresent(seriesAddedEntry, 'v', canonical_metadata, "serSeqDesignation");
			setIfSubfieldPresent(seriesAddedEntry, 'v', canonical_metadata, "serMedium");
		}

		DataField publisher1 = (DataField) marcRecord.getVariableField("264");
		if (publisher1 != null) {
			setIfSubfieldPresent(publisher1, 'a', canonical_metadata, "placeOfPublication");
			setIfSubfieldPresent(publisher1, 'b', canonical_metadata, "publisher");
			setIfSubfieldPresent(publisher1, 'c', canonical_metadata, "dateOfPublication");
		} else {
			DataField publisher2 = (DataField) marcRecord.getVariableField("260");
			if (publisher2 != null) {
				setIfSubfieldPresent(publisher2, 'a', canonical_metadata, "placeOfPublication");
				setIfSubfieldPresent(publisher2, 'b', canonical_metadata, "publisher");
				setIfSubfieldPresent(publisher2, 'c', canonical_metadata, "dateOfPublication");
			}
		}

		DataField title_field = (DataField) marcRecord.getVariableField("245");
		if (title_field != null) {
      setIfSubfieldPresent(title_field, 'n', canonical_metadata, "titleNumberOfPart");
    }

		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("500")) {
			addToCanonicalMetadata("notes", vf, "a", canonical_metadata);
		}

		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("520")) {
			addToCanonicalMetadata("summary", vf, "a", canonical_metadata);
		}

		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("505")) {
			addToCanonicalMetadata("contents", vf, "a", canonical_metadata);
		}

		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("504")) {
			addToCanonicalMetadata("bibNotes", vf, "a", canonical_metadata);
		}

		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("490")) {
			addToCanonicalMetadata("series", vf, "abcdefghijklmnopqrstuvwxyz", canonical_metadata);
		}
		for (VariableField vf : (List<VariableField>) marcRecord.getVariableFields("830")) {
			addToCanonicalMetadata("series", vf, "abcdefghijklmnopqrstuvwxyz", canonical_metadata);
		}

		canonical_metadata.put("language", interpretLanguages(marcRecord));

		// Commented - 700s need to be added as author objects
		// addToCanonicalMetadata("author", "700", "other", marcRecord,
		// canonical_metadata);

		// Extract some subject metadata
		addToCanonicalMetadata("subjects", "600", "personal-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "610", "corporate-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "611", "meeting-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "630", "uniform-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "647", "named-event", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "648", "chronological-term", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "650", "topical-term", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "651", "topical-term", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "653", "index-term-uncontrolled", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "654", "faceted", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "655", "faceted", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "657", "faceted", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "658", "faceted", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "662", "hierarchial-place-name", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "690", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "691", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "695", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "696", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "697", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "698", "local-subject", marcRecord, canonical_metadata);
		addToCanonicalMetadata("subjects", "699", "local-subject", marcRecord, canonical_metadata);

		addToCanonicalMetadata("agents", "100", "name-personal", marcRecord, canonical_metadata);
		addToCanonicalMetadata("agents", "110", "name-corporate", marcRecord, canonical_metadata);
		addToCanonicalMetadata("agents", "111", "name-meeting", marcRecord, canonical_metadata);

		// addToCanonicalMetadata("agents", "130", "uniform-title", marcRecord,
		// canonical_metadata);

		addToCanonicalMetadata("physical-description", "300", null, marcRecord, canonical_metadata);
		addToCanonicalMetadata("content-type", "336", null, marcRecord, canonical_metadata);
		addToCanonicalMetadata("media-type", "337", null, marcRecord, canonical_metadata);

		DataField edition_field = (DataField) marcRecord.getVariableField("250");
		if (edition_field != null) {
			canonical_metadata.put("edition", tidy(edition_field.getSubfieldsAsString("a")));
		}

		return irb.canonicalMetadata(canonical_metadata);
	}

	public default IngestRecordBuilder enrichWithMetadataScore(final IngestRecordBuilder irb, final Record marcRecord) {
		int score = 0;
		IngestRecord currentRecordView = irb.build();
		
		Map<String, Object> canonical_metadata = currentRecordView.getCanonicalMetadata();
		
		if (canonical_metadata != null) {
			
			// Total the counts of the properties in the List
			score = Stream.of("subjects", "agents")
				.map(canonical_metadata::get)
				.filter(Objects::nonNull)
				.map( List.class::cast )
				.mapToInt( List::size )
				.sum();
			
			// Record has metadata - have a bonus point!
			score++;
		}
		
		score += Optional.ofNullable(currentRecordView.getIdentifiers())
			.map( Collection::size )
			.orElse( 0 );
		
		return irb.metadataScore(score);
	}

	private void setIfSubfieldPresent(DataField f, char subfield, Map<String, Object> target, String key) {
		if (f != null) {
			Subfield subfield_v = f.getSubfield(subfield);
			if (subfield_v != null) {
				target.put(key, tidy(subfield_v.getData()));
			}
		}
	}

	private String tidy(String inputstr) {
		String result = null;
		if ( inputstr != null )
			result = REGEX_REMOVE_PUNCTUATION.matcher(inputstr).replaceAll("");
		return result;
	}


	private void addToCanonicalMetadata(String property, VariableField vf, String tags,
			Map<String, Object> canonical_metadata) {

		String value = ((DataField) vf).getSubfieldsAsString("a");

		if (value != null) {
			List<Object> the_values = (List<Object>) canonical_metadata.get(property);
			if (the_values == null) {
				the_values = new ArrayList<>();
				canonical_metadata.put(property, the_values);
			}
			the_values.add(value);
		}
	}

	private void addToCanonicalMetadata(String property, String tag, String subtype, Record marcRecord,
			Map<String, Object> canonical_metadata) {

		@SuppressWarnings("unchecked")
		List<Object> the_values = (List<Object>) canonical_metadata.get(property);

		if (the_values == null)
			the_values = new ArrayList<>();

		for (VariableField vf : marcRecord.getVariableFields(tag)) {
			DataField df = (DataField) vf;
			Map<String, String> the_entry = new HashMap<>();
			if (subtype != null)
				the_entry.put("subtype", subtype);
			the_entry.put("label", df.getSubfieldsAsString("abcdefg"));
			the_values.add(the_entry);
		};

		if (the_values.size() > 0) {
			canonical_metadata.put(property, the_values);
		}
	}

}
