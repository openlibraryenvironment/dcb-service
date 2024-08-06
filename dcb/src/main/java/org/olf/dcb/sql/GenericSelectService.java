package org.olf.dcb.sql;

import jakarta.inject.Singleton;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;

@Singleton
public class GenericSelectService {
	private static final Logger log = LoggerFactory.getLogger(GenericSelectService.class);

	private static final String SELECT_PREFIX = "select ";
	
	private final DefaultDataSourceConnectionOperations transactionManager;

    public GenericSelectService(
    		DefaultDataSourceConnectionOperations transactionManager
    ) {
    	this.transactionManager = transactionManager;
    }

    /**
     * Executes an arbitrary select statement against the database
     * @param sql The sql to be executed
     * @return A map containing the results of executing the sql
     */
    public Map<String, Object> select(String sql) {
    	Map<String, Object> result = new HashMap<String, Object>();

    	// Ensure we have been supplied some sql
    	if (sql != null) {
    		result.put("sqlStatement", sql);
	    	if (sql.regionMatches(true, 0, SELECT_PREFIX, 0, SELECT_PREFIX.length())) {
            	transactionManager.executeRead( status -> {
    	            try {
		            	// Prepare the sql
		            	PreparedStatement preparedStatement = status.getConnection().prepareStatement(sql);
		                try {
		                	// Execute the query
		                	ResultSet resultSet = preparedStatement.executeQuery();
	
		                	// Get hold of the name of each field we are selecting
		                	ResultSetMetaData metadata = resultSet.getMetaData();
		                    int columnCount = metadata.getColumnCount();
		                    ArrayList<String> columnNames = new ArrayList<String>();
		                    for (int index = 1; index <= columnCount; index++) {
		                    	columnNames.add(metadata.getColumnName(index));
		                    }
	
		                    // Create our array of records
		                    ArrayList<HashMap<String, Object>> records = new ArrayList<HashMap<String, Object>>();
		                    result.put("hits", records);
	
		                    // Loop through all the found records
		                    while (resultSet.next()) {
		                        HashMap<String, Object> record = new HashMap<String, Object>();
		                        records.add(record);
		                        for (String columnName : columnNames) {
		                        	record.put(columnName, resultSet.getObject(columnName));
		                        }
		                    }
		                    
		                    // Close the result set
		                    resultSet.close();
		                } catch (Exception e) {
		                	// Problem with executing the statement
				    		result.put("error", "Exception executing statement: " + e.toString());
		                }
		                
		                // Close the statement
		                preparedStatement.close();
    	            } catch (Exception e) {
                    	// Problem with preparing the statement
    		    		result.put("error", "Exception preparing statement: " + e.toString());
    	            }
	                
	                return(null);
            	});
	    	} else {
	    		result.put("error", "Can only execute select statements");
	    	}
    	} else {
    		result.put("error", "No sql supplied");
    	}
    	
    	return(result);
    }
}
