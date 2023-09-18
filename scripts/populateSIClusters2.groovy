#!/usr/bin/env groovy

@Grab('io.github.http-builder-ng:http-builder-ng-apache:1.0.4')

import groovyx.net.http.ApacheHttpBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.config.RequestConfig
import groovyx.net.http.HttpBuilder
import groovyx.net.http.FromServer
import static groovyx.net.http.ApacheHttpBuilder.configure
import groovy.json.JsonOutput
import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;

def target='default'

// Read or set up config
Map cfg = initialise();

HttpBuilder keycloak = configure {
  request.uri = cfg[target].KEYCLOAK_BASE
}

HttpBuilder dcb_http = configure {
  request.uri = cfg[target].DCB_BASE
}

HttpBuilder es_http = configure {
  request.uri = cfg[target].ES_BASE
}

process(dcb_http, es_http, cfg, target);
updateConfig(cfg);

System.exit(0)


private Map initialise() {
  String CONFIG_FILE='./cfg.json'
  Map cfg = [:]
  JsonSlurper j = new JsonSlurper();
  File config_file = new File(CONFIG_FILE);
  if ( !config_file.exists() ) {
    //
  }
  else {
    cfg = j.parse(config_file);
  }

  return cfg;
}

private void updateConfig(Map cfg) {
  String CONFIG_FILE='./cfg.json'
  File cfg_file = new File(CONFIG_FILE);
  File backup = new File(CONFIG_FILE+'.bak');
  cfg_file.renameTo(backup)
  String result_json = new JsonBuilder( cfg ).toPrettyString()
  cfg_file << result_json
}

private void process(HttpBuilder http, HttpBuilder es_http, Map config, String target, boolean shortstop=false) {
  int page_counter=0;
  // String since=null;
  // String since="2023-07-10T23:20:38.677389Z"
  String since=config[target].CURSOR
  println("Cursor: ${since}");

  // 
  boolean moreData = true;
  while ( moreData ) {
    boolean gotdata=false
    int retries=0;
    while ( !gotdata && retries++ < 5 ) {
      println("Get page[${page_counter++}] retries=[${retries}] of data with since=${since}");
      try {
        Map datapage = getPage(http, since,1000);
        if ( datapage != null ) {
          println("Got page of ${datapage.content.size()} items... ${datapage.pageable} total num records=${datapage.totalSize}");
          if ( ( datapage.content.size() == 0 ) || ( shortstop ) ) {
            moreData=false;
          }
          else {
            println("postPage ${config}");
            postPage(es_http, datapage, shortstop, page_counter, config[target]);
            datapage.content.each { r ->
	    	since = r.dateUpdated;
                  gotdata=true
    	    }
    	  Thread.sleep(2000);
          }
        }
      }
      catch(Exception e) {
      }
      config[target].CURSOR=since
      updateConfig(config);
    }
  }
}


private Map getPage(HttpBuilder http, String since, int pagesize) {

  Map result = null;

  def http_res = http.get {
    request.uri.path = "/clusters".toString()
    request.uri.query = [
        'page':0,
        'size':pagesize
    ]
    if ( since != null ) {
      request.uri.query.since = since;
    }
    request.accept='application/json'
    request.contentType='application/json'
    // request.headers['Authorization'] = 'Bearer '+token
  
    response.success { FromServer fs, Object body ->
      // println("Got response : ${body}");
      result = body;
      result.content.each { e ->
         // Just iterate over everything to make sure we have fetched it all
      }

      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get Page : Problem body:${body} fs:${fs} status:${fs.getStatusCode()}");
    }
  }

  println("Got page since ${since}");
  return result;
}

private String getIdentifier(Map record, String type) {
  String result = null;
  Map m = record.identifiers?.find { it.namespace == type }
  if ( m != null ) {
    // println("Found value ${m.value} for ${type}");
    result = m.value;
  }
  else {
  }

  return result;
}

private void extractYearOfPublication(Map record, StringWriter sw) {
  String dateOfPublication = record['dateOfPublication'];
  if ( dateOfPublication != null ) {
    def match = ( dateOfPublication =~ /(?:(?:19|20)[0-9]{2})/ )
    if ( match.size() == 1 ) {
      sw.write("\"yearOfPublication\": ${match[0]},".toString());
    }
  }
}

private void checkFor(String field, Map record, StringWriter sw) {
  if ( record[field] != null ) {
    // println("${field} is present : ${record[field]}");
    sw.write("\"${field}\": \"${esSafeValue(record[field])}\",".toString());
  }
  else {
  }
}

