# Changelog

## Version 4.0.2

### Changes
* [Chore]
	* logging adjustments to BorrowingAgencyService
	* don't pretty print json logs
* [Feature]
	* NONCIRC items are not requestable

## Version 4.0.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add missing dependency for json log formatting

### Fixes
* [General]
	* for apparent behaviour of polaris. When passed an itemType in a string, polaris seems to take only the first character of the string as a part of the integer. Changed parameter type to Integer and parse it with parseInt before setting

## Version 4.0.0

### Additions
* [General]
	* **BREAKING** -  Rename consortial FOLIO host LMS client DCB-774
	* **BREAKING** -  Expect FOLIO base-url without /oai path DCB-774

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* more polaris logging
	* Switch logging to json, better message for failed to place supplier hold error
	* Add logging around URI resolution during FOLIO ingest DCB-774

## Version 3.0.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Better error logging around create item in Polaris
	* Add @body annotation to lookup api call
	* update post annotation on user lookup
	* refactor validate by pin
	* more logging around user auth
	* rest annotations on new lookup methof
	* Add rest annotation
	* Work on workflow doc
* [Core]
	* defer creation of invalid response when doing patron auth
* [Feature]
	* lookup user by username
	* graphql - Add ReferenceValue and NumericRangeMapping queries
	* Add BibRecord to PatronRequest GraphQL [DCB-748]
	* graphql - Add virtual patron identity to supplier request

### Fixes
* [Graphql]
	* Reciprocal mapping syntax error
* [General]
	* Update PatronRequest GraphQL type [DCB-487]
	* Correct GraphQLFactory clusterRecord naming [DCB-748]
	* When authenticating uniqueid+pin for sierra, use the standard auth method

## Version 3.0.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Replace spaces with tabs in host LMS service
	* Add dependency on Sierra specific mapper from shared item mapper
	* Remove unused status mapping method when mapping items
* [Feature]
	* graphql - Location data fetchers
* [Refactor]
	* Map status using separate code and due date
	* Extract method for mapping item status and due date
	* Move unknown host LMS exception to top level
	* Rename item mappers to refer to host LMS type
	* Remove overload for getting items by local bib ID
	* Inline existing method into get by bib record overload
	* Replace usages of get items by bib ID
	* Introduce method for getting items from host LMS by bib record
	* Move item status fallback definitions to specific mappers
	* Move remaining item mapping logic to Polaris package
	* Use Sierra specific item mapper directly
	* Move map Sierra result to item to Sierra specific mapper
	* Move local item type method to Sierra specific item mapper
	* Move parsed due date method to Sierra specific item mapper
	* Add location to agency mapper dependency to Sierra specific item mapper
	* Add item type mapper dependency to Sierra specific item mapper
	* Add item status mapper dependency to Sierra specific item mapper
	* Move method for enriching item with an item type to mapper
	* Move method for enriching item with an agency to service
	* Extract method for mapping an item's location to an agency
	* Extract method for setting agency information on an item
	* Use zip to enrich item with agency code and name
	* Use property chaining to set item agency code and name
	* Rename item agency name field
	* Rename enrich item with agency based upon location method
* [Test]
	* Disable ingest for all config defined host LMS
	* Remove unecessary borrowing agency mocks in patron request API tests
	* Extract constant for the barcode of supplied item in patron request API tests
	* Extract constant for the location of supplied item in patron request API tests
	* Use code to define items in Sierra during patron request API tests
	* Remove unecessary mock response in patron request API tests
	* Use constant for host LMS code where possible in patron request API tests
	* Remove unused host LMS in patron API tests
	* Move mapping to Sierra items response to fixture
	* Use simpler Sierra item definition when defining mock responses
	* Define items from Sierra in code rather than resource
	* Check that Polaris items are not deleted
	* Check that Sierra items are not deleted
	* Check no hold count is provided for Polaris item
	* Check hold count for Sierra items
	* Replace spaces with tabs in three item Sierra response
	* Remove nested class in item status mapper tests
	* Move Polaris item status mapping tests to separate class
	* Move Sierra item status mapping tests to separate class
	* Move map sierra item statsus method to nested class
	* Remove commented out method in reference value mapping fixture
	* Define one location to agency mapping during Sierra host LMS client item test
	* Define agency during Sierra host LMS client item test
	* Delete reference value mappings before each Sierra host LMS client items test
	* Define item type mapping during Sierra host LMS client item test
	* Delete agencies before each Sierra host LMS client items test
	* Move Polaris host LMS client tests to same package as production

### Fixes
* [General]
	* Defensive code in data fetchers - null safety
	* Add further validation of uploaded mappings [DCB-508]
	* Tolerate Sierra item's without a status
	* Warn when ingest configuration is invalid for a host LMS
	* Do not change Polaris item status based upon presence of due date

## Version 3.0.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* move mapping around in ReferenceValueMappingService to provide more meaningful logging
* [Feature]
	* Disable auto location configuration for sierra. data too messy to rely upon, locations don't have agencies attached

## Version 3.0.0

### Additions
* [General]
	* **BREAKING** -  Add new ingest source type for host LMS configuration DCB-739

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* more logging
	* defensive code rejecting locations which specify an unknown agency
	* graphql - implement more sort defaults
	* Allow polaris to use BASIC/BARCODE+PIN as well as other variants as an auth profile
	* Include source system ID in bib
	* Tolerate no client type for host LMS in config DCB-739
	* Tolerate no ingest source type for host LMS in config DCB-739
	* Remove redundant unchecked conversion of type DCB-739
	* Log error when class cannot be found for name DCB-739
	* Raise specific error when host LMS ingest source class is not an ingest source DCB-739
	* Raise specific error when host LMS client class is not a host LMS client DCB-739
	* Raise specific error when host LMS ingest source class cannot be found DCB-739
	* Raise specific error when host LMS client class cannot be found DCB-739
	* Define invalid host LMS configuration exception DCB-739
	* Make FOLIO LMS client a bean DCB-739
	* Configure ingest source class for host LMS from config DCB-739
	* Add ingest source class field to data host LMS DCB-739
	* Add ingest source class column to host LMS table DCB-739
