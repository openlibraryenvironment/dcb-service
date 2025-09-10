# Changelog

## Version 8.48.0

### Additions
* [General]
	* Do not perform re-resolution for manually selected item
	* early version of Resolution preview API

### Changes
* [Chore]
	* Added a comment to the tracking sql to remind you to keep the index aligned with the where clause

### Fixes
* [General]
	* correct the delete holding path in AlmaApiClient
	* reply to prompt causing error for Polaris PUA request [DCB-2024]
	* correct pickup location when creating sierra pickup hold [DCB-2023]
	* rename the migration file
	* Extended the sql endpoint to also explain queries
	* Updated the index for patron request on next poll time, so it only include items we are interested in

## Version 8.47.0

### Additions
* [General]
	* Edit consortial max loans [DCB-1936]
	* supplier request API [DCB-2009]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Commenting how to disable specific Jobs

### Fixes
* [General]
	* Stop DCB preventing renewal on renewable items [DCB-2006]
	* polaris borrowing holds on RET-PUA [DCB-2013]

## Version 8.46.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Missed a return
	* The location controller will not always received the host lms id, so we populate the host lms from the agency

## Version 8.46.0

### Additions
* [General]
	* enable Alma supplier holds [DCB-1930]
	* Include all alpha subfields in title, add new BLOCKING_WORK_TITLE identifier which takes uniform title variants as primary source. Update title and canonical record when updating a bib, use default for patron_request is_too_long
	* Add a datestamp to AvailabilityReport

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Prevent library staff editing mappings if DENY_LIBRARY_MAPPING_EDIT is set [DCB-1995]
	* Second migration to catch nulls from 8.45 [DCB-2001]
	* tone down logging for no pickupPatronId when active workflow is not RET-PUA

### Fixes
* [General]
	* Prevent renewal prevention from trying to cancel the original loan [DCB-2006]
	* Use kebab-case so active-request-limit is configurable [DCB-2007]
	* Prevent expedited checkout request-related NPEs [DCB-2001]
	* try to unmask errors when polling patron request via TrackingServiceV3
	* map call number from holdings data not bib data in Alma adapter [DCB-2005]

## Version 8.45.0

### Additions
* [General]
	* Expose tracking intervals [DCB-2001]
	* Store and react to edge-dcb renewable boolean in item data

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* remove AlmaClientConfig dependency injection
	* spaces in polaris bib leader fields [DCB-1969]

## Version 8.44.0

### Additions
* [General]
	* Include filtered items in the resolution audit entry DCB-1975

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* refactor IngestJob so reactive stream can be called directly from tests, add mn event on success
	* add batch delete script as an example of how to

### Fixes
* [General]
	* updating virtual item barcode in polaris adaptor [DCB-1990]

## Version 8.43.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* increased the maximum pool size to 20 from 2
	* Experimental change to availability job - backpressure aware DB updates
	* add state to foilo oai checkpoint
* [Refactor]
	* leader value when creating temp bibs in polaris [DCB-1969]

### Fixes
* [General]
	* When ingest encounters a DB error, just throw an error, don't try and set the record state in the DB. Reduce some logging verbosity

## Version 8.43.0

### Additions
* [General]
	* Implement full expedited checkout [DCB-1555]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* re-enable disabled ingest tests
	* Check supplier item status before moving expedited checkout request to RETURN_TRANSIT [DCB-1555]
* [Refactor]
	* replace onErrorContinue with doOnError in checkPatronRequest

### Fixes
* [Build]
	* disable invalid tests for ingest
* [General]
	* DCB-1794 Languages were being interpreted in another class, so have removed what I added to do with languages
	* We also extract 041, 700, 710 and 711 marc fields into the metadata
	* Use correct parameters when placing a same library request in FOLIO DCB-1917

## Version 8.42.0

### Additions
* [Locks]
	* More defensive code
* [General]
	* Extend availability date for available items with holds DCB-1960

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* sql fix works

## Version 8.41.0

### Additions
* [General]
	* extract barcode parsing logic to PatronIdentity [DCB-1786]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Lower frequency of log reports to syslog for validate cluster progress
	* add validateClusters syslog entries
	* Switch off repeat request checks by default. Rever DCB-1090 default, leave capability in place

## Version 8.40.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Extra type safety for chas

### Fixes
* [General]
	* handle tracking errors to ensure virtual item updates happen [DCB-1959]

## Version 8.40.0

### Additions
* [General]
	* Add Syslog service and application event listenet
	* Add syslog domain class and repository

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* DCB-1786 renewal count not propagating
	* allow polaris to transition to fetching updated bibs after full harvest in new fetch bibs approach

## Version 8.39.0

### Additions
* [General]
	* Add bibs without source record UUID alarm
	* alternative way to fetch updated bibs in polaris

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Adjust handling of row in alarm task for bibs without source record ids
	* Add admin endpoint to validate a specific cluster
	* Add function to reproces a single clusters bib records
	* Performance improvements to cluster validation and new Boolean TooLong  field on PatronRequest which is included in tracking query
	* Change logging to log.error - Folio
* [Wip]
	* performance improvements for reclustering

### Fixes
* [General]
	* use next in graphql source record fetcher in case left truncated wildcard results in more than one record
	* Ensure available items are prioritised for holds [DCB-1957]

## Version 8.38.0

### Additions
* [General]
	* Change the way we pull data for the availability job
	* New RET-EXP workflow for expedited checkout [DCB-1555]
	* Support same libary borrowing for FOLIO DCB-1917

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* remove circular location dependency when creating a polaris item (pua) [DCB-1950]
	* removed fnial from locationCode
	* Extended the remote_location_code field on bib_availability_count to be 128 characters, truncated the remote_location_code to ensure it is not greater than 128 characters
	* Removed an errant comma that was left in the mappings

## Version 8.37.0

### Additions
* [General]
	* support placing hold at local agency in alma

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* align alma raw item status
	* add request body for posting blocking note in polaris

## Version 8.36.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* tone down cluster matching logging

### Fixes
* [General]
	* move PUA checkout validation to hostlms clients [DCB-1941]

## Version 8.36.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Improve error handling for mapping creation [DCB-1937]
	* Better diagnostic method for when no patron identities are loaded for patron
	* Re-wire per-agency request limits

### Fixes
* [General]
	* Improve validation of location mappings [DCB-1938]
	* Delete detection for folio

## Version 8.36.0

### Additions
* [General]
	* Add Agency level max cosortial loans, and skeletal preflight

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Remove availability from mappings-2
	* Always restore es interval to 30s at end of ES update run
	* Wire in per-agency tenant limit check (Not fully implemented yet, but placeholder present)

### Fixes
* [General]
	* Use the configured default pickup library for Alma requesting [DCB-1930]

## Version 8.35.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* bump commit to trigger build

### Fixes
* [General]
	* Accidental SQL removal

## Version 8.35.0

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 8.34.0

### Additions
* [General]
	* Basic support for alma renewals [DCB-1930]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Fix isEnabledForPickupAnywhere naming
	* Add isPickupAnywhere attribute to the GraphQL schema
	* Initial commit for Alma cancellation and renewals [DCB-1930]
	* DCB-1929 - When cleaning up a removed HostLMS, also delete the SourceRecords AND set ingest=false in the config

### Fixes
* [Temporary]
	* default item hold count to 0 in ALMA adapter - MId term this value needs to be retrieved from GET /almaws/v1/items/{mms_id}/{holding_id}/{item_pid}
* [General]
	* Fix serialisation error with config endpoint

## Version 8.33.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Alerts when location mapping fails
	* log when we can't find an ID for a source record
	* More logging for null bib records
	* extra logging to pin down conversion errors

### Fixes
* [General]
	* Add a null filter when normalising identifiers

## Version 8.33.0

### Additions
* [General]
	* Null metadata handling, and read OAI-PMH Header status field
	* Implement cluster detachment and source_record processing state reset when cleaning up clusters
	* Adds a tracking section to the /info endpoint that returns the duration and count of the last tracking run
	* Edit whether a location is enabled for pickup anywhere.
	* GET: config in admin controller
	* Cluster reprocessing
	* Initial work around revalidating clusters - service and invocaion endpoint
	* Added better error reporting to ingest

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Further tone down of alma logging
	* more error trapping on cluster cleanup
	* Stringify UUID when updating source record processing state in cleanup
	* Adjusting validate cluster sql
	* formatting
	* null safe checking on check clusters
	* Reduce volume on cluster cleanup logging
	* Cluster cleanup should use match_point and not bib_identifier
	* update info for tracking
	* nullsafe handling of last tracking count
	* Tone down alma duration logging, more logging when cluster cleanup fails
	* More work on cluster cleanup
	* Improvements to cluster validation
	* Add a value hint to match_point so we can demonstrate to users why clustering has happened. Maintain a UUID for the source_record directly in bibRecord so we can reset the processingState should we discover a bad cluster record and want to selectively force reprocessing of the records that contributed to a cluster
	* Working on cluster verification
	* Log information about the components of a cluster ready for detecting overgrouped clusters

### Fixes
* [General]
	* Change SQL for setting processing state on source record
	* More defensive code around cluster cleanup
	* Missing import
	* datecreated to date_created in validateCluster
	* Error in nullsafe checking
	* Corrected selectedBib to selected_bib in bad cluster detection
	* SourceRecord service is optional
	* Operations should be POST
	* Bad SQL when seeking clusters that contain more than one record set
	* Update apache bean utils to 1.11.0 DCB-1905
	* Make ISBN less terminal

## Version 8.32.1

### Changes
* [Build]
	* GitHub - Updated checkout action to fetch tags.
	* Ensure correct full version number as an image tag
* [Chore]
	* Changelog - Generate the changelog
	* refactor edition normalisation

### Fixes
* [General]
	* Use our default Alma system location if the item location is closed.
	* use correct location code for alma borrowing/supplying
	* Add the pickup Host LMS' client class to the Alma context
	* Stop requests involving Alma failing when a non-Alma pickup location is used
	* use locations data from alma before creating an item and placing a hold
	* create user requesting in alma
	* Prevent invalid identifiers being passed to create an Alma user
	* Restore use of primary ID when creating a patron for Alma

## Version 8.32.0

### Additions
* [General]
	* Add ability to define static templates for metrics in config

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Extra error trapping and reporting

### Fixes
* [General]
	* finding user by identifier / alma adaptor
	* Nullable dependency
	* Package private not private.
	* allow the local id to be passed for creating patrons in alma
	* Changed the label for Sierra Fixed Field 71 to match that at https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html
	* alma item mapping for rtac
	* remove IllegalArgumentException for author when creating bib

## Version 8.31.5

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add primary ID to barcodes field for Alma patrons [DCB-1907]

### Fixes
* [General]
	* use first local id when multiple local ids are found during the ValidatePatronTransition

## Version 8.31.4

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Removed the double quotes that were added the other day as requested
	* Remove blocking calls from LocationController and replace with reactive variants. Suspect that when post to location controller is made with an unknown UUID the blocking call breaks in unhelpful ways. New messages should clarify the error when scripts wrongly create UUIDs and explect them to match

## Version 8.31.3

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Apparently Set.of(null, ...) is not allowed, so have replaced it in this method

## Version 8.31.2

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* use correct pickup library code for PUA in Polaris systems
	* Updated the tests to go with the previous change
	* Folio CreateTransaction fails if the patron group has a reserved character in it, so have enclosed it in double quotes, so it dosn't treat it as a special character

## Version 8.31.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* Change transactional boundaries to only wrap the db fetch

## Version 8.31.0

### Additions
* [General]
	* Web metrics to include host and uri unless overridden.
	* Detect renewal at FOLIO borrowing library DCB-1894
	* Perform renewal at FOLIO supplying library DCB-1894

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add logging to troubleshoot FOLIO on-site-borrowing [DCB-1555]
	* Add instrumented HttpClient as a way to inject a HttpClient that adds the host label to the prometheus metrics endpoint

### Fixes
* [General]
	* Lower metric entries for lookups by removing bib ID
	* Global preflights error on host_lms column [DCB-1903]
	* Fix global limits preflight error [DCB-1903]

## Version 8.30.0

### Additions
* [Alma]
	* placing hold adjustments
	* include holding id from placing holds and tracking items
	* refactor bib and items methods
	* handle 204 No Content responses using Void.class
	* change return type for delete patron
* [General]
	* Allow alarms to pass a Map of additional properties that will be rendered in slack or teams
	* Configurable availability timeout during resolution DCB-1896

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add logging for reindex error
	* Tidy up reporting, open up prometheus metrics
	* GraphQL support for alarms [DCB-1902]
	* error trapping on alarms service
	* Additional log tuning
	* Extra configuration of intercept url endpoint to allow OPTIONS on /graphql to anyone but secure POST - to try and reduce the incidence of exceptions in the logs
	* Useful info logged on startup
	* More logging adjustments to get prod defaults into a sensible shape
	* Reduce logging be default a little

### Fixes
* [General]
	* collectList when broadcasting alarms to slack and teams
	* Syntax error in startup event listener
	* disable per-test XML output to reduce build report size

## Version 8.29.0

### Additions
* [Alma]
	* use remotePatronId when validating patron
	* changes to deleteAlmaUser
* [General]
	* Improvements to alarms and notifications in preparation for Teams and Slack integration

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add missing file

## Version 8.28.0

### Additions
* [Alma]
	* add validate patron and delete patron to testIls
	* External Id must be empty for internal user.
	* error improvements and add required create user field
	* try to convert HttpClientResponseExceptions to Alma specific errors
	* validate responses for creating user/patron
* [General]
	* Timeout availability during resolution DCB-1896
	* Introduce idea of office hours
	* First pass of alarm notifications to system wide slack webhook. Set an env var like "DCB_GLOBAL_NOTIFICATIONS_WEBHOOKS[0]":"https://hooks.slack.com/services/***/***/***" to have alarms broadcast to the selected slack channel

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Remove the CLUSTER_CHECK_CONCURRENCY var
	* use alternate base url for AlmaHostLmsClient class
	* change return type for alma test method
	* log webhooks at startup
	* defensive code for webhook url
	* adjusting folio ping, and add resumeOnError
	* more defensive code arounf Folio ping operation
	* Register handlers for uncaught exceptions and log them
	* alter HttpClient for slack publishing
	* Additional logging
	* Trial delay as a short term measure
	* Attempt to implement ping for FOLIO

### Fixes
* [General]
	* Stop job when transitioning into hours.
	* Throttles per sourceSystemId for availability check.
	* Correction to the query for unprocessed availability.
	* Office hours methods should pass if no data is supplied.
	* Ensure we wait for chunk to be processed
	* Prevent cyclic check of resources every 2 days
	* Remove duplicate "and" from getActiveRequestCountForPatron [DCB-1897]
	* build - remove no args annotation on AlmaError class

## Version 8.27.0

### Additions
* [General]
	* Implement some alarms

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 8.26.1

### Additions
* [General]
	* Set needsAttention flag on Location when dynamically creating a new location
	* Add Workflow methods to location, when dynamically creating a location as a part of holdings harvesting mark the new location record as needing the Review Workflow- because there are properties on a location only a human can supply
	* Add userMessage to CheckResult - a string intended to be displayed in a user interface that explains the error in more readable terms

### Changes
* [Chore]
	* extra logging around federated lock release
	* Changelog - Generate the changelog

### Fixes
* [General]
	* use alternate base url for AlmaApiClientImpl

## Version 8.26.0

### Additions
* [General]
	* DBMemoize locs

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* sierra item status not being updated [DCB-1893]

## Version 8.25.0

