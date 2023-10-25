# Changelog

## Version 1.7.2

### Changes
* [Chore]
	* When setting page size for polaris, limit the value to 100
* [Refactor]
	* Move BibRecordService to svc package

### Fixes
* [Harvesting]
	* Sierra date ranges require the time zone signifier Z
* [Ingest]
	* Syntax error caused by previous merge
* [Marc   Ingest]
	* DCB-504 DCB-521 - check 264 for publication info before 260
* [General]
	* Ignore resumption initially if we have a since.
	* Save removed resumption token
	* Remove related bib identifiers before the bib

## Version 1.7.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [Ingest]
	* In sierra - deleted=true means ONLY deleted records, suppressed=true means ONLY suppressed records. The default is to return all and thats what we want

## Version 1.7.0

### Additions
* [General]
	* Re-elect selected bib on cluster if current is deleted

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* docs - Added state model json

### Fixes
* [Harvesting]
	* Send suppressed=true and deleted=true when harvesting from Sierra
* [Security]
	* remove anon auth from rest endpoints
* [General]
	* Query for page then expand in separate query

## Version 1.6.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* Move record clustering service into package.
* [Rework]
	* Cluster controller to use service and improve response time

### Fixes
* [Polaris]
	* Overrid the isEnabled method to honour settings from config

## Version 1.6.0

### Additions
* [General]
	* Soft delete of ClusterRecords

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* release - updated Release build instructions
	* ingest - Tone down logging on papi ingest - was filling logs with every marc record
	* Use existing delete all method for mappings DCB-479
	* Move item status mapper tests to same package as production code DCB-479
	* Use only Available for Polaris fallback item status mapping DCB-479
	* Use only hyphen for Sierra fallback item status mapping DCB-479
	* Extract method for determining local item type code DCB-479
	* Reformat code in item mapper DCB-479
* [Feature]
	* graphql - Add paginated supplier requests and agency groups. Leave old groups api intact for now
	* graphql - DCB-501 add paginated patron requests query to graphql endpoint
	* graphql - DCB-500 add locations query to graphql endpoint
	* graphql - Added agencies and hostLms
	* graphql - Graphql query methods for clusterRecords and sourceRecords with associated navigation
* [Refactor]
	* Rename map status method to indicate use of Sierra fallback DCB-479
	* Extract method for defining mappings in status mapping tests DCB-479
	* Extract constant for host LMS code in status mapping tests DCB-479
	* Rename delete all reference value mappings method DCB-479
	* Move defining item status mapping to fixture DCB-479
	* Extract method for mapping item status in tests DCB-479
	* Extract param array overload for defining fallback mapper DCB-479
	* Expose default status fallback mapper DCB-479
	* Introduce parameter for fallback mapper DCB-479
	* Introduce functional interface for fallback mapper DCB-479
	* Extract method for mapping item status to code DCB-479
	* Use empty instead of zero length when fallback mapping DCB-479
* [Test]
	* Use unknown status fallback when testing reference value status mappings DCB-479
	* Move reference value status mapping tests to nested class DCB-479
	* Move Sierra specific status mapping tests to nested class DCB-479
	* Use Sierra item status fallback mapper in tests DCB-479

### Fixes
* [Graphql]
	* AgencyGroups data fetcher is now reactive
* [Mappings]
	* DCB-502 patch marc leader item type mappings
* [General]
	* Properly implement touch

### References
* [Fixes]
	* Issue #DCB-478

## Version 1.5.0

### Additions
* [General]
	* turn on authentication on the place request API [DCB-322]
	* authenticate patrons registered at libraries on polaris systems [DCB-476]

### Changes
* [Chore]
	* Reformat item type mapper
	* updated the logging around the tracking service
* [Feature]
	* graphql - Use Pageable in InstanceClusterDataFetcher and add page vars to schema
	* graphql - Add instanceCluster and SourceBib data fetchers
