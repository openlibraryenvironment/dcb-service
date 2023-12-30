CREATE TABLE delivery_network_edge (
  id uuid PRIMARY KEY,
  date_created timestamp,
  date_updated timestamp,
  from_location_fk uuid,
  to_location_fk uuid,
	active boolean,
  planning_cost integer,
  route_code varchar(32),
  segment_code varchar(32),
  CONSTRAINT fk_loc_from FOREIGN KEY (from_location_fk) REFERENCES public.location (id),
  CONSTRAINT fk_loc_to FOREIGN KEY (to_location_fk) REFERENCES public.location (id)
);
