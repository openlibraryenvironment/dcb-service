package services.k_int.integration.marc4j;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.marc4j.marc.DataField;
import org.marc4j.marc.Leader;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;

public interface Marc4jRecordUtils {
	
	public static Stream<String> concatSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields) {
		return concatSubfieldData(marcRecord, tag, subfields, " ");
	}
	
	public static Stream<String> concatSubfieldData(final Record marcRecord, @NotEmpty final String tag, @NotEmpty final String subfields, @NotNull final String delimiter) {
	
		return marcRecord.getVariableFields(tag).stream()
			.filter( Objects::nonNull )
			.map( DataField.class::cast )
			.map( field -> extractOrderedSubfields(field, subfields)
					.collect(Collectors.joining(delimiter)));
	}
	
	public static Stream<String> extractOrderedSubfields( DataField field, String orderedSpec ) {
		
		if (field == null) return Stream.empty();
		
		return orderedSpec.chars()
			.mapToObj( v -> (char)v )
			.flatMap( charVal -> {
				return field.getSubfields(charVal).stream();
			})
			.map( Subfield::getData )
		;
	}
	
	public static String typeFromLeader ( Leader leader ) {
		
		return switch ( leader.getTypeOfRecord() ) {
			case 'a' -> {
				yield switch (leader.getImplDefined1()[0]) {
					case 'a', 'c', 'd', 'm' -> "Book";
					default -> "Continuing Resources";
				};
			}
			case 'c', 'd' -> "Notated Music";
			case 'e', 'f' -> "Maps";
			case 'g' -> "Video Recording";
                        case 'i' -> "Audiobook";
                        case 'j' -> "Music";
                        case 'k' -> "Visual Material";
                        case 'o' -> "Kit";
			case 'm' -> "Electronic Resource";
			case 'p' -> "Mixed Material";
			case 't' -> "Book";			
			case 'r' -> "Object";			
			default -> "Unknown";
		};
	}
}
