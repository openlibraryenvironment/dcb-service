package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.List;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaUser {
	// CONTACT, PUBLIC, STAFF
	CodeValuePair record_type;
	@ToString.Include
	String primary_id;
	String first_name;
	String last_name;
	Boolean is_researcher;
	String link;
	CodeValuePair gender;
	String password;
	CodeValuePair user_title;
	// FACULTY, STAFF, GRAD, UNDRGRD, GUEST, ACADSTAFF, ALUM, PT
	CodeValuePair user_group;
	CodeValuePair campus_code;
	CodeValuePair preferred_language;
	// EXTERNAL, INTERNAL, INTEXTAUTH
	CodeValuePair account_type;
	@ToString.Include
	String external_id;
	// ACTIVE, INACTIVE, DELETED
	CodeValuePair status;
	@ToString.Include
	List<UserIdentifier> user_identifiers;
}
