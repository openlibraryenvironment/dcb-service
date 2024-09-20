select pr.status_code "status",
	   /* pr.local_item_id "bLocalItemId", */
	   pr.raw_local_item_status "bRawLocalItemStatus",
	   pr.local_item_status "bLocalItemStatus",
	   /* sr.local_item_id "sLocalItemId", */
	   sr.raw_local_item_status "sRawLocalItemStatus",
	   sr.local_item_status "sLocalItemStatus",
	   count(*) "total",
	   min(current_status_timestamp) "minStatusChange",
	   max(current_status_timestamp) "maxStatusChange"
from patron_request pr, supplier_request sr
where sr.patron_request_id = pr.id and
	  pr.id in (Select id
				from patron_request pr1
				where poll_count_for_current_status > 0 and
					  (
						  (status_code in ('SUBMITTED_TO_DCB', 'PATRON_VERIFIED', 'RESOLVED') and 
						   EXTRACT(EPOCH FROM (now() - current_status_timestamp)) > 86400) OR
						  (status_code in ('REQUEST_PLACED_AT_SUPPLYING_AGENCY', 'CONFIRMED') and
						   EXTRACT(EPOCH FROM (now() - current_status_timestamp)) > 21600) OR
						  (status_code in ('REQUEST_PLACED_AT_BORROWING_AGENCY', 'PICKUP_TRANSIT', 'RECEIVED_AT_PICKUP', 'READY_FOR_PICKUP', 'RETURN_TRANSIT') and
						   EXTRACT(EPOCH FROM (now() - current_status_timestamp)) > 604800) OR
						  (status_code = 'LOANED' and
						   EXTRACT(EPOCH FROM (now() - current_status_timestamp)) > 3456000)
					  )
			   )
group by 1, 2, 3, 4, 5
order by 1, 2, 3, 4, 5

