CREATE TABLE publication
(
  id character varying(255) NOT NULL,
  title character varying NOT NULL,
  isbn character varying(25) NOT NULL,
  embargo_date timestamp,
  state character varying(20) NOT NULL,
  state_details character varying,
  checksum character varying NOT NULL,
  created_date timestamp,
  last_modified_date timestamp,

  CONSTRAINT storage_pkey PRIMARY KEY (id)
);

ALTER TABLE publication OWNER TO publications;

