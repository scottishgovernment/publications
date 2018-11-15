package scot.gov.publications.rest;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.FormParam;

public class UploadRequest {

    private byte[] fileData;

    public byte[] getFileData() {
        return fileData;
    }

    @FormParam("file")
    @PartType("application/octet-stream")
    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }
}
