-- A library's figure for a week, for the trend alerts that cannot be rebuilt from the audit trail:
-- a count at a moment (items awaiting pickup) and a median (turnaround). Written once a week by
-- WeeklyMeasureJob, kept 52 weeks. docs/operational/staff-notifications.md. New table only.

CREATE TABLE library_weekly_measure (
	agency_code varchar(32) NOT NULL,
	measure varchar(48) NOT NULL,
	-- The Monday, UTC, the week starts on
	week_start date NOT NULL,
	value double precision NOT NULL,
	-- How many requests the figure is drawn from
	samples integer NOT NULL,
	recorded_at timestamptz NOT NULL,
	PRIMARY KEY (agency_code, measure, week_start)
);

CREATE INDEX idx_library_weekly_measure_week ON library_weekly_measure (week_start);