### Additions
* [General]
	* alma create item
	* implement ping for alma via test endpoint
	* Implement sierra tokenInfo endpoint and bind it to the ping method. Also tidy up the repeated QueryResultClass from sierra
	* alarms
	* Exclude all previous supplying agencies during re-resolution DCB-1885

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add logs to help debug [DCB-1893]
	* don't make the processing status field final with it's assigned value
	* Add builder.default annotaton to source record
	* ToDo added
	* Alma Refactoring
	* Additional error reporting when hold fails
	* refactor place hold
	* memoize location data from Alma
	* make alma item mapper flow more reactive in preparation for location processing chain
	* working towards unified location handling
	* info logging on audit
	* Improve ping and audit actions
	* Fleshing out alarams infrastructure
	* Refactor interop test, structure setup, core, cleanup phases
	* Improve data change log recording for library entities[DCB-1744]
	* Update library setup script to take provided Keycloak config
	* Keep a count of pages when updating source records for reprocessing
	* Set confidence to 0 for ONLY-ISBN-13
	* enable git info
* [Refactor]
	* enrich polaris hold count for live availability
	* some DRY goodness

### Fixes
* [General]
	* Add apikey into alma::getAllItemsForBib
	* re-introduce default processing_required so that new records are processed
	* Implementation for sierra patron delete
	* Prevent all source records from being set to PROCESSING_REQUIRED [DCB-1886]

## Version 8.24.0

### Additions
* [General]
	* Support creating and deleting individual library or consortium contacts [DCB-1744]
	* graphql pickup location query now observes PUA functional setting. When enabled, now sorts by isLocal,Agency-name,LocationName. When PUA not set, just returns the loacations for the supplied agency.
	* ISBN values how have a confidence of 9 and will be excluded from matching. If a record carries a single unique normalised ISBN it is added as a new ONLY-ISBN-13 identifier type with a confidence of 1. Net effect is that ISBN matching will now only happen on a record that carries a single unique ISBN
	* Introduce ImplementationToolsController as a target for methods to run interop tests against configured HostILS systems

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* correct path to git props file in build
	* Tidy reingest call
	* Extend states used for active request counting in global limits preflight check
	* add non-sensitive environment variables to health endpoint
	* more info on ISBN processing
	* Lower concurrency on availabilty check job, place structure for global limits preflight check, surface polling durations in app startup for logs
	* Preparing work for exposing raw values from Host ILS systems in admin app
	* Improve handling of patron barcodes [DCB-1555]
	* update dependency of apidocs build workflow
	* improvements to Alma DTOs for items
	* Preparatory work for exclusions in record clustering
	* Add context to interop test service
	* add deleteUser to HostLmsClient in prep for interop test endpoint
	* additional props for Alma create user
	* Preparation work for setting resolved agency on patron records, which in turn will allow us to know if a users agency is participating in borrowing - useful for shared systems where one agency is participating and one is not
	* upgrade deploy-pages workflow to use upload-pages v3
	* Rename expedited checkout migration [DCB-1555]
	* Add expedited checkout flag to patron request command [DCB-1555]
	* Improve handling of second Host LMS in libraries data fetcher
* [Feature]
	* Allow deep LMS specific routines to provide raw data and decision logs up to Item records in RTAC responses - in order that the admin app can explain why items are suppressed or not. Also adjust the mechanism used to configure polling intervals

### Fixes
* [General]
	* Allow LIBRARY_ADMIN and CONSORTIUM_ADMIN users to do patron lookup [DCB-1890]
	* Wrong query in houskeeping service reprocess all routine

## Version 8.23.0

### Additions
* [General]
	* Add LBRatio field to Library

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add targetLoanBorrowerRatio to GraphQL [DCB-1884]
	* upgrade GraalVM in release yml
	* Refactor ItemWithDistance into SupplyCandidateItem so that it can carry Loan to Borrow Ratios for different ranking methods
	* add availability date and due date to resolution audits [DCB-1882]

### Fixes
* [General]
	* supplier rawLocalItemStatus tracking failure [DCB-1874]

## Version 8.22.0

### Additions
* [General]
	* Terminate request when re-resolution is unsuccessful DCB-1411
	* FOLIO renewal prevention first pass
	* Polaris renewal blocking via item blocks
	* Use renewal count to prevent renewals in sierra systems

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Bump commit to test new CICD pipeline
	* Extend alma user DTO
	* rename alma types
	* refine alma patron
	* Use AlmaClientFactory to yield Client
	* updated note on polaris lms client
	* Add some messages to audit log in reresolution processing

## Version 8.21.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add sorted item list to resolution audit [DCB-1828]

### Fixes
* [General]
	* Alternate pathing for decorating request context with pickup location - should address print label issue

## Version 8.21.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add optional configuration to info endpoint [DCB-1828]
* [Feature]
	* Add a confidence value to BibIdentifiers so that record matching can...

## Version 8.21.0

### Additions
* [General]
	* Add reingest operation to admin controller. Use with care

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add some audit messages when placing the hold on a supplier system
	* renewal logging
	* Signposting FOLIO renewal prevention
	* additional logging in OAI client
	* Refactor match point cleanup to use batched deletes in a separate transaction
* [Test]
	* PUA borrower skipped loan scenario [DCB-1534]
	* pickup anywhere scenario using dummy lms [DCB-1534]

### Fixes
* [General]
	* explicitly label rawLocalItemStatus as nullable in repository methods for tracking, adjust Sierra pickup location to match new spec

## Version 8.20.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* tidy logging around FOLIO bib suppression
	* Adds a default inferSuppression method to the base OaiPmhIngestSource and an override to the folio adapter ready to address folio 999 sub t suppression
* [Test]
	* shouldTerminatePUARequestsWhenReResolutionIsNotRequired

### Fixes
* [General]
	* add context hierarchy to mapLocalPatronTypeToCanonical [DCB-1865]
	* Add FOLIO default behaviour for inferring suppression from 999 subfield t. DCB-1670
	* exclude deleted mappings when finding NRMs [DCB-1865]

## Version 8.20.0

### Additions
* [General]
	* extend re-resolution for 3 legged transactions [DCB-1690]
	* Implement range searches via GraphQL [DCB-1536]
	* extend supplier cancellation for terminated requests [DCB-1690]
	* Add with fallback option to ref value mapping service to support wildcards in location to agency lookups
	* Alma client can now request user lists

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* AlmaItem progress
	* Add Alma Client and associated resources

### Fixes
* [General]
	* Include deleted field when fetching Sierra patron by ID DCB-1862
	* Include block fields when fetching Sierra patron by ID DCB-1862
	* use av loan period code id for polaris lms [DCB-1860]

## Version 8.19.0

### Additions
* [General]
	* Update FOLIO transaction during re-resolution DCB-1832
	* extend HandleBorrowerSkippedLoanTransit for 3-legged transactions [DCB-1853]
	* extend RETURN_TRANSIT for 3-legged transactions [DCB-1687]
	* extend BorrowerRequestLoaned for 3 legged transactions [DCB-1685]
	* Populate holdCount for getItem in polaris

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* merge
	* Groundwork for alma adapter
	* correct audit message [DCB-1534]
	* Additional filtering of match points

### Fixes
* [General]
	* add REQUEST_PLACED_AT_PICKUP_AGENCY to possible source statuses [DCB-1683]
	* add REQUEST_PLACED_AT_PICKUP_AGENCY to possible source statuses [DCB-1682]

## Version 8.18.0

### Additions
* [General]
	* extend patron cancellation for 3-legged transactions [DCB-1689]
	* Detect holds at owning libraries and ask borrowing system to prevent renewals. DCB-1533 DCB-1846

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* set the latest index version back to 2
	* Updated the mappings, so "selectedBib.*" became "selectedBib" as enabled can only be at the object and it causes a problem for the ICU analyzer
* [Refactor]
	* combine similar supplying agency operations for deleting and cancelling hold [DCB-1689]

### Fixes
* [General]
	* dcb-1852 - the mn client converts List<String> parameters to a string with spaces after the comma. This confuses sierra
	* Ensure that pickupLibrary is populated when called from SupplyingAgencyService
	* CancelledPatronRequestTransition error when no supplier hold exists to cancel

## Version 8.17.0

### Additions
* [General]
	* Prune removed bib_identifiers, add normalised versions of ISBN and ISSN to identifiers

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add methods for modelling tracking and implementation of renewal prevention
	* WIP towards supplier side holds
	* PageSize to 100
* [Wip]
	* bib identifier purge

### Fixes
* [General]
	* Make getSettings in ES and OS implementations compatible with alias indexes

## Version 8.16.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* upgrade formal-release.yml actions/cache@v2 to v3

## Version 8.16.0

### Additions
* [General]
	* Extend RECEIVED_AT_PICKUP monitoring for 3-legged transactions [DCB-1683]
	* pickup system cleanup [DCB-1688]
	* Extend PICKUP_TRANSIT monitoring for 3-legged transactions [DCB-1682]
	* enable pickup request interaction for polaris host lms [DCB-1534]
	* pickup workflow for folio interaction [DCB-1830]
	* PlacePatronRequestAtPickupAgencyStateTransition [DCB-1534]
	* Suppress selectedBib in ES/OS records
	* Add decorateWithPickupLibrary to RequestWorkflowContextHelper. This arranges for the Library of the pickup location to be added to the context IF pickup agency is set, and by extension makes PickupLibrary available as a property to any workflow steps.
	* track status changes in pickup library's Host LMS [DCB-1801]
	* create request records for 3-legged transactions [DCB-1680]
	* Add the NO_LEND derived loan policy
	* Add a configuration setting - dcb.tracking.dryRun which defauls to false. Can be set as an env var DCB_TRACKING_DRYRUN. This allows the tracking loop to select requests that need to be tracked, updates the tracking info, but the DOES NOT process the tracking data. This is useful for testing the performance of the tracking loop on live data without the side effect of updating live requests
	* Add dcb.scheduled.tasks.skipped (env var DCB_SCHEDULED_TASKS_SKIPPED) which is a list of strings allowing more fine grained control of which scheduled tasks run when scheduled tasks are enabled. In yml this is a list of simple class names, in an env var the strings are encoded as comma separated values, for example DCB_SCHEDULED_TASKS_SKIPPED=IngestService,IngestJob,SourceRecordService,TrackingServiceV3

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Tone down chunk logging
	* Add preparatory config for DCB-1835
	* Add ARCHIVED state to PatronRequest.Status enum
	* Examples of flags for dry run and disabling tracking in run_dev_server.sh
	* Extra info when reindex call errors out
	* Add getItem variant that accepts a list of fields to return
	* Add Location to  PlaceHoldRequestCommand and WorkflowContext in preparation for DCB-1237
	* Properly model shelving location and inferredLoanPolicy in DCB core so that decisions about requestability can be taken at the core level, rather than overloading the semantics of item location. LMS Clients should be providing data to core where decisions are made, not overloading the semantics of Location in order to coerce a behaviour upstream
	* Create an openrs-core library ready to start extracting dependencies of...
	* add migration for additional pickup anywhere fields [DCB-1534]
	* Extra logging on skipping of services
	* Add a documentation field to the condition clause of rulesets, enabling us to add documentation to the various inclusion and suppression rulesets to make them easier to understand for the poor souls who come later
* [Config]
	* Include  "Awaiting pickup", "Awaiting delivery",  "In transit" and "Paged" in the list of FOLIO item states that are considered AVAILABLE - DCB-1831

### Fixes
* [General]
	* Last json deserialisation error
	* Handle null subfield values in records from FOLIO
	* Ensure transaction exists around mono sink
	* Added intermediate @Transactional to avoid error in reindexing call
	* Sierra getItem now includes fixedFields in the list of requested fields, making FixedField 71, as well as all other fixed fields, availabe for renewal tracking etc
	* DCB-1237 - Adjust notes field in RequestWorkflowContext to generate format needed for Sierra and Polaris. Set new fields as specified for FOLIO based on new pickupLibrary property in RequestContext
	* Fix requester note not being set [DCB-1535]
	* Use LocationID in location code for Polaris libraries. Extract location name from the Location Name field rather than shelving location. It is important that Location to Agency mappings are based on LocationID and not Shelving location code

## Version 8.15.1

### Changes
* [CHORE]
	* Added an endpoint /admin/threads to dump out all the
* [Chore]
	* Changelog - Generate the changelog
	* Add holdCount to HostLMSItem. Bind sierra hold count to that, placeholder in polaris ready for get hold info by item, placeholder in FOLIO adapter for same.
	* Adjust netty max depth and bulk ES/OS defaults
	* Bump external opensearch deps to try and resolve connection issues on AWS

### Fixes
* [General]
	* Polaris LMS client was not setting owning context when mapping item records for responding to getItems for a bib

## Version 8.15.0

### Additions
* [General]
	* route to and apply own library request workflow [DCB-1803]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* the CP subsystem was removed from hazelcast in 5.5. Stepping down version. We need to do something about this mid-term as it's essential for cluster running. jgroups-raft seems to be a good alternative
	* Wrap opensearch health function in max duration call

### Fixes
* [General]
	* Null offset in sierra lms client, chore: additional logging in health endpoint, use a dedicated executor for health endpoint
	* Removed "br_.derived_type IS NULL" from the query, since derived type should always be populated

## Version 8.14.0

### Additions
* [General]
	* Set out of sequence flag when supplier renewal request fails DCB-1786
	* Update renewal count when supplier renewal request fails DCB-1786

### Changes
* [Chore]
	* Add support for editing localId [DCB-1824]

## Version 8.13.0

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 8.12.0

### Additions
* [General]
	* Update renewal count when supplier renewal setting is disabled DCB-1786

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Fix issue where ignored items were included in count [DCB-1777]

## Version 8.11.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Show correct success count when locations are ignored [DCB-1777]
	* add action audits to scope of functional setting check [DCB-1786]

## Version 8.11.0

### Additions
* [General]
	* Import pickup locations through DCB Admin [DCB-1777]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* single library workflow API test [DCB-1806]
	* Standardise location and mapping validation process [DCB-1777]
	* Add specific location upload URL [DCB-1777]
	* Add lastImported to Location [DCB-1777]
* [Refactor]
	* active workflow determination [DCB-1802]

### Fixes
* [General]
	* allow functional setting to be checked before attempting transition [DCB-1786]
	* Provide a default for blank print labels / delivery stops [DCB-1777]
	* Extra nullsafe checks on json marc serde, first pass at normalising edition statements in blocking title, tidy up some warnings

## Version 8.10.0

### Additions
* [General]
	* trigger supplier renewal transition [DCB-1786]
	* support performing a renewal in the sierra lms client [DCB-1793]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* PD form
	* Add com.jaredsburrows.license plugin
	* NFR Arm support - Add an optional build parameter that allows builders to override the base image

## Version 8.9.0

### Additions
* [General]
	* allow the folio lms client to always succeed when performing renewals [DCB-1793]
	* support performing a renewal in polaris lms [DCB-1793]
	* Synchronise renewal counts when DCB tracks a renewal via hostlms [DCB-1785]
	* return local renewal count for item from Polaris client [DCB-1784]
	* renewal count for item from sierra client [DCB-1784]
	* return local renewal count for item from folio [DCB-1784]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Throw error if existing location code provided [DCB-1778]
	* Upgrade actions/upload-artifact to v4 in formal-release.yml
	* Upgrade actions/upload-artifact to v4 in release.yml
	* Upgrade actions/upload-artifact to v4
	* Remove trailing and leading spaces for created mappings [DCB-1619]
	* Disallow duplicate fromValues when creating a mapping [DCB-1619]

### Fixes
* [General]
	* indentation in TrackingServiceV3
	* keep renewal fields and state fields aligned on state a change [DCB-1785]
	* Display source records for FOLIO and ALMA systems [DCB-1789]
	* state change on checkSupplierItem [DCB-1785]

## Version 8.8.0

### Additions
* [General]
	* adding data fetcher for InactiveSupplierRequest 's [DCB-1782]
	* add InactiveSupplierRequest type to graphql schema [DCB-1782]
	* Add getters/setters for bibParams
	* add renewal counts to request data models [DCB-1782]
	* Support creation of individual pickup locations [DCB-1778]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add pickup location creation validation [DCB-1778]
	* Add TRIGGER_SUPPLIER_RENEWAL functional setting [DCB-1783]
	* Update GraphQL schema with SourceRecord [DCB-1788]
	* Record proper usernames in the data change log [DCB-1717]