* [Feature]
	* Shipping and Routing Labels
	* Administrative metatdata for Location imports
	* Add order clause to graphql schema, implement for agencies and default to name
	* Check for existing mapping data on import [DCB-603]
* [Refactor]
	* Remove unused dependencies in borrowing agency service
	* Introduce specific exception when cannot find selected bib for cluster record
	* Consolidate finding cluster record in shared index service
	* Move find selected bib method to shared index service
	* Move get cluster record method to shared index service
	* Move get selected bib to shared index service
	* Add shared index service dependency to borrowing agency service
	* Extract method for finding selected bib
	* Use switch if empty instead of specific check for no bibs for cluster record
	* Rename bibs field in clustered bib
	* Remove bibs from clustered bib
	* Use bib records during live availability
	* Include bib records in clustered bib
	* Remove finding host LMS from shared index service
	* Remove host LMS field from bib
	* Extract method for getting config from host LMS client
	* Extract method for getting host LMS code from host LMS client
	* Extract method for getting items using host LMS client
	* Get host LMS client by code during patron resolution preflight check
	* Extract method for getting host LMS client by ID
	* Find host LMS inside live availability service
	* Move null check to common get type method DCB-739
	* Introduce parameter for name getting class from a name DCB-739
	* Introduce generic parameter for type of class to get from name DCB-739
	* Extract method for getting type from class name DCB-739
	* Rename get client type method for host LMS DCB-739
	* Use ternary operator for falling back to client class for ingest source DCB-739
	* Introduce get ingest source type method on host LMS interface DCB-739
	* Config host LMS no longer implements host LMS interface DCB-739
* [Test]
	* Check that items from Sierra have a canonical item type
	* Check that items from Sierra have no agency description
	* Check that items from Sierra have no agency code
	* Check whether items from Sierra are suppressed
	* Check local item type code for items from Sierra
	* Check host LMS code on items from Sierra
	* Use property matchers for cluster record subjects
	* Use fixture to delete agencies in host LMS fixture
	* Stop deleting records after place request at borrowing agency tests
	* Stop deleting records after patron request API tests
	* Stop creating shelving location in patron request API tests
	* Remove unused constructor in Sierra mock server responses
	* Use test resource loader during cluster records API tests
	* Use test resource loader during configuration import tests
	* Use provider for test resources in Polaris host LMS client tests
	* Use provider for Sierra patrons API fixture
	* Use provider for Sierra bibs API fixture
	* Use provider for Sierra API items fixture
	* Use Provider for Sierra API pickup locations fixture
	* Use provider for Sierra API login fixture
	* Use test resource provider in Sierra API login fixture
	* Stop returning created Polaris host LMS
	* Demonstrate placing request at borrowing agency fails when selected bib cannot be found
	* Introduce parameter for selected bib for cluster record
	* Demonstrate placing request at borrowing agency fails when cluster record cannot be found
	* Weaken type constraints on bib matchers
	* Demonstrate failure when host LMS has invalid ingest source class DCB-739
	* Demonstrate failure when host LMS has invalid client class DCB-739
	* Demonstrate failure when host LMS ingest source class is unknown DCB-739
	* Demonstrate failure when host LMS client class is unknown DCB-739
	* Constrain client and ingest source types in fixture DCB-739
	* Define ingest source class explicitly for Polaris host LMS DCB-739
	* Introduce parameter for ingest source class when creating host LMS DCB-739

### Fixes
* [General]
	* Amend annotations on ReferenceValueMapping to fix failing tests [DCB-603]

## Version 2.6.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Remove unused factory method for availability report
	* Reformat Polaris host LMS client
	* Reformat Sierra host LMS client
	* Remove use of record number when getting recently placed hold
* [Refactor]
	* Introduce guard clause for null item type
	* Use location code from item when mapping to agency
	* Extract method for parsing Sierra due date
	* Return item status instead of code when mapping local item status DCB-479
	* Move pickup location to agency mapping to reference value mapping service
	* Use reference mapping service in location to agency mapping service
	* Use location to agency mapping service in dummy host LMS
	* Extract service for mapping a location to an agency
	* error response handling when placing a hold in the polaris client [DCB-746]
	* Rename item local ID field
	* Hide decision between title and item holds within Sierra host LMS client
	* Remove unused record number from place hold request parameters
	* Remove unused record type from place hold request parameters
* [Test]
	* Use additional item matchers pulled forward from DCB-479
	* Introduce parameter for type when defining FOLIO host LMS
	* Demonstrate Polaris fallback mapping DCB-479
	* Remove unecessary context rebuilds
	* Use item barcode matcher in live availability service tests
	* Use item matchers for items from Polaris
	* Add missing due date checks for items from Sierra
	* Move item matchers to separate class
	* Extract methods for item matchers
	* Use has property matchers for checking items from Sierra
	* Demonstrate getting items using the Sierra host LMS client

### Fixes
* [General]
	* Use lowercase token_filter by default

## Version 2.6.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add note on HTTP_HOSTS env vars
	* Add missing test resource loader file
	* Remove check in Polaris host LMS client for item level requests only
* [Refactor]
	* use logon branch id as fallback for patron branch id [DCB-746]
	* Move Polaris hold request parameters to separate class
	* Remove unecessary setters in Polaris hold request parameters
	* Inline place item level request method in Polaris host LMS client
	* Move item vs title request logic to Sierra host LMS clent only
	* Always use local item ID when placing request in Polaris
	* Rename local patron ID parameter when placing hold request
	* Remove unused record type from Polaris hold request parameters
	* Pass local item ID when placing hold request
	* Pass local bib ID when placing hold request
	* Use builder for placing patron hold request to Sierra
	* Introduce parameter object for placing a hold request
	* patron branch id when creating a patron in polaris [DCB-746]
