package org.olf.dcb.export.model;

import java.util.ArrayList;
import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Accessors(chain = true)
@Data
@ExcludeFromGeneratedCoverageReport
@Serdeable
@ToString
public class ProcessingResult {
	public List<String> successes = new ArrayList<String>();
	public List<String> failures = new ArrayList<String>();
	public List<String> messages = new ArrayList<String>();
	
	public void failed(String code, String name, String failureMessage) {
		failures.add(code + " / " + name + "- " + failureMessage);
		message(failureMessage);
	}
	
	public void success(String code, String name) {
		successes.add(code + " / " + name);
	}
	
	public void message(String message) {
		messages.add(message);
	}
	
	public int getSuccessful() {
		return(successes.size());
	}
	
	public int getFailed() {
		return(failures.size());
	}
}