* [Refactor]
	* Rename item bib ID field DCB-479
	* Rename create numeric range mapping method DCB-479
	* Rename delete all host LMS method DCB-479
	* Move numeric range mapping methods to fixture DCB-479
	* Move duplicated resolve method from host LMS clients to separate class DCB-479
	* Rename get items by bib ID method DCB-479
	* Use annotation for log in shared index service DCB-479
	* Use builder when creating resolution bib record DCB-479
	* Rename resolution bib record ID to source record ID DCB-479
	* Remove host LMS code parameter from get items method DCB-479
	* Use code from host LMS instead of parameter when getting items in Sierra client DCB-479
	* Use code from host LMS instead of parameter when getting items in Polaris client DCB-479
	* Use code from host LMS instead of parameter when getting items in dummy client DCB-479
* [Test]
	* Check local bib ID for supplier request after resolution DCB-479
	* Remove reliance on order in shared index service tests

### Fixes
* [Graphql]
	* Declare a page type in the schema for the reactive page

## Version 1.4.0

### Additions
* [General]
	* provide live availability results from polaris hosts [DCB-402]
	* Added Folio OAI PMH as a source.

### Changes
* [Chore]
	* Additional logging as a part of ingest - logging source, title, sourceId, deleted and suppression flags
	* tidy readme
* [Refactor]
	* Item result to item mapper [DCB-402]

### Fixes
* [General]
	* remove polaris ingest limiter

## Version 1.3.0

### Additions
* [General]
	* populate shared index from polaris [DCB-282]

### Changes
* [Chore]
	* correct Computer File type mapping for Marc records

### Fixes
* [General]
	* duplicate key value violates unique constraint raw source pkey [DCB-282]

## Version 1.1.0

### Additions
* [General]
	* default search property can now be chosen per type.
	* generate dummy titles

### Changes
* [Chore]
	* Add type hints for lucene
	* Refactor dummy client for DCB-324
	* refactor dummy record source to use same kind of reactive paging as normal clients
	* constrain native image to xeon-v2 optimisations for now
	* constrain native exes to xeon-v2 optimisations.. for now
	* Take notice of num-records-to-generate parameter in dummy HostLMSClient
	* Added a release.md to describe the release process
* [Feature]
	* Convert ResolutionStrategy choose item to a reactive method
	* Add type filter to locations

### Fixes
* [General]
	* Clustering null values

## Version 1.0.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Build - Updated tag matching for GH Actions
	* Diable the AOT capabilities for now
	* Optimization settings.

### Fixes
* [General]
	* Broke the Optimisation layer.
	* Native logging.

## Version 1.0.0

### Additions
* [Build]
	* **BREAKING** -  Restructure into sub projects
* [Bib]
	* all models changed to 'record' based, with builders
	* Extention of ImportRecord added
	* Added test marc file
	* added tests / added extra fields
	* extended ImportedRecord identifiers
	* reactive types
* [Clustering]
	* Improved clustering
	* First pass at model.
* [Core   API]
	* Seed PatronRequest controller
* [Framework]
	* Multiple Lms/Agencies initially from config only.
* [Locations]
	* Added list method with temporary, static data