* [Test]
	* Use source record ID when getting items from Polaris
	* Use test resource loader in Polaris LMS client tests
	* Move test resource loader to test package
	* Move resource loading to separate class

### Fixes
* [General]
	* Refactor the background index job.
	* polaris item status fallback mapping

## Version 2.6.0

### Additions
* [General]
	* checkOutItemToPatron [DCB-743]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Report server error when host LMS cannot be found for bib during live availability
	* Remove redundant check for host LMS not found during live availability
	* Throw exception inline when finding resolution stragegy
	* Make item resolver finl in resolution service
	* Return bad request response when no cluster record found for live availability
	* Use switch when empty to report error when cluster record cannot be found
	* Replace spaces with tabs in resolution service
	* Remove explicit http status code from live availability controller
	* Remove unecessary use of tuple in dummy host LMS client
	* Remove use of tuple within supplying agency service
	* Replace spaces with tabs in supplier request
	* no security needed on list location
* [Refactor]
	* Remove cluster record overload in live availability service
	* Get item availability for cluster record ID during resolution
	* Throw explicit exception when no bibs found for cluster record during live availability
	* Move method for getting available items by ID to service
	* Move method for getting availability report to service
	* Extract method in controller for getting item availability by clustered bib ID
	* Add shared index service dependency to live availability service
	* Remove use of tuple when placing hold request in Sierra
	* Remove use of tuple when placing hold request in Polaris
	* Remove use of tuple when getting hold request from Polaris
	* Rename non-tuple based place hold request method
	* Remove tuple based place hold request method
	* Inline any use of the tuple method
	* Use non-tuple method in supplying agency service
	* Use non-tuple method in borrowing agency service
	* Introduce place hold request method that returns object instead of tuple
* [Test]
	* Move live availability fails when host LMS cannot be found test
	* Demonstrate live availability API fails when cluster record not found
	* Remove redundant test for cluster record with no bibs
	* Move test for no cluster record can be found to live availability service tests
	* Use property matchers for checking order of items in live availability report
	* Remove unecessary assertions in live availability tests
	* Stop deleting host LMS before each live availability test
	* Remove after each from shared index service tests
	* Delete all host LMS before live availability service tests
	* Use property matchers when checking availability report
	* Extract client from live availability API tests
	* Demonstrate live availability API when cluster record has no bibs
	* Use cluster record ID overload in service tests
	* Use throwable matchers in shared index service tests
	* Move bib matchers to separate class
	* Move has ID matcher to separate class
	* Use matcher for clustered bib ID
	* Inline constant resources path in Polaris host LMS client tests
	* Stop reloading context for Polaris host LMS client tests
	* Remove unused dependencies from Polaris host LMS client tests
	* Use sneaky throws when getting resources in Polaris host LMS client tests
	* Move local request matchers to separate class

### Fixes
* [Graphql]
	* correct PatronRequest type mappings in graphql
* [General]
	* Try with a 3second delay
	* Reinstate the delay
	* Ensure endpoint is protected
	* Do not overwhelm the indexer with 2 subs
	* Null handling.

## Version 2.5.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* working on item patch
	* try adding an empty string to messages when updating item status
* [Feature]
	* add RequesterNote to patron request

### Fixes
* [Graphql]
	* Misspelled data fetcher for patron request joins
* [General]
	* Improve badResumption token handling

## Version 2.5.0

### Additions
* [General]
	* patron cancels polaris request [DCB-529]
	* implement circulation polling and transition for polaris requests [DCB-528]
	* place request in borrowing library on a polaris system [DCB-485]
	* Added authentication for Search Index.

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Remove creation of grant during startup DCB-739
	* Move status codes defined only in tests to startup DCB-739
	* Remove unused methods on host LMS repository DCB-739
	* Replace spaces with tabs in host LMS service DCB-739
	* Use builder when creating status codes during startup DCB-739
	* Remove unecessary return statement when boostrapping codes DCB-739
	* Remove unused dependencies from startup event listener DCB-739
	* Replace spaces with tabs in startup event listener DCB-739
	* Remove non-static access to data host LMS builder DCB-739
	* Remove redundant pubic modifiers on host LMS interface DCB-739
	* Docs - Add SI docs
	* Shout when cert verification is disabled
	* Enable generation of jacoco csv file
	* uncomment metrics implementation
	* Replace spaces with tabs in data host LMS class DCB-739
* [Feature]
	* Clear out messages when updating item status
	* add messaging to cancellation handler
	* graphql - Add patronRequest to supplierRequest type
* [Refactor]
	* Use lombok to generate getters for config host LMS properties DCB-739
	* Use for each to save all config host LMS DCB-739
	* Move save or update of host LMS to repository DCB-739
	* Extract method for saving config host LMS DCB-739
	* Accept list of config host LMS during startup DCB-739
	* Remove unecessary introspection from config host LMS DCB-739
	* Make get all host LMS private DCB-739
	* Accept only config host LMS during startup DCB-739
	* Move method for getting ingest source by code to host LMS service DCB-739
