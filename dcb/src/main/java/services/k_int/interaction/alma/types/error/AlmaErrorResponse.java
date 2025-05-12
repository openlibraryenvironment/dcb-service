package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable.Deserializable
public class AlmaErrorResponse {
	private boolean errorsExist;
	private AlmaErrorList errorList;

	public boolean isErrorsExist() { return errorsExist; }
	public void setErrorsExist(boolean errorsExist) { this.errorsExist = errorsExist; }

	public AlmaErrorList getErrorList() { return errorList; }
	public void setErrorList(AlmaErrorList errorList) { this.errorList = errorList; }
}
