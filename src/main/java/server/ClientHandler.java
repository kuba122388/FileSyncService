package server;

import common.json.JsonUtils;
import common.model.ClientInfo;
import common.model.FileInfo;
import common.model.TaskList;
import common.utils.FileWorker;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final int syncInverval;
    private final Path archivePath = Paths.get("archive").toAbsolutePath();

    public ClientHandler(Socket socket, int syncInterval) {
        this.clientSocket = socket;
        this.syncInverval = syncInterval;
    }

    @Override
    public void run() {
        System.out.println("ClientHandler started for: " + clientSocket.getRemoteSocketAddress());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            // Read Client files to archive
            String json = reader.readLine();
            System.out.println(json);
            ClientInfo clientInfo = JsonUtils.fromJson(json, ClientInfo.class);

            // Setting Client path in archive
            Path clientsDirectory = archivePath.resolve(clientInfo.clientId());
            System.out.println(clientsDirectory);

            // Check if Client has folder for that id
            if (!Files.exists(clientsDirectory)) {
                System.out.println("Directory for id: " + clientInfo.clientId());
                Files.createDirectory(clientsDirectory);
            }

            // Check if Client folder contains any files yet
            FileWorker fileWorker = new FileWorker(clientsDirectory);
            List<FileInfo> filesServerside = fileWorker.walkFolder();
            List<FileInfo> filesClientside = clientInfo.files();

            // Create map based on <Filepath, ModificationDate> with files on server
            Map<String, Long> serverFilesMap = new HashMap<>();
            for (FileInfo file : filesServerside) serverFilesMap.put(file.filePath(), file.modificationDate());

            // Initialize info for client to upload outdated files
            List<FileInfo> outdatedFiles = new ArrayList<>();

            // Comparing Files
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

            TaskList taskList = new TaskList(outdatedFiles);
            String taskListJson = JsonUtils.toJson(taskList);
            writer.write(taskListJson + "\n");
            writer.flush();
            System.out.println("Sent information about files needed to be updated !");


            try (DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream())) {

                for (int i = 0; i < taskList.outdatedFiles().size(); i++) {

                    String relativePath = dataIn.readUTF();
                    long fileLength = dataIn.readLong();
                    long lastModified = dataIn.readLong();

                    // 3. Przygotuj plik docelowy
                    Path outputPath = archivePath.resolve(clientInfo.clientId()).resolve(relativePath);
                    Files.createDirectories(outputPath.getParent()); // upewnij się, że folder istnieje

                    File outputFile = outputPath.toFile();

                    try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(outputPath.toFile()))) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;

                        while (totalRead < fileLength &&
                                (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileLength - totalRead))) != -1) {
                            fileOut.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                        System.out.println("Odebrano plik: " + relativePath + " (" + totalRead + " bajtów)");
                    }

                    outputFile.setLastModified(lastModified);
                }

                LocalDateTime nextSync = LocalDateTime.now().plusMinutes(syncInverval);
                writer.write(nextSync + "\n");
                writer.flush();

            } catch (IOException e) {
                System.err.println("Błąd podczas odbierania plików: " + e.getMessage());
            }



        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
