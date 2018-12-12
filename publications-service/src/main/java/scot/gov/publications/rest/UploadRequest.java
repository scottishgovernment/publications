package scot.gov.publications.rest;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.FormParam;

public class UploadRequest {

    private String filename;

    private byte[] fileData;

    public String getFilename() {
        return filename;
    }

    @FormParam("filename")
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public byte[] getFileData() {
        return fileData;
    }

    @FormParam("file")
    @PartType("application/octet-stream")
    public void setFileData(byte[] fileData) {
        this.fileData = fileData;
    }
}