### Fixes
* [General]
	* DCB-1745 Added a migration for the fix Steve made for the sierra null pointer during harvesting
	* #1745
	* Fix source records not displaying in DCB Admin
	* typo in TrackingServiceV3
	* update sierra virtual item on re-resolution [DCB-1780]

## Version 8.7.0

### Additions
* [General]
	* Support creation of individual reference value mappings [DCB-1619]

### Changes
* [CHORE]
	* Added an uptime to the health message
* [Chore]
	* Changelog - Generate the changelog
	* log record conversion errors
	* Defensive null code around marc serde fields

### Fixes
* [General]
	* Have added the annotation to CqlQuery to stop the exception occurring
	* DCB-1211 Reduced the number of bibs deleted at a time from 10000 to 1000
	* DCB-1770 Tried to ensure the processing state is always populated for source record so we can just lookup the index instead of performing a full table scan

## Version 8.6.0

### Additions
* [General]
	* Respect consortia setting for allowing same library borrowing DCB-1560
	* Only increment resolution count upon successful resolution DCB-1411

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add functional setting type for OWN_LIBRARY_BORROWING [DCB-1560]
	* Fixed the bug where we had a jsonb field and it errored out
	* Additional logging in ingest service
	* add specific INFO logging level for org.olf.dcb.ingest

## Version 8.5.0

### Additions
* [General]
	* Extend availability date for checked out items based upon holds DCB-1739
	* Add support for editing print labels [DCB-1237]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Default hold count to zero when hold count is null DCB-1739
	* Calculate availability date for checked out items without a due date differently DCB-1739
	* Make availability date null for unavailable or unknown items DCB-1739
	* Add default reason when creating contact [DCB-1697]
* [Refactor]
	* change selection filter to include items with holds [DCB-1738]
* [Test]
	* shouldSelectEarliestDueDateIrrespectiveOfGeographicProximity [DCB-1734]

### Fixes
* [General]
	* Update non-public note for Polaris item during re-resolution DCB-1411
	* Availability date for all available items should be the same
	* Don't allow blank values in mappings import [DCB-1658]

## Version 8.4.0

### Additions
* [General]
	* Set availability date to due date for checked out items [DCB-1734]
	* change selection filter to include checked out items [DCB-1733]
	* Add contact roles to DCB [DCB-1697]
	* Add data fetcher for creating functional settings and update scripts [DCB-1746]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* DCB-1664 Extended the output for the import / ingest details api to include the checkpoint id
	* DCB-1664 Added an interface that gives us details about the state of the import / ingest for the hosts
	* Add a secured utility endpoint at /clusters/ID/exportMembers which provides a zipfile response containing JSON format bib records for all the members of the cluster. The intent is to create an easy method to easily exrtract stand-alone test record sets to test clustering scenarios
	* Update functional setting script [DCB-1746]
	* Update migration and scripts for role creation [DCB-1697]
	* Rename migration [DCB-1697]
	* Add SELECT_UNAVAILABLE_ITEMS consortial setting [DCB-1731]
	* Add defensive code to marcxml subfield processing to trap for FOLIO weirdness
* [Refactor]
	* change resolution strategies to sort only [DCB-1726]
	* move item status mapping to each Host LMS type [DCB-1747]
	* remove item status mappings ability by Host LMS [DCB-1747]
	* resolution to support availability date sorting [DCB-1726]

### Fixes
* [General]
	* Amend role migration to handle invalid roles [DCB-1697]

## Version 8.3.2

### Changes
* [Chore]
	* adjust logging polaris again
	* More logging in polaris - ingest seems to cycle on last page
	* Handle errors when getting and setting ES/OS settings. Fixes DCB-1722
	* Extend information held in polaris checkpoint records to more easily tell if a run has completed or is in progress
	* Refactor date parsing for polaris to return Instant not string
	* Logging - improve reporting of failed date parsing for polaris, remove redundant static Logger definitions from classes also using @Slf4j annotation
	* Add AWS dependencies to enable secrets manager integration when enabled

### Fixes
* [General]
	* Force polaris modified dates to lower case before regex

## Version 8.3.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* It seems that MS turns the last modified date into a strange string. Add a parser for it
	* Use getStringValue instead of cocerceToString trying to extract record modification times from polaris bibs

### Fixes
* [General]
	* Wrong path in build for reflection config

## Version 8.3.0

### Additions
* [General]
	* Re-resolve supplier cancelled request [DCB-1411]
	* Add support for deleting consortia [DCB-1638]
	* Add data change log support for new entities[DCB-1638]
	* Add support for adding consortium contacts and other consortium config [DCB-1638]
	* Add support for consortium contacts and functional settings [DCB-1638]
	* Extend Consortium data model to support consortium config [DCB-1638]
	* add resolution count to patron request model [DCB-1411]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Prevent consortium creation if a consortium is already present [DCB-1638]
	* Remove Beta requirement for ingestV2, pass isDevelopment flag through to MatchPoint builder in preparation for storing match point values in dev mode to help diagnose clustering issues
	* Set OAI set on first pass through
	* More logging in OAI ingest source, use orElseNull to simplify Optional handling on oai-set
	* Phase two - embedded record contains instance of io.micronaut.json.tree.JsonString. Logging the values to see whats going wrong with parsing
	* Change logging of last modified date from polaris to investigate issues
	* Check for login failure in libraries_setup.sh [DCB-1638]
	* Support editing the website URLs for a consortium [DCB-1638]
	* Commenting in polaris api client explaining the problem with ingest
	* Support editing the description of a consortium [DCB-1638]
	* Clarify documentation of consortium feature [DCB-1638]
	* add dcb.circulation.tracking-profile to info endpoint [DCB-1473]
	* update resolution count for existing requests [DCB-1411]
	* Additional info logging for polaris record ingest
	* More work on native image flags and parameters
	* Comment out exact reachability param for graal build
	* Add reflection section to dcb/src/main/resources/META-INF/native-image/resource-config.json, add buildArgs.add('--exact-reachability-metadata') to build.gradle - attempt to address native image issues
* [Feature]
	* Add bootstrap.yml to enable integration with distributed configuration systems like hashicorp vault and AWS Secrets Manager
* [Refactor]
	* find and validate existence of a single consortium during re-resolution [DCB-1411]

### Fixes
* [General]
	* Perform each delete chunk in it's own transaction. DCB-1421
	* Increase record-id length to 256 DCB-1437
	* Polaris Ingest should track highest timestamp seen to enable paging.
	* Handle the deletion of legacy consortia [DCB-1638]

## Version 8.2.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add 'flag key' to log warnings [DCB-1473]
	* Updated the failed to resolve shelving location check to be more generic
* [Refactor]
	* set circulation tracking intervals for production context [DCB-1473]
* [Test]
	* add API level test for placing 3-legged request [DCB-1677]

## Version 8.2.2

### Changes
* [Chare]
	* The 2 duplicate scripts were referencing each other scripts
* [Chore]
	* Changelog - Generate the changelog
	* DCB-1645 Changed NO_ITEMS_AVAILABLE_AT_ANY_AGENCY to NO_ITEMS_SELECTABLE_AT_ANY_AGENCY
	* added an extra error that we trap in the error overview

### Fixes
* [General]
	* borrowing agnecy uses resolved agency from supplier request [DCB-1669]
	* DCB-1657 Added routines that will build the uuid correctly for host lme, agency, location, numeric range mapping and reference value mapping, the associated controllers have been updated so hat if a null or zero uuid is passed in then the id will be generated

## Version 8.2.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Added a couple of errors we are now taking into account
	* New migration for updating next_expected_status
	* Sort reference value mappings by lastImported nulls last [DCB-1620]

### Fixes
* [General]
	* use shelf location to determine whether polaris items are displayed [DCB-1614]
	* migration for NO_ITEMS_AVAILABLE_AT_ANY_AGENCY [DCB-1102]

## Version 8.2.0

### Additions
* [General]
	* resolution to exclude items from a different library on the same server [DCB-1102]
	* Support uploading mappings by category [DCB-1620]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* clarified patronHostLmsCode field for future alignment [DCB-1641]
	* Restrict update participation status to CONSORTIUM_ADMIN [DCB-1567]
	* migrate existing request statuses NO_ITEMS_AVAILABLE_AT_ANY_AGENCY [DCB-1102]
* [Refactor]
	* rename NO_ITEMS_AVAILABLE_AT_ANY_AGENCY status [DCB-1102]

### Fixes
* [General]
	* Alter markAsDeleted query to return the correct deleted count
	* include agency in shouldExcludeItemFromSameServerAsTheBorrower test [DCB-1102]
	* Fix incorrect deleted mapping count being returned [DCB-1620]

## Version 8.1.0

### Additions
* [General]
	* polaris item suppression by collection [DCB-1527]
	* DCB-1568 We now have the import that will take the output of the export

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* ^Cd item suppression ruleset test script
	* add reachability-metadata.json
	* Add initialise at build time args to graalvm command
	* add additional reflection metadata to graalvm config

### Fixes
* [General]
	* DCB-1568 We now delete the existing configuration as part of the import
	* DCB-1568 Ensured the catalogueing lms is exported when only a catalogueing host is exported
	* DCB-1568 If no host lms ids or agency codes are specified we export all host lms ids
	* DCB-1568 We can export using agency codes, have also replaced flatMap with map in the export services

## Version 8.0.0

### Additions
* [General]
	* **BREAKING** -  Remove ability to choose identifier for Polaris and FOLIO DCB-1574
* [General]
	* DCB-1568 Added the library  contact and person to the export
	* DCB-1568 Added the library to the export
	* DCB-1568 Export of library configuration
	* Support editing and deleting mappings via DCB Admin [DCB-1407]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Refer to configuration property in missing value warning DCB-1574
	* Remove ability to choose identifier when finding Polaris patron DCB-1574

### Fixes
* [General]
	* Remove ability to choose identifier when finding FOLIO patron DCB-1574
	* DCB-1568 Converted the export to use the SiteConfiguration class instead of using a Map
	* DCB-1568 Added library group and library group members to the export

## Version 7.4.4

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Added a new error that we know about
	* Updated the tracking failed and added a new script for tracking failures

### Fixes
* [General]
	* DCB-1307 Tests seem to have a problem if the index block is defined, so have moved the default version of the index  out of application.yml
	* DCB-1307 Defaulting the index name to be dcb-shared to see if that fixes the tests
	* DCB-1307 When creating the index we now take the definition of the index from the resources/sharedIndex

## Version 7.4.3

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* patron search functionality [DCB-1574]

## Version 7.4.2

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* washington university failing to authenticate [DCB-1574]

## Version 7.4.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add audit for reason tracking borrowing system skipped
	* build - Enable any branch whos name starts testrel- to be published as a docker container with the name dcb-BRANCHNAME - for example dcb-testrel-clustering3
* [Refactor]
	* Distinguish patron search failure and no results [DCB-1562]

### Fixes
* [General]
	* diagnostic audit data for virtual checkout failure

## Version 7.4.0

### Additions
* [General]
	* Only check due date when Sierra item is available DCB-1553

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* simplify setting owning context [DCB-1509]
* [Refactor]
	* tests to support canonicalItemType mapping [DCB-1509]

### Fixes
* [General]
	* Add safeguards to location deletion [DCB-1496]
	* back out of moving stalled request to RETURN_TRANSIT [DCB-1517]
	* adding stacktrace to audit [DCB-1465]

## Version 7.3.0

### Additions
* [General]
	* introduce owning context from agency [DCB-1509]
	* add owningContext to AvailabilityResponseView
	* Added transient field to the MatchPoint
	* Add an optional filters parameter to liveAvailabilty which can control filtering - which is needed for interfaces where we need to show item status even if it is not available
	* Ingest V2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Add number subfield from title into canaonical metadata
	* Update graphql_prs script to provide more info about requests
	* update perftest script to allow borrowing & supplying
	* Add 490 and 830 properties to canonical metadata
	* Admin action for dedupe.
	* Add series statement to canonical metadata
* [Refactor]
	* use static constants for failures in the NumericItemTypeMapper
	* NumericItemTypeMapper to raise a problem [DCB-1521]

### Fixes
* [General]
	* use correct hostLmsCode in problem [DCB-1509]
	* error during finalise we get the response item links breakable [DCB-1374]
	* Duplicated MatchPoints for single bibs
	* Ensure we clear the interrupt message.
	* set local patron id during patron identity validation [DCB-1425]
	* Revert lock system.
	* allow supplier item status available to trigger HandleBorrowerRequestReturnTransit [DCB-1517]
	* remove username field when creating virtual patron in polaris hostlms

## Version 7.2.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Polaris uses RFC 1123 for dates.

## Version 7.2.0

### Additions
* [General]
	* rollback request api [DCB-1492]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Test]
	* shouldRollbackPatronRequestToPreviousStateSuccessfully [DCB-1492]

### Fixes
* [General]
	* Don't allow users to edit location types [DCB-1405]
	* NullPointerException during finalisation [DCB-1398]
	* Map FOLIO item volume to live availability DCB-1491

## Version 7.1.0

### Additions
* [General]
	* identify patron using barcode when placing DCB request [DCB-1425]
	* Support editing location names [DCB-1462]

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* NullPointerException in PAPIClient [DCB-1469]
	* Tolerate null status for deleted Sierra item DCB-1396
	* Remove whitespace in contact names and emails [DCB-1408]

## Version 7.0.0

### Additions
* [Data   Import]
	* **BREAKING** -  New Job runner and ingest flows
* [General]
	* manually finalise incomplete requests [DCB-1346]
	* Support editing of locations, libraries and contacts [DCB-1408]
	* Add support for deleting locations [DCB-1342]
	* Add delete library capability [DCB-1342]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Rename migration file
	* Add fixedDelay to source record service, some additional logging to confirm source harvesting
	* Ensure delete operations are secured by role [DCB-1342]

### Fixes
* [General]
	* use context hierarchy to getAgencyForShelvingLocation [DCB-1426]
	* NullPointerException in log message [DCB-1426]
	* Tests cast client to none parent class

## Version 6.28.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Use agency name for service point in FOLIO supplying library DCB-1237 (reversion)
	* Use agency name for service point ID in FOLIO supplying library DCB-1237 (reversion)

## Version 6.28.0

### Additions
* [General]
	* Use pickup location name for service point ID in FOLIO supplying library DCB-1237
	* Use pickup location name for service point in FOLIO supplying library DCB-1237

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Tolerate no local IDs from Host LMS DCB-1402

## Version 6.27.0

### Additions
* [General]
	* Add facilitity to execute arbritary select statements

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Fix nullable status of lastEditedBy [DCB-1302]

## Version 6.26.0

### Additions
* [General]
	* Support supplying a category and optional URL for changes [DCB-1302]
	* Support supplying reason for mappings upload [DCB-1302]
	* Introduce user data change log in DCB [DCB-1302]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Simplify DataChangeLog class [DCB-1302]
	* Update framework

### Fixes
* [General]
	* stop empty mono returning at source [DCB-1375]
	* handle mono empty returned for cleanup [DCB-1345]
	* NullPointerException in deleteItemIfPresent [DCB-1345]
	* Remove dupe dependency

## Version 6.25.0

### Additions
* [General]
	* handle missing virtual records during cleanup [DCB-1345]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* clearnupPatronRequest to return UUID [DCB-1345]
	* add response body to RecordIsNotAvailableProblem [DCB-1327]

### Fixes
* [General]
	* use flatMap for progressing workflow [DCB-1345]
	* handle exceeded total request limit in polaris [DCB-1353]
	* Back out graphql change.
	* Do not attempt to cancel borrowing request when hasn't been placed DCB-1230
	* Incorrect transition name for supplier request cancellation DCB-1230

## Version 6.24.0

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.23.0