* [Test]
	* remove DCB test annotation from startup event listener tests DCB-739
	* Use fixture to create host LMS during patron identity repository tests DCB-739
	* Use builder in host LMS tests DCB-739
	* Demonstrate status codes created at startup DCB-739
	* Demonstrate grant created at startup DCB-739
	* Use builder during host LMS repository tests DCB-739
	* Delete host LMS before each  host LMS repository test DCB-739
	* Remove redundant http client field in host LMS repository test class DCB-739
	* Replace spaces with tabs in host LMS repository tests DCB-739
	* Check format of host LMS ID from config DCB-739
	* Extract method for host LMS ID matcher DCB-739
	* Demonstrate loading host LMS from config on startup DCB-739
	* Demonstrate getting a ingest source from a FOLIO host LMS DCB-739
	* Move host LMS matchers to separate class DCB-739
	* Move message matcher to throwable matchers class DCB-739
	* Define Polaris host LMS before each test in nested class DCB-739
	* Move Polaris host LMS tests to nested class DCB-739
	* Define Sierra host LMS before each test in nested class DCB-739
	* Move Sierra host LMS in database tests to nested class DCB-739
	* Creation of Polaris ingest source based upon host LMS DCB-739
	* Creation of Polaris client based upon host LMS DCB-739
	* Rename method for defining Polaris host LMS DCB-739
	* Make code first parameter when defining Sierra host LMS DCB-739
	* Rename method for creating low level Sierra client DCB-739
	* Move method for getting ingest source in tests to fixture DCB-739
	* Extract method for getting an ingest source by host LMS code DCB-739
	* Creation of Sierra ingest source based upon host LMS DCB-739
	* Creation of Sierra client based upon host LMS DCB-739
	* Use has property matcher during host LMS tests DCB-739
	* Group negative host LMS tests together DCB-739
	* Use fixture to save host LMS DCB-739
	* Use single value helper method when finding host LMS by code DCB-739
	* Include LMS in names of existing host LMS tests DCB-739

### Fixes
* [Tests]
	* Require config prefix
* [General]
	* add patron to WorkFlowContext

## Version 2.4.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Use correct local_request_status when seeking patron requests on the borrowing side to track

## Version 2.4.0

### Additions
* [General]
	* Added reindex job to admin controller.
	* Create index for OS or ES on startup.

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Create a new transaction for each of the tracking events
	* add logger when subscribe complete for tracking calls
	* Indexing tweaks
	* Case insensitivity
* [Test]
	* Remove redundant deletion of bib records separately to cluster records DCB-739
	* Delete all cluster records before each Polaris ingest test DCB-739
	* Delete all cluster records before each Sierra ingest test DCB-739
	* Use empty set of bibs when creating cluster record DCB-739
	* Rename delete all bib records method DCB-739
	* Rename delete all cluster records method DCB-739
	* Remove redundant ID parameter when creating a Sierra host LMS DCB-739
	* Use ID from returned host LMS instead of defining it first DCB-739
	* Use builder when defining host LMS DCB-739
	* Use create host LMS method when creating Polaris host LMS DCB-739
	* Use create host LMS method when creating Sierra host LMS DCB-739
	* Rename Sierra specific create host LMS method DCB-739
	* Introduce parameters for client class and config when creating host LMS DCB-739
	* Delete all cluster records before each cluster record tests DCB-739
	* Delete all bib records before deleting all cluster records DCB-739
	* Use sneaky throws to avoid checked IO exception in cluster record tests DCB-739
	* Remove unused log from cluster record tests DCB-739

## Version 2.3.0

### Additions
* [General]
	* Configurable number of retry attempts for getting patron holds from Sierra
	* Background job for indexing items.

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Change page size to integer property DCB-739
	* Change page size Sierra setting to optional DCB-739
	* Change type of get holds retry attempts setting
	* Reformat host LMS property definition DCB-479
	* Reformat Sierra client settings
	* Tweaks to keep the Java compiler happy.
* [Feature]
	* Added env code and env description to info endpoint for display
* [Refactor]
	* Move method for getting integer property from config to class DCB-739
	* Introduce class for integer property definition DCB-739
	* Extract parameter for property definition when getting optional integer config value DCB-739
	* Use property definition for getting integer value from config DCB-739
	* Extract field for page size property definition DCB-739
	* Extract method for getting integer property value DCB-739
	* Move get holds retry attempts property to field DCB-739
	* Convert property definition to value object DCB-739
	* Extract method for defining an integer property definition DCB-739
	* Extract method for defining a boolean property definition DCB-739
	* Extract method for defining a string property definition DCB-739
	* Extract method for defining a URL property definition DCB-739
* [Test]
	* Only retry getting holds from Sierra once in most tests

## Version 2.2.2

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Add missing AVAILABLE status to updateItem impletation in sierra adapter

## Version 2.2.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* corrected static item status code mapping

## Version 2.2.0

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 2.1.5

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Error getting deleted flag for items

## Version 2.1.4

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [Workflow]
	* More Gracefully handle deletion of a virtual item

## Version 2.1.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* More tuning for logging
	* refine logging
	* turn down logging
	* Use toState() rather than strings when updating local reflections of remote states

### Fixes
* [Graphql]
	* Add PatronRequest to PatronRequestAudit object

## Version 2.1.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Extend Item mappings for sierra

## Version 2.1.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [Flyway]
	* Add out-of-order:true to allow branches to merge with migrations out of sequence
* [General]
	* set PR state to READY_FOR_PICKUP when appropriate

## Version 2.1.0

### Additions
* [General]
	* Shared index service

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Refactor supplier checkout so we can set the item state before attempting the checkout
	* graphql - Add default sort order to hostlmss, patronrequest and supplierrequest
	* rename patronIdentity upsert method to clarify what it's doing
	* improve error trapping in sierra checkOut
	* more warn logging in unexpected conversion scenarios
	* warn when we try and convert an item record which is missing key properties
* [Feature]
	* Set a supplier item status to RECEIVED before attempting to check it out
	* graphql - Add audit query to graphql

## Version 2.0.5

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Split out parsing of patron barcode, add comment explaining what the string manipulation is about
	* Remove unused code from FOLIO LMS client DCB-479
	* Use annotation for log in host LMS fixture DCB-479
	* Remove get all bib data method from host LMS client interface
* [Refactor]
	* Rename save host LMS method in fixture DCB-479
	* Reduce access to methods in host LMS fixture DCB-479

### Fixes
* [General]
	* store barcode when creating or updating patron identity records relating to virtual patron records in supplying systems

## Version 2.0.4

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add some more unique words to test data generators

