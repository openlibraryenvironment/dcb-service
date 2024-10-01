select pra.audit_date::date "Date", rhl.name "Requester", shl.name "Supplier", pr.status_code "Status", pra.patron_request_id "RequestId",
       pra.audit_data->>'requestUrl' "RequestURL",
	   concat('https://libraries-dcb-hub-admin-scaffold-uat-git-production-knowint.vercel.app/patronRequests/audits/', pra.id) "URL"
from patron_request_audit pra, patron_request pr, host_lms rhl, host_lms shl, supplier_request sr, agency a
where pra.brief_description like 'Tracking failed : %' and
	  not pra.audit_data->>'Error' like 'java.lang.NullPointerException%' and
	  not pra.audit_data->>'Error' = 'org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException: Local item status Withdrawn is unhandled.' and
	  not pra.audit_data->>'Error' like '%Connection closed before response was received%' and
	  not pra.audit_data->>'Error' like '%connection timed out after%' and
	  not pra.audit_data->>'Error' = 'org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException: Local item status Claim Returned is unhandled.' and
	  not pra.audit_data->>'Error' like '%Channel closed while still aggregating message%' and
	  not pra.audit_data->>'Error' like '%, responseStatusCode=401, %' and
	  not pra.audit_data->>'Error' like '%, responseStatusCode=404, %' and
	  not pra.audit_data->>'Error' like '%, responseStatusCode=500, %' and
	  not pra.audit_data->>'Error' like '%, responseStatusCode=503, %' and
	  not pra.audit_data->>'Error' like '%, errorMessage=Connect Error: Connection refused: %' and
	  not pra.audit_data->>'Error' like '%, errorMessage=Read Timeout, %' and
	  not pra.audit_data->>'Error' like '%errorMessage=Read Timeout%' and
	  not pra.audit_data->>'Error' = 'io.micronaut.http.client.exceptions.ReadTimeoutException: Read Timeout' and
	  not pra.audit_data->>'Error' = 'org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException: Local item status Lost is unhandled.' and
	  not pra.audit_data->>'Error' like '%org.olf.dcb.core.interaction.polaris.exceptions.HoldRequestException: Unexpected error when trying to get hold with id: %' and
	  not pra.audit_data->>'Error' like '%502 Service temporarily unavailable.%' and
	  not pra.audit_data->>'Error' = 'org.olf.dcb.core.interaction.polaris.exceptions.UnknownItemStatusException: Local item status In-Repair is unknown.' and
	  not pra.audit_data->>'Error' = 'org.olf.dcb.core.interaction.polaris.exceptions.UnhandledItemStatusException: Local item status Claim Missing Parts is unhandled.' and
	  not pra.audit_data->>'Error' like '%errorMessage=Connect Error%' and
	  not exists (select 1 
				  from patron_request_audit pra1 
				  where pra1.patron_request_id = pra.patron_request_id and
						pra1.audit_date > pra.audit_date and
						pra1.brief_description like 'Tracking failed : %') and
	  pra.from_status != 'ERROR' and
	  pr.id = pra.patron_request_id and
  	  rhl.code = pr.patron_hostlms_code and
	  sr.patron_request_id = pr.id and
	  a.id = sr.resolved_agency_id and
	  shl.id = a.host_lms_id
order by 1 desc, 2, 3
