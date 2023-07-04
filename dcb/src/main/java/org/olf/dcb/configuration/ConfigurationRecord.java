package org.olf.dcb.configuration;



/**
 * Anticipating different kinds of config records in the future - an interface that allows us to mark a DTO as some kind
 * of config record from a HostLMS - E.G. "A Branch Record"
 */
public interface ConfigurationRecord {

        public String getRecordType();

}
