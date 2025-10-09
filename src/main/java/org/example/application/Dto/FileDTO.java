package org.example.application.Dto;


import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

import java.io.Serializable;

@XmlType(name = "FileUploadDTO")
@XmlAccessorType(XmlAccessType.FIELD)
public class FileDTO implements Serializable {
    private String fileName;
    private byte[] content;

    public FileDTO() {}

    public FileDTO(String fileName, byte[] content) {
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }
}
