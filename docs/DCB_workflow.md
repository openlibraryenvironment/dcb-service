


The main loop of DCB is driven by the PatronRequest.status field which is of enum type Status, defined at the top of PatronRequest


Current states


                SUBMITTED_TO_DCB,
                PATRON_VERIFIED,
                RESOLVED,
                NO_ITEMS_SELECTABLE_AT_ANY_AGENCY,
                REQUEST_PLACED_AT_SUPPLYING_AGENCY,
                REQUEST_PLACED_AT_BORROWING_AGENCY,
                RECEIVED_AT_PICKUP,
                READY_FOR_PICKUP, // 
                LOANED,         // Currently onloan
                PICKUP_TRANSIT, // In transit to pickup location
                RETURN_TRANSIT, // In transit back to owning location from lender
                CANCELLED,
                COMPLETED,    // Everything is finished, regardless and ready to be finalised
                FINALISED,    // We've cleaned up everything and this is the end of the line
                ERROR




What happens on place request?

- An authenticated client POSTs a [PlacePatronRequestCommand](https://github.com/openlibraryenvironment/dcb-service/blob/main/dcb/src/main/java/org/olf/dcb/request/fulfilment/PlacePatronRequestCommand.java) to `/patrons/requests/place`.
- `citation`, `pickupLocation`, and `requestor` are schema-required. Current request placement also
  operationally requires `citation.bibClusterId`, `pickupLocation.code` containing a DCB Location UUID,
  and requestor `localId`, `localSystemCode`, and `agencyCode`. `homeLibraryCode`, notes, description,
  explicit item, volume designator, and expedited flag are optional.
- This then calls [patronRequestService.placePatronRequest](https://github.com/openlibraryenvironment/dcb-service/blob/main/dcb/src/main/java/org/olf/dcb/request/fulfilment/PatronRequestService.java)
- placePatronRequest first executes prefligt checks.
- If the command passes we upsert a patron record based on the requesting patron identity
- We create and then save a PatronRequest object in the DB
- We initiate the PatronRequestWorkflow engine which will try and push the request through a sequence of transitions
- We return the newly create PatronRequest - it's state will be representative of the request at the time

How does the PatronRequestWorkflow work

- the [initiate]() method of [PatronRequestWorkflowService]() is an entrypoint which just triggers asynchronous work in the [progressAll]() method.
- progressAll will attempt to find any actions which can be applied to the request in it's current state - it calls progressUsing with a selected action
- progressUsing will call applyTransition for any available action
- applyTransition calls the attempt method on the request, and *then recursively calls itself* to apply any subsequent actions. Repeat

