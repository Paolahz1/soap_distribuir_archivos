package org.example.domain.model;

public class Node {
    private Long  nodeId;
    private String ip;
    private boolean isAvailable;

    public Node(String ip, Long  id) {
        this.ip = ip;
        this.nodeId = id;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Long  getNodeId() {
        return nodeId;
    }

    public void setNodeId(Long  nodeId) {
        this.nodeId = nodeId;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }
}