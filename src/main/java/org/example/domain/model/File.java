package org.example.domain.model;

public  class File{
    private String id;
    private String name;
    private long size;
    private Long ownerId;
    private Long directoryId;

    public File(String id, String name, long size, Long ownerId, Long directoryId) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.ownerId = ownerId;
        this.directoryId = directoryId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getDirectoryId() {
        return directoryId;
    }

    public void setDirectoryId(Long directoryId) {
        this.directoryId = directoryId;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}