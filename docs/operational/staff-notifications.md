# Staff notifications

`staffNotifications(agencyCode, kinds)` is one library's notification feed, read by the bell in
DCB Admin for Libraries. It is worked out when it is read from what DCB already holds; nothing is
stored per member of staff, and there is no table.

## What is in it

| Kind | Item | From |
| --- | --- | --- |
| `REQUESTS_NEED_ACTION` | A Home count that needs staff and is not zero: ready for pickup, not yet sent, returning | `patron_request`, by status and scope |
| `REQUEST_EXCEPTIONS` | Requests that stopped with an error, borrowed or supplied, or that no library could fill, updated in the last day | `patron_request`, by status, scope and `date_updated` |
| `NOTE_ADDED` | A request the library is party to that another library, or the consortium, noted in the last week, with the newest such note's id and author | `patron_request_note`, through `RequestInvolvement` |
| `TREND` | A weekly figure well above the library's last eight weeks (S-23) | `patron_request_audit` transitions |

Counts use the same statuses and scopes as DCB Admin for Libraries' Home tiles (`HomeBucket`
names each by the id the app uses), so the bell's link opens the grid the tile would. No item
carries a patron or the text of a note. An item's id is stable for the same fact (a count's id
includes its value), so the app can remember what has been read.

## Who may read it

Staff roles. A library user reads only their own libraries' feeds; an account with several names
one. Consortium staff name the library. `kinds` narrows the feed on the server to the kinds a
person has switched on; an unknown kind is refused.

## Cost

At most 50 items, newest first; by construction there are at most 30 (six counts, twenty noted
requests, four trends). Per library, not per person:

- the feed is cached for a minute (Caffeine, 1,000 libraries at most): six count queries and at
  most twenty-one note queries, two at a time;
- trends are cached for twelve hours (same bound): eighteen count queries over the audit trail,
  and for the stored measures two reads of their recorded weeks and two live figures.

Trends are worked out when a library's staff first read the feed rather than by a weekly job, so
no instance runs eighteen queries for each of five hundred libraries nobody is looking at. A cold
read after the twelve hours costs the same as the job would have for that one library.

## Trends

`TrendMeasure` lists the measures, each with its threshold: this week alerts when it reaches the
measure's minimum and its ratio times the average of the previous eight weeks. Weeks run Monday to
Monday, UTC. A measure counts requests moving into a status in the week, from the audit trail, so
a past week's figure does not change as its requests move on.

| Measure | Counts | Alerts at |
| --- | --- | --- |
| `UNFILLED_BORROWING` | the library's patrons' requests that no library could fill | 3 or more, and twice the trailing average |
| `FAILED_SUPPLYING` | requests the library was supplying that stopped with an error | 3 or more, and twice the trailing average |
| `MEDIAN_TURNAROUND_HOURS` | median hours from placing to ready for pickup, for the library's patrons | 72 or more, and half again the trailing average |
| `AWAITING_PICKUP` | the library's patrons' items on a hold shelf | 5 or more, and twice the trailing average |

The first two are counted from the audit trail, so any past week can be counted again. The last
two cannot: a count of items on the shelf is true only at the moment it is taken. So
`WeeklyMeasureJob` records them every Monday at 01:30 UTC, for the week just ended, in
`library_weekly_measure`. It reads DCB's own tables only: two queries a library, two
libraries at a time. Rewriting a week replaces it, so two instances running together do no harm.
Figures are kept 52 weeks.

A stored measure compares this week so far with the recorded trailing eight, and says nothing
until at least four of those weeks are recorded. A new deployment therefore has these two trends
from its fifth Monday.
