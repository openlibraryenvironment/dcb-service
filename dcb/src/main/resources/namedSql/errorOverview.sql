select case
		   when pra.audit_data->'responseBody'->>'description' = 'There is a problem with your library record.  Please see a librarian.'
		   then
			   'Problem with your library card, DCB-1428'
		   when pra.audit_data->'responseBody'->>'description' = 'XCirc error : There is a problem with your library record.  Please see a librarian.'
		   then
			   'Problem with library your record, DCB-1446'
		   when pra.audit_data->>'detail' like '%Patron has exceeded the holds limit on this material type.%'
		   then
			   'Patron exceeded hold limit for material type, DCB-1431'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Page requests are not allowed for this patron and item combination%'
		   then
			   'Page requests are not allowed, DCB-????'
		   when pra.audit_data->>'Message' = 'Other copies of this item may be available. Do you want this specific item or the first available copy?'
		   then
			   'Other copies available, DCB-1276'
		   when pra.audit_data->>'responseBody' like 'System.NullReferenceException: Object reference not set to an instance of an object.%'
		   then
			   'Null reference exception, DCB-1444'
		   when pra.audit_data->>'Message' like '%This item will not fill this request because it does not belong to a responder branch for this pickup location.'
		   then
			   'Not belong to responder branch, DCB-1475'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%No item with barcode % exists%'
		   then
			   'No Item with barcode, DCB-1457'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Current transaction status equal to new transaction status: dcbTransactionId: %, status: %'
		   then
			   'New status same as existing, DCB-1455'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Patron has reached the maximum number of overdue checked out items.%'
		   then
			   'Max overdue items, DCB-1480'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Maximum number of overdues%'
		   then
			   'Maximum overdues, DCB-????'
		   when pra.audit_data->>'detail' like 'Item with barcode: % already exists in host%'
		   then
			   'Item with barcode already exists, DCB-1275'
		   when pra.audit_data->>'Message' like 'The following links will be broken if you continue deleting item record%'
		   then
			   'Item record links breakable, DCB-1374'
		   when pra.audit_data->>'Message' like 'Title: %This item is not holdable.'
		   then
			   'Item not holdable, DCB-1504'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Item with id % already has an open DCB transaction%'
		   then
			   'Item has DCB transaction, DCB-1449'
		   when pra.brief_description like 'PAPIService returned [-4], with message: Incorrect syntax near %'
		   then
			   'Incorrect syntax, DCB-1432'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' = 'User with type patron is retrieved. so unable to create transaction'
		   then
			   'Folio user is retrieved, DCB-1463'
		   when (pra.audit_data->>'responseBody' like '%404 - File or directory not found.%' or
				 pra.audit_data->>'responseBody' like '%Error 404. The requested resource is not found.%')
		   then
			   'File or directory does not  exist, DCB-1445'
		   when pra.brief_description = 'Failed to cancel hold request'
		   then
			   'Failed to cancel hold request, DCB-1433'
		   when pra.audit_data->>'detail' = 'Duplicate hold requests exist'
		   then
			   'Duplicate hold exist, DCB-1429'
		   when pra.audit_data->'Full response'->'Prompt'->>'Title' = 'Duplicate hold requests exist'
		   then
			   'Duplicate holds exist, DCB-????'
		   when pra.audit_data->>'errorMessage' like 'Connect Error: connection timed out after%'
		   then
			   'Connection timeout, DCB-1451'
		   when (pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
				 pra.audit_data->>'Error' like '%Connection closed before response was received%' or
				 pra.brief_description = 'Connection closed before response was received')
		   then
			   'Connection closed, DCB-1484'
		   when pra.audit_data->>'errorMessage' like 'Connect Error: Connection refused: %'
		   then
			   'Connection refused, DCB-1484'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to find existing item with id % and barcode %'
		   then
			   'Cannot place request, DCB-????'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%CQLParseException: expected boolean, got ''/'': barcode==%'
		   then
			   'Barcode ends with slash, DCB-1470'
		   when pra.audit_data->'responseBody'->>'description' = 'Request denied - already on hold for or checked out to you.'
		   then
			   'Already on hold, DCB-1452'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has an open request for this item%'
		   then
			   'Already has request for item, DCB-1430'
		   when pra.audit_data->>'responseStatusCode' = '401'
		   then
			   'Unauthorised, DCB-1453'
		   when pra.audit_data->>'detail' like 'Unable to find existing user with barcode %'
		   then
			   'Unable to find existing user, DCB-1434'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Cannot cancel transaction dcbTransactionId: %'
		   then
			   'Unable to cancel patron, DCB-1458'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Status transition will not be possible from %'
		   then
			   'Status transition not possible, DCB-1399'
		   when (pra.audit_data->>'detail' = 'Duplicate barcode detected' or
				 pra.audit_data->>'detail' like '%This barcode has already been taken%')
		   then
			   'Duplicate barcode detected, DCB-2076'
		   when pra.audit_data->>'detail' like '%Item is blocked'
		   then
			   'Item is blocked, DCB-????'
		   when (pra.audit_data->'responseBody'->>'description' = 'This record is not available' or 
		         pra.audit_data->>'detail' like '%XCirc Error: This record is not available')
		   then
			   'Record not available, DCB-1273'
		   when (pra.audit_data->>'errorMessage' = 'Read Timeout' OR
				 pra.audit_data->>'Error' like '%errorMessage=Read Timeout%')
		   then
			   'Read timeout, DCB-1450'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' = 'updateTransactionStatus:: status update from ITEM_CHECKED_OUT to CLOSED is not implemented'
		   then
			   'Item checkout to close not implemented, DCB-1520'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to create item with barcode % as it exists in inventory%'
		   then
			   'Unable to create item with barcode as already exists, DCB-1576'
		   when pra.audit_data->'responseBody'->>'Message' like 'Item Record with ID % not found.'
		   then
			   'Item record XXXX not found, DCB-1579'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Hold requests are not allowed for this patron and item combination%'
		   then
			   'Hold requests not allowed for this patron and item combination, DCB-1597'
		   when pra.audit_data->>'responseBody' like 'HTTP 500 Internal Server Error.%If the issue persists, please report it to EBSCO Connect.%'
		   then
			   'Folio Internal Server Error, DCB-1613'
		   when pra.audit_data->>'detail' like 'No holds to process for local patron id:%'
		   then
			   'No holds to process for local patron, DCB-1615'
		   when pra.brief_description = 'Multiple Virtual Patrons Found'
		   then
			   'Nultiple virtual patrons found, DCB-1653'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%One or more Pickup locations are no longer available%'
		   then
			   'Pickup locations no longer available, DCB-1654'
		   when pra.brief_description = 'Staff Auth Failed'
		   then
			   'Staff authentication failed, DCB-????'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has this item on loan%'
		   then
			   'Requester already has this item on loan, DCB-????'
		   when pra.brief_description = 'Fallback(0): no error message was determined'
		   then
			   'No error message was determined, DCB-????'
		   when pra.audit_data->>'detail' like '%Inactive users cannot make requests%'
		   then
			   'Inactive users cannot make requests, DCB-???'
		   else
			   concat('not caught: ', pra.id, ', ', pra.brief_description)
		   end "description",
	   case
		   when pra.audit_data->'responseBody'->>'description' = 'There is a problem with your library record.  Please see a librarian.'
		   then
			   'errors/problemWithYourLibraryCard'
		   when pra.audit_data->'responseBody'->>'description' = 'XCirc error : There is a problem with your library record.  Please see a librarian.'
		   then
			   'errors/problemWithLibraryRecord'
		   when pra.audit_data->>'detail' like '%Patron has exceeded the holds limit on this material type.%'
		   then
			   'errors/patronExceededHoldLimitForMaterialType'
		   when (pra.audit_data->>'detail' = 'Duplicate barcode detected' or
				 pra.audit_data->>'detail' like '%This barcode has already been taken%')
		   then
			   'errors/duplicateBarcodeDected'
		   when pra.audit_data->>'detail' like '%Item is blocked'
		   then
			   'errors/itemIsBlocked'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Page requests are not allowed for this patron and item combination%'
		   then
			   'errors/pageRequestAreNotAllowed'
		   when pra.audit_data->>'Message' = 'Other copies of this item may be available. Do you want this specific item or the first available copy?'
		   then
			   'errors/otherCopiesAvailable'
		   when pra.audit_data->>'responseBody' like 'System.NullReferenceException: Object reference not set to an instance of an object.%'
		   then
			   'errors/nullReferenceExceptionPolaris'
		   when pra.audit_data->>'Message' like '%This item will not fill this request because it does not belong to a responder branch for this pickup location.'
		   then
			   'errors/notBelongToResponderBranch'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%No item with barcode % exists%'
		   then
			   'errors/noItemWithBarcode'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Current transaction status equal to new transaction status: dcbTransactionId: %, status: %'
		   then
			   'errors/newStatusSameAsExisting'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Patron has reached the maximum number of overdue checked out items.%'
		   then
			   'errors/maxOverdueItems'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Maximum number of overdues%'
		   then
			   'errors/maximumOverdues'
		   when pra.audit_data->>'detail' like 'Item with barcode: % already exists in host%'
		   then
			   'errors/itemWithBarcodeAlreadyExists'
		   when pra.audit_data->>'Message' like 'The following links will be broken if you continue deleting item record%'
		   then
			   'errors/itemRecordLinksBreakable'
		   when pra.audit_data->>'Message' like 'Title: %This item is not holdable.'
		   then
			   'errors/itemNotHoldable'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Item with id % already has an open DCB transaction%'
		   then
			   'errors/itemHasDcbTransaction'
		   when pra.brief_description like 'PAPIService returned [-4], with message: Incorrect syntax near %'
		   then
			   'error/incorrectSyntax'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' = 'User with type patron is retrieved. so unable to create transaction'
		   then
			   'errors/folioUserIsRetrieved'
		   when (pra.audit_data->>'responseBody' like '%404 - File or directory not found.%' or
				 pra.audit_data->>'responseBody' like '%Error 404. The requested resource is not found.%')
		   then
			   'errors/fileOrDirectoryNotExists'
		   when pra.brief_description = 'Failed to cancel hold request'
		   then
			   'errors/failedToCancelHoldRequest'
		   when pra.audit_data->>'detail' = 'Duplicate hold requests exist'
		   then
			   'errors/duplicateHoldsExist'
		   when pra.audit_data->'Full response'->'Prompt'->>'Title' = 'Duplicate hold requests exist'
		   then
			   'errors/duplicateHoldExists'
		   when pra.audit_data->>'errorMessage' like 'Connect Error: connection timed out after%'
		   then
			   'errors/connectionTimeout'
		   when (pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
				 pra.audit_data->>'Error' like '%Connection closed before response was received%' or
				 pra.brief_description = 'Connection closed before response was received')
		   then
			   'errors/connectionsClosed'
		   when pra.audit_data->>'errorMessage' like 'Connect Error: Connection refused: %'
		   then
			   'errors/connectionRefused'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to find existing item with id % and barcode %'
		   then
			   'errors/cannotPlaceRequest'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%CQLParseException: expected boolean, got ''/'': barcode==%'
		   then
			   'errors/barcodeEndsWithSlash'
		   when pra.audit_data->'responseBody'->>'description' = 'Request denied - already on hold for or checked out to you.'
		   then
			   'errors/alreadyOnHold'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has an open request for this item%'
		   then
			   'errors/alreadyHasRequestForItem'
		   when pra.audit_data->>'responseStatusCode' = '401'
		   then
			   'errors/unAuthorised'
		   when pra.audit_data->>'detail' like 'Unable to find existing user with barcode %'
		   then
			   'errors/unableToFindExistingUser'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Cannot cancel transaction dcbTransactionId: %'
		   then
			   'errors/unableToCancelTransaction'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Status transition will not be possible from %'
		   then
			   'errors/statusTransitionNotPossible'
		   when (pra.audit_data->'responseBody'->>'description' = 'This record is not available' or 
		         pra.audit_data->>'detail' like '%XCirc Error: This record is not available')
		   then
			   'errors/recordNotAvailable'
		   when (pra.audit_data->>'errorMessage' = 'Read Timeout' OR
				 pra.audit_data->>'Error' like '%errorMessage=Read Timeout%')
		   then
			   'errors/readTimeout'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' = 'updateTransactionStatus:: status update from ITEM_CHECKED_OUT to CLOSED is not implemented'
		   then
			   'errors/itemCheckedOutToClosedNotImplemented'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to create item with barcode % as it exists in inventory%'
		   then
			   'errors/itemAlreadyExists'
		   when pra.audit_data->'responseBody'->>'Message' like 'Item Record with ID % not found.'
		   then
			   'errors/itemRecordNotFound'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Hold requests are not allowed for this patron and item combination%'
		   then
			   'errors/holdRequestsNotAllowed'
		   when pra.audit_data->>'responseBody' like 'HTTP 500 Internal Server Error.%If the issue persists, please report it to EBSCO Connect.%'
		   then
			   'errors/folioInternalError'
		   when pra.audit_data->>'detail' like 'No holds to process for local patron id:%'
		   then
			   'errors/noHoldsToProcess'
		   when pra.brief_description = 'Multiple Virtual Patrons Found'
		   then
			   'errors/multipleVirtualPatrons'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%One or more Pickup locations are no longer available%'
		   then
			   'errors/invalidPickupLocation'
		   when pra.brief_description = 'Staff Auth Failed'
		   then
			   'errors/staffAuthFailed'
		   when pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has this item on loan%'
		   then
			   'errors/requesterAlreadyHasItemOnLoan'
		    when pra.brief_description = 'Fallback(0): no error message was determined'
			then
			   'errors/fallbackNoErrorDetermined'
		   when pra.audit_data->>'detail' like '%Inactive users cannot make requests%'
		   then
			   'errors/inactiveUsersCannotMakeRequests'
		   else
			   concat('not caught: ', pra.id, ', ', pra.brief_description)
		   end "namedSql",
	   count(*) "total",
	   max(pra.audit_date::date) "mostRecent",
	   min(pra.audit_date::date) "earliest"
