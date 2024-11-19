select pra.audit_date::date "Date", pra.to_status "Status", count(*) "Total"
from patron_request_audit pra
where pra.from_status <> pra.to_status AND
	  pra.audit_date::date > (now()::date - 8) and 
	  pra.audit_date::date < now()::date
group by 1, 2
union all
select pr.date_created::date "Date", 'SUBMITTED_TO_DCB' "Status", count(*) "Totsl"
from patron_request pr
where pr.date_created::date > (now()::date - 8) and
	  pr.date_created::date < now()::date
group by 1, 2
order by 1, 2
