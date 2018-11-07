package scot.gov.publications;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.FormParam;

public class FileUpload {

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
