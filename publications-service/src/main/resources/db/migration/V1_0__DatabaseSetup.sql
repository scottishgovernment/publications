CREATE TABLE publication
(
  id character varying(255) NOT NULL,
  title character varying NOT NULL,
  isbn character varying(25) NOT NULL,
  embargodate timestamp,
  state character varying(20) NOT NULL,
  statedetails character varying,
  checksum character varying NOT NULL,
  createddate timestamp,
  lastmodifieddate timestamp,

  CONSTRAINT storage_pkey PRIMARY KEY (id)
);

