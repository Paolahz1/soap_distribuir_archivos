package org.example.application.Dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "OperationResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class OperationResponse {
    private boolean success;
    private String message;
    private String errorCode;

    public OperationResponse() {}

    public OperationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public OperationResponse(boolean success, String message, String errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
    }

    // Factory methods para crear respuestas comunes
    public static OperationResponse success(String message) {
        return new OperationResponse(true, message);
    }

    public static OperationResponse error(String message) {
        return new OperationResponse(false, message);
    }

    public static OperationResponse error(String message, String errorCode) {
        return new OperationResponse(false, message, errorCode);
    }

    // Getters y setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return "OperationResponse{" +
                "success=" + success +
                ", message='" + message + '\'' +
                (errorCode != null ? ", errorCode='" + errorCode + '\'' : "") +
                '}';
    }
}