from patron_request_audit pra, patron_request pr
where pra.from_status != 'ERROR' and
	  pr.id = pra.patron_request_id and
	  pr.status_code = 'ERROR' and
	  (
		  pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
		  pra.brief_description = 'Connection closed before response was received' or
		  pra.audit_data->>'detail' = 'Duplicate barcode detected' or
		  pra.audit_data->>'detail' like '%This barcode has already been taken%' or
		  pra.audit_data->>'detail' like '%Item is blocked' or
		  pra.audit_data->>'detail' like 'Item with barcode: % already exists in host%' or
		  pra.audit_data->'responseBody'->>'description' = 'This record is not available' or
		  pra.audit_data->>'detail' = 'Duplicate hold requests exist' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Status transition will not be possible from %' or 
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to find existing item with id % and barcode %' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Page requests are not allowed for this patron and item combination%' or
		  pra.audit_data->'responseBody'->>'description' = 'There is a problem with your library record.  Please see a librarian.' or
		  pra.audit_data->'Full response'->'Prompt'->>'Title' = 'Duplicate hold requests exist' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has an open request for this item%' or
		  pra.audit_data->>'detail' like '%Patron has exceeded the holds limit on this material type.%' or
		  pra.audit_data->>'Message' = 'Other copies of this item may be available. Do you want this specific item or the first available copy?' or
		  pra.brief_description like 'PAPIService returned [-4], with message: Incorrect syntax near %' or
		  pra.brief_description = 'Failed to cancel hold request' or
		  pra.audit_data->>'detail' like 'Unable to find existing user with barcode %' or
		  pra.audit_data->>'responseBody' like 'System.NullReferenceException: Object reference not set to an instance of an object.%' or
		  pra.audit_data->>'responseBody' like '%404 - File or directory not found.%' or
		  pra.audit_data->>'responseBody' like '%Error 404. The requested resource is not found.%' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Patron has reached the maximum number of overdue checked out items.%' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Item with id % already has an open DCB transaction%' or
		  pra.audit_data->>'errorMessage' = 'Read Timeout' or
		  pra.audit_data->>'errorMessage' like 'Connect Error: connection timed out after%' or
		  pra.audit_data->'responseBody'->>'description' = 'XCirc error : There is a problem with your library record.  Please see a librarian.' or
		  pra.audit_data->'responseBody'->>'description' = 'Request denied - already on hold for or checked out to you.' or
		  audit_data->>'responseStatusCode' = '401' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Current transaction status equal to new transaction status: dcbTransactionId: %, status: %' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%No item with barcode % exists%' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Cannot cancel transaction dcbTransactionId: %' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' = 'User with type patron is retrieved. so unable to create transaction' or
		  pra.audit_data->>'Message' like '%This item will not fill this request because it does not belong to a responder branch for this pickup location.' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%CQLParseException: expected boolean, got ''/'': barcode==%' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Maximum number of overdues%' or
		  pra.audit_data->>'errorMessage' like 'Connect Error: Connection refused: %' or
		  pra.audit_data->>'Message' like 'The following links will be broken if you continue deleting item record%' or
		  pra.audit_data->>'Message' like 'Title: %This item is not holdable.' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' = 'updateTransactionStatus:: status update from ITEM_CHECKED_OUT to CLOSED is not implemented' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to create item with barcode % as it exists in inventory%' or
		  pra.audit_data->'responseBody'->>'Message' like 'Item Record with ID % not found.' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Hold requests are not allowed for this patron and item combination%' or
		  pra.audit_data->>'responseBody' like 'HTTP 500 Internal Server Error.%If the issue persists, please report it to EBSCO Connect.%' or
		  pra.audit_data->>'detail' like 'No holds to process for local patron id:%' or
		  pra.brief_description = 'Multiple Virtual Patrons Found' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%One or more Pickup locations are no longer available%' or
		  pra.brief_description = 'Staff Auth Failed' or
		  pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has this item on loan%' or
		  pra.brief_description = 'Fallback(0): no error message was determined' or
		  pra.audit_data->>'detail' like '%Inactive users cannot make requests%'
	  )
