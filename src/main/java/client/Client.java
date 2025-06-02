package client;

import common.json.JsonUtils;
import common.model.ClientInfo;
import common.model.FileInfo;
import common.model.TaskList;
import common.utils.FileWorker;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class Client {
    public static void startClient(InetAddress ip, int port) {
        try (Socket socket = new Socket(ip, port)) {
            Scanner scanner = new Scanner(System.in);

            System.out.println("Enter your ID: ");
            String userID = scanner.nextLine();

            System.out.println("Enter your directory to archive: ");
            String directory = scanner.nextLine();

            Path basePath = Paths.get(directory);
            FileWorker fileWorker = new FileWorker(basePath);
            List<FileInfo> files = fileWorker.walkFolder();

            ClientInfo clientInfo = new ClientInfo(userID, files);
            String data = JsonUtils.toJson(clientInfo);

            System.out.println("Sending data to server...");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                writer.write(data + "\n");
                writer.flush();

                TaskList taskList;
                String taskListJson = reader.readLine();
                taskList = JsonUtils.fromJson(taskListJson, TaskList.class);

                for (FileInfo fileInfo : taskList.outdatedFiles()) {
                    File file = new File(fileInfo.filePath());

                    if (!file.exists()) {
                        System.out.println("File does not exist: " + fileInfo.filePath());
                        continue;
                    }

                    DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

                    dataOut.writeUTF(fileInfo.filePath());
                    dataOut.writeLong(file.length());

                    try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file))) {
                        byte[] buffer = new byte[8192];
                        int count;

                        while ((count = fileIn.read(buffer)) > 0) {
                            dataOut.write(buffer, 0, count);
                        }

                        dataOut.flush();
                        System.out.println("File sent: " + fileInfo.filePath());
                    }
                }


            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
