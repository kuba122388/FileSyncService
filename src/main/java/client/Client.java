package client;

import common.json.JsonUtils;
import common.model.ClientData;
import common.model.FileInfo;
import common.model.TaskList;
import common.utils.FileWorker;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

public class Client implements Runnable {
    private InetAddress serverIp;
    private int serverPort = -1;
    private String userID;
    private String directoryPath;

    private final boolean autoFind;
    private final Object lock = new Object();

    public Client(boolean findServer) {
        this.autoFind = findServer;
    }

    @Override
    public void run() {
        MulticastDiscovery multicastDiscovery = new MulticastDiscovery(lock);

        if (autoFind) {
            discoverServer(multicastDiscovery);
        }

        while (true) {
            promptUserInput();


            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverIp, serverPort), 5000);
                connectAndSync(socket);

                if (autoFind) {
                    multicastDiscovery.setPaused(true);
                }
            } catch (IOException e) {
                System.out.println("Could not connect to server within 5 seconds.\n");
            }
        }
    }

    private void discoverServer(MulticastDiscovery discovery) {
        Thread thread = new Thread(discovery);
        thread.start();
        synchronized (lock) {
            try {
                lock.wait();
                serverIp = discovery.getIp();
                serverPort = discovery.getServerUSPPort();
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void promptUserInput() {
        Scanner scanner = new Scanner(System.in);

        while (!autoFind) {
            try {
                System.out.print("Enter server IP: ");
                serverIp = InetAddress.getByName(scanner.nextLine());

                System.out.print("Enter server port: ");
                serverPort = Integer.parseInt(scanner.nextLine());

                if (serverPort < 1 || serverPort > 65535) {
                    System.out.println("Invalid port\n");
                    continue;
                }
                break;
            } catch (Exception e) {
                System.out.println("Invalid IP or port, try again.\n");
            }
        }

        System.out.print("Enter your ID: ");
        userID = scanner.nextLine();

        System.out.print("Enter your directory to archive: ");
        directoryPath = scanner.nextLine();
    }

    private void connectAndSync(Socket socket) {
        try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            List<FileInfo> files = getFiles();
            ClientData clientInfo = new ClientData(userID, files);
            String clientInfoJson = JsonUtils.toJson(clientInfo);

            System.out.println("\nSending information about files to archive...");
            writer.write(clientInfoJson + "\n");
            writer.flush();

            String taskListJson = reader.readLine();
            TaskList taskList = JsonUtils.fromJson(taskListJson, TaskList.class);

            Thread.sleep(1000); // Delay before sending files

            if (taskList.outdatedFiles().isEmpty()) {
                System.out.println("All files are up to date!");
            } else {
                int filesSent = sendFiles(socket, taskList);
                if (filesSent != taskList.outdatedFiles().size()) {
                    System.out.println("Some files could not be sent.\n");
                }
            }

            String localDateString = reader.readLine();
            LocalDateTime nextSync = LocalDateTime.parse(localDateString);
            System.out.println("Next synchronization: " + nextSync);

            Duration waitTime = Duration.between(LocalDateTime.now(), nextSync);
            Thread.sleep(waitTime);

        } catch (IOException | InterruptedException e) {
            System.out.println("Error during synchronization: " + e.getMessage());
        }
    }

    private int sendFiles(Socket socket, TaskList taskList) throws IOException {
        int filesSent = 0;

        for (FileInfo fileInfo : taskList.outdatedFiles()) {
            File file = new File(directoryPath, fileInfo.filePath());

            if (!file.exists()) {
                System.out.println("File does not exist: " + fileInfo.filePath());
                continue;
            }

            System.out.println("Sending file: " + fileInfo.filePath());
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

            dataOut.writeUTF(fileInfo.filePath());
            dataOut.writeLong(file.length());
            dataOut.writeLong(file.lastModified());

            try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[8192];
                int count;

                while ((count = fileIn.read(buffer)) > 0) {
                    dataOut.write(buffer, 0, count);
                }

                dataOut.flush();
                System.out.println("File sent: " + fileInfo.filePath());
                filesSent++;
            }
        }

        return filesSent;
    }

    private List<FileInfo> getFiles() {
        Path basePath = Paths.get(directoryPath);
        FileWorker fileWorker = new FileWorker(basePath, false);
        return fileWorker.walkFolder();
    }
}
