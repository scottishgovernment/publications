# Publications

This project provides a REST service which supports uploading of ZIP
files from APS onto www.gov.scot. It is used by the Publications tab 
within Rubric for www.gov.scot.  

It collaborates with:
* proxette: used to ensure that only users with admin access can access this api
* publishing-site: uses this api to provide the publications upload functionality
* jcr / hippo: used to insert publications on www.gov.scot
* postgres: has a simple posgres database used to store processing information
* s3: publications are uploaded to s3
 
When a ZIP file is uploaded a job is added to a queue and an entry 
returned to allow clients to track the status of the publication.

## Usage
How to run ... 

## Configuration
s3 configuration...

## Endpoints

GET /health

POST /publications

GET /publications

GET /publications/publicationid

