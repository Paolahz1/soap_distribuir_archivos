package org.example.infrastructure.remote;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente para descubrir y conectarse a nodos remotos que implementan NodeFileService.
 */
public class NodeRegistryClient {

    /**
     * Descubre nodos remotos en una lista de hosts y un puerto de registro dado.
     * Devuelve los stubs RMI ya conectados.
     */
    public static List<NodeFileService> discoverNodes(List<String> nodeHosts, int registryPort) {
        List<NodeFileService> nodes = new ArrayList<>();

        for (String host : nodeHosts) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, registryPort);
                String[] boundNames = registry.list();

                for (String name : boundNames) {
                    NodeFileService stub = (NodeFileService) registry.lookup(name);
                    nodes.add(stub);
                    System.out.println("Nodo encontrado: " + name + " en " + host);
                }

            } catch (Exception e) {
                System.err.println("Error al conectar con nodo en " + host + ": " + e.getMessage());
            }
        }

        return nodes;
    }
}
