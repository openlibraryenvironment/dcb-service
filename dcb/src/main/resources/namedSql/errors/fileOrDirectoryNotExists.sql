select pra.audit_date::date "Date", rhl.name "Requester", shl.name "Supplier", pra.patron_request_id "RequestId",
       pra.audit_data->>'requestUrl' "RequestURL",
	   concat('https://libraries-dcb-hub-admin-scaffold-uat-git-production-knowint.vercel.app/patronRequests/audits/', pra.id) "URL"
from patron_request_audit pra, patron_request pr, host_lms rhl, host_lms shl, supplier_request sr, agency a
where (pra.audit_data->>'responseBody' like '%404 - File or directory not found.%' or
       pra.audit_data->>'responseBody' like '%Error 404. The requested resource is not found.%') and
	  pra.from_status != 'ERROR' and
	  pr.id = pra.patron_request_id and
	  pr.status_code = 'ERROR' and
  	  rhl.code = pr.patron_hostlms_code and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  shl.id = a.host_lms_id
order by 1 desc, 2, 3
