


The main loop of DCB is driven by the PatronRequest.status field which is of enum type Status, defined at the top of PatronRequest


Current states


                SUBMITTED_TO_DCB,
                PATRON_VERIFIED,
                RESOLVED,
                NO_ITEMS_AVAILABLE_AT_ANY_AGENCY,
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

