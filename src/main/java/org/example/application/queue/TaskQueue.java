package org.example.application.queue;

import org.example.domain.port.StorageCommand;

import java.util.Queue;
import java.util.concurrent.*;

public class TaskQueue {

    private final Queue<StorageCommand<?>> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public TaskQueue() {
        startBackgroundProcessor();
    }

    /*
     * Inicia un hilo en segundo plano que procesa los comandos encolados.
     */
    private void startBackgroundProcessor() {
        Runnable processor = () -> {
            while (true) {
                StorageCommand<?> command = queue.poll();
                if (command != null) {
                    executor.submit(() -> {
                        try {
                            System.out.println("Ejecutando comando: " + command.getClass().getSimpleName());
                            return command.execute();
                        } catch (Exception e) {
                            System.err.println("Error ejecutando comando: " + e.getMessage());
                            throw e;
                        }
                    });
                }
                try {
                    Thread.sleep(50); // evita busy-wait y mantiene vivo el hilo
                } catch (InterruptedException ignored) {}
            }
        };

        Thread thread = new Thread(processor, "TaskQueue-Processor");
        thread.setDaemon(false); // mantiene la app viva
        thread.start();
    }

    /**
     * Encola un comando y devuelve un Future con el resultado.
     */
    public <T> Future<T> enqueue(StorageCommand<T> command) {
        CompletableFuture<T> future = new CompletableFuture<>();
        queue.add(() -> {
            try {
                T result = command.execute();
                future.complete(result);
                return result;
            } catch (Exception e) {
                future.completeExceptionally(e);
                throw e;
            }
        });
        return future;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
