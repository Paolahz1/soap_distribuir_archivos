package org.example.application.queue;

import org.example.domain.port.StorageCommand;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskQueue {

    private final Queue<StorageCommand> queue = new ConcurrentLinkedQueue<>();
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
                StorageCommand command = queue.poll();
                if (command != null) {
                    executor.submit(() -> {
                        try {
                            System.out.println("Ejecutando comando: " + command.getClass().getSimpleName());
                            command.execute();
                        } catch (Exception e) {
                            System.err.println("Error ejecutando comando: " + e.getMessage());
                        }
                    });
                }
                try {
                    Thread.sleep(50); // Evita uso excesivo de CPU
                } catch (InterruptedException ignored) {}
            }
        };

        Thread thread = new Thread(processor, "TaskQueue-Processor");
        thread.setDaemon(false); // Cambiado a false para que el hilo no sea daemon y mantenga la aplicación viva
        thread.start();
    }

    public void enqueue(StorageCommand command) {
        queue.add(command);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
