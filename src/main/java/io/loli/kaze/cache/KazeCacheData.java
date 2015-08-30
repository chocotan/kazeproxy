package io.loli.kaze.cache;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Map;

public class KazeCacheData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int status;
    private String contentType;
    private byte[] data;
    private transient ByteArrayOutputStream os;

    private Map<String, String> headers;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public KazeCacheData() {
    }

    public KazeCacheData(int status, String contentType, byte[] data) {
        super();
        this.status = status;
        this.contentType = contentType;
        this.data = data;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public ByteArrayOutputStream getOs() {
        return os;
    }

    public void setOs(ByteArrayOutputStream os) {
        this.os = os;
    }

}
