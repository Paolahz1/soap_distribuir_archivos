package org.example.application.service;

import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Selector de nodos con balanceo inteligente considerando:
 * - Carga relativa (usado/capacidad)
 * - Tareas activas en tiempo real
 * - Redundancia configurable
 */
public class NodeSelector {

    private static final Logger LOGGER = Logger.getLogger(NodeSelector.class.getName());

    private final List<Map.Entry<Long, NodeFileService>> nodes;
    private final FileRepository fileRepository;
    private final ScheduledExecutorService syncScheduler;

    // Métricas de balanceo
    private final Map<Long, AtomicInteger> nodeFileCount = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> nodeSpaceUsed = new ConcurrentHashMap<>();
    private final Map<Long, Long> nodeCapacity = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> nodeActiveTasks = new ConcurrentHashMap<>();

    // Configuración
    private static final int REPLICATION_FACTOR = 2;
    private static final int MAX_ACTIVE_TASKS = 100;
    private static final double ACTIVE_TASKS_WEIGHT = 0.05;
    private static final long SYNC_INTERVAL_MINUTES = 5;

    // Control de inicialización
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final CountDownLatch initLatch = new CountDownLatch(1);

    public NodeSelector(Map<Long, NodeFileService> nodeMap, FileRepository fileRepository) {
        if (nodeMap == null || nodeMap.isEmpty()) {
            throw new IllegalArgumentException("El mapa de nodos no puede estar vacío");
        }

        this.nodes = new ArrayList<>(nodeMap.entrySet());
        this.fileRepository = fileRepository;
        this.syncScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NodeSelector-Sync");
            t.setDaemon(true);
            return t;
        });

        // Inicializar contadores de tareas activas
        for (Long nodeId : nodeMap.keySet()) {
            nodeActiveTasks.put(nodeId, new AtomicInteger(0));
        }

        LOGGER.info("NodeSelector inicializado con " + nodes.size() + " nodos, factor replicación: " + REPLICATION_FACTOR);

        // Inicializar métricas en background
        initializeAsync();

        // Programar sincronización periódica
        scheduleSyncTask();
    }

    /**
     * Inicializa métricas de forma asíncrona para no bloquear el hilo principal.
     */
    private void initializeAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.info("Iniciando carga de métricas de nodos...");

                for (Map.Entry<Long, NodeFileService> node : nodes) {
                    Long nodeId = node.getKey();

                    try {
                        // Cargar métricas desde BD
                        long capacity = fileRepository.getNodeCapacity(nodeId);
                        long spaceUsed = fileRepository.getNodeSpaceUsed(nodeId);
                        int fileCount = fileRepository.countFilesByNode(nodeId);

                        nodeCapacity.put(nodeId, capacity);
                        nodeSpaceUsed.put(nodeId, new AtomicLong(spaceUsed));
                        nodeFileCount.put(nodeId, new AtomicInteger(fileCount));

                        LOGGER.fine("Node-" + nodeId + " -> Capacidad: " + formatBytes(capacity) +
                                ", Usado: " + formatBytes(spaceUsed) + ", Archivos: " + fileCount);

                    } catch (SQLException e) {
                        LOGGER.log(Level.WARNING, "Error al cargar métricas de Node-" + nodeId + ", usando valores por defecto", e);
                        // Valores por defecto
                        nodeCapacity.put(nodeId, 10L * 1024 * 1024 * 1024); // 10GB
                        nodeSpaceUsed.put(nodeId, new AtomicLong(0));
                        nodeFileCount.put(nodeId, new AtomicInteger(0));
                    }
                }

                initialized.set(true);
                initLatch.countDown();
                LOGGER.info("Métricas de nodos cargadas exitosamente");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error crítico al inicializar métricas", e);
                // Asegurar que el latch se libere incluso en error
                initialized.set(true);
                initLatch.countDown();
            }
        });
    }

    /**
     * Programa tarea de sincronización periódica con la BD.
     */
    private void scheduleSyncTask() {
        syncScheduler.scheduleAtFixedRate(() -> {
            try {
                syncWithDatabaseInternal();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error en sincronización periódica", e);
            }
        }, SYNC_INTERVAL_MINUTES, SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Selecciona múltiples nodos para subir archivo con redundancia.
     * Estrategia híbrida: carga relativa + tareas activas
     */
    public List<Map.Entry<Long, NodeFileService>> selectNodesForUpload(long fileSize) {
        // Esperar inicialización (timeout 10 segundos)
        try {
            if (!initLatch.await(30, TimeUnit.SECONDS)) {
                LOGGER.warning("Timeout esperando inicialización, usando valores por defecto");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("Inicialización interrumpida");
        }

        if (nodes.size() == 1) {
            return Collections.singletonList(nodes.get(0));
        }

        List<Map.Entry<Long, NodeFileService>> selectedNodes = new ArrayList<>();

        // Calcular peso combinado para cada nodo
        Map<Long, Double> nodeWeights = calculateNodeWeights();

        // Filtrar nodos sobresaturados
        List<Map.Entry<Long, NodeFileService>> availableNodes = nodes.stream()
                .filter(e -> nodeActiveTasks.get(e.getKey()).get() < MAX_ACTIVE_TASKS)
                .collect(Collectors.toList());

        // Fallback si todos están saturados: usar todos
        if (availableNodes.isEmpty()) {
            LOGGER.warning("Todos los nodos exceden MAX_ACTIVE_TASKS, usando fallback");
            availableNodes = new ArrayList<>(nodes);
        }

        // Seleccionar nodo primario: menor peso
        Map.Entry<Long, NodeFileService> primaryNode = availableNodes.stream()
                .min(Comparator.comparingDouble(e -> nodeWeights.get(e.getKey())))
                .orElse(nodes.get(0));

        selectedNodes.add(primaryNode);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Nodo PRIMARIO: Node-" + primaryNode.getKey() +
                    " (peso: " + String.format("%.3f", nodeWeights.get(primaryNode.getKey())) + ")");
        }

        // Seleccionar réplicas
        int replicasNeeded = Math.min(REPLICATION_FACTOR - 1, availableNodes.size() - 1);
        Set<Long> usedNodeIds = new HashSet<>();
        usedNodeIds.add(primaryNode.getKey());

        List<Map.Entry<Long, NodeFileService>> sortedNodes = availableNodes.stream()
                .filter(e -> !usedNodeIds.contains(e.getKey()))
                .sorted(Comparator.comparingDouble(e -> nodeWeights.get(e.getKey())))
                .collect(Collectors.toList());

        for (int i = 0; i < replicasNeeded && i < sortedNodes.size(); i++) {
            Map.Entry<Long, NodeFileService> replicaNode = sortedNodes.get(i);
            selectedNodes.add(replicaNode);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("RÉPLICA-" + (i + 1) + ": Node-" + replicaNode.getKey() +
                        " (peso: " + String.format("%.3f", nodeWeights.get(replicaNode.getKey())) + ")");
            }
        }

        // Incrementar tareas activas para nodos seleccionados
        for (Map.Entry<Long, NodeFileService> node : selectedNodes) {
            nodeActiveTasks.get(node.getKey()).incrementAndGet();
        }

        LOGGER.info("Seleccionados " + selectedNodes.size() + " nodos para archivo de " + formatBytes(fileSize));

        return selectedNodes;
    }

    /**
     * Calcula el peso de cada nodo combinando carga y tareas activas.
     * Peso menor = mejor candidato
     */
    private Map<Long, Double> calculateNodeWeights() {
        Map<Long, Double> weights = new HashMap<>();

        for (Map.Entry<Long, NodeFileService> node : nodes) {
            Long nodeId = node.getKey();

            // Carga relativa (0.0 - 1.0)
            long used = nodeSpaceUsed.get(nodeId).get();
            long capacity = nodeCapacity.get(nodeId);
            double loadRatio = capacity > 0 ? (double) used / capacity : 0.0;

            // Tareas activas (normalizado)
            int activeTasks = nodeActiveTasks.get(nodeId).get();

            // Peso combinado
            double weight = loadRatio + (ACTIVE_TASKS_WEIGHT * activeTasks);

            weights.put(nodeId, weight);
        }

        return weights;
    }

    /**
     * Marca una tarea como completada en un nodo.
     */
    public void completeTask(Long nodeId) {
        int remaining = nodeActiveTasks.get(nodeId).decrementAndGet();

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Tarea completada en Node-" + nodeId + ", tareas activas: " + remaining);
        }
    }

    /**
     * Registra la subida exitosa de un archivo.
     */
    public void recordFileUpload(Long nodeId, long fileSize) {
        nodeFileCount.get(nodeId).incrementAndGet();
        nodeSpaceUsed.get(nodeId).addAndGet(fileSize);

        LOGGER.fine("Métricas actualizadas Node-" + nodeId + ": +" + formatBytes(fileSize));
    }

    /**
     * Registra la eliminación de un archivo.
     */
    public void recordFileDeletion(Long nodeId, long fileSize) {
        nodeFileCount.get(nodeId).decrementAndGet();
        nodeSpaceUsed.get(nodeId).addAndGet(-fileSize);

        LOGGER.fine("Métricas actualizadas Node-" + nodeId + ": -" + formatBytes(fileSize));
    }

    /**
     * Obtiene stub de un nodo por ID.
     */
    public NodeFileService getStubById(Long nodeId) {
        return nodes.stream()
                .filter(e -> e.getKey().equals(nodeId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Imprime estadísticas detalladas.
     */
    public void printNodeStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("              ESTADÍSTICAS DE DISTRIBUCIÓN DE CARGA                \n");

        long totalFiles = 0;
        long totalSpace = 0;
        long totalCapacity = 0;
        int totalActiveTasks = 0;

        for (Map.Entry<Long, NodeFileService> node : nodes) {
            Long nodeId = node.getKey();
            int files = nodeFileCount.get(nodeId).get();
            long used = nodeSpaceUsed.get(nodeId).get();
            long capacity = nodeCapacity.get(nodeId);
            int activeTasks = nodeActiveTasks.get(nodeId).get();
            double percent = calculateUsagePercent(used, capacity);

            totalFiles += files;
            totalSpace += used;
            totalCapacity += capacity;
            totalActiveTasks += activeTasks;

            String taskIndicator = activeTasks > 50 ? "⚠" : activeTasks > 0 ? "●" : "○";

            sb.append(String.format("║ Node-%d: %s                                                       ║%n", nodeId, taskIndicator));
            sb.append(String.format("║   Archivos: %4d | Espacio: %8s / %8s (%5.1f%%)          ║%n",
                    files, formatBytes(used), formatBytes(capacity), percent));
            sb.append(String.format("║   %s | Tareas activas: %3d              ║%n", activeTasks));
        }

        sb.append("╠════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ TOTAL:                                                             ║%n"));
        sb.append(String.format("║   Archivos: %4d | Espacio: %8s / %8s (%5.1f%%)           ║%n",
                totalFiles, formatBytes(totalSpace), formatBytes(totalCapacity),
                calculateUsagePercent(totalSpace, totalCapacity)));
        sb.append(String.format("║   Tareas activas totales: %3d                                      ║%n", totalActiveTasks));

        double avgPercent = calculateUsagePercent(totalSpace, totalCapacity);
        double maxDeviation = calculateMaxDeviation(avgPercent);
        String balanceStatus = maxDeviation < 10 ? "✓ EXCELENTE" : maxDeviation < 20 ? "✓ BUENO" : "⚠ DESBALANCEADO";

        sb.append(String.format("║   Balance: %s (desv. máx: %.1f%%)                            ║%n",
                balanceStatus, maxDeviation));
        sb.append("╚════════════════════════════════════════════════════════════════════╝\n");

        LOGGER.info(sb.toString());
    }


    /**
     * Sincronización interna con la BD.
     */
    private void syncWithDatabaseInternal() {
        LOGGER.fine("Sincronizando métricas con BD...");

        for (Map.Entry<Long, NodeFileService> node : nodes) {
            Long nodeId = node.getKey();

            try {
                long spaceUsed = fileRepository.getNodeSpaceUsed(nodeId);
                int fileCount = fileRepository.countFilesByNode(nodeId);

                nodeSpaceUsed.get(nodeId).set(spaceUsed);
                nodeFileCount.get(nodeId).set(fileCount);

            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error sincronizando Node-" + nodeId, e);
            }
        }

        LOGGER.fine("Sincronización completada");
    }

    /**
     * Cierra recursos del selector.
     */
    public void shutdown() {
        LOGGER.info("Cerrando NodeSelector...");
        syncScheduler.shutdown();
        try {
            if (!syncScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                syncScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            syncScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Métodos auxiliares

    private double calculateMaxDeviation(double avgPercent) {
        return nodes.stream()
                .mapToDouble(e -> {
                    Long nodeId = e.getKey();
                    long used = nodeSpaceUsed.get(nodeId).get();
                    long capacity = nodeCapacity.get(nodeId);
                    double percent = calculateUsagePercent(used, capacity);
                    return Math.abs(percent - avgPercent);
                })
                .max()
                .orElse(0.0);
    }



    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private double calculateUsagePercent(long used, long capacity) {
        return capacity > 0 ? (used * 100.0 / capacity) : 0.0;
    }

    public List<Map.Entry<Long, NodeFileService>> getAllNodes() {
        return new ArrayList<>(nodes);
    }
}