private postPage(HttpBuilder http, Map datapage, boolean shortstop, int page_counter, Map config) {

  StringWriter sw = new StringWriter();

  int ctr=0;
  datapage.content.each { r ->

    if ( ( r.title != null ) && ( r.title.length() > 0 ) ) {
      List bib_members = [];

      // Add in the IDs of all bib records in this cluster so we can access the cluster via any of it's member record IDs
      // bib_members.add(r.selectedBib.bibId);
      r.bibs.each { memberbib -> 
        // println("Looking for ${memberbib} in ${bib_members}");
        if ( bib_members.find{ it.bibId == memberbib.bibId } == null ) {
          if ( memberbib.bibId == r.selectedBib.bibId )
            memberbib['primary']="true"
          bib_members.add(memberbib);
        }
      }

      String isbn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISBN');
      String issn = getIdentifier(r.selectedBib.canonicalMetadata, 'ISSN');
      // println(r.title);
      // sw.write("{\"index\":{\"_id\":\"${r.clusterId}\"}}\n".toString());
      sw.write("{\"index\":{\"_id\":\"${r.selectedBib.bibId}\"}}\n".toString());
      sw.write("{\"bibClusterId\": \"${r.clusterId}\",".toString())
      sw.write("\"title\": \"${esSafeValue(r.title)}\",".toString());

      boolean first = true;
      sw.write("\"members\": [")
      bib_members.each { member ->
        if ( first ) 
          first=false
        else 
          sw.write(", ");

        sw.write("{\"bibId\":\"${member.bibId}\",\"title\":\"${member.title}\",\"sourceRecordId\":\"${member.sourceRecordId}\",\"sourceSystem\":\"${member.sourceSystem}\"}");
      }
      sw.write("],");

      checkFor('placeOfPublication',r.selectedBib.canonicalMetadata,sw);
      checkFor('publisher',r.selectedBib.canonicalMetadata,sw);
      checkFor('dateOfPublication',r.selectedBib.canonicalMetadata,sw);
      checkFor('derivedType',r.selectedBib.canonicalMetadata,sw);

      extractYearOfPublication(r.selectedBib.canonicalMetadata, sw);

      if ( isbn )
        sw.write("\"isbn\": \"${isbn}\",".toString());

      if ( issn )
        sw.write("\"issn\": \"${issn}\",".toString());
      sw.write("\"metadata\":")
      sw.write(JsonOutput.toJson(r.selectedBib.canonicalMetadata));
      sw.write("}\n")

      ctr++;
    }
    else {
      // println("NULL title encountered  : ${r?.sourceRecordId} ${r}");
      File f = new File("./bad".toString())
      f << r
    }
  }

  String reqs = sw.toString();

  if ( false ) {
    println("reqs....");
    File pagefile = new File("./pages/${page_counter}.json")
    if ( pagefile.exists() )
      pagefile.delete();
    pagefile << reqs
  }

  boolean posted=false;
  int retry=0;

  while ( !posted && retry++ < 5 ) {
    println("Posting[${retry}] ./pages/${page_counter}.json to elasticsearch size=${reqs.length()}");
    try {
      def http_res = http.put {
        request.uri.path = "/mobius-si/_bulk".toString()
        request.uri.query = [
          refresh:true,
          pretty:true
        ]
        request.accept='application/json'
        request.contentType='application/json'
        request.headers.'Authorization' = "Basic "+("${config.ES_UN}:${config.ES_PW}".toString().bytes.encodeBase64().toString())
  
        request.body=reqs;
        // request.headers['Authorization'] = 'Bearer '+token
   
        response.success { FromServer fs, Object body ->
          println("Page of ${ctr} posted OK");
          posted=true
        }
        response.failure { FromServer fs, Object body ->
          println("Post Page : Problem body:${body} (${body?.class?.name}) fs:${fs} status:${fs.getStatusCode()}");
        }
      }
    }
    catch ( Exception e ) {
      println("Problem: ${e.message}");
    }

    if ( !posted ) {
      Thread.sleep(1000)
    }
  }
}

private String esSafeValue(String v) {
  if ( v != null )
    return v.replaceAll("\"","\\\\\"");

  return null;
}

private Map getHostLmss(HttpBuilder http, String token) {
  Map result = null;
  def http_res = http.get {
    request.uri.path = "/hostlmss"

    request.accept='application/json'
    request.contentType='application/json'
    request.headers.'Authorization' = "Bearer "+token

    response.success { FromServer fs, Object body ->
      println("Got response : ${body}");
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get LMS : Problem body: ${body.class.name} ${body} (${new String(body)}) fs:${fs} status:${fs.getStatusCode()}");
    }
    return result;
  }
}

private String getLogin(HttpBuilder http, String user, String pass) {
  String result = null;
  def http_res = http.post {
    request.uri.path = "/realms/reshare-hub/protocol/openid-connect/token"

    request.contentType = 'application/x-www-form-urlencoded'

    request.body = [
      "client_id":"dcb",
      "client_secret":"RncJxvqxtOpeboB6dYFegzF47q8gyK2x",
      "username":user,
      "password":pass,
      "grant_type":"password"
    ]

    response.success { FromServer fs, Object body ->
      // println("Got response : ${body}");
      result = body.access_token;
      // result = body.entries[0].link.substring(body.entries[0]?.link.lastIndexOf('/')+1)
    }
    response.failure { FromServer fs, Object body ->
      println("Get Login Problem ${body.class.name} ${body} ${fs} ${fs.getStatusCode()}");
    }
    return result;
  }

}
