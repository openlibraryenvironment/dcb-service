package org.olf.dcb.request.resolution;

import java.net.URI;
import org.zalando.problem.AbstractThrowableProblem;

public class UnableToResolvePatronRequest extends AbstractThrowableProblem {

        private static final URI TYPE = URI.create("https://openlibraryfoundation.org/dcb/problems/patronRequest/UnableToResolvePatronRequest");

	public UnableToResolvePatronRequest(String message) { 
                super(TYPE,message); 
        }
}
