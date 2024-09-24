SELECT pr.status_code "Status", count(*) "Total"
from patron_request pr
where pr.next_scheduled_poll is not null
group by 1
order by 1
