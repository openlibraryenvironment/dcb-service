package services.k_int.interaction.sierra.bibs;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Serdeable
@Builder
public class BibPatch {
	String[] authors;
	String[] callNumbers;
	String[] titles;
	String[] editions;
	String[] descriptions;
	String[] series;
	String[] notes;
	String[] subjects;
	String[] pubInfo;
	String[] addedAuthors;
	String[] addedTitles;
	String[] continues;
	String[] relatedTo;
	String[] bibUtilNums;
	String[] standardNums;
	String[] lccn;
	String[] govDocs;
	String[] holdings;
	String[] tocData;
	String[] misc;
	String lang;
	Integer skip;
	String location;
	LocalDate catalogDate;
	String bibLevel;
	String materialType;
	String bibCode3;
	String country;
	String marcType;
}
