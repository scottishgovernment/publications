package scot.gov.publications.rest;

import scot.gov.publications.repo.Publication;

import java.io.PrintWriter;
import java.io.StringWriter;

public class UploadResponse {

    // was the upload accepted?
    private boolean accepted;

    // message - 'acepted' if it was accetped and an reason string if it was not
    private String message;

    // the stack trace that caused the submission to fail (if any)
    private String stackTrace;

    private Publication publication;

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public Publication getPublication() {
        return publication;
    }

    public void setPublication(Publication publication) {
        this.publication = publication;
    }

    public static UploadResponse error(String message, Throwable t) {
        UploadResponse response = new UploadResponse();
        response.setAccepted(false);
        response.setMessage(message);
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        response.setStackTrace(sw.toString());
        return response;
    }

    public static UploadResponse accepted(Publication publication) {
        UploadResponse response = new UploadResponse();
        response.setAccepted(true);
        response.setMessage("accpted");
        response.setPublication(publication);
        return response;
    }
}
