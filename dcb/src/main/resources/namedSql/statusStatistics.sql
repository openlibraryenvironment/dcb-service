select yearweek "yearWeek",
	   status "status",
	   count(*) "totalRequests",
	   AVG(duration) mean,
	   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration) median,
	   min(duration) minimum,
	   max(duration) maximum
from (select to_char(pra.audit_date, 'IYYY-IW') "yearweek",
			 pra.from_status "status", 
			 EXTRACT(EPOCH FROM (pra.audit_date - COALESCE((select min(pra1.audit_date)
															from patron_request_audit pra1
															where pra1.patron_request_id = pr.id and
																  pra1.to_status = pra.from_status and
																  pra1.from_status != 'ERROR' and
																  pra1.from_status != pra1.to_status),
												  pr.date_created)
								)
					) "duration"
	 from patron_request_audit pra, patron_request pr
	 where pra.from_status !=  pra.to_status and
		   pra.from_status != 'ERROR' and
		   pra.to_status != 'ERROR' and
		   not exists (select 1 from patron_request_audit pra2 where pra2.patron_request_id = pr.id and pra2.audit_date < pra.audit_date and pra2.from_status = pra.from_status and pra2.to_status = pra.to_status) and
		   pra.patron_request_id = pr.id and
		   pr.status_code in (/*'COMPLETED', */'FINALISED')
	 ) audit_rows
group by 1, 2
order by 1, 2
