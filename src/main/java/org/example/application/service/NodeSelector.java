package org.example.application.service;

import org.example.infrastructure.remote.NodeFileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selector de nodos RMI usando round-robin entre stubs ya conectados.
 */
public class NodeSelector {

    private final List<Map.Entry<Long, NodeFileService>> nodes;
    private final AtomicInteger index = new AtomicInteger(0);

    public NodeSelector(Map<Long, NodeFileService> nodeMap) {
        if (nodeMap == null || nodeMap.isEmpty()) {
            throw new IllegalArgumentException("El mapa de nodos no puede estar vacío");
        }
        this.nodes = new ArrayList<>(nodeMap.entrySet());
    }

    /**
     * Devuelve el siguiente nodo (id + stub) usando round-robin.
     */
    public Map.Entry<Long, NodeFileService> selectNext() {
        int i = index.getAndUpdate(n -> (n + 1) % nodes.size());
        return nodes.get(i);
    }

    /**
     * Devuelve el stub de un nodo específico por id.
     */
    public NodeFileService getStubById(Long nodeId) {
        return nodes.stream()
                .filter(e -> e.getKey().equals(nodeId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