* [General]
	* provide instance holdings display details [DCB-273]
	* add swagger annotations [DCB-229]
	* provide a dcb api endpoint to allow locate to validate a patron login [DCB-229]
	* make agencies endpoint return information needed to support auth flows [DCB-228]
	* dcb 224 - add domain model for groups and group members
	* request cancellation by patron [DCB-211]
	* Override the binding of the status property
	* Wildcards
	* Group parsing.
	* Simple change to enable negation and other modifiers.
	* First pass at working field parser
	* Generate setter as chainable accessors
	* added parent location field to location
	* set fixed field 031 to n for suppression in createBib [DCB-205]
	* Refactor Patron methods to object that includes barcode [DCB-206]
	* record audit log entry for each transition in workflow [DCB-195]
	* Recognise fixedField 158 in patron records and return value in SierraPatronDTO
	* record a message when patron request enters error status [DCB-199]
	* update virtual patrons type in supplying host lms [DCB-192]
	* Add toCSV method
	* use reference value mapping when mapping item status [DCB-191]
	* remove default patron type mapping [DCB-190]
	* move patron request to error status when any error happens during workflow [DCB-185]
	* mapping location to virtual item
	* Report errors from live availability API
	* place a hold request in borrowing agencys local system
	* Ignore failed requests during live availability
	* create a virtual bib record in borrowing agencys local system
	* create item in sierra
	* trigger place request at borrowing agency immediately after request is placed at supplying agency
	* Determine patron type by host LMS
	* save the location code of the chosen item from supplying agency [DCB-171]
	* save the barcode of the chosen item from supplying agency [DCB-171]
	* place hold request in supplying agencys local system [DCB-157]
	* Sort items found in Sierra by location code then call number
	* change default patron type to 210
	* lookup or create virtual patron record for the borrower in the lender system
	* Collect patron's home library code when placing patron request
	* Fetch patron when found
	* Remove patron's agency from patron request
	* Add field to cluster record to denote the selected display bib, add canonical JSONB field to bib record to store a homogenous metadata record we can pass to downstream systems
	* updated so that shelving locations live under locations
	* Rework of the ingest paging for Sierra
	* Provide better error when patron local system cannot be found
	* find or create a patron record when a patron request is placed [DCB-148]
	* pull back the current number of holds in live availability
	* only allow items at specific locations to be chosen during resolution [DCB-145]
	* establish the ability to have more than one transition [DCB-146]
	* Choose first available item when resolving patron request
	* Use real shared index and live availability when resolving patron request
	* split out item data from fake shared index into fake live availability service [DCB-135]
	* Respond with bad request when host LMS code is unknown
	* Report no item availability when sierra responds with no records found error
	* Fail when host LMS cannot be found in config or database
	* barcode and call number added to item
	* add location to item record
	* find host LMS by code from config or database
	* get items by bib id from local system [DCB-127]
	* Locations now know about their owning agency, their type and if they are to be included in lists of pickup locations
	* create end to end live availability with static item response
	* "Demo" environment profile
	* Expose background task delay duration as a configuration value
	* Accept task delay duration in ISO-8601 format
	* Consume HostLMS from both config AND the database. Final commit for DCB-90
	* Add dateCreated and dateModified to patronRequest. N.B. had to remove final keyword from fields in domain class as this prevents the lombok NoArgsConstructor from creating an initial empty object to populate with a builder
	* introduce patron request workflow [DCB-106]
	* Add and document metrics
	* introduce patron request status [DCB-110]
	* Add source and record IDs to BibRecord and set when harvesting records from Sierra
	* added location repository tests
	* resolve patron request when placed [DCB-113]
	* Rejig host lms sierra adapter and implement retries.
	* Make fake shared index always return single item
	* store supplier request to record resolution decision
	* Ability to save a supplier request with a patron request
	* Add ability to set number of records per HostLms page fetch.
	* HostLms/Agencies from config
	* Select first item from (fake) shared index [DCB-95]
	* Agency Model
	* Add manual serializer for page type

### Changes
* [General]
	* **BREAKING** -  Upgrade to micronaut 4.0.5
* [Build]
	* GitHub - Updated actions to remove warnings
	* GitHub - Add token to increase rate-limit
	* Github - Build native too.
