package com.mycompany.msr.amis;

public class ApiClientException extends RuntimeException {

    private final int statusCode;

    public ApiClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
