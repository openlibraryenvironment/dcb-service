select pr.date_updated::date "Date", rhl.name "Requester", shl.name "Supplier", pr.id "RequestId",
	   concat('https://libraries-dcb-hub-admin-scaffold-uat-git-production-knowint.vercel.app/patronRequests/', pr.id) "URL"
from patron_request pr, host_lms rhl, host_lms shl, supplier_request sr, agency a
where pr.error_message not like 'Could not update item % status for hostlms: %' and
	  pr.error_message != 'Unable to create virtual patron at polaris - error code: -3529' and
	  pr.error_message != 'Unable to create virtual patron at polaris - error code: -3612' and
	  pr.error_message != 'Property cause is reserved' and
	  pr.error_message not like 'Unable to map canonical patron type "YOUNG ADULT" to a patron type on Host LMS:%' and
	  pr.error_message not like 'No mapping found from ptype%' and
	  pr.error_message not like 'Unable to map canonical item type "UNKNOWN" to a item type on Host LMS: %' and
	  pr.error_message != 'Patron has unexpected blocks' and
	  pr.error_message not like 'Failed to resolve shelving loc % to agency' and
	  not exists (select 1 from patron_request_audit pra
	              where pra.patron_request_id = pr.id and
				        (pra.audit_data->>'errorMessage' = 'Connection closed before response was received' or
						 pra.brief_description = 'Connection closed before response was received' or
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
						 pra.audit_data->>'detail' like '%XCirc Error: This record is not available' or
						 pra.audit_data->'responseBody'->'errors'->0->>'message' like 'Unable to create item with barcode % as it exists in inventory%' or
						 pra.audit_data->'responseBody'->>'Message' like 'Item Record with ID % not found.' or
						 pra.audit_data->'responseBody'->'errors'->0->>'message' like '%Hold requests are not allowed for this patron and item combination%' or
						 pra.audit_data->>'responseBody' like 'HTTP 500 Internal Server Error.%If the issue persists, please report it to EBSCO Connect.%' or
						 pra.audit_data->>'detail' like 'No holds to process for local patron id:%' or
						 pra.brief_description = 'Multiple Virtual Patrons Found' or
						 pra.audit_data->'responseBody'->'errors'->0->>'message' like '%One or more Pickup locations are no longer available%' or
						 pra.brief_description = 'Staff Auth Failed' or
						 pra.audit_data->>'detail' = 'Duplicate barcode detected' or
						 pra.audit_data->>'detail' like '%This barcode has already been taken%' or
						 pra.audit_data->>'detail' like '%Item is blocked' or
						 pra.audit_data->'responseBody'->'errors'->0->>'message' like '%This requester already has this item on loan%' or
						 pra.brief_description = 'Fallback(0): no error message was determined' or
						 pra.audit_data->>'detail' like '%Inactive users cannot make requests%'
					 )
				 ) and
	  pr.status_code = 'ERROR' and
  	  rhl.code = pr.patron_hostlms_code and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  shl.id = a.host_lms_id
order by 1 desc, 2, 3