* [Chore]
	* Pin junit for now.
	* tidy accessors when working out which workflow model should be in force
	* patron barcode is not propagating to virtual identity, which prevents checkout at supplying org.
	* supporting calls for checkout to virtual patron at supplying agency
	* logging around virtual patron enrichment in workflow context
	* Fill out patron virtual identity when building request workflow context
	* better logging around tracking borrower system events
	* defensive code to check that patron system id is known
	* don't pester supplying agencies for request status if we have already established that the request is missing
	* Correct error in missing item detection
	* Add updateRequestStatus to host lms interaction
	* start to address the issue of multiple supplier requests for a single patron request
	* Add then() in line with https://micronaut-projects.github.io/micronaut-r2dbc/1.0.0.M2/guide/index.html
	* log out sierra bib patch for debugging
	* Testing field grouping
	* Use list instead of array for Sierra serialisation
	* Remove unused background executor
	* Move workflow into its own package.
	* tidy FixedField by moving it up a level to the sierra package
	* Unused dependency.
	* Consolidate migrations
	* Re-comment out noisy log configuration
	* Tidy up sierra client
	* minor refactoring of place request at supplier
	* move setting of requestingIdentity to validate patron step where we already have everything we need in our hand
	* Tweak log message that was formatted incorrect.
	* Adds extract logging to location filtering
	* remove repeated code to add shelving location
	* revert logback file back
	* remove unused method in shared index service
	* change param names in PatronRequest method
	* Reinstate sierra bib fixture changes
	* Sync up testing with new mock approach, additional logging in borrower request service
	* add missing file
	* sort patron requests for display on discovery scaffold
	* comment out .transform(this::handle404AsEmpty)  so as not to disturb...
	* wip for better 404 handling on hold lookup
	* Remove duplcate patron hold dto
	* allow nullables in host lms hold
	* Add Extra location and pickupLocation fields on SierraHold DTO
	* error trapping around hold lookup
	* link tracking service with supplying agency service
	* Build - Do formal and snapshot releases of Tags
	* rename local_system_hold_x to local_request_x in PatronRequest, tracking changes
	* Add Transactional annotation to methods in RecordClusteringService to ensure that reads happen in same transactional context as writes
	* Add some stats logging
	* extra defensive code around location creation
	* logging around shelving location insert
	* revert to pageAllResults over backpressureAwareBibResultGenerator in SierraApiClient. Add initial ingest health indicator.
	* more tidying of author data
	* missing imports and repo methods
	* add missing get/id methods to bib and cluster controller
	* update news
	* Tidy up DB migration
	* Remove explicit column mapping for local item ID in supplier request
	* Add explicit column mapping for item ID in supplier request
	* additional index for paging through cluster records by dateUpdated
	* Consistent indentation in table definitions
	* Change all primary keys to inline statements
	* Change all foreign keys to inline references
	* Move patron related foreign keys to inline in table definition
	* Move host LMS table higher up database script in order to reference later
	* Group patron related SQL statements together
	* Upper case create table statements in schema migration
	* Fix indentation in host LMS tests
	* add missing post handler
	* use a subscription to trigger the state update
	* Better stack trace
	* instrument state storage
	* Make IngestRecord carry HostLms rather than HostLms UUID so we can store the actual object later on. Bump gradle plugin deps for mn
	* Item result set no longer inherits from sierra error
	* Extra logging in LocationController
	* Add /symbols controller and initial GET paths
	* Add locationSymbol domain class, migration, repository, tests (Combined with LocationRepo tests)
	* Use DataAgency for the relationship inside location rather than a raw UUID.
	* Test the pipeling transformer.
	* Remove unnecessary whitespace
	* improve formatting of java doc for delay parameter
	* Allow larger response bodies in HTTP clients
	* fix indentation for created and updated date in patron request
	* Use lombok for consistency.
	* Tweaks and added "Id" annotation to interface.
	* more production log tuning
	* Added a little extra logging info to sierra ingest source
	* Improve JDBC pooling
	* Improve the docker memory usage
	* Remove parallel to remove dependency on fork join.
	* dump stack error here.
	* Re-Instate conventional commit plugin - looks like  a merge error took it out
	* use junit assertions for better feedback in patron request tests
	* re-apply map chaining in patron request service
	* fix: remove doOnNext
	* tidy up
	* add updated code
	* add latest changes
	* remove unused repository from controller
	* move patron request service to request fulfilment package
	* restructure test
	* Tidy config and quick pass over documentation
	* Formatting
	* Bump some dependencies
	* Model rejig and tidy.
	* remove test config dependencies
	* remove host from tests
	* dropped in sierra tests
	* Github Actions - Be specific and use env var for root path
	* Docs - Tweak the output to use relative path.
	* Build - Include build workflow changes.
	* Build - Acutally build the docs now
	* Added publishing of API docs.
	* Build - Remove the coverage gate
	* Build - Change the paths for openapi docs
	* Comment
	* Swapped out to Immutables from RecordBuilder
	* simplified functions
	* labeling functions
	* added missing tests back
	* Build - Add the dep for record builder annotation and format file
	* dev - Added editor config
	* Formatting - Tabs used for indent.
	* Build - Missed workflow change
	* Build - Teaks to workflows.
	* Ensure correct labels
	* Build - Update the secret names
	* Tweaks to the release workflow.
	* Docs - Tweaked documentation
	* Build - Add action for pages deployment
	* Build - Tweak the actions setup.
	* Build - Reinstated the test results publishing with permissions
	* Disable the report publish for now.
	* Reimported from other project to try build fixes.
	* Wrong directory
	* YAML file still broken
	* YAML formatting
	* Actually publish results.
	* Publish reports
	* First pass at project creation
