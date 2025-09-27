package org.example.application.service;

import org.example.infrastructure.remote.NodeFileService;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selector de nodos RMI usando round-robin entre stubs ya conectados.
 */
public class NodeSelector {

    private final List<NodeFileService> nodes;
    private final AtomicInteger index = new AtomicInteger(0);

    public NodeSelector(List<NodeFileService> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("La lista de nodos no puede estar vacía");
        }
        this.nodes = nodes;
    }

    /**
     * Devuelve el siguiente stub disponible usando round-robin.
     */
    public NodeFileService selectNext() {
        int i = index.getAndUpdate(n -> (n + 1) % nodes.size());
        return nodes.get(i);
    }
}
