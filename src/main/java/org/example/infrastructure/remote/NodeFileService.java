package org.example.infrastructure.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface NodeFileService extends Remote {

    // Crear directorios o subdirectorios
    boolean createDirectory(String ownerId, String path) throws RemoteException;

    // Subir/almacenar archivos
    boolean uploadFile(String fileId, byte[] content) throws RemoteException;

    // Leer/descargar archivos
    byte[] downloadFile(String filePath) throws RemoteException;
    List<byte[]> downloadFiles(List<String> filePaths) throws RemoteException;

    // Mover/renombrar archivos o directorios
    boolean moveFile(String sourcePath, String destinationPath) throws RemoteException;
    boolean renameFile(String currentPath, String newName) throws RemoteException;
    boolean moveDirectory(String sourcePath, String destinationPath) throws RemoteException;

    // Eliminar archivos o directorios
    boolean deleteFile(String filePath) throws RemoteException;
    boolean deleteFiles(List<String> filePaths) throws RemoteException;
    boolean deleteDirectory(String directoryPath) throws RemoteException;

    // Compartir archivos o directorios
    boolean shareFile(String filePath, String targetUser) throws RemoteException;
    boolean shareFiles(List<String> filePaths, String targetUser) throws RemoteException;
    boolean shareDirectory(String directoryPath, String targetUser) throws RemoteException;

    // Métodos auxiliares
    List<String> listFiles(String directoryPath) throws RemoteException;
    boolean exists(String path) throws RemoteException;
    boolean isDirectory(String path) throws RemoteException;
}