* [Core]
	* extend bib data in cluster response
* [Feature]
	* framework for adding members to agency group in graphql
	* Make a HostLMSClient know about what properties it will accept in it's settings block
	* Use item type mapping when placing a request, allow an administrator to upload a file of item type mappings
	* Parse virtual patron barcodes into an array and pass through to checkout
	* take account of duedate when resolving sierra item status
	* set lending agency item to offsite when checked in to patron home
	* active workflow
	* add HandleBorrowerItemInTransit
	* Add patron hostlms to request workflow context
	* add patron home agency to request context
	* RequestWorkflowContext contains Requesting PatronIdentity
	* Use agency code of the selected pickup location as the pickup location value
	* Implement patron type mapping
	* emit an application event any time a state change is detected on a supplier request
	* propagate hold info up to supplyingLibraryService
	* implement getHold
	* Validate patron with their home library before continuing the request workflow
	* Send sufficient info with a clustered bib so that the UI can retrieve the source records and show them along-side the cluster for testing
	* Add cluster reason to bib record, patron and supplier hold status to patron request and supplier request
	* Array of bib-ids in cluster records
	* add metadata score to bibRecord
	* add selected bib record to cluster record result.
	* Add identifiers to bib records
	* Save pickup locations when running configuration service harvest
	* Create Agencies and Shelving Locations for configured HostLMS systems.
	* Stash intermediate state of ingest service
	* Add process state domain class and related repository / service - in prep for storing hostLMS state
	* Can delay task execution DCB-123
	* trigger patron request workflow asynchronously (DCB-119)
* [Refactor]
	* SHARED static is still a thing.
	* Remove side effect mutation.
	* use bib object for passing of parameters to create bib
	* Consolidate HTTP error response handling in sierra client
	* Move private methods to bottom of the host LMS API client class
	* Return empty publisher when finding patrons
	* Use empty publisher instead of empty result set
	* use interface methods for retrieving bib metadata
	* supplying agency service tests to use fixture instead of mocks
	* Consolidaet duplication in host LMS fixture
	* Remove fake availability and shared index services
	* add localBibId and localItemId to PatronRequest
	* handle null agency on shelving location
	* shelving location creation moved and test names changed
	* reinstate SierraItemsAPIFixture tests code
	* decide requestability for each item
	* Use builders for creating patron requests
	* rework tuples in supplying agency service
	* Set supplier request status during resolution
	* Separate finding from creating patron
	* Split find or create from patron service
	* Rename supplier request item ID to local item ID
	* Move not found mapping to api client
	* clean up after integrating request resolution with shared index
	* Use Host LMS instead of code when determining availability
	* Use zip instead of pair
	* add overload for zero delay task execution
	* move state transition delay parameter to request workflow
	* request fulfilment design
	* Lombok instead of Immutables
	* using record, test uses json
	* added service class for logic
	* audit controller returns bean
	* Store incoming request: citation id, patron id, location code/id
	* POJOs added for request body's
	* Tidy imports.
* [Test]
	* write tests for cluster api [DCB-201]
	* Remove sierra text error (use JSON based errors instead)
	* Use transitions instead of services as starting point for tests
	* Use serialisation for sierra error responses
	* Demonstrate re-authentication after denied request to Sierra
	* Remove sierra LMS client tests
	* Remove body expectations when placing request in Sierra
	* use concrete client in login tests
	* Remove mock based supplying agency tests
	* move resolution tests to integration style
	* Replace patron service mock based tests with integration tests
	* Delete redundant mock style tests for patron request service
	* Add integration tests for finding or creating a patron
	* Consolidate patron identity fixture into patron fixture
	* Convert live availability tests to integration
	* Disable location filtering by default
	* Define host LMS in code
	* adding shelving location in tests
	* Move borrowing agency service tests into separate class
	* Demonstrate live availability API tolerating errors
	* Reduce duplication in sierra api fixtures
	* Limit connection pool size when context rebuilds
	* Include hold count in API tests for live availability
	* Use Mockito Extension
	* Include parameter binding for SQL statements in logs
	* Delete host LMS after tests
	* Move deleting patron identities to fixture
	* Use fixture in host LMS tests
	* Delete all patron identities prior to host LMS tests
	* Increase timeout for patron request to be resolved in tests
	* Use single connection pool for Postgres in tests
	* Demonstrate live availability fails when host LMS cannot be found
	* Add tests for Sierra LMS Client
	* checking audit controller gets a patron request by id
	* reinstate test for propagating error for saving request from service
	* save patron request to db
	* include HTTP request and response bodies in test logs
	* placePatronRequestValidation