### Fixes
* [General]
	* Don't trim patron barcode when attempting to check out

## Version 2.0.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Feature]
	* When creating a (vpatron) in sierra, allow the caller to specify an expiryDate or default it to 10 years from now

## Version 2.0.2

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* align tests with reverted patron id change

## Version 2.0.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* revert DCB: change as the likely culprit for failure to place hold at lending site

## Version 2.0.0

### Additions
* [General]
	* **BREAKING** -  Disallow unknown hold policy for Sierra host LMS when placing request at supplying agency DCB-613
	* **BREAKING** -  Disallow unknown hold policy for Sierra host LMS during placing request at borrowing agency DCB-613
* [General]
	* Optionally disable patron resolution preflight check DCB-612
	* Optionally disable pickup location to agency preflight check DCB-612
	* Optionally disable pickup location preflight check DCB-612
	* Default hold policy for Sierra client to item DCB-613

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Reformat production of invalid hold policy error DCB-613
	* Rename variables for record number and type when placing request at supplying agency DCB-613
	* Add method for item level request policy to host LMS client interface DCB-613
* [Feature]
	* Create endpoint to import uploaded mappings [DCB-527]
* [Refactor]
	* add DCB prefix to unique id generation
	* Use optional when checking hold policy DCB-613
	* Extract method for checking hold policy in Sierra host LMS client DCB-613
	* Use common policy for title level requests when placing at supplying agency DCB-613
	* Rename method for checking title level request policy when placing requests DCB-613
	* Move implementation of title hold policy to host LMS client implementations DCB-613
	* Move title hold policy decision to host LMS client interface DCB-613
	* Move getting hold policy from config to extracted method DCB-613
	* Extract method for whether policy is to place title hold DCB-613
* [Test]
	* Remove separate tests for disabling individual preflight checks DCB-612
	* Remove redundant preparation in preflight checks service tests DCB-612
	* Only include always failing check during preflight checks service tests DCB-612
	* Define test only preflight check DCB-612
	* Check all indidual checks are enabled DCB-612
	* Extract method for creating Sierra host LMS client with client config DCB-613
	* Use host LMS code constants for defining mappings when placing requests at borrowing agency DCB-613
	* statically import request statuses when placing request at borrowing agency DCB-613
	* Use host LMS code constant for defining mappings when placing requests at supplying agency DCB-613

### Fixes
* [General]
	* Switch lastImported to use Instant [DCB-527]

## Version 1.11.0

### Additions
* [General]
	* Choose between placing title or item level request in borrowing agency DCB-613
	* Optionally disable preflight all checks DCB-612
	* Check requesting patron's local patron type is mapped to canonical DCB-530
	* Check requesting patron is recognised in host LMS DCB-531
	* Check requesting patron's host LMS is recognised DCB-531

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Reformat reactive chain when placing request at borrowing agency DCB-613
	* Replace spaces with tabs in place request at borrowing agency service DCB-613
	* Remove unused dependencies from place patron request at borrowing agency service DCB-613
	* Change case of host LMS in preflight check failure description DCB-531
	* Label the pickup location in agency mapping preflight check failure messages DCB-519
	* Add debug logging for finding pickup location to agency mappings DCB-519
	* Map failure to find patron mapping to failed check DCB-530
	* Replace spaces with tabs in numeric range mapping DCB-530
	* Replace spaces with tabs in supplying agency service
	* Remove unused dependencies from supplying agency service
	* Replace spaces with tabs in patron request API tests
	* Inline always null local barcode in patron service
	* Remove unused method from patron service
	* Add resolve patron preflight check class DCB-531
* [Feature]
	* graphql - Convert hostLms props, PatronRequestAudiy props and canonical record to JSON scalar type
	* enable graphql scalar type and use it for raw record
	* sierra - Add Fixed Fields when pulling bibs from Sierra in anticipation of involving 031-BCODE3 in the decision to suppress or not. There is a do not submit to central flag we need to observe
* [Refactor]
	* Return virtual bib ID in tuple when creating virtual bib in borrowing host LMS DCB-613
	* Extract variables for record type and number when placing request at borrowing agency DCB-613
	* Defer finding alternative pickup location to agency mappings during preflight checks DCB-519
	* Carry local patron information in unable to convert type exception DCB-530
	* Provide local ID when attempting to map local patron type DCB-530
	* Return specific error when attempting to map non-numeric local patron type DCB-530
	* Rename method in patron type mapper to refer to patron type DCB-530
	* Return specific error when no patron type mapping could be found DCB-530
	* Extract method for getting requesting patron's local ID DCB-530
	* Include host LMS service dependency for resolve patron preflight check DCB-531
	* Return specific error when patron cannot be found in Sierra host LMS DCB-531
	* Extract method for creating a patron not found error in Sierra client DCB-531
* [Test]
	* test: Expect virtual item ID as record number when placing borrowing Sierra hold DCB-613
	* Replace spaces with tabs in place request at borrowing agency tests DCB-613
	* Expect local bib ID as record number when placing supplying Sierra hold DCB-613
	* Introduce parameter for expected record number when placing Sierra hold DCB-613
	* Place title level requests at supplying agency DCB-613
	* Define local bib ID when placing Sierra requests DCB-613
	* Introduce parameter for Sierra host LMS client hold policy DCB-613
	* Configure Sierra host LMS to use item requests DCB-613
	* Introduce parameter for expected record type for Sierra patron request DCB-613
	* Expect item record type when placing requests in Sierra DCB-613
	* Delete all check related state prior to preflight checks service tests DCB-612
	* Add preflight checks service test class DCB-612
	* Demonstrate preflight check failure when no patron type mapping is defined DCB-530
	* Delete mappings before each resolve patron preflight check test DCB-530
	* Define patron type mapping before attempting to successfully resolve patron DCB-531
	* Delete all reference value mappings before attempting to resolve patron DCB-531
	* Define successful response for mock Sierra before resolving patron DCB-531
	* Define credentials for mock Sierra before resolving patron DCB-531
	* Define a host LMS before resolving a patron DCB-531
	* Delete all numeric range mappings during tests DCB-531
	* Rename reference value mapping repository field in fixture DCB-531
	* Add example for failed patron type mapping when validating patron DCB-531
	* Define patron type mappings in unsuccesful place request API tests DCB-531
	* Define patron type mappings in unresolvable request API test DCB-531

