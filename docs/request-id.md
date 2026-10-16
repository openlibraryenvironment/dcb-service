# Request id

Every HTTP request dcb-service receives gets a request id. It is written into every log line
the request produces and returned in the `X-Request-Id` response header.

## Where it comes from

- A caller that sends `X-Request-Id` keeps its own id, as long as the value is 1–64 characters
  from `A–Z a–z 0–9 . _ -`. A discovery service sends the reference its patron was shown, so one
  id finds the request in both services' logs.
- Anything else is replaced with a generated 12-character hex id. The header is written into
  log lines, so a value containing a line break could otherwise forge one.

## Where to find it

| Appender (`DCB_LOG_APPENDERS`) | Where the id appears |
|---|---|
| `STDOUT` (JSON) | the `mdc.requestId` field |
| `STDOUT_SYNC` (the default) and the log file | in brackets after the level: `INFO  [ui-7f3a21] org.olf.dcb…` |

A line logged outside any request, such as a scheduled job or startup, shows empty brackets.

## What it does not cover

The id lasts only as long as the inbound HTTP call. It covers request validation and the work
done before the response is sent. It does **not** follow a patron request into later
state-machine transitions or tracking sweeps, which run on their own schedule. For those,
search by the patron request id instead: it appears in the audit log, and a discovery service
can show it in its support summary.

The id is never sent on to a member library's LMS.
