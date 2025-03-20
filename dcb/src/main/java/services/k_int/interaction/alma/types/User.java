package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class User {
        String primary_id;
        String first_name;
        String last_name;
        Boolean is_researcher;
        String link;
        CodeValuePair status;
        CodeValuePair gender;
        String password;
}
