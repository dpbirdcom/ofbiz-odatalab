package com.dpbird.odata.edm;

import java.nio.ByteBuffer;

public class ParameterContext {
    private String parameterName;
    private ByteBuffer file;
    private String fileName;
    private Long fileSize;
    private String fileMimeType;

    public ParameterContext(ByteBuffer file, String fileName, Long fileSize, String fileMimeType) {
        this.file = file;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileMimeType = fileMimeType;
    }

    public ParameterContext() {
    }

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public ByteBuffer getFile() {
        return file;
    }

    public void setFile(ByteBuffer file) {
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileMimeType() {
        return fileMimeType;
    }

    public void setFileMimeType(String fileMimeType) {
        this.fileMimeType = fileMimeType;
    }
}
