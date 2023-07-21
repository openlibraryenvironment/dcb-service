package services.k_int.interaction.sierra.bibs;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;

@Data
@Serdeable
@Builder
public class BibPatch {
	List<String> authors;
	List<String> callNumbers;
	List<String> titles;
	List<String> editions;
	List<String> descriptions;
	List<String> series;
	List<String> notes;
	List<String> subjects;
	List<String> pubInfo;
	List<String> addedAuthors;
	List<String> addedTitles;
	List<String> continues;
	List<String> relatedTo;
	List<String> bibUtilNums;
	List<String> standardNums;
	List<String> lccn;
	List<String> govDocs;
	List<String> holdings;
	List<String> tocData;
	List<String> misc;
	String lang;
	Integer skip;
	String location;
	LocalDate catalogDate;
	String bibLevel;
	String materialType;
	String bibCode3;
	String country;
	String marcType;
	Map<Integer, FixedField> fixedFields;
}