### Fixes
* [General]
	* Field for raw source record is json not rawSource

## Version 1.10.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* db - Add missing unique constraint to host lms, date fields for agency, protocol fields for paronRequest and supplierRequest
	* Improve logging on item fetch failure

### Fixes
* [Graphql]
	* nested fetchers should use findBy and not findAllBy

## Version 1.10.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* clean up logging
* [Test]
	* Check for audit entry when patron validation fails when host LMS responds with an error DCB-531
	* Move audit assertion methods to bottom of validate patron tests DCB-531
	* Move audit entry matchers to separate class DCB-531
	* Extract methods for audit entry matchers DCB-531
	* Use matchers for checking audit entries when validating a patron DCB-531
	* Import patron request statuses in validate patron tests DCB-531
	* Move local patron type matcher to separate class DCB-531
	* Use matcher for checking validated patron local type DCB-531
	* Make patron requests fixture a singletion DCB-531
	* Consolidate use of delete all patron requests methods DCB-531
	* Replace delete all audit entries with delete all patron requests DCB-531
	* Introduce delete all method in patron requests fixture DCB-531
	* Use query all and delete by ID when deleting audit entries DCB-531
	* Rename delete all audit entries method DCB-531
	* Group delete methods together in patron request fixture DCB-531
	* Use only audit entry method in other tests DCB-531
	* Check for single audit entry DCB-531
	* Use publisher to list conversion instead of flux when fetching audit entry DCB-531
	* Extract method for finding only audit entry DCB-531
	* Replace spaces with tabs in validate patron tests DCB-531

### Fixes
* [Polaris]
	* Return Mono.empty rather than null for methods not yet implemented

## Version 1.10.0

### Additions
* [General]
	* Report failed preflight checks in event log DCB-532

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* scripts - fix graphql_hostlms script - hostlms doesn't have dates
	* graphql - Add created and modified dates to patron request
* [Feature]
	* graphql - Add PatronRequestAudit into graphql model and attach to PatronRequest object
	* graphql - Add supplierRequests to patronRequest
* [Refactor]
	* Define event as a value object DCB-532
	* Extract method for creating an event from a failed check result DCB-532
	* Define event type as a enum DCB-532
	* Introduce method for reporting failed checks in the event log DCB-532
	* Add event log repository dependency to preflight checks service DCB-532
* [Test]
	* Extract method for checking failed check event in log DCB-532
	* Check that successfully placed patron request generates no events in the log DCB-532
	* Demonstrate failed preflight check for mapping only DCB-532
	* Delete all event log entries before each patron request API test DCB-532

### Fixes
* [Mappings]
	* Expunge residual ShelvingLocation references - we're now just on a flat list of Locations

## Version 1.9.0

### Additions
* [General]
	* Find location by ID when code is a UUID during agency mapping preflight check DCB-519
	* Find location by ID when code is a UUID during preflight pickup location check DCB-519
	* Find agency mapping in requestor host LMS context during preflight check DCB-519
	* Find agency mapping in explicit pickup location context during preflight check DCB-519
	* Fail preflight checks when pickup location is mapped to an unrecognised agency DCB-519
	* Fail preflight checks when pickup location is not mapped to an agency DCB-519
	* Fail preflight checks when pickup location is not recognised DCB-519

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Do not attempt to find pickup location by ID when code is not a UUID DCB-519
	* Replace spaces with tabs in reference value mapping service DCB-519
	* Do not attempt to find mapping when pickup location context is null or empty DCB-519
	* Replace spaces with tabs in reference value mapping fixture DCB-519
	* Add agency repository dependency to agency mapping preflight check DCB-519
	* Introduce pickup location to agency mapping check DCB-519
	* Add dependency on location repository to preflight checks service DCB-519
	* Consistent debug log messages in patron request service DCB-519
	* Use annotation for patron request service log DCB-519
	* Hard code failure for unknown pickup location code DCB-519
	* Extract shelving location lookup for more fine grained exception reporting
	* Decompose create bib to throw more granualr exceptions
* [Refactor]
	* Introduce method for possibly mapping a location ID to a code DCB-519
	* Extract method for finding agency mapping by code and context or host LMS code DCB-519
	* Add location service dependency to agency mapping preflight check DCB-519
	* Move find by code method to agency service DCB-519
	* Introduce agency service dependency for agency mapping preflight check DCB-519
	* Move find by ID string overload to location service DCB-519
	* Move find by code method to location service DCB-519
	* Move find by ID method to location service DCB-519
	* Add location service dependency for pickup location preflight check DCB-519
	* Introduce method for finding a location by ID during pickup location preflight check DCB-519
	* Extract method for finding pickup location by Code DCB-519
	* Move empty or null from context check to reference value mapping service DCB-519
	* Reduce duplication when finding a location to agency mapping DCB-519
	* Extract method for getting requestor's local system code DCB-519
	* Introduce method for finding agency mapping by context DCB-519
	* Extract method for getting pickup location context from command DCB-519
	* Pass command instead of pickup location code only when finding mapping DCB-519
	* pass command instead of pickup location code when checking mapping DCB-519
	* Extract method for getting pickup location code from command DCB-519
	* Extract method for finding a pickup location to agency mapping in DCB context DCB-519
	* Extract method for finding an agency by code DCB-519
	* Extract method for checking agency without assessing previous check result DCB-519
	* Extract method for finding agency mapping DCB-519
	* Introduce method for checking whether agency exists during preflight check DCB-519
	* Return tuple of result and agency code during agency mapping check DCB-519
	* Extract method for checking pickup location to agency mapping DCB-519
	* Extract method for performing preflight checks DCB-519
	* Extract method for creating failed checks exception from results DCB-519
	* Extract method for mapping failed check result to failure DCB-519
	* Extract method for checking whether a check has failed DCB-519
	* Extract method for checking whether all checks have passed DCB-519
	* Add reference value mapping service dependency to pickup location to agency mapping check DCB-519
	* Inject collection of preflight checks DCB-519
	* Define flux from list of checks DCB-519
	* Extract interface for preflight check DCB-519
	* Reduce flux of preflight checks DCB-519
	* Return list of check results from each check DCB-519
	* Move pickup location preflight check to separate class DCB-519
	* Return result when checking for recognised pickup location DCB-519
	* Rename preflight check class DCB-519
	* Rename preflight check exception DCB-519
	* Move hard coded pre-flight check to separate service DCB-519
	* Use zip to find or create a patron when placing a request DCB-519
	* Add hard coded check into reactive chain for placing a patron request DCB-519
	* Move hard coded failure to service DCB-519
	* Move check failed exception to request fulfilment package DCB-519