### Additions
* [General]
	* Do not cancel borrowing request when already cancelled DCB-1230
	* Cancel local borrowing request when no alternative supplier found DCB-1230
	* Move request to no items available when supplier cancels request DCB-1230
	* Audit supplier request cancellation DCB-1230
	* Mark supplier request cancelled when local request is cancelled DCB-1230
	* Mark patron request as not supplied when supplier request is cancelled DCB-1230
	* extend logging to support issue triage for this record is not available error [DCB-1327]

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* add polaris workflow reply [DCB-1276]
	* add virtual patron pin via sierra config [DCB-1335]

## Version 6.22.2

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* remove pin to revert [DCB-1265]

## Version 6.22.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* remove 'Registration Has Expired' block on virtual patron [DCB-1300]

## Version 6.22.0

### Additions
* [General]
	* add delay and retries when finding a virtual patron in polaris lms [DCB-1278]
	* Delete Host LMS data
	* add clean up of supplier side requests [DCB-1289]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Reinstate single resource failure in ingest, not page.
	* Reinstate null folding.
	* error handling in PatronRequestAuditService
* [Refactor]
	* add error handling for unhandled request errors
	* transfrom errors on all transitions that are attempted

### Fixes
* [General]
	* add pin to new created virtual patrons in Sierra [DCB-1265]
	* remove unneeded error handling in ConsortialFolioHostLmsClient
	* Readd Admin role requirement.
	* Don't nest new transaction so deeply.

## Version 6.21.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* add error handling to tracking service v3

## Version 6.21.0

### Additions
* [General]
	* cleanup virtual records following cancellation [DCB-1289]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* parse barcode list before adding to CancelHoldRequestParameters [DCB-1174]
* [Refactor]
	* cancel hold in polaris client [DCB-1174]

### Fixes
* [General]
	* remove convertContextToMap in RequestWorkflowContextHelper
	* add virtual patron barcode to CancelHoldRequestParameters [DCB-1174]

## Version 6.20.1-rc.1

### Changes
* [Chore]
	* more error auditData on action failure
	* add problem detail to auditData on action failure
	* add HOLD_MISSING to cancellation check [DCB-1174]
	* add logs for getting hold after placing
* [Refactor]
	* CancelledPatronRequestTransition to use workflow error transformer [DCB-1174]

### Fixes
* [General]
	* gaurd against cancelling already cancelled holds [DCB-1174]

## Version 6.20.0

### Additions
* [General]
	* Disallow inactive patrons from requesting DCB-1253
	* patron cancellation [DCB-1174]

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Drop single resource only, on error saving/marshalling
	* Lets ignore nulls in stream.

## Version 6.19.0

### Additions
* [General]
	* do not attempt tracking in borrowing library prematurely [DCB-1233]

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* try matching tno in polaris hold notes to avoid escaped quotes

## Version 6.18.0

### Additions
* [General]
	* Trigger confirmed transition when local supplying request is in transit DCB-1243

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* extend request confirmation hardening to Sierra [DCB-1218]

## Version 6.17.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* tidy polaris hold note
* [Refactor]
	* add fallback matchers for getting hold by patron in polaris
	* activation date added to note to match in polaris get hold
* [Test]
	* Streamline Polaris configurations [DCB-1084]

## Version 6.17.0

### Additions
* [General]
	* Repeat attempts to find placed holds [DCB-1216]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* Improve logging for 'No hold request found []' errors [DCB-1215]

## Version 6.16.0

### Additions
* [General]
	* Disallow patrons without a barcode to place requests DCB-1213

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.15.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add a PR errorMessage fallback instead of null being saved

### Fixes
* [General]
	* keep pr ref aligned after determining error message [DCB-1212]

## Version 6.15.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* use NonPublicNote instead of NonPublicNotes when creating vitem Polaris

## Version 6.15.0

### Additions
* [General]
	* add support for custom library credential prompts [DCB-1186]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* tidy virtual checkout audit
* [Refactor]
	* RequestWorkflowContextHelper to have Error recovery

## Version 6.14.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* use org.zalando.problem when unable to handle Polaris API workflow
	* response to no item type mapping found in polaris client

## Version 6.14.0

### Additions
* [General]
	* Exclude items from same agency as borrower from resolution DCB-1173
	* Fail preflight when resolution cannot select an item DCB-1173
	* LOCAL workflow extended.

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* add a note for staff when creating a polaris virtual item

## Version 6.13.0

### Additions
* [General]
	* Extend numeric range mapping import and implement soft delete [DCB-1153]

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.12.5

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* audit log a patron type update failure [DCB-1184]
	* log NOOP return for updatePatron in ConsortialFolioHostLmsClient [DCB-1184]
	* log NOOP for update patron in ConsortialFolioHostLmsClient [DCB-1184]

### Fixes
* [General]
	* ensure checkout failure audits [DCB-1180]
	* Truncate failed check message in event log DCB-1182

## Version 6.12.4

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* revert shouldBeAbleToGetItemsByBibId back

## Version 6.12.3

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* tolerate nulls and toString Errors when auditing in HandleBorrowerItemLoaned [DCB-1180]

## Version 6.12.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* correct naming of method in PatronRequestService
* [Refactor]
	* update README.md with new config values for [DCB-1090]
* [Test]
	* polaris item duedate with single digit min with/without space [DCB-1146]
	* DateTimeParseException fix [DCB-1146]

### Fixes
* [General]
	* polling a polaris item that is not found [DCB-1174]
	* request window rule for PreventDuplicateRequestsPreFlightCheck [DCB-1090]

## Version 6.12.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* Tolerate null home library code for patron DCB-1135

## Version 6.12.0

### Additions
* [General]
	* manual selection of an item by a patron [DCB-1120]
	* Disallow patron from placing request when agency is not participating in borrowing DCB-1135
	* Fetch participation fields for updated agency when omitted by the client DCB-1125
	* Exclude items from live availability when agency participation unknown DCB-1134
	* Extend mappings import to reference value mappings [DCB-1152]
	* Add nanosecond timings to live availability response

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* streamline polaris configurations [DCB-1084]

### Fixes
* [General]
	* Up the concurrent streams for http2 multiplexing

## Version 6.11.0

### Additions
* [General]
	* Disallow patron from placing request when agency is not participating in borrowing DCB-1135
	* Fetch participation fields for updated agency when omitted by the client DCB-1125
	* Exclude items from live availability when agency participation unknown DCB-1134
	* prevent duplicate request within N seconds [DCB-1090]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Test]
	* PreventDuplicateRequestsPreflightCheckTests

## Version 6.10.0

### Additions
* [General]
	* pass back raw local statuses when request/item is created
	* Support toggling library participation through GraphQL [DCB-1131]
	* add tracking of raw local request/item statuses
	* Exclude items from non-supplying agencies DCB-1134

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* check for error strings in finalisation [DCB-1140]
	* ensure patron requests are updated when tracking [DCB-1139]
	* tidy PAPI API auth
* [Refactor]
	* sierra update item status
	* auditing for actions [DCB-1139]

### Fixes
* [General]
	* resume request/item tracking when local id is null [DCB-1138]

## Version 6.9.0

### Additions
* [General]
	* audit tracking system failures in admin
	* Store whether an agency participates in borrowing DCB-1125
	* Store whether an agency participates in supplying DCB-1125g
	* Exclude items with an agency without a Host LMS from availability DCB-1114
	* Exclude items without an agency from availability DCB-1114

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* remove unneeded empties in the application services client
* [Refactor]
	* ensure UTC time is used in activation date [DCB-1123]
	* staff auth token generation in polaris application services
	* server logging for failed requests via AbstractHttpResponseProblem
	* activation date in polaris client [DCB-1123]
	* add PolarisRequestNotFoundException
	* re add combined poll count [DCB-1039]
	* auditData with different poll counts [DCB-1039]
	* distinguish poll checks [DCB-1039]

## Version 6.8.0

### Additions
* [General]
	* Record resolution in audit DCB-1093

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* cannotFulfilPatronRequestWhenNoRequestableItemsAreFound

## Version 6.7.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* return empty mono when virtual patron isn't found in polaris

## Version 6.7.0

### Additions
* [General]
	* Add consortium date for welcome page [DCB-1103]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* findVirtualPatron in polaris
	* pull one identity from repo instead of full list [DCB-1086]

### Fixes
* [General]
	* check null barcode and password before polaris patron validate
	* Make consortia use UUIDUtils [DCB-1103]
	* unable to progress request [DCB-1086]

## Version 6.6.0

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.5.1

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* re add defer back to HandleSupplierRequestConfirmed [DCB-1095]

## Version 6.5.0

### Additions
* [General]
	* Determine whether Polaris patron is blocked DCB-1004

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Update HostLMS GraphQL type [DCB-1097]
* [Refactor]
	* streamline polaris configurations [DCB-1084]

### Fixes
* [General]
	* Polaris hold status Shipped now maps to DCB status confirmed [DCB-1095]
	* Add libraries setup script [DCB-1067]

## Version 6.4.0

### Additions
* [General]
	* add patron request metrics to audit entries [DCB-1039]
	* Determine whether a FOLIO patron is blocked DCB-1004

### Changes
* [Chore]
	* Changelog - Generate the changelog

### Fixes
* [General]
	* invalid postal code specified when trying create virtual patron on polaris system [DCB-1046]

## Version 6.3.0

### Additions
* [General]
	* Item suppression
	* Fail preflight check when patron is blocked DCB-1004
	* Object filtering on Sierra
	* capture request workflow and state transition metrics [DCB-1039]

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Refactor]
	* move all state transition metrics to the patron request object [DCB-1039]

### Fixes
* [General]
	* unable to resolve patron home library code to an agency [DCB-1059]
	* Tidy keys for metrics output
	* Tidy keys for ingest source info endpoint
	* remove any reference to pcode in DCB [DCB-1058]
	* Incorrect migrations for audit data
	* outOfSequenceFlag Boolean not boolean in graphql

## Version 6.2.0

### Additions
* [General]
	* Introduce Libraries, Consortia and LibraryGroups [DCB-969]
	* Fail preflight checks if borrowing patron has been deleted DCB-973 DCB-1003
	* Check patron type eligibility before request is placed DCB-1003
	* handle deleted patron in sierra [DCB-973]
	* Provide code for each failed preflight check DCB-1003
	* add UnknownItemStatusException when we cannot map a polaris local item status [DCB-879]

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* comment explaining why we added timeout for http client
* [Refactor]
	* allow tracking of virtual items with null local item ids in the TrackingServiceV3 [DCB-1036]
	* Folio transation status OPEN changed to map to DCB ITEM_TRANSIT [DCB-1021]
	* have difference between unahandled and unknown local item statuses in polaris [DCB-879]

### Fixes
* [General]
	* Improve distinction of library contacts [DCB-969]
	* Fix duplicate contacts [DCB-969]
	* Report specific error when Sierra local patron type is null or empty DCB-1004

## Version 6.1.0

### Additions
* [General]
	* Use problem title for error message when transitioning to error status
	* add override baseurl for polaris application services API

### Changes
* [Chore]
	* Changelog - Generate the changelog

## Version 6.0.2

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* Improve logging when there are problems with patron type mappings
	* ensure that checkout errors go to log and audit
	* more logging in polaris checkout
* [Refactor]
	* polaris patron checkout

## Version 6.0.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
* [Feature]
	* Allow ITEM_AVAILABLE when the borrower hold is MISSING to bypass the LOANED state in a borrowing system

### Fixes
* [General]
	* Exclude deleted items from live availability DCB-976

## Version 6.0.0

### Additions
* [Build]
	* **BREAKING** -  Restructure into sub projects
* [Tracking]
	* **BREAKING** -  Add a new tracking implementation and disable the previous implementation
* [General]
	* **BREAKING** -  Add partitions to data model and consolidate migrations
	* **BREAKING** -  Rename consortial FOLIO host LMS client DCB-774
	* **BREAKING** -  Expect FOLIO base-url without /oai path DCB-774
	* **BREAKING** -  Add new ingest source type for host LMS configuration DCB-739
	* **BREAKING** -  Disallow unknown hold policy for Sierra host LMS when placing request at supplying agency DCB-613
	* **BREAKING** -  Disallow unknown hold policy for Sierra host LMS during placing request at borrowing agency DCB-613
* [Auth]
	* Implement validatePatronByUniqueIdAndSecret option for patron auth
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
* [Tracking]
	* Improvements
