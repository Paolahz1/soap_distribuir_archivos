package org.example.application.service;

import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeService {


    private final FileRepository fileRepository;

    public NodeService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /**
     * Registra un nodo en la BD si no existe, o devuelve su id si ya está registrado.
     */
    public Long upsertNode(String ip, int port) throws SQLException {
        return fileRepository.upsertNode(ip, port);
    }

    /**
     * Asocia un archivo con un nodo en la BD.
     */
    public  String registerFileNode(String fileUuid, Long nodeId) throws SQLException {
        return fileRepository.registerFileNode(fileUuid, nodeId);
    }

    /**
     * Devuelve todos los nodos donde está replicado un archivo.
     */
    public List<Long> getNodeIdsByFile(String fileUuid) throws SQLException {
        System.out.println("LLEGA A NodeService.getNodeIdsByFile");
        return fileRepository.getNodesByFile(fileUuid);
    }

    /**
     * Registra todos los stubs descubiertos en la BD y devuelve un mapa nodeId → stub.
     */
    public Map<Long, NodeFileService> registerDiscoveredNodes(List<String> hosts, List<Integer> ports) {
        if (hosts.size() != ports.size()) {
            throw new IllegalArgumentException("El número de hosts debe coincidir con el número de puertos");
        }

        Map<Long, NodeFileService> nodeMap = new HashMap<>();

        for (int i = 0; i < hosts.size(); i++) {
            System.out.println("Descubriendo nodo RMI en " + hosts.get(i) + ":" + ports.get(i));
            String host = hosts.get(i);
            int port = ports.get(i);

            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                String[] boundNames = registry.list();

                if (boundNames.length == 0) {
                    System.err.println("No hay servicios registrados en " + host + ":" + port);
                    continue;
                }

                for (String name : boundNames) {
                    try {
                        NodeFileService stub = (NodeFileService) registry.lookup(name);

                        // Registrar en BD
                        Long nodeId = upsertNode(host, port);
                        nodeMap.put(nodeId, stub);

                        System.out.println("Nodo registrado: " + host + ":" + port + " | service=" + name + " | id=" + nodeId);

                    } catch (Exception e) {
                        System.err.println("Error al obtener stub '" + name + "' en " + host + ":" + port + ": " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                System.err.println("Error descubriendo nodo en " + host + ":" + port + ": " + e.getMessage());
            }
        }

        if (nodeMap.isEmpty()) {
            throw new IllegalStateException("No se pudo conectar a ningún nodo RMI. Verifica que los nodos estén activos.");
        }

        System.out.println("Total de nodos descubiertos: " + nodeMap.size());
        return nodeMap;
    }

}
