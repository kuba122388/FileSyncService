package server;

import common.json.JsonUtils;
import common.model.ClientData;
import common.model.FileInfo;
import common.model.TaskList;
import common.utils.FileWorker;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final int syncInterval;
    private final Path archivePath;
    private final Runnable onComplete;


    public ClientHandler(Socket socket, int syncInterval, Path archivePath, Runnable onComplete) {
        this.clientSocket = socket;
        this.syncInterval = syncInterval;
        this.archivePath = archivePath;
        this.onComplete = onComplete;
    }

    @Override
    public void run() {

        System.out.println("ClientHandler started for: " + clientSocket.getRemoteSocketAddress());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            // Read Client files to archive
            String json = reader.readLine();
            ClientData clientData = JsonUtils.fromJson(json, ClientData.class);

            // Setting Client path in archive
            Path clientsDirectory = getClientsDir(clientData.clientId());

            // Get information about files in the Client directory on Server and Client side
            FileWorker fileWorker = new FileWorker(clientsDirectory, true);
            List<FileInfo> filesServerside = fileWorker.walkFolder();
            List<FileInfo> filesClientside = clientData.files();

            // Create map based on Map<Filepath, ModificationDate> with files on server
            Map<String, Long> serverFilesMap = new HashMap<>();
            for (FileInfo file : filesServerside) serverFilesMap.put(file.filePath(), file.modificationDate());

            // Get outdated files
            List<FileInfo> outdatedFiles = getOutdatedFiles(serverFilesMap, filesClientside);
            int filesToUpdate = outdatedFiles.size();

            // Create and send files that needs to be uploaded/updated
            TaskList taskList = new TaskList(outdatedFiles);
            String taskListJson = JsonUtils.toJson(taskList);
            writer.write(taskListJson + "\n");
            writer.flush();

            // Display info about list of tasks if it contains any file that needs to be uploaded
            System.out.println();
            if (filesToUpdate != 0) System.out.println("- Sent information about files needed to be uploaded ! -\n");
            else System.out.println("- None of the files needs to be updated ! -\n");

            try (DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream())) {
                // Download files
                for (int i = 0; i < taskList.outdatedFiles().size(); i++) {
                    downloadFile(dataIn, clientData);
                }

                // Send next synchronization time
                LocalDateTime nextSync = LocalDateTime.now().plusMinutes(syncInterval);
                writer.write(nextSync + "\n");
                writer.flush();

            } catch (IOException e) {
                System.err.println("Problem occurred while receiving files: " + e.getMessage());
            }

            deleteRedundantFiles(filesServerside, filesClientside, clientsDirectory);
            System.out.println("Client served, waiting for the next one...");
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            onComplete.run();
        }
    }

    private void deleteRedundantFiles(List<FileInfo> filesServerside, List<FileInfo> filesClientside, Path clientsDirectory) {
        for (FileInfo serverFile : filesServerside) {
            boolean existsOnClient = filesClientside.stream().anyMatch(clientFile -> clientFile.filePath().equals(serverFile.filePath()));

            if (!existsOnClient) {
                Path fileToDelete = clientsDirectory.resolve(serverFile.filePath());
                try {
                    Files.deleteIfExists(fileToDelete);
                    System.out.println("Successfully deleted file: " + fileToDelete);
                } catch (IOException e) {
                    System.err.println("There was a problem with deleting: " + fileToDelete);
                }
            }
        }
    }

    private void downloadFile(DataInputStream dataIn, ClientData clientData) throws IOException {
        String relativePath = dataIn.readUTF();
        long fileLength = dataIn.readLong();
        long lastModified = dataIn.readLong();

        Path outputPath = archivePath.resolve(clientData.clientId()).resolve(relativePath);
        Files.createDirectories(outputPath.getParent());

        File outputFile = outputPath.toFile();

        try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;

            while (totalRead < fileLength &&
                    (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileLength - totalRead))) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("File received: " + relativePath + " (" + totalRead + " bytes)");
        }

        outputFile.setLastModified(lastModified);
    }

    private Path getClientsDir(String path) throws IOException {
        Path clientsDirectory = archivePath.resolve(path);

        // Check if Client has folder for that id
        if (!Files.exists(clientsDirectory)) {
            System.out.println("Created directory for client - id: " + path);
            Files.createDirectories(clientsDirectory);
        }

        return clientsDirectory;
    }

    private List<FileInfo> getOutdatedFiles(Map<String, Long> serverFilesMap, List<FileInfo> filesClientside) {
        List<FileInfo> outdatedFiles = new ArrayList<>();

        // Check if file that Client request is already on server and if it needs update
        for (FileInfo file : filesClientside) {
            Long modificationDate = serverFilesMap.get(file.filePath());
            if (modificationDate != null) {
                if (modificationDate.equals(file.modificationDate())) {
                    System.out.println("File: " + file.filePath() + " is up to date.");
                } else {
                    System.out.println("File: " + file.filePath() + " needs to be updated.");
                    outdatedFiles.add(file);
                }
            } else {
                System.out.println("File: " + file.filePath() + " needs to be uploaded.");
                outdatedFiles.add(file);
            }
        }
        return outdatedFiles;
    }
}