group by 1, 2
union
(
select case
		   when pr.error_message = 'Patron has unexpected blocks'
		   then
			   'Patron unexpected block, DCB-1459'
		   when pr.error_message like 'Could not update item % status for hostlms: %'
		   then
			   'Could not update item status, DCB-1398'
		   when (pr.error_message = 'Unable to create virtual patron at polaris - error code: -3529' or 
				 pr.error_message = 'Unable to create virtual patron at polaris - error code: -3612')
		   then
			   'Unable to create virtual patron, DCB-1401'
		   when pr.error_message = 'Property cause is reserved'
		   then
			   'Property cause reserved, DCB-1428'
		   when (pr.error_message like 'Unable to map canonical patron type "YOUNG ADULT" to a patron type on Host LMS:%' or
				 pr.error_message like 'No mapping found from ptype%')
		   then
			   'Unable to map canonical patron type, DCB-????'
		   when pr.error_message like 'Unable to map canonical item type "UNKNOWN" to a item type on Host LMS: %'
		   then
			   'Unable to map canonical item type, DCB-1454'
		   when pr.error_message like 'Failed to resolve shelving loc % to agency'
		   then
			   'Failed to resolve shelving location, DCB-1669'
/*
		   when 
		   then
			   ', DCB-????'
*/
		   else concat('not caught: ', pr.error_message)
		   end "description",
	   case
		   when pr.error_message = 'Patron has unexpected blocks'
		   then
			   'errors/patronUnexpectedBlocks'
		   when pr.error_message like 'Could not update item % status for hostlms: %'
		   then
			   'errors/couldNotUpdateItemStatus'
		   when (pr.error_message = 'Unable to create virtual patron at polaris - error code: -3529' or
				 pr.error_message = 'Unable to create virtual patron at polaris - error code: -3612')
		   then
			   'errors/unableToCreateVirtualPatron'
		   when pr.error_message = 'Property cause is reserved'
		   then
			   'errors/propertyCauseReserved'
		   when (pr.error_message like 'Unable to map canonical patron type "YOUNG ADULT" to a patron type on Host LMS:%' or
				 pr.error_message like 'No mapping found from ptype%')
		   then
			   'errors/unableToMapCanonicalPatronType'
		   when pr.error_message like 'Unable to map canonical item type "UNKNOWN" to a item type on Host LMS: %'
		   then
			   'errors/unableToMapCanonicalItemType'
		   when pr.error_message like 'Failed to resolve shelving loc % to agency'
		   then
			   'errors/failedToResolveShelvingLocation'
/*
		   when 
		   then
			   ''
*/
		   else concat('not caught: ', pr.error_message)
		   end "namedSql",
	   count(*) "total",
	   max(pr.date_created::date) "mostRecent",
	   min(pr.date_created::date) "earliest"
from patron_request pr
where pr.date_updated::date > TO_DATE('20240611','YYYYMMDD') and
	  pr.status_code = 'ERROR' and
	  (
		   pr.error_message like 'Could not update item % status for hostlms: %' or
		   pr.error_message = 'Unable to create virtual patron at polaris - error code: -3529' or
		   pr.error_message = 'Unable to create virtual patron at polaris - error code: -3612' or
		   pr.error_message = 'Property cause is reserved' or
		   pr.error_message like 'Unable to map canonical patron type "YOUNG ADULT" to a patron type on Host LMS:%' or
		   pr.error_message like 'No mapping found from ptype%' or
		   pr.error_message like 'Unable to map canonical item type "UNKNOWN" to a item type on Host LMS: %' or
		   pr.error_message = 'Patron has unexpected blocks' or
		   pr.error_message like 'Failed to resolve shelving loc % to agency'
	  )
group by 1, 2
)
order by 4 desc, 1
