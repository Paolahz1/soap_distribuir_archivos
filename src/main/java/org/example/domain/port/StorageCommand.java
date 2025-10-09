package org.example.domain.port;

/*
    Interfaz genérica para comandos que interactúan con el sistema de almacenamiento distribuido.
    Cada comando implementa la lógica específica en su mé_todo execute(), que será invocado
    por el TaskQueue en un hilo separado.
 */
public interface StorageCommand<T> {
    T  execute() throws  Exception; // ejecutado en el nodo
}
