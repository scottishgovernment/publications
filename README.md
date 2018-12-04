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

`GET /publications?page=<page>&size=<size>&q=<searchterm>`

Returns a paged list of publications know to the service. The results are
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
