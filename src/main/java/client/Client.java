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
    InetAddress serverIp;
    int serverPort = -1;
    private String userID;
    private String directoryPath;

    private boolean autoFind;

    final Object lock = new Object();

    public Client(boolean findServer) {
        autoFind = findServer;
    }

    @Override
    public void run() {
        MulticastDiscovery multicastDiscovery = new MulticastDiscovery(lock);

        if (autoFind) {
            Thread thread = new Thread(multicastDiscovery);
            thread.start();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    // Empty
                }
            }
            serverIp = multicastDiscovery.getIp();
            serverPort = multicastDiscovery.getServerUSPPort();
        }

        while (true) {
            if(!autoFind) getUserInput();

            Socket socket = new Socket();

            try {
                // Connect to server within 5 s
                socket.connect(new InetSocketAddress(serverIp, serverPort), 5000);

                // Create list about files to archive
                List<FileInfo> files = getFiles();

                // Information about client with files to upload
                ClientData clientInfo = new ClientData(userID, files);
                String clientInfoJson = JsonUtils.toJson(clientInfo);

                // Process of sending data:
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    // Sending clientInfo as a Json
                    System.out.println("\nSending information about files to archive...");
                    writer.write(clientInfoJson + "\n");
                    writer.flush();

                    // Get feedback (list of tasks) about files needed an update
                    TaskList taskList;
                    String taskListJson = reader.readLine();
                    taskList = JsonUtils.fromJson(taskListJson, TaskList.class);
                    Thread.sleep(1000); // Delay between sending files

                    // Sending files to server
                    if (taskList.outdatedFiles().isEmpty()) {
                        System.out.println("All files were up to date!");
                    } else {
                        int filesSent = sendFiles(socket, taskList);
                        if (filesSent != taskList.outdatedFiles().size()) {
                            System.out.println("There was a problem with sending some files.\n");
                        }
                    }

                    // Get the time of from server of the next update
                    String localDateString = reader.readLine();
                    LocalDateTime nextSync = LocalDateTime.parse(localDateString);

                    // Displaying next synchronization time and sleeping the thread
                    System.out.println("Next synchronization: " + nextSync);
                    Duration diff = Duration.between(LocalDateTime.now(), nextSync);
                    Thread.sleep(diff);
                    if (autoFind) multicastDiscovery.setPaused(true);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                System.out.println("Could not connect to server within 5 seconds.\n");
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ignored
                }
            }
        }
    }

    private int sendFiles(Socket socket, TaskList taskList) throws IOException {
        int filesSend = 0;
        for (FileInfo fileInfo : taskList.outdatedFiles()) {
            File file = new File(directoryPath + "\\" + fileInfo.filePath());

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
                filesSend++;
            }
        }

        return filesSend;
    }

    private List<FileInfo> getFiles() {
        Path basePath = Paths.get(directoryPath);
        FileWorker fileWorker = new FileWorker(basePath, false);
        return fileWorker.walkFolder();
    }

    private void getUserInput() {
        Scanner scanner = new Scanner(System.in);

        if (!autoFind) {
            while (true) {
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
        }
        System.out.print("Enter your ID: ");
        userID = scanner.nextLine();

        System.out.print("Enter your directory to archive: ");
        directoryPath = scanner.nextLine();
    }

}