* [Tests]
	* added sierra api mocks
* [WIP]
	* DOES NOT COMPILE. Arranging skeletal shape.
	* Need to creat AGENCY1 in tests
	* Latest changes...
	* use a generator so that we get some backpressure in the sierra adapter
	* post handler for /symbols to allow creation and update of LocationSymbol entries
* [Wip]
	* Committing for sharing
	* Committing the latest changes to the transition workflow
	* Committing for backup purposes.
	* Bib api
	* Reordered import.
	* Latest round of changes
	* Increased read timeout of clients to 30 seconds default
	* Pipeline methods with database interaction sorted.
	* Very early pipeline implementation.
	* Retry output and added Annotation for coverage excl
	* Fixes AppConfig binding.
	* Ensure AppTasks can be disabled.
	* FIx date sending to GOKb
	* Kill switch for scheduler and fix for "null" string param
	* Change signature of interface method
	* Start of the test for the GOKb API
	* Test and Build tweaks
	* Rejig and begin test extension
	* Latest changes for postgres persistence
	* Latest code for backup
	* Committing latest work in progress.

### Fixes
* [Build]
	* API docs published
* [Docs]
	* Tweak redoc lib path
* [General]
	* Conversion of author
	* Should be orElse not orElseGet
	* Test phase in Gradle needs lombok defining explicitly.
	* Map-derived Bib record properties were not persisting.
	* Depth limit in JSON
	* Expand matchkey selection to include type (tolerates nulls)
	* Ensure identifiers are complete
	* add label to fixed field in SierraLmsClient.createBib
	* ensure correct list object to assert in getClusterRecords test
	* PatronRequestApiTests pass
	* Use map
	* converting list to string exception
	* Use record status constant for setting core bibliographic metadata
	* Fix missing import for fixed fields in bib patch
	* add mocks for PlacePatronRequestAtSupplyingAgencyTests tests
	* Prevent java.lang.Class from being serialized in OpenAPI schema
	* hostlms, agency and shelving location deleted afterAll in tests
	* ensure resulting Goldrush key text is lower-cased.
	* change hold count in mock
	* handle errors when using HTTP client exchange rather than retrieve
	* Reinstate the metadata processing.
	* Connection no longer dropped after retry.
	* Improve speed
	* Retryable clustering
	* test uses different unique id
	* Use micronaut Nullable annotation for Sierra serialisation classes
	* Fetch patron after saving to avoid circular references
	* add the hold count in response view
	* Incorrect casing of Sierra Lms client test
	* secure the hostlms endpoint
	* recreate unadded files for live availability work
	* Interleave sources.
	* Get lombok to carry over micronaut nullability to contructors
	* Field casing for properties
	* Ensure validation inline with the column defs in schema
	* MN resolution workaround
	* Improved paging
	* Ambiguous Transaction manager selection.
	* Mark none transactional exclusions on repository
	* Add type-hints for native image
	* Precompile regex patterns.
	* Truncate instant before attempting conversion to LocalDateTime
	* config comes through as a string. Ensure we handle that properly
	* changed uri of test by mistake, switch back
	* inject patron request repo
	* bib cluster id now uses uuid
	* bib_cluster_id change to varchar
	* return exception when saving patron request fails
	* patron agency code set, was setting patron id
	* test contained uneeded code
	* API docs path changed
	* Build and docs
	* Make the native compile work again.
	* Syntax...
	* ISSN mixed up with ISBN
	* added streams
	* wrong field used
	* factored out Identifier superclass / used different structure for author
	* used another method to get substring / added test for import record

### References
* [Fixes]
	* Issue #DCB-194
	* Issue #DCB-226
* [Provides]
	* Issue #DCB-90