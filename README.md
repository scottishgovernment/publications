# Publications

This project provides a REST service which tracks publications previously 
uploaded as ZIP files from APS and allows the uploaded ZIP files to be 
downloaded.

Uploading of ZIP files and importing them into Hippo is no longer handled 
by this service.

It collaborates with:
* proxette: used to ensure that only users with admin access can access this api
* publishing-site: uses this api to provide the publications functionality
* postgres: has a simple posgres database used to store processing information
* s3: uploaded publications are stored in s3
 
## Configuration

## Endpoints

`GET /publications?page=<page>&size=<size>&q=<searchterm>`

Returns a paged list of publications known to the service. The results are
sorted by last modified date with the most recent first.

* `page`
    * the page to return
    * 1 based
    * defaults to 1 if not specified
* `size`
    * the size of the page to return
    * 1 based
    * defaults to 10 if not specified
* `q`
    * Query string
    * Performs a case insensitive partial match on the title and ISBN fields
    * defaults to empty string which will return all results
        
{
    "totalSize": 1,
    "page": 1,
    "pageSize": 10,
    "publications": [
        {
            "id": "26581f80-d334-4f82-844a-55865d36c8ee",
            "title": "Social Security Scotland Digital and Technology Strategy",
            "isbn: "9781787810754",
            "embargodate": 1538118000000,
            "state": "DONE",
            "statedetails": null,
            "checksum": "b58f351f9b2774038c1c0711ab02cdce",
            "createddate": 1543838449031,
            "lastmodifieddate": 1543838461611
        }
    ]
}


`GET /publications/<id>`

Return the details of a publication

* `id`
    * id of the publication to return

 {
    "id": "26581f80-d334-4f82-844a-55865d36c8ee",
    "title": "Social Security Scotland Digital and Technology Strategy",
    "isbn: "9781787810754",
    "embargodate": 1538118000000,
    "state": "DONE",
    "statedetails": null,
    "checksum": "b58f351f9b2774038c1c0711ab02cdce",
    "createddate": 1543838449031,
    "lastmodifieddate": 1543838461611
}

## Maintenance
The service tracks publicaitons in a database and also stores the uploaded 
zip files in s3.  Files in s3 that are not referenced by any publicaitons 
in the database can be throught of as orphans.

The service provides endpoints to allow the identification and deletion 
of these orphans with the intention that we can periodically remove them.

`GET /maintenance/orphans`
Fetches a list of orphaned zip files in s3.

["550d51c216439a2e343434b329fc210b", "bd3281126c1f5ee7d4747349f084ddcc"]


`DELETE /maintenance/orphans`
Initiates a job to delete all orphaned zip files.  As this might take some time 
this endpoint returns immediately.  The caller should then poll /maintenance 
to see results.

`GET /maintenance`
Get the maintenance status of the service. 

{
    "inMaintenance":false,
    "lastStartTime":null,
    "data":{}
}

## Monitoring
The healthcheck endpoint is `GET /health`. The endpoint returns a JSON response
with the properties listed below. The status code is `200` if the service is
healthy, and `503` otherwise.

* `ok`
    * true if the service is healthy, false otherwise
    * Type: boolean     
* `errors`
  * If present, indicates reasons that the service is unhealthy.
  * Type: array of strings