* [General]
	* Use Host LMS from agency for items DCB-947
	* Ingest counts per second
	* Exclude items without an agency from resolution DCB-947
	* update a Polaris virtual item to pickup transit [DCB-950]
	* Confirm FOLIO supplying agency request immediately DCB-884
	* Startup defaults and JVM info sources
	* Fallback to general information field for language DCB-394
	* Split concatenated language codes during MARC ingest DCB-394
	* implement borrower ILL flow for polaris
	* DCB-918 add local-id to location modelling to allow DCB to reference host lms locations by their internal ID
	* JVM properties as source and startup reporter.
	* determine assigned branch id for Polaris virtual item creation [DCB-910]
	* Update item barcode uupon confirmation DCB-884
	* Wait for confirmation before placing request at borrowing agency DCB-884
	* Test integration that selects the correct TransactionManager
	* Search hooks
	* Distributed Federated locks in reactive flow
	* Secure the various internal endpoints by IP or ROLE_SYSTEM
	* Remove option to disable patron request preflight checks DCB-892
	* Record item barcode after Sierra title level request placed DCB-872
	* Timeout live availability requests with Unknown fallback status
	* Ingest terminator for graceful shutdown
	* Handle unexpected responses from Polaris DCB-855
	* concurrency groups
	* Make wildcard searches case insensitive by default.
	* Handle unexpected responses from Sierra DCB-849
	* Report unexpected response from Sierra when placing hold DCB-849
	* reworked cluster merging
	* close FOLIO borrowing library request when item has been received back [DCB-851]
	* Detect item returned by patron in FOLIO borrowing agency DCB-826
	* Detect when item has been borrowed by patron DCB-826
	* Detect when item has arrived at pickup location DCB-826
	* Reworked clustering
	* circulate requests involving consortial folio libraries
	* Detect item returned to FOLIO supplying agency DCB-825
	* Give sierra the capacity to use hierarchical mappings where only overrides need to be explicit for a system and default consortial mapping can be used for most sites
	* Implement context hierarchy lookups and tests
	* Detect missing request in FOLIO DCB-825
	* Detect FOLIO request has been cancelled DCB-827
	* Index on derived type
	* Detect FOLIO request has been placed in transit DCB-825
	* place request in borrowing library on folio system [DCB-492]
	* Place request at FOLIO supplying agency
	* Add check for year as volume from polaris
	* Add ability to stop reindex job
	* Add code to index document template.
	* validate patron in folio borrowing system [DCB-766]
	* handle patron authentication for folio [DCB-765]
	* implement polaris client override methods deleteItem and deleteBib [DCB-789]
	* add a barcode field when creating a patron in polaris
	* Strip punctuation from the subject labels.
	* Map FOLIO material type name to canonical item type DCB-479
	* Map location to agency for items from FOLIO DCB-479
	* Fallback mapping for FOLIO item statuses DCB-479
	* Drop instances with invalid OAI-PMH identifier during FOLIO ingest DCB-797
	* Parse OAI identifier for instance ID during FOLIO ingest DCB-797
	* checkOutItemToPatron [DCB-743]
	* patron cancels polaris request [DCB-529]
	* implement circulation polling and transition for polaris requests [DCB-528]
	* place request in borrowing library on a polaris system [DCB-485]
	* Added authentication for Search Index.
	* Added reindex job to admin controller.
	* Create index for OS or ES on startup.
	* Configurable number of retry attempts for getting patron holds from Sierra
	* Background job for indexing items.
	* Shared index service
	* Optionally disable patron resolution preflight check DCB-612
	* Optionally disable pickup location to agency preflight check DCB-612
	* Optionally disable pickup location preflight check DCB-612
	* Default hold policy for Sierra client to item DCB-613
	* Choose between placing title or item level request in borrowing agency DCB-613
	* Optionally disable preflight all checks DCB-612
	* Check requesting patron's local patron type is mapped to canonical DCB-530
	* Check requesting patron is recognised in host LMS DCB-531
	* Check requesting patron's host LMS is recognised DCB-531
	* Report failed preflight checks in event log DCB-532
	* Find location by ID when code is a UUID during agency mapping preflight check DCB-519
	* Find location by ID when code is a UUID during preflight pickup location check DCB-519
	* Find agency mapping in requestor host LMS context during preflight check DCB-519
	* Find agency mapping in explicit pickup location context during preflight check DCB-519
	* Fail preflight checks when pickup location is mapped to an unrecognised agency DCB-519
	* Fail preflight checks when pickup location is not mapped to an agency DCB-519
	* Fail preflight checks when pickup location is not recognised DCB-519
	* Add DcbInfoSource
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
	* Make updatePatronRequest return the UUID of the PR and not the full object
	* Additional logging supplier item tracking
	* Add nextScheduledPoll to schema [DCB-989]
	* Extend skipped loan detection to RET-LOCAL workflow
	* Include missing borrower items in guard condition for skipped loan
	* add HandleBorrowerSkippedLoanTransit to try to recover from a missed loan phase or a cancellation return
	* remove unused WorkflowAction
	* remove residual workflow actions
	* Added dev durations to tracking helper
	* Extend checkOutItem to have item barcode and patron id available ready to address DCB-983
	* Disable 2 supplying agency tests until we can look at them on tuesday
	* Extra logging when a patron type changes
	* more logging for HandleSupplierInTransit
	* Additional logging around isApplicable for HandleSupplierInTransit
	* Surface suppressed flag in RTAC results
	* preparation for tracking refactor
	* Added DummyRequestData class to DummyHostLms to be able to track requests
	* make next scheduled check nullable
	* Added previous state and is loaned to vpatron props
	* Added tracking helpers
	* add missig migration
	* Preparation for pre scheduled tracking, paused requests and needs attention
	* Add location.localId to graphql schema
	* Sierra now tracks the highest record timestamp seen at ingest time in preparation for refining the delta process
	* comment out graalvm jar it causes the native image build to explode
	* use concatMap when tracking rather than constrained flatmap
	* Added necessary options for larger zip file archives
	* Stop explicitly specifying db pool size and instead compute defaults from allocated cores
	* add timers to tracking service
	* signpost for todo needed on hierarchical host LMS setup
	* Wire in remaining test backoff poll calls
	* allow awaiting pickup at borrower to progress in transit
	* Allow virtual items to be tracked even in a missing state
	* remove another extra audit message, use ctx.workflowMessages instead. Tone down logging on polaris url factory
	* additional logging
	* Reinstate flag to not reflect loaned back to polaris, remove additional excessive audit messages
	* remove excessive state change message
	* Bump commit to force CI
	* push nullsafe protection down into sierra client
	* Back off nullsafe protections in tracking code
	* preparation for hold cleanup and defensive code on action tracking
	* tidy
	* more logging
	* Refactor to allow pickup location to propagate to borrowing system hold
	* logging for new tracking options
	* add missing migration file
	* Report host lms code when constructing the adapter to aid in troubleshooting from logs
	* add update methods to patron request repository for more granular polling control
	* add update methods to supplier request repository for more granular polling control
	* Add extra tracking states to test
	* Add LOANED and AVAILABLE to list of tracked states on supplier item
	* Add fields to enable more fine grained control of the polling period for downstream systems
	* Add an audit message on validation
	* enable requests to be completed without a return transit
	* stop emitting guard passed audit message - it's confusing
	* revert change which required a bib hold to be converted to an item hold within the retry period - we can't force that and need to be able to cope with this happening asynchronously
	* Include item information when fetching Polaris hold DCB-844
	* Consider on hold item request from Sierra confirmed DCB-884
	* Fetch Sierra item after fetching hold DCB-884
	* Use elvis operator for interpreting item ID from Sierra hold DCB-884
	* Use derestify method for interpreting Sierra hold ID DCB-884
	* Added the ability for state changes to have additional properties
	* added micronaut.http.client.connect-ttl to application.yml
	* Raise error when Sierra hold status is null or empty DCB-884
	* make place request endpoint callable by any authenticated user not just administrator role
	* Disable DCB as a Token issuer
	* Map pending Polaris hold to confirmed local status DCB-884
	* Use property accessor to get Polaris hold properties DCB-884
	* Map created folio transaction to confirmed local status DCB-884
	* Introduce dedicated workflow action for confirmed supplier request DCB-884
	* Replace spaces with tabs in unhandle state action DCB-884
	* Remove typo from get tracking record type method name DCB-884
	* Describe why audit method in Host LMS reactions has to be public
	* Introduce confirmed Host LMS request status DCB-884
	* tune timeout on RTAC api call - 7s instead of 5
	* logging around missing patron hold request tracking
	* Extra hint that problem props are supplier side
	* remove dcbContext from problem - it's too verbose. Transactional annotation for audit log message
	* Reinstate getting item barcode for placed Sierra hold DCB-872
	* remove unused config value - default-patron-home-location-code
	* Remove unused constructor parameter in place request at borrowing agency transition
	* back out sierra retrieve-item-after-hold change - sierra is not behaving as expected for this approach - back to item level requesting
	* null item id protection
	* Improve error message when Sierra hold record number is invalid integer DCB-872
	* Remove unused state transition method in supplier request DCB-872
	* refactor HandleSupplierItemAvailable.java - WIP
	* Revert to using requesting agency when placing requests in sierra libraries
	* Preparation for LocalRequest being able to return the requested item barcode and id when placing a bib level hold. Non-null return values should overwrite values in the supplier request when placing a request at a supplying agency
	* Remove redundant generic type in application services auth filter
	* Remove redundant static modifier on patron request status enum
	* Replace spaces with tabs in workflow service
	* Remove unused string utility methods
	* Wrap long lines in audit service
	* Reduce duplication in property access utility methods DCB-855
	* Include request path in unexpected response problem parameters DCB-855
	* Add log message for when getting request defaults from Polaris fails DCB-855
	* Remove unused patron find method in Polaris Host LMS client DCB-855
	* Reformat PAPI auth filter DCB-855
	* Add a get record count report method to the BibRepository in preparation for API endpoint that reports bib record count per source system
	* Minor refactor of HandleSupplierInTransit to allow for onError to report problems setting transit on downstream systems
	* Defensive code around nulls in audit data
	* correct tracker refdata tests
	* Include patron as audit data when virtual patron creation in Polaris fails
	* Make HTTP request related methods in Polaris client package-private DCB-855
	* Include patron in error when creating virtual patron in Polaris
	* Improve error message when home patron identity cannot be found
	* Handle unexpected responses during retrieve operations to Polaris DCB-855
	* correct exception message
	* Remove unecessary casts when reading Polaris client config DCB-855
	* Replace spaces with tabs in application services client DCB-855
	* Make types used in PAPI client interface public DCB-855
	* Remove unused field in Polaris Host LMS client DCB-855
	* Save audit data with audit record DCB-849
	* Replace spaces with tabs in request audit service DCB-849
	* Make request workflow context serdeable DCB-849
	* Include parameters from underlying problem when placing request at supplying agency DCB-849
	* Include json request body for unexpected response DCB-849
	* Include text request body for unexpected response DCB-849
	* Tolerate no request body for unexpected response DCB-849
	* Tolerate no request or host LMS code for unexpected response DCB-849
	* add log for debugging duplicate error audit
	* Reformat get method in Sierra client DCB-849
	* Rename generic type arguments when making request DCB-855
	* Handle unexpected responses when getting RTAC holdings from FOLIO DCB-855
	* Additional logging around state saving for FOLIO
	* Move detection of unexpected response from Sierra to lower level client DCB-849
	* Use request path in title for unexpected response from unknown host LMS DCB-849
	* Use request URI in title for unexpected response from unknown host LMS DCB-849
	* Remove unused unexpected response exception DCB-849
	* Include request method in unexpected response problem DCB-849
	* Parse unexpected text response body as a string DCB-849
	* Parse unexpected JSON response body as map DCB-849
	* Include unexpected response status code in problem DCB-849
	* report problem for unexpected response when creating FOLIO transaction DCB-849
	* insert a function to report if we're about to create a new cluster when one already exists for a bib
	* extra info in log messages
	* Remove unused fields in borrower request return transit handler
	* Replace spaces with tabs in borrower request return transit handler
	* Replace spaces with tabs in host LMS item
	* Remove unused fields in supplier item state change handler
	* Make fields final in borrower item in unhandled state handler
	* Make fields final in borrower item missing handler
	* Replace spaces with tabs in borrower item missing handler
	* Make fields final in borrower item on hold shelf handler
	* Replace spaces with tabs in borrower item on hold shelf handler
	* Use string format placeholders in borrower item loaned handler
	* Make fields final in borrower item loaned handler
	* Replace spaces with tabs in borrower item loaned handler
	* Make fields final in borrower item available handler
	* Replace spaces with tabs in borrower item available handler
	* Remove unused field in borrower item in transit handler
	* Replace spaces with tabs in borrower item in transit handler
	* Removed unused field in handle borrower item received handler
	* Replace spaces with tabs in handle borrower item received handler
	* Tolerate null response when raising unexpected response exception DCB-849
	* Tolerate null request when raising unexpected response exception DCB-849
	* Include response in error message when unexpected response is received DCB-849
	* Detect unexpected HTTP response when creating FOLIO transaction DCB-849
	* Tone down some logging on stats service
	* Add zero length checking to resumption token
	* updated .gitignore
	* Remove remaining Character usage from any DTO that will be encoded as JSON
	* Add shutdown listener in preparation for cleaner shutdown handling
	* Return unmapped when transaction status is recognised yet unhandled DCB-825
	* Raise error when transaction status is not recognised DCB-825
	* Remove unused fields from supplier item available handler
	* Remove unused field in host LMS reactions
	* Add link to transaction status docs DCB-825
	* Make fields final in supplier in transit handler
	* Replace spaces with tabs in supplier in transit handler
	* Use switch expression for mapping FOLIO transaction status DCB-825
	* Map created transaction status to hold placed status DCB-825
	* Fetch transaction status from FOLIO DCB-825
	* More logging updates for clustering
	* Additional logging for cluster writing
	* Trying a cheeky way to force touching of a cluster record
	* preparatory work for multiple supplier mode
	* Add find by ID to supplier request repository
	* Tolerate finding no supplier requests for patron request
	* Use builder for defining Sierra code tuple
	* Replace spaces with tabs in Sierra code tuple class
	* Replace spaces with tabs in Sierra patron hold request object
	* Don't track supplier requests when the owning request is in a COMPLETE, FINALISED or ERROR state. When retrieving the state of a supplier hold, emit a state change to ERROR when we encounter an ERROR
	* Default polaris barcode prefix to DCB- if none specified
	* Replace spaces with tabs in handler supplier request placed workflow action
	* Refactoring around sort order
	* Make tracking service field final
	* Reformat tracking service
	* Remove empty init method from tracking service
	* Remove unused fields from tracking service
	* Attempt to address UnsupportedCharsetException: UTF-32BE in native exec by adding docker-native build option -H:+AddAllCharsets
	* Adjust oai parameter logging
	* Add some fields to error logging on Folio OAI source
	* Throw exception when relative URI does not start with a forward slash DCB-490
	* Log validation error when placing request at FOLIO supplying agency DCB-490
	* Log body of requests to FOLIO DCB-490
	* Log parameters when placing request at FOLIO supplying agency DCB-490
	* Pass patron group name when creating a FOLIO transaction DCB-490
	* Remove patron group ID from FOLIO user representation DCB-490
	* Map patron group name from FOLIO to local patron type DCB-490
	* Find local FOLIO patron type from canonical patron type DCB-490
	* Find canonical patron type for local FOLIO patron type DCB-490
	* Raise error when creating FOLIO transaction returns unauthorised response DCB-490
	* Raise error when creating FOLIO transaction returns not found response DCB-490
	* Raise error when creating FOLIO transaction returns validation response DCB-490
	* Pass generated service point ID based upon pickup agency code when creating a FOLIO transaction DCB-490
	* Pass pickup agency code as library code when creating a FOLIO transaction DCB-490
	* Pass pickup agency name as service point name when creating a FOLIO transaction DCB-490
	* Pass patron barcode when creating a FOLIO transaction DCB-490
	* Pass patron ID when creating a FOLIO transaction DCB-490
	* Pass item barcode when creating FOLIO transaction DCB-490
	* Supplying request in FOLIO uses lender role DCB-490
	* Pass item ID to FOLIO create transaction API DCB-490
	* Return hold placed local status for FOLIO requests DCB-490
	* Return transaction ID as local ID of FOLIO request DCB-490
	* Make basic request to create FOLIO DCB transaction DCB-490
	* Reformat determine patron type method DCB-490
	* Replace spaces with tabs in patron type service tests DCB-490
	* Try setting varFields messages when issuing sierra ItemPatch to clear in transit status per DCB-795
	* Change to polaris explicit volume field
	* logging around polaris item mapper, first pass volume normalisation in sierra
	* refactor extraciton of volume statement in sierra
	* refactor sierra item mapping logging
	* logging around index bulk ops and volume processing
	* Change apikey to header rather than parameter for FOLIO OAI - per EBSCO request today
	* Pass item barcode when placing request at supplying agency DCB-490
	* Add fallback error for when pickup location to agency mapping fails DCB-490
	* Pass pickup agency when placing request at supplying agency DCB-490
	* Add pickup agency to place request parameters DCB-490
	* Remove redundant modifiers for agency properties DCB-490
	* Replace spaces with tabs in workflow context DCB-490
	* Pass paron type and barcode when placing request at supplying agency DCB-490
	* Add barcode and host LMS to patron identity to string contents DCB-490
	* Incremental refactor preparing for supplier preflight
	* take heed of isActive flag on supplier request
	* more meaningful message when unable to map item type
	* Add patron request UUID to tracking record in preparation for writing audit records with respect to tracking events
	* Change hold status handling in polaris client to switch, add translations for Shipped to IN_TRANSIT, tidy indenting in tracking service
	* Factor in patron request when looking for tracked requests - don't watch anything in an ERROR state
	* Null-safe protection on patron barcode prefix for polaris
	* defensive code in polaris adapter to make sure items array length > 0
	* correct local item status name
	* Extra info to make grepping for onTrackingEvent more meaningful when watching a specific flow
	* adjust error logging in tracking code
	* Pass host LMS code as last parameter for multiple users error DCB-490
	* Include host LMS code in failed to get items exceptions DCB-479
	* Include host LMS code in FOLIO client log entries DCB-479 DCB-490
	* Include host LMS code in likely API key error DCB-479
	* Add patron request id to create item command so we can give more informative logging in failure scenarios
	* Map suppress from discovery for FOLIO items DCB-479
	* Remove preferred first name from FOLIO virtual patron's local names DCB-490
	* Generate new ID only when creating FOLIO virtual patron DCB-490
	* Rename variable for created virtual patron ID DCB-490
	* backout stats counter
	* default hazelcast constructor
	* Try specifying hazelcast servicelabelname in yaml file
	* remove unneeded hz config object
	* null detection around hazelcast
	* trying alternate hazelcast config
	* Return explict error for non-implemented FOLIO host LMS client methods DCB-490
	* Fail when no patron group to type mapping found DCB-490
	* Map FOLIO user patron group to canonical patron type DCB-490
	* Fail finding a virtual patron when requesting patron has no barcode DCB-490
	* Fail finding FOLIO users when API key is invalid DCB-490
	* Fail finding virtual patron in FOLIO when multiple users found DCB-490
	* Tolerate missing properties when mapping FOLIO user to patron DCB-490
	* Map all FOLIO user's names to local names DCB-490
	* Map FOLIO user's barcode DCB-490
	* Map FOLIO user's patron group to local patron type DCB-490
	* Return empty when no user found for barcode DCB-490
	* Include trace level request logging for FOLIO client DCB-490
	* Only accept JSON when finding users in FOLIO DCB-490
	* Use CQL to find FOLIO virtual patron by barcode DCB-490
	* Fetch users from FOLIO when finding virtual patron DCB-490
	* Extra error checking around POLARIS create patron - validate the response and check for a nonzero error code
	* Add some defers to empty handling to prevent premature side effects
	* Extra logging on polaris createPatron
	* add logging for creating patron in polaris
	* add log before local hold request is made (polaris)
	* Removed unused code in Sierra host LMS client DCB-490
	* Replace spaces with tabs in patron class DCB-490
	* log when supplying agency service clean up is called
	* refactor cancelled action a little
	* re Added hazelcast, added doOnError to cancel api endpoint for logging
	* toggle bib record creation - both work
	* Explore alternate way of populating member bibs for indexing
	* refactor some error scenarios to defer invoking the onError until it's actually needed
	* instrument item availability checks
	* Better logging on availability status checks
	* Disable case sensitivity for sierra userId lookup
	* tidy public interface for authv2 - use uniqueIds as a parallel for sierra
	* Check username returned by authV2 interface is aligned with public API
	* align auth and lookup methods on AuthV2 API
	* The AuthV2 iterface should return the username as it is known at the DCB boundary - i.e. prefixed with the agency
	* Additional info returning DCB patron info
	* Extra validation on authv2 methods
	* info logging on config import
	* Don't cancel a request after it's been finalised if we detect a missing hold
	* Add tests for AuthAPI v2
	* More logging refinements
	* more logging changes
	* tone down ingest logging
	* log level adjustments, some ingest infos change to trace
	* Reformat patron service DCB-490
	* Replace spaces with tabs in supplying agency service DCB-490
	* more logging refinements
	* more logging when exchanging messages with polaris for create item
	* logging for failure on create item
	* extra logging on polaris create item
	* Split finding agency by code and getting mapping value DCB-479
	* Use property accessor for location code when mapping to agency DCB-479
	* Unmapped FOLIO item type should be mapped to unknown canonical type DCB-479
	* Map null FOLIO item type to unknown canonical type DCB-479
	* Map FOLIO item type to canonical item type DCB-479
	* Remove unused dependencies for finalise request transition DCB-479
	* Warn when no reference value mapping found DCB-479
	* Should tolerate null location when enriching item with agency DCB-479
	* Use is empty collection method when getting items from FOLIO DCB-479
	* Use is empty method during FOLIO fallback item status mapping DCB-479
	* Add warning when attempting to map location to agency with empty from context DCB-479
	* Include host LMS code for items from FOLIO DCB-479
	* Map material type name to item type code for FOLIO items
	* Assume that items from FOLIO are not suppressed DCB-479
	* Include hold count in items from FOLIO DCB-479
	* Assume that items from FOLIO are not deleted DCB-479
	* Use material type rather than loan type for FOLIO item type DCB-479
	* Include location code in items from FOLIO DCB-479
	* Include barcode in items from FOLIO DCB-479
	* Include due date in items from FOLIO DCB-479
	* Fail when receive multiple outer holdings from RTAC DCB-479
	* Interpret holdings not found error as zero items assocated with instance DCB-479
	* Improve error message for likely invalid API key response from RTAC DCB-479
	* Fail when receive holdings not found error from RTAC DCB-479
	* Fail when receive instance not found error from RTAC DCB-479
	* Include errors in RTAC response classes DCB-479
	* Handle zero inner holdings returned from FOLIO DCB-479
	* Introduce limited FOLIO fallback mapping DCB-479
	* Map local FOLIO item status using reference mappings DCB-479
	* Introduce item status mapper dependency for FOLIO LMS client DCB-479
	* Add API key to FOLIO settings description DCB-479
	* Use client config for FOLIO base URL DCB-497
	* Use client config for FOLIO api key DCB-497
	* Map holdings from FOLIO to items DCB-479
	* Delete all host LMS before each FOLIO host LMS client test DCB-479
	* Remove commented out code when initialising ingest record DCB-797
	* Added some extra properties to polaris create item workflow request
	* change log level on auth by barcode+name fail
	* improve error logging from polaris
	* Replace spaces with tabs in Polaris host LMS client DCB-490
	* Enrich supplier request with patron request object when tracking - to enable audit logging
	* comment out aws logging jar
	* Comment out AWS log appender
	* logging adjustments to BorrowingAgencyService
	* don't pretty print json logs
	* Add missing dependency for json log formatting
	* more polaris logging
	* Switch logging to json, better message for failed to place supplier hold error
	* Add logging around URI resolution during FOLIO ingest DCB-774
	* Better error logging around create item in Polaris
	* Add @body annotation to lookup api call
	* update post annotation on user lookup
	* refactor validate by pin
	* more logging around user auth
	* rest annotations on new lookup methof
	* Add rest annotation
	* Work on workflow doc
	* Replace spaces with tabs in host LMS service
	* Add dependency on Sierra specific mapper from shared item mapper
	* Remove unused status mapping method when mapping items
	* move mapping around in ReferenceValueMappingService to provide more meaningful logging
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
	* Remove unused factory method for availability report
	* Reformat Polaris host LMS client
	* Reformat Sierra host LMS client
	* Remove use of record number when getting recently placed hold
	* add note on HTTP_HOSTS env vars
	* Add missing test resource loader file
	* Remove check in Polaris host LMS client for item level requests only
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
	* working on item patch
	* try adding an empty string to messages when updating item status
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
	* Create a new transaction for each of the tracking events
	* add logger when subscribe complete for tracking calls
	* Indexing tweaks
	* Case insensitivity
	* Change page size to integer property DCB-739
	* Change page size Sierra setting to optional DCB-739
	* Change type of get holds retry attempts setting
	* Reformat host LMS property definition DCB-479
	* Reformat Sierra client settings
	* Tweaks to keep the Java compiler happy.
	* More tuning for logging
	* refine logging
	* turn down logging
	* Use toState() rather than strings when updating local reflections of remote states
	* Extend Item mappings for sierra
	* Refactor supplier checkout so we can set the item state before attempting the checkout
	* graphql - Add default sort order to hostlmss, patronrequest and supplierrequest
	* rename patronIdentity upsert method to clarify what it's doing
	* improve error trapping in sierra checkOut
	* more warn logging in unexpected conversion scenarios
	* warn when we try and convert an item record which is missing key properties
	* Split out parsing of patron barcode, add comment explaining what the string manipulation is about
	* Remove unused code from FOLIO LMS client DCB-479
	* Use annotation for log in host LMS fixture DCB-479
	* Remove get all bib data method from host LMS client interface
	* Add some more unique words to test data generators
	* Reformat production of invalid hold policy error DCB-613
	* Rename variables for record number and type when placing request at supplying agency DCB-613
	* Add method for item level request policy to host LMS client interface DCB-613
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
	* db - Add missing unique constraint to host lms, date fields for agency, protocol fields for paronRequest and supplierRequest
	* Improve logging on item fetch failure
	* clean up logging
	* scripts - fix graphql_hostlms script - hostlms doesn't have dates
	* graphql - Add created and modified dates to patron request
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
	* Add supplier request scalar fields to graphql schema
	* More deliberate failure and exception reporting when unable to map a patron type
	* Add script for fetching info from UAT environment
	* Bit of a messy merge :(
	* Replace spaces with tabs in workflow context helper DCB-519
	* ingest - Add status to ingest state to make it clear when a process is RUNNING or COMPLETE
	* Remove erroneous comment in patron request controller DCB-519
	* Fix formatting in place patron request command DCB-519
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
	* defer creation of invalid response when doing patron auth
	* extend bib data in cluster response
* [Feature]
	* Allow sierra RECEIVED item state to trigger completion from RETURN_TRANSIT
	* /patrons/requests/{patronRequestId}/update
	* Use location@agency as pickup location when placing a supplier request at a sierra agency
	* DCB-939 - graphql endpoint for pickup locations sorted by local agency locations first
	* Use more descriptive note text
	* Introduce common function to generate more descriptive note to request workflow context
	* Add backoff timing on tracking service
	* Allow an item state of LOANED to allow the RECEVED flow to trigger - letting us handle a failed detection of check-in
	* DCB-926 add remaining tracking
	* DCB-925 first part of tracking improvements
	* DCB-926 add domain model fields for better control of poll intervals when tracking
	* DCB-919 Add pickup location fields in to request workflow context
	* add method to HostLmsClient which controls if borrower checkouts should be reflected back to the supplying library
	* Add record counts to admin endpoint, add workflow messages to request workflow context
	* Propagate hold item ID through tracking service
	* Updates to workflow to allow bypass of borrowing library hold states and jump directly to LOANED from IN_TRANSIT
	* Refactor to cleanly decouple tracking service and state model, merge location participation database changes.
	* Add NumericRangeMappingController
	* When placing a hold in sierra, examine the returned hold structure and take note of the requested itemId - propagate that back and record it in the supplier request. If the hold was a bib hold, this will be the actual item requested
	* DCB-877 for sierra supplier use item location as pickup location
	* Add localItemLocationCode to PlaceHoldRequestParameters - allows a supplying agency hold to use the current location of the item as a pickup location at the supplying library
	* Re-apply the transactional annotation needed to stop Folio OAI from deadlocking - adding additional annotations needed to stop non-folio tests from deadlocking
	* add transition to pickup_transit in audit log
	* re-enable parallel processing of ingest items. TRIAL
	* Add 245 to goldrush key
	* Add ability to set eager_global_ordinals in ES schema declaration
	* Watch app shutdown status and exit any ingest sources when shutdown detected
	* Add sort direction to GraphQL [DCB-480]
	* sierra - When parsing volume statements, handle no. as number and normalise to n in parsed volume statement
	* Improve audit logging on circulation of home item to vpatron for DCB-838
	* Order audit rows by date in graphql patron request response
	* Add volume designator to various API responses and DTOs
	* first pass at parsing polaris call numbers containing volume information
	* Collect raw sierra volume var field and attach to Item records
	* Implement tracking service audit logging
	* Add UniqueID+NAME authentication to sierra host lms for WASHU
	* Added handlers for supplier request cancelled and for unhandled state transitions in supplier requests - a noop that will just update the supplier request record
	* Understand label / comment field in reference data import
	* When creating an item in SIERRA, Read back sierra create item before contunuing
	* trial adding stats to indo endpoint
	* evict member info if we detect a node is no longer present. Trialling leader election for cluster operation
	* Add the node start date and if the node is running scheduled tasks to the cluster information shared via hazelcast. Subsequently, these should now appear in the app info endpoint giving the admin app visibility of all DCB nodes
	* Use hazelcast initially to maintain a map of DCB nodes which are reported back in the info endpoint
	* Fill out cleanup transition
	* domain class for delivery network edge
	* Allow a HUB location type to be posted which does not have an attached agency. Tidy up some spacing in Location model class
	* Replace mapping table handling of pickup locations ONLY with direct references to UUIDs from the Location table
	* Return username and userid in Authv2 LocalPatronDetails
	* Add variable field support to sierra adapter and request varFields when fetching bibs
	* Add v2 auth api which provides domain style login identities
	* Add auth v2 controller
	* graphql - Add locations to agency
	* graphql - Add data fetcher for reqesting patron identity
	* NONCIRC items are not requestable
	* lookup user by username
	* graphql - Add ReferenceValue and NumericRangeMapping queries
	* Add BibRecord to PatronRequest GraphQL [DCB-748]
	* graphql - Add virtual patron identity to supplier request
	* graphql - Location data fetchers
	* Disable auto location configuration for sierra. data too messy to rely upon, locations don't have agencies attached
	* Shipping and Routing Labels
	* Administrative metatdata for Location imports
	* Add order clause to graphql schema, implement for agencies and default to name
	* Check for existing mapping data on import [DCB-603]
	* add RequesterNote to patron request
	* Clear out messages when updating item status
	* add messaging to cancellation handler
	* graphql - Add patronRequest to supplierRequest type
	* Added env code and env description to info endpoint for display
	* Set a supplier item status to RECEIVED before attempting to check it out
	* graphql - Add audit query to graphql
	* When creating a (vpatron) in sierra, allow the caller to specify an expiryDate or default it to 10 years from now
	* Create endpoint to import uploaded mappings [DCB-527]
	* graphql - Convert hostLms props, PatronRequestAudiy props and canonical record to JSON scalar type
	* enable graphql scalar type and use it for raw record
	* sierra - Add Fixed Fields when pulling bibs from Sierra in anticipation of involving 031-BCODE3 in the decision to suppress or not. There is a do not submit to central flag we need to observe
	* graphql - Add PatronRequestAudit into graphql model and attach to PatronRequest object
	* graphql - Add supplierRequests to patronRequest
	* info - Add ingest and tracking intervals to info endpoint
	* Flexible handling of pickup location context - allow the place request command to optionally specify a context for the pickup location, if not provided default to the patron home context
	* Add canonicalPType field to interaction Patron and set this coming out of the Sierra host LMS adapter
	* graphql - Add ProcessStatus query to graphql endpoint
	* ingest - Add human readable timestamps HRTS to process state for sierra to make it eaiser to diagnose harvesting issues
	* Change patron type lookups to range mapping
	* Add pickup location code context to data model
	* auth - Implement UniqueID+Name validation method
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
	* revise place hold method in polaris
	* add HostLmsItem status Available to applicable for HandleBorrowerRequestReturnTransit
	* Remove map to hold status only when fetching hold from Polaris DCB-884
	* Pass hold instead of only status when determining status DCB-884
	* Extract method for determining Sierra hold status DCB-884
	* Introduce guard clause for empty hold response from Sierra DCB-884
	* Return publisher when mapping Sierra hold to request DCB-884
	* handle virtual item with duplicate barcode - polaris workflow [DCB-809]
	* Make tracking record an interface DCB-884
	* Move Host LMS reactions class DCB-884
	* Move null if empty method to utility class
	* add item id and item barcode to LocalRequest (Polaris)
	* Audit logging in HandleSupplierItemAvailable - we cannot use .subscribe inside a stream so the audit messages are not being logged
	* Extract method for adding body to HTTP request
	* Use instance of pattern matching to remove explicit cast to problem
	* Extract method for truncating string to a maximum length
	* added StringUtils methods for removing brackets
	* use exception for multiple virtual patrons found
	* Move specific handling for patron blocks to error transformer parameter DCB-855
	* Introduce an error handling transformer for Polaris retrieve method DCB-855
	* Move iterable to array method to utility class
	* patron type exception messages [DCB-867]
	* Use common method for getting services config in application services auth filter DCB-855
	* Use common method for getting services config in application services client DCB-855
	* Extract method for getting PAPI config DCB-855
	* Move service config method to Polaris Host LMS client DCB-855
	* Extract method for making patron search request in Polaris DCB-855
	* Extract method for getting Polaris Host LMS client services config DCB-855
	* Extract method for defining empty PAPI client credentials DCB-855
	* Use error handler to handle no records error from Sierra DCB-849
	* Remove optional execute of error handling transform when retrieving from Sierra DCB-849
	* Provide no extra error handling when retrieving from Sierra DCB-849
	* Explicitly remove cached token when retrieve receives unauthorised response DCB-849
	* Explicitly remove cached token when exchange receives unauthorised response DCB-849
	* Introduce parameter for error handler when retrieving from Sierra DCB-849
	* Move detecting unexpected response from FOLIO to common request method DCB-855
	* Move detecting unathorised response from FOLIO to common request method DCB-855
	* Provide error handler when creating a FOLIO transaction DCB-855
	* Raise specific exception when transaction not found in FOLIO DCB-855
	* Extract parameter for an error handler when making FOLIO HTTP request DCB-855
	* Extract method for finding FOLIO user by barcode DCB-855
	* Extract method for interpreting unexpected response body DCB-849
	* Move unexpected response problem creation to separate class DCB-849
	* use patron barcode not username for patron lookup in folio client
	* replace not implemented error with NOOP when attempting to finalise folio related requests [DCB-850]
	* Introduce overload for raising unexpected response exception without a request DCB-849
	* Move HTTP to log output methods to separate class DCB-849
	* Rename local item ID parameter when getting items DCB-825
	* Introduce parameter for request ID when getting item from host LMS DCB-825
	* Rename get hold method to get request DCB-825
	* Move finding canonical FOLIO patron type implementation to client DCB-490
	* Extract method for creating FOLIO transaction DCB-490
	* Remove supplier host LMS code parameter DCB-490
	* Remove reference value mapping service parameter DCB-490
	* Push find local patron type implementation down to specific clients DCB-490
	* Move finding local patron type to host LMS client interface DCB-490
	* Get supplying host LMS client before mapping patron type DCB-490
	* Extract method for finding local patron type DCB-490
	* Extract method for finding canonical patron type DCB-490
	* Remove host LMS code parameter for finding canonical patron type DCB-490
	* Move finding canonical patron type to host LMS client DCB-490
	* Introduce patron type mapper dependency to Dummy host LMS client DCB-490
	* Introduce patron type mapper dependency to Polaris host LMS client DCB-490
	* get requesting host LMS client before finding patron type DCB-490
	* Introduce host LMS service dependency for patron type service DCB-490
	* Extract method for creating unable to place request problem DCB-490
	* Use common predicate for detecting unauthorized response from FOLIO DCB-490
	* Extract methods for HTTP status predicates DCB-490
	* Move common HTTP response predicates to separate class DCB-490
	* Move non-null values list method to collection utils class DCB-490
	* Extract method for mapping FOLIO user to patron DCB-490
	* Introduce parameter for query when finding FOLIO users DCB-490
	* Move method for creating exact equality query DCB-490
	* Introduce CQL Query class DCB-490
	* Extract method for constructing CQL query to find users DCB-490
	* Extract method for mapping first FOLIO user to patron DCB-490
	* Use flat map to map FOLIO users to a patron DCB-490
	* Extract method for making HTTP requests in FOLIO client DCB-490
	* Extract method for defining authorised FOLIO request DCB-490
	* Extract method for finding users in FOLIO DCB-490
	* pass along local item id when creating patron [DCB-809]
	* revert patron code back in polaris client
	* add default patron code when creating a patron in Polaris
	* add the item location id during the place hold request
	* Remove unique ID path from Sierra patron auth DCB-490
	* Inline patron auth when finding Sierra virtual patron DCB-490
	* Separate pathon auth and finding virtual patron in dummy host LMS DCB-490
	* Remove patron find path from patron auth DCB-490
	* Get local barcode from home identity when finding virtual patron DCB-490
	* Throw specific exception when patron does not have home identity DCB-490
	* Extract method for getting home identity DCB-490
	* Return null unique ID when no home identity DCB-490
	* Push find virtual patron implementation down to each client DCB-490
	* Introduce method for finding virtual patron in host LMS client DCB-490
	* Remove redundant zip when finding virtual patron DCB-490
	* Remove intermediary unique ID method when creating virtual patron DCB-490
	* Remove intermediary unique ID method when checking virtual patron exists DCB-490
	* Move getting unique ID to patron class DCB-490
	* Use identities from patron when determining unique ID DCB-490
	* Extract method for determining unique ID for patron DCB-490
	* Move finding resolved agency from identity to service DCB-490
	* Remove location repository dependency from context helper DCB-490
	* Use service to find pickup location in context DCB-490
	* Add location service dependency to context helper DCB-490
	* Remove host LMS repository dependency from context helper DCB-490
	* Use service to find host LMS for agency in context DCB-490
	* Add host LMS service dependency to context helper DCB-490
	* Remove patron repository dependency from context helper DCB-490
	* Use service to find patron for context DCB-490
	* Add patron service dependency to context helper DCB-490
	* Move finding agency by ID to service DCB-490
	* Make previous status mapping method private DCB-479
	* Extract method for mapping an item status with a due date DCB-479
	* Extract method for mapping item status without a due date DCB-479
	* Extract method for finding item type mapping DCB-479
	* Use service when mapping item status DCB-479
	* Improve logging when finding mapping with no target category DCB-479
	* Move method for finding mapping without target category to service DCB-479
	* Extract method for finding mapping without target category DCB-479
	* Use service to map item type when creating an item in Sierra DCB-479
	* Use service to map item type when creating an item in Polaris DCB-479
	* Use service to map location when placing a request at borrowing agency DCB-479
	* Use service to map location to agency during resolution DCB-479
	* Use service for finding mappings during patron validation DCB-479
	* Move finding pickup location to agency mapping to service DCB-479
	* Add location to agency service as workflow context dependency DCB-479
	* Move mapping pickup location to agency to dedicated service DCB-479
	* Introduce method for consuming successful reactive stream DCB-479
	* Extract method for finding reference value mapping DCB-479
	* Use property access utility to get item location code DCB-479
	* Add location to agency mapping service dependency for client DCB-479
	* Move getting property from nullable object to util class
	* Extract method for whether multiple outer holdings are returned by RTAC DCB-479
	* Extract method for determining if errors are holdings not found DCB-479
	* Extract method for whether RTAC response has any outer holdings DCB-479
	* Move likely invalid API key check to initial response check DCB-479
	* Extract method for whether RTAC response has no errors DCB-479
	* Rename method for checking RTAC response DCB-479
	* Move unknown fallback status mapper to tests only
	* Extract method for defining mock response for holdings DCB-479
	* Map a holding to an item using a publisher DCB-479
	* Move get config value to host LMS property definition DCB-479
	* Define FOLIO settings as fields DCB-479
	* Move method for mocking RTAC holdings response to fixture DCB-479
	* Make get holdings method in FOLIO client private DCB-479
	* Introduce parameter for instance ID when getting FOLIO holdings DCB-479
	* Add HTTP client field to FOLIO LMS client DCB-479
	* Move getting holdings to FOLIO LMS client DCB-479
	* Introduce parameter for http client when getting holdings DCB-479
	* Extract method for mocking RTAC holdings response using mock server DCB-479
	* Move FOLIO RTAC serialisation types to production code DCB-479
	* Extract method for getting holdings from FOLIO DCB-479
	* Remove previous method for placing request in host LMS DCB-490
	* Introduce method for placing request at borrowing agency DCB-490
	* Introduce method for placing request at supplying agency DCB-490
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
	* Use lombok to generate getters for config host LMS properties DCB-739
	* Use for each to save all config host LMS DCB-739
	* Move save or update of host LMS to repository DCB-739
	* Extract method for saving config host LMS DCB-739
	* Accept list of config host LMS during startup DCB-739
	* Remove unecessary introspection from config host LMS DCB-739
	* Make get all host LMS private DCB-739
	* Accept only config host LMS during startup DCB-739
	* Move method for getting ingest source by code to host LMS service DCB-739
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
	* Rename save host LMS method in fixture DCB-479
	* Reduce access to methods in host LMS fixture DCB-479
	* add DCB prefix to unique id generation
	* Use optional when checking hold policy DCB-613
	* Extract method for checking hold policy in Sierra host LMS client DCB-613
	* Use common policy for title level requests when placing at supplying agency DCB-613
	* Rename method for checking title level request policy when placing requests DCB-613
	* Move implementation of title hold policy to host LMS client implementations DCB-613
	* Move title hold policy decision to host LMS client interface DCB-613
	* Move getting hold policy from config to extracted method DCB-613
	* Extract method for whether policy is to place title hold DCB-613
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
	* Define event as a value object DCB-532
	* Extract method for creating an event from a failed check result DCB-532
	* Define event type as a enum DCB-532
	* Introduce method for reporting failed checks in the event log DCB-532
	* Add event log repository dependency to preflight checks service DCB-532
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
	* Move method for finding pickup location to agency mapping to service DCB-519
	* Extract method for finding pickup location to agency mapping DCB-519
	* Use value object instead of record for response from place patron request API DCB-519
	* Remove mapping to HTTP response when placing a request DCB-519
	* Change place patron request command to value object rather than record DCB-519
	* Use tuple when placing initial patron request DCB-519
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
	* Demonstrate fetch FOLIO request does not have item information DCB-884
	* Demonstrate empty hold being returned by Sierra DCB-884
	* Mock getting item after getting hold from Sierra DCB-884
	* Check for item ID when getting request from Sierra DCB-884
	* Use title level hold when detecting request is placed at supplying agency DCB-884
	* Demonstrate getting item level request from Sierra DCB-884
	* Demonstrate getting title level request from Sierra DCB-884
	* Demonstrate detection of expected local status changes DCB-884
	* Remove Polaris hold example json DCB-884
	* Use serialisation to provide mock response for Polaris hold DCB-884
	* Include item ID and barcode in example Polaris hold used in mocks DCB-884
	* Check item properties for placed hold in Polaris DCB-884
	* Check supplier request local status changed to confirmed DCB-884
	* Check audit record is recorded after confirmation DCB-884
	* Pass patron request with confirmed state change DCB-884
	* Pass supplier request with confirmed state change DCB-884
	* Invoke Host LMS reactions during confirmation test DCB-884
	* Delete all Host LMS before confirmation reaction tests DCB-884
	* Delete all agencies before confirmation reaction tests DCB-884
	* Delete all supplier requests before confirmation reaction tests DCB-884
	* Delete all patron requests before confirmation reaction tests DCB-884
	* Introduce test class for reactions to supplier request confirmation DCB-884
	* Use fixture to create agency before fetching all agencies DCB-884
	* Use fixture to create host LMS when creating agency DCB-884
	* Make agency repository tests independent DCB-884
	* Demonstrate getting item after hold placed tolerates not found response DCB-872
	* Limit mock expectations for getting holds from Sierra to single time DCB-872
	* Stop removing existing mock expectations for getting holds from Sierra DCB-872
	* Rename methods for mocking get holds for patron from Sierra DCB-872
	* Reinstate check on item ID after request is placed DCB-872
	* Demonstrate placing a title level hold in Sierra DCB-872
	* Rename method for mocking place hold request in Sierra DCB-872
	* Remove unecessary sneaky throws from place hold request in Sierra supplying agency test DCB-872
	* Demonstrate patron request status changes to error when error occurs in reactive chain
	* Delete all patron requests before workflow service tests
	* Add patron request workflow service test class
	* Check request URL included in unexpected response problem DCB-855
	* Demonstrate failure when finding a patron in Polaris returns server error DCB-855
	* Rename Polaris LMS Host test methods DCB-855
	* Make methods in Polaris Host LMS client tests package-private DCB-855
	* Demonstrate placing a hold in Polaris failing DCB-855
	* Demonstrate failure when creating virtual item in Polaris DCB-855
	* Use specific start workflow mock when creating item in Polaris DCB-855
	* Verify when a patron search happens in Polaris DCB-871
	* Demonstrate specific failure when receive server error response for patron blocks from Polaris DCB-855
	* Demonstrate tolerance of patron blocks not being found in Polaris DCB-855
	* Demonstrate unexpected response when creating a bib in Polaris DCB-855
	* Demonstrate unexpected response when getting an item from Polaris DCB-855
	* Extract method for mocking starting a workflow in Polaris DCB-855
	* Extract method for mocking continuing a workflow in Polaris DCB-855
	* Remove unused mocking of workflow when deleting item in Polaris DCB-855
	* Extract method for mocking creating a bib in Polaris DCB-855
	* Extract method for mocking placing a hold in Polaris DCB-855
	* Extract method for mocking check out item to patron in Polaris DCB-855
	* Extract method for mocking get bib from Polaris DCB-855
	* Extract methods in Polaris mock fixture to reduce duplication DCB-855
	* Extract method for mocking get item barcode from Polaris DCB-855
	* Extract method for mocking update patron in Polaris DCB-855
	* Extract method for mocking get patron barcode from Polaris DCB-855
	* Extract method for mocking create patron in Polaris DCB-855
	* Extract method for mocking get hold from Polaris DCB-855
	* Change patron ID parameter type from int to string DCB-855
	* Extract method for mocking get patron block summary from Polaris DCB-855
	* Extract method for mocking get item from Polaris DCB-855
	* Extract method for mocking get patron by barcode from Polaris DCB-855
	* Extract method for mocking patron authentication for Polaris DCB-855
	* Extract method for mocking get item statuses from Polaris DCB-855
	* Extract method for mocking get material types from Polaris DCB-855
	* Extract method for mocking get items for bib from Polaris DCB-855
	* Extract method for mocking application services auth in Polaris DCB-855
	* Remove use of Polaris test utils in ingest tests DCB-855
	* Extract method for mocking Polaris PAPI staff auth DCB-855
	* Move mocking paged bibs from Polaris to fixture DCB-855
	* Remove ordering from Polaris ingest tests DCB-855
	* Use test resource loader in Polaris ingest tests DCB-855
	* Removed unused code from Polaris ingest tests DCB-855
	* Inline use of Polaris test utils inside fixture DCB-855
	* Remove unused auth code in Polaris test utils DCB-855
	* Use utility method for getting value from publisher in Polaris client tests DCB-855
	* Move creation of resource loader to mock fixture DCB-855
	* Extract method for mocking patron search in Polaris DCB-855
	* Move mock methods from Polaris tests to fixture DCB-855
	* Check for audit data when placing request at supplying agency fails DCB-849
	* Extract matchers for audit data DCB-849
	* Demonstrate adding an audit entry for an error DCB-849
	* Demonstrate login failure in Sierra Host LMS client DCB-849
	* Rename place request at supplying agency test class DCB-849
	* Vary property types in unexpected json response DCB-849
	* Introduce matcher for each json property in unexpected response DCB-849
	* Extract matcher for problem parameters DCB-849
	* Introduce tests for mapping response exception to unexpected response problem DCB-849
	* Match on throwable problem rather than default problem DCB-849
	* Move unexpected response matchers to separate class DCB-849
	* Match properties for exceptions extending throwable DCB-849
	* Demonstrate unexpected response from Sierra DCB-849
	* Use host LMS item matchers in Polaris tests DCB-825
	* Move host LMS item matchers to separate class DCB-825
	* Add folio interaction package to commented out log settings DCB-825
	* Extract method for mocking authorised request to FOLIO DCB-825
	* Use matchers when getting request from Polaris DCB-825
	* Move host LMS request matchers to separate class DCB-825
	* Initial test to demonstrate detecting request has been placed DCB-825
	* Introduce method for creating supplier request during tracking tests
	* Delete unused json Sierra hold examples
	* Use more varied local request IDs in tracking tests
	* Move local status matcher to separate class
	* Demonstrate detection that request has been placed at supplying agency
	* Add find by ID to supplier request fixture
	* Introduce parameter for get hold by ID response from Sierra
	* Use serialisation for mock get Sierra hold by ID response
	* Extract method for get Sierra hold by ID request matching
	* Rename methods for mocking get Sierra hold by ID
	* Remove unused method for mocking get item from Sierra
	* Use serialisation for item fetched from Sierra in place request at borrowing agency tests
	* Use serialisation for item fetched from Sierra in patron request API tests
	* Delete all locations before patron request API tests
	* Use serialisation for get item from Sierra responses during tracking tests
	* Extract variables for local record IDs at borrowing agency
	* Define separate supplying agency host LMS for tracking tests
	* Extract method for defining host LMS during tracking tests
	* Delete all host LMS before tracking tests
	* Introduce builder consumer parameter for extending patron request attributes
	* Extract method for creating a patron request during tracking tests
	* Use fixture to save supplier requests during tracking tests
	* Remove usused status code repository from tracking tests
	* Use fixture to find patron requests during tracking tests
	* Use fixture to create patron requests during tracking tests
	* Move request is finalised matcher to matchers class
	* Extract method for waiting for patron request to become finalised
	* Use utility method to get single value from publisher during tracking tests
	* Delete all patron requests prior to each tracking test
	* Delete all reference value mappings prior to tracking tests
	* Enable negative flow tests for placing at FOLIO supplying agency DCB-490
	* Enable positive flow test for placing at FOLIO supplying agency DCB-490
	* Disable positive flow test for placing at FOLIO supplying agency DCB-490
	* Disable negative flow tests for placing at FOLIO supplying agency DCB-490
	* Use single instance for testing place request at FOLIO supplying agency DCB-490
	* Use separate client for each place at FOLIO supplying agency test DCB-490
	* Remove patron group ID from mock FOLIO users DCB-490
	* Include patron group name in mock FOLIO users DCB-490
	* Delete all agencies prior to placing request at FOLIO supplying agency DCB-490
	* Introduce class for placing request at FOLIO supplying agency tests DCB-490
	* Define host LMS during patron type tests DCB-490
	* Delete all host LMS before each patron service test DCB-490
	* Define required parameters for all Sierra host LMS DCB-490
	* Check hold is placed at borrowing agency DCB-490
	* Delete all agencies before Polaris host LMS client tests DCB-490
	* Pickup location should be associated with borrowing agency DCB-490
	* Verify pickup location sent to Sierra when placing supplying request DCB-490
	* Verify that hold request was made to Sierra during supplying agency tests DCB-490
	* Use property matcher for Sierra invalid hold policy message DCB-490
	* polaris delete item and delete bib override methods [DCB-789]
	* Remove duplicate tests for unauthorized status code predicate DCB-490
	* Make test names specific to finding a virtual patron DCB-490
	* Delete reference value mappings before each FOLIO host LMS client patron tests DCB-490
	* Move patron matchers to separate class DCB-490
	* Check no mapping for FOLIO patron's names DCB-490
	* Check no mapping for FOLIO patron's home library code DCB-490
	* Introduce parameter for users when mocking find users DCB-490
	* Move patron local IDs matcher to separate class DCB-490
	* Add class for FOLIO host LMS client patron tests DCB-490
	* Use stricter mock expectations when finding Polaris virtual patron DCB-490
	* Use find virtual patron method instead of auth in Polaris tests DCB-490
	* Use serialisation instead of json file for find patron response DCB-490
	* Introduce parameter for mock response when finding patron DCB-490
	* Inline single use assertion methods when placing supplying request DCB-490
	* Remove unecessary home library mapping when placing supplying request DCB-490
	* Extract method for placing request at supplying agency DCB-490
	* Remove test for invalid hold policy when placing supplying request DCB-490
	* Check invalid hold polcy in host LMS client test DCB-490
	* Rename supplying agency when placing request DCB-490
	* Change before all to before each place request at supplying agency DCB-490
	* Move defining mock fetch hold response to tests that need it DCB-490
	* Extract method for checking the patron request was placed at supplying agency DCB-490
	* Extract methods for patron request property matchers DCB-490
	* Use property matchers for checking patron request DCB-490
	* Extract method for checking patron request has error DCB-490
	* Use property matchers for audit entries when placing supplying request DCB-490
	* Return created cluster record ID DCB-490
	* Return saved patron request from fixture DCB-490
	* Remove commented out code from place supplying request tests DCB-490
	* Verify that create patron request was not made when placing a request at supplying agency DCB-490
	* Verify that create patron request was made when placing a request at supplying agency DCB-490
	* Verify that update patron request was not made when placing a request at supplying agency DCB-490
	* Verify that update patron request was made when placing a request at supplying agency DCB-490
	* Move verify find patron request method to fixture DCB-490
	* Extract method for verifying that find patron request was made DCB-490
	* Verify that find patron request was made when placing a request at supplying agency DCB-490
	* Should not use item type mapping associated with another host LMS DCB-479
	* Remove redundant test for agency mapping for items from FOLIO DCB-479
	* Include agency mapping in general get items from FOLIO test DCB-479
	* Should enrich item with agency mapped from location code DCB-479
	* Tolerate absence of agency when enriching item with agency DCB-479
	* Extract method for enriching an item with an agency DCB-479
	* Delete all agencies before enriching an item with an agency DCB-479
	* Tolerate absence of mapping when enriching item with agency DCB-479
	* Delete all mappings before enriching an item with an agency DCB-479
	* Tolerate null location code when enriching item with agency DCB-479
	* Extract method for creating example item DCB-479
	* Delete all agencies before each FOLIO host LMS client item tests DCB-479
	* Use single instance for FOLIO host LMS client item tests DCB-479
	* Check that whether items from FOLIO are suppressed is unknown DCB-479
	* Replace string literal for RTAC error with serialised class DCB-479
	* Extract method for JSON mock RTAC response DCB-479
	* Use unusual mappings when checking FOLIO item status mapping DCB-479
	* Delete mappings before FOLIO LMS item tests DCB-479
	* Add test for FOLIO settings description DCB-479
	* Create FOLIO client before each test DCB-479
	* Move location without code matcher to item matchers DCB-479
	* Remove test for directly fetching holdings from FOLIO DCB-479
	* Create FOLIO client when getting items from FOLIO DCB-479
	* Create FOLIO host LMS during client tests DCB-479
	* Use stricter mock expectations when fetching items from FOLIO DCB-479
	* Initial test for fetching items from FOLIO using mock server for response DCB-479
	* Extract method for defining mock OAI-PMH response DCB-797
	* Define single record OAI-PMH response during FOLIO ingest tests DCB-797
	* Define FOLIO host LMS during ingest tests DCB-797
	* Trigger ingest during FOLIO ingest tests DCB-797
	* Delete all cluster records before each FOLIO ingest test DCB-797
	* Delete all host LMS before each FOLIO ingest test DCB-797
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
	* Use source record ID when getting items from Polaris
	* Use test resource loader in Polaris LMS client tests
	* Move test resource loader to test package
	* Move resource loading to separate class
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
	* Only retry getting holds from Sierra once in most tests
	* Remove separate tests for disabling individual preflight checks DCB-612
	* Remove redundant preparation in preflight checks service tests DCB-612
	* Only include always failing check during preflight checks service tests DCB-612
	* Define test only preflight check DCB-612
	* Check all indidual checks are enabled DCB-612
	* Extract method for creating Sierra host LMS client with client config DCB-613
	* Use host LMS code constants for defining mappings when placing requests at borrowing agency DCB-613
	* statically import request statuses when placing request at borrowing agency DCB-613
	* Use host LMS code constant for defining mappings when placing requests at supplying agency DCB-613
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
	* Extract method for checking failed check event in log DCB-532
	* Check that successfully placed patron request generates no events in the log DCB-532
	* Demonstrate failed preflight check for mapping only DCB-532
	* Delete all event log entries before each patron request API test DCB-532
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
	* Rename delete all agencies method DCB-519
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
	* Move fields to before all method in Polaris client tests DCB-855
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
* [Flyway]
	* Add out-of-order:true to allow branches to merge with migrations out of sequence
* [Graphql]
	* Correct misaligned property name for virtualPatron in SupplierRequest
	* Reciprocal mapping syntax error
	* correct PatronRequest type mappings in graphql
	* Misspelled data fetcher for patron request joins
	* Add PatronRequest to PatronRequestAudit object
	* nested fetchers should use findBy and not findAllBy
	* Remove duplicated field in SupplierRequest
	* AgencyGroups data fetcher is now reactive
	* Declare a page type in the schema for the reactive page
* [Harvesting]
	* Sierra date ranges require the time zone signifier Z
	* Send suppressed=true and deleted=true when harvesting from Sierra
* [Ingest]
	* Syntax error caused by previous merge
	* In sierra - deleted=true means ONLY deleted records, suppressed=true means ONLY suppressed records. The default is to return all and thats what we want
* [Mappings]
	* Expunge residual ShelvingLocation references - we're now just on a flat list of Locations
	* DCB-502 patch marc leader item type mappings
* [Marc   Ingest]
	* DCB-504 DCB-521 - check 264 for publication info before 260
* [Polaris]
	* Reviewing the polaris docs at https://qa-polaris.polarislibrary.com/polaris.applicationservices/help/workflow/add_or_update_item_record and aligning data types as polaris doesn't like getting integers as strings. We no longer resumeOnError for createItem but instead just return the Problem as an error
	* Return Mono.empty rather than null for methods not yet implemented
	* Overrid the isEnabled method to honour settings from config
* [Security]
	* remove anon auth from rest endpoints
* [Tests]
	* Require config prefix
* [Workflow]
	* More Gracefully handle deletion of a virtual item
	* When the resolver was not able to choose an item because all the options are electronic, resove to no items available
* [General]
	* Exclude suppressed items from live availability DCB-976
	* transitioning to supplier item available before item has been loaned
	* if condition improved for matching patron types
	* Use item barcode when submitting a checkOut to sierra - DCB-983
	* use the determined patron type to update the virtual patron
	* DCB-980 Use location.code when placing a borrowing hold on a sierra system instead of agency code. This ensures that the specific code the user selected is used in the borrowing library system.
	* HandleSupplierInTransit is triggered by ITEM type and not REQUEST type
	* patron update for patron type in Sierra client
	* use pickup locations local id for folios borrowing request
	* use new query param for polaris ingest 'startdatemodified'
	* polaris workflow Hold requests are not permitted on Provisional records
	* get item with duplicate barcode in polaris [DCB-949]
	* Stop requesting FOLIO transaction status for empty ID
	* Trying a different engine to see if that resolves the statemodel generation problem
	* Allow FOLIO CLOSED state to complete a dcb request
	* repeated error detection for supplier request
	* updateItemThenCheckout should be passed itemid and not item barcode
	* DCB-931 - Use explicit mapping for canonical item type to folio
	* don't assume process graphql will come with a sort option
	* Owning location not being set in sierra, preventing return transit flow
	* DCB-923 include calls to delete item and bib in reactive flow
	* DCB-922 attempt to clear transit message in sierra by setting message to zero length string
	* Correct received token, log mapping failures as errors
	* Temporarily revert the extra property sources.
	* cloudwatch op-in MICRONAUT_METRICS_EXPORT_CLOUDWATCH_ENABLED=true
	* Revert health endpoint to being insecure
	* multiple patron records being found during polaris patron lookup [DCB-907]
	* Extra defensive code when looking into location data from sierra
	* Ensure tracking service is sequential.
	* Added the means to generate the state table model as a diagram
	* Request tracking flow out of order
	* Added getPossibleSourceStatus to workspace handlers
	* Map item barcode for Polaris hold to barcode instead of item ID DCB-872
	* parse patron barcodes in folio requests
	* Handle missing borrower request was setting the local item status and not the local request status in a cancelled scenario - leading to repeated attempts to process the message
	* correct URI path for numeric range mapping controller
	* Raise error when no item level hold found in Sierra DCB-872
	* continue flow for -1 failure resp from polaris find virtual patron
	* check for error in response of Polaris patron search
	* Truncate patron request error message to shorter length than database column
	* patron barcode parsing [DCB-826]
	* remove prefixing barcode when searching for patron, polaris
	* ensure empty mono emmits patronNotFound exception
	* Unable to create virtual patron at polaris - errorcode:-3505: Duplicate barcode
	* Add rule to track virtual items in status REQUESTED so we can detect CHECK-IN
	* Audit messages not saving
	* expected error message in shouldThrowExceptionWhenNoMappingFromSpinePatronTypeToBorrowingPatronType
	* change error mapping round when we determinePatronType
	* Rework to use MN internals.
	* use NonNull instead of NotNull annotation to vaildate place request body
	* changes for process state update
	* Ensure transactional encapsulation of "updateState"
	* remove extra Error Transformer after applyTransition
	* Use the patronRequest status to control the action of a missing request at the requesting library to avoid accidentially cancelling the request
	* shouldFinaliseRequestWhenSupplierItemAvailable and successfullyGetPatronByUsername
	* Stats service logging
	* Use String and not character encoding for varField fieldTag - suspect character is encoding as an ASCII integer and not a string in json output
	* reinstate createAuditEntry in correct order
	* comment out audit assersions in ValidatePatronTests to help tests pass (temp)
	* error handling for audit - repeated additional line [DCB-847]
	* Potential fix for duplicating clusters.
	* Don't explode when not provided with a orderBy sort direction in graphql
	* Default sort direction if not specified in patron request data fetcher
	* Trim empty resumption token.
	* Add forward slash to path for creating FOLIO transaction DCB-490
	* Error in accessing year regex mapper
	* label on state change was wrong way round
	* Reusing builder in Opensearch causes immediate error.
	* Stop attempting to bulk update on empty lists
	* validate a polaris hold response success [DCB-832]
	* Guard code around invalid bulk operation.
	* Use patron home location for item location when creating an item for POLARIS
	* Rely on declarative config for hazelcast
	* DCB-807 description is not trimmed to 254 characters and can lead to exceptions inserting PR
	* createPatron test in PolarisLmsClientTests
	* for POLARIS only, Use a default patron home location code defined in the HostLMS config instead of trying to map the requestingPatron home location
	* use PATB for looking up patron barcode in polaris adaptor
	* comment out old getMembersOld method
	* path name for /transtion/cleanup to /transition/cleanup, also allow user to request cleanup on ERROR states
	* Aling geo-distance filter with requirement to use only location UUID for pickup location
	* use equals for comparing selected bib to members when generating index record. populate location for some sierra records
	* Don't attempt to post a bulk request unless there are actually ops in the queue
	* Constrain the message in audit log description strings to max 254 characters
	* 404 looking up patron blocks is not an error
	* Dupe bibs in ingest record view.
	* for apparent behaviour of polaris. When passed an itemType in a string, polaris seems to take only the first character of the string as a part of the integer. Changed parameter type to Integer and parse it with parseInt before setting
	* Update PatronRequest GraphQL type [DCB-487]
	* Correct GraphQLFactory clusterRecord naming [DCB-748]
	* When authenticating uniqueid+pin for sierra, use the standard auth method
	* Defensive code in data fetchers - null safety
	* Add further validation of uploaded mappings [DCB-508]
	* Tolerate Sierra item's without a status
	* Warn when ingest configuration is invalid for a host LMS
	* Do not change Polaris item status based upon presence of due date
	* Amend annotations on ReferenceValueMapping to fix failing tests [DCB-603]
	* Use lowercase token_filter by default
	* Refactor the background index job.
	* polaris item status fallback mapping
	* Try with a 3second delay
	* Reinstate the delay
	* Ensure endpoint is protected
	* Do not overwhelm the indexer with 2 subs
	* Null handling.
	* Improve badResumption token handling
	* add patron to WorkFlowContext
	* Use correct local_request_status when seeking patron requests on the borrowing side to track
	* Add missing AVAILABLE status to updateItem impletation in sierra adapter
	* corrected static item status code mapping
	* Error getting deleted flag for items
	* set PR state to READY_FOR_PICKUP when appropriate
	* store barcode when creating or updating patron identity records relating to virtual patron records in supplying systems
	* Don't trim patron barcode when attempting to check out
	* align tests with reverted patron id change
	* revert DCB: change as the likely culprit for failure to place hold at lending site
	* Switch lastImported to use Instant [DCB-527]
	* Field for raw source record is json not rawSource
	* return for UUID based pickup location codes
	* interpret pickup location strings of 36 chars as a UUID
	* Ensure we remove the related match-points before the bib
	* PickupLocation mappings are a legacy thing now - we just have Location code mappings and put everything into one pot. At some point a merge had reverted a curried value of Location back to PickupLocation which was causing a lookup failure now that we no longer need PickupLocation mappings
	* Cleanup on finish, not just cancellation or error.
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

## Version 2.7.1

### Changes
* [Chore]
	* Changelog - Generate the changelog
	* update guidance

## Version 2.7.0

### Additions
* [General]
	* Complete integration of new AlarmsService - allow services to raise alarms on conditions that need human attention. First example raises an alarm when a patron request transition is unable to progress

### Changes
* [Chore]
	* working on alarms, don't ever set ES refresh-interval to -1 when unblocking the circuit breaker

### Fixes
* [General]
	* Bump git properties plugin, seems to re-add git.properties for info endpoint