* [Test]
	* Delete locations for pickup location to agency mapping preflight check tests DCB-519
	* Provide requestor local system code in preflight tests DCB-519
	* Provide pickup location context in preflight tests DCB-519
	* Extract method for defining pickup location to agency mapping in preflight check tests DCB-519
	* Remove duplicated code for defining location to agency mappings DCB-519
	* Rename parameters for defining location to agency mappings DCB-519
	* Remove pickup location to agency mappings in patron request API tests DCB-519
	* Explicit DCB context in mappings for preflight tests DCB-519
	* Define an agency during succesful preflight agency mapping test DCB-519
	* Delete all agencies before agency mapping preflight checks DCB-519
	* Move preflight checks common code to abstract base class DCB-519
	* Remove preflight checks service tests DCB-519
	* Split out pickup location to agency mapping preflight tests DCB-519
	* Split out pickup location preflight tests DCB-519
	* Move checks failure response class to clients package DCB-519
	* Extract matcher for preflight check failure description in API tests DCB-519
	* Delete all reference value mappings during preflight tests DCB-519
	* Move recognised pickup location check tests to nested class DCB-519
	* Define pickup location in patron request API tests DCB-519
	* Move method for creating a pickup location to fixture DCB-519
	* Extract method for creating a pickup location DCB-519
	* Create location in known location preflight check test DCB-519
	* Delete all locations prior to preflight check tests DCB-519
	* Tests for hard coded pickup location check DCB-519
	* Disabled test for placing a request with unknown pickup location DCB-519
	* Reduce maximum PostgreSQL connections to one

### Fixes
* [Graphql]
	* Remove duplicated field in SupplierRequest
* [Workflow]
	* When the resolver was not able to choose an item because all the options are electronic, resove to no items available

## Version 1.8.4

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* return for UUID based pickup location codes

## Version 1.8.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add supplier request scalar fields to graphql schema

### Fixes
* [General]
	* interpret pickup location strings of 36 chars as a UUID

## Version 1.8.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* More deliberate failure and exception reporting when unable to map a patron type

### Fixes
* [General]
	* Ensure we remove the related match-points before the bib

## Version 1.8.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add script for fetching info from UAT environment
* [Feature]
	* info - Add ingest and tracking intervals to info endpoint
* [Test]
	* Rename delete all agencies method DCB-519

### Fixes
* [General]
	* PickupLocation mappings are a legacy thing now - we just have Location code mappings and put everything into one pot. At some point a merge had reverted a curried value of Location back to PickupLocation which was causing a lookup failure now that we no longer need PickupLocation mappings
	* Cleanup on finish, not just cancellation or error.

## Version 1.8.0

### Additions
* [Auth]
	* Implement validatePatronByUniqueIdAndSecret option for patron auth
