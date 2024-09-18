select pr.date_updated::date "Date", rhl.name "Requester", shl.name "Supplier", pr.id "RequestId", pr.previous_status_code "previousStatus",
	   concat('https://libraries-dcb-hub-admin-scaffold-uat-git-production-knowint.vercel.app/patronRequests/', pr.id) "URL"
from patron_request pr, host_lms rhl, host_lms shl, supplier_request sr, agency a
where pr.error_message like 'Could not update item % status for hostlms: %' and
	  pr.status_code = 'ERROR' and
  	  rhl.code = pr.patron_hostlms_code and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  shl.id = a.host_lms_id
order by 1 desc, 2, 3
