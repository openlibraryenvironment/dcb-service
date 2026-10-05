


# Sierra errors

##  XCirc error: XCirc error : There is a problem with your library record.  Please see a librarian. - [132 / 2]

We think this happens when a patron has passed it's expiry date.

## REST API version

DCB calls `/iii/sierra-api/v6/` unless the Host LMS sets `api-version` to `v4`,
`v5` or `v6`. Any other value is refused when the Host LMS is saved. DCB is
tested against v6 only. Use an older version only for a Sierra that does not
serve v6, and test a full request cycle against it first.