* [General]
	* Add DcbInfoSource

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* tidy
	* Bit of a messy merge :(
	* Replace spaces with tabs in workflow context helper DCB-519
	* ingest - Add status to ingest state to make it clear when a process is RUNNING or COMPLETE
	* Remove erroneous comment in patron request controller DCB-519
	* Fix formatting in place patron request command DCB-519
* [Feature]
	* Flexible handling of pickup location context - allow the place request command to optionally specify a context for the pickup location, if not provided default to the patron home context
	* Add canonicalPType field to interaction Patron and set this coming out of the Sierra host LMS adapter
	* graphql - Add ProcessStatus query to graphql endpoint
	* ingest - Add human readable timestamps HRTS to process state for sierra to make it eaiser to diagnose harvesting issues
	* Change patron type lookups to range mapping
	* Add pickup location code context to data model
	* auth - Implement UniqueID+Name validation method
* [Refactor]
	* Move method for finding pickup location to agency mapping to service DCB-519
	* Extract method for finding pickup location to agency mapping DCB-519
	* Use value object instead of record for response from place patron request API DCB-519
	* Remove mapping to HTTP response when placing a request DCB-519
	* Change place patron request command to value object rather than record DCB-519
	* Use tuple when placing initial patron request DCB-519
* [Test]
	* Replace empty JSON object with null object for placing a request with no information DCB-519
	* Extract method for placing a request using a command DCB-519
	* Use serialised object instead of raw JSON when placing a request DCB-519
	* Remove patron request status workaround DCB-519
	* Use value objects instead of records when placing requests DCB-519
	* Use client for placing request with unknown local system DCB-519
	* Use login client in declarative HTTP client with JWT test DCB-519
	* Use declarative client for login DCB-519
	* Return body only when logging in DCB-519
	* Use login client in configuration import API tests DCB-519
	* Replace spaces with tabs in configuration import API tests DCB-519
	* Use login client in patron auth API tests DCB-519
	* Remove unused fields / variables in patron auth API tests DCB-519
	* Use login client in JWT authentication tests DCB-519
	* Use login client in agency API tests DCB-519
	* Use login client in patron request API client DCB-519
	* Use constructor injection for patron request API client DCB-519
	* Use dedicated HTTP client for login client DCB-519
	* Reorganise fields in patron request API tests DCB-519
	* Move method for getting an access token to login client class DCB-519
	* Make saving mapping method private in fixture DCB-519
	* Use fixture to define shelving location to agency mapping when getting items from Polaris DCB-519
	* Use fixture to define location to agency mapping when validating a patron DCB-519
	* Use fixture to define shelving location to agency mapping when placing request at borrowing agency DCB-519
	* Use fixture to define shelving location to agency mapping when checking live availability DCB-519
	* Remove duplicate reciprocal patron type mappings DCB-519
	* Use fixture to define pickup location to agency mapping when placing request at borrowing agency DCB-519
	* Use fixture to define location to agency mapping when placing request at borrowing agency DCB-519
	* Replace spaces with tabs in place request at supplying agency tests DCB-519
	* Extract method for defining patron type mapping DCB-519
	* Rename place patron request API test DCB-519
	* Extract method for defining location to agency mapping DCB-519
	* Extract method for defining shelving location to agency mapping DCB-519
	* Extract method for defining pickup location to agency mapping DCB-519
	* Remove unused log message parameter in place patron request API tests DCB-519
	* Replace spaces with tabs in place patron request API tests DCB-519
	* Inline access token variable in place patron request API tests DCB-519
	* Remove commented out code in place patron request API tests DCB-519
	* Use annotation for log in patron request API tests DCB-519

## Version 1.7.3

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
	* Re-elect selected bib on cluster if current is deleted
	* Soft delete of ClusterRecords
	* turn on authentication on the place request API [DCB-322]
	* authenticate patrons registered at libraries on polaris systems [DCB-476]
	* provide live availability results from polaris hosts [DCB-402]
	* Added Folio OAI PMH as a source.
	* populate shared index from polaris [DCB-282]
	* default search property can now be chosen per type.
	* generate dummy titles
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
	* Changelog - Generate the changelog
	* When setting page size for polaris, limit the value to 100
	* docs - Added state model json
	* release - updated Release build instructions
	* ingest - Tone down logging on papi ingest - was filling logs with every marc record
	* Use existing delete all method for mappings DCB-479
	* Move item status mapper tests to same package as production code DCB-479
	* Use only Available for Polaris fallback item status mapping DCB-479
	* Use only hyphen for Sierra fallback item status mapping DCB-479
	* Extract method for determining local item type code DCB-479
	* Reformat code in item mapper DCB-479
	* Reformat item type mapper
	* updated the logging around the tracking service
	* Additional logging as a part of ingest - logging source, title, sourceId, deleted and suppression flags
	* tidy readme
	* correct Computer File type mapping for Marc records
	* Add type hints for lucene
	* Refactor dummy client for DCB-324
	* refactor dummy record source to use same kind of reactive paging as normal clients
	* constrain native image to xeon-v2 optimisations for now
	* constrain native exes to xeon-v2 optimisations.. for now
	* Take notice of num-records-to-generate parameter in dummy HostLMSClient
	* Added a release.md to describe the release process
	* Build - Updated tag matching for GH Actions
	* Diable the AOT capabilities for now
	* Optimization settings.
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
	* Rollup beta for weekly release
	* graphql - Add paginated supplier requests and agency groups. Leave old groups api intact for now
	* graphql - DCB-501 add paginated patron requests query to graphql endpoint
	* graphql - DCB-500 add locations query to graphql endpoint
	* graphql - Added agencies and hostLms
	* graphql - Graphql query methods for clusterRecords and sourceRecords with associated navigation
	* graphql - Use Pageable in InstanceClusterDataFetcher and add page vars to schema
	* graphql - Add instanceCluster and SourceBib data fetchers
	* Convert ResolutionStrategy choose item to a reactive method
	* Add type filter to locations
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
	* Move BibRecordService to svc package
	* Move record clustering service into package.
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
	* Item result to item mapper [DCB-402]
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
* [Rework]
	* Cluster controller to use service and improve response time
* [Test]
	* Use unknown status fallback when testing reference value status mappings DCB-479
	* Move reference value status mapping tests to nested class DCB-479
	* Move Sierra specific status mapping tests to nested class DCB-479
	* Use Sierra item status fallback mapper in tests DCB-479
	* Check local bib ID for supplier request after resolution DCB-479
	* Remove reliance on order in shared index service tests
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
* [Graphql]
	* AgencyGroups data fetcher is now reactive
	* Declare a page type in the schema for the reactive page
* [Harvesting]
	* Sierra date ranges require the time zone signifier Z
	* Send suppressed=true and deleted=true when harvesting from Sierra
* [Ingest]
	* Syntax error caused by previous merge
	* In sierra - deleted=true means ONLY deleted records, suppressed=true means ONLY suppressed records. The default is to return all and thats what we want
* [Mappings]
	* DCB-502 patch marc leader item type mappings
* [Marc   Ingest]
	* DCB-504 DCB-521 - check 264 for publication info before 260
* [Polaris]
	* Overrid the isEnabled method to honour settings from config
* [Security]
	* remove anon auth from rest endpoints
* [General]
	* Ignore resumption initially if we have a since.
	* Save removed resumption token
	* Remove related bib identifiers before the bib
	* Query for page then expand in separate query
	* Properly implement touch
	* remove polaris ingest limiter
	* duplicate key value violates unique constraint raw source pkey [DCB-282]
	* Clustering null values
	* Broke the Optimisation layer.
	* Native logging.
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
	* Issue #DCB-478
	* Issue #DCB-194
	* Issue #DCB-226
* [Provides]
	* Issue #DCB-90