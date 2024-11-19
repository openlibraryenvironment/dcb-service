SELECT pr.status_code "Status", count(*) "Total"
from patron_request pr
where pr.next_scheduled_poll < now()
group by 1
order by 1
