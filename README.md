# Publications

This project provides a REST service which supports uploading ZIP files 
from APS to Hippo. 

It collaborates with:
* proxette: used to ensure that only users with admin access can access this api
* publishing-site: uses this api to provide the publications upload functionality
* jcr / hippo: used to insert publications on www.gov.scot
* postgres: has a simple posgres database used to store processing information
* s3: publications are uploaded to s3
 
When a ZIP file is uploaded a job is added to a queue and an entry 
returned to allow clients to track the status of the publication.
 
## Configuration

## Endpoints

`POST /publications/`

Create a new publications uby uploding a zip file.

This endpoint will either return an error if the file is not a valid zip 
file containing metadata and a manifest or the details of a job that can 
be polled to determine if the publication was imported successfully.

TThe file should be specified as a the form field 'file'. 
 
If the zip is accepted then the output will look like:
 
201
{
    "accepted": true,
    "message": "accepted",
    "publication": {
        "id": "11f8ec4d-ecc9-4e95-ac7a-62a97754b4e5",
        "title": "Support for Nursing and Midwifery Students in Scotland 2018-19",
        "isbn": "9781788517348",
        "embargodate": 1544003700000,
        "state": "PENDING",
        "statedetails": null,
        "checksum": "8b754477ce0f2756a83a7007627692d8",
        "createddate": null,
        "lastmodifieddate": null
    }
}

If it is rejected the response will look like: 

400
{
    "accepted": false,
    "message": "Failed to extract metadata from zip",
    "publication": null
}

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
