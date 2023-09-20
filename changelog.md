# Version 1.1.0-SNAPSHOT

## Additions
* [General]
	* default search property can now be chosen per type.
	* generate dummy titles

## Changes
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

## Fixes
* [General]
	* Clustering null values