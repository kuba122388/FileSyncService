package server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPServer implements Runnable {
    private final int port;
    private final int syncInterval;
    private final BlockingQueue<Socket> clientQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isBusy = new AtomicBoolean(false);

    public TCPServer(int port, int syncInterval) {
        this.port = port;
        this.syncInterval = syncInterval;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            String archiveName = "archive";
            Path archivePath = createUSPDir(archiveName);

            System.out.println("Server TCP started! Waiting for connections...");

            Thread queueMonitor = getQueueThread(archivePath);
            queueMonitor.start();

            while (true) {
                Socket clientSocket = serverSocket.accept();

                if (isBusy.get()) {
                    try {
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                        writer.write("BUSY\n");
                        writer.flush();
                    } catch (IOException e) {
                        System.err.println("Error sending BUSY signal.");
                    }

                    clientQueue.offer(clientSocket);
                    System.out.println("Server busy. Client added to queue: " + clientSocket.getRemoteSocketAddress());
                } else {
                    try {
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                        writer.write("READY\n");
                        writer.flush();
                    } catch (IOException e) {
                        System.err.println("Error sending READY signal.");
                    }
                    isBusy.set(true);
                    handleClient(clientSocket, archivePath);
                }
            }

        } catch (IOException e) {
            System.out.println("Encountered problem when opening server socket on port: " + port);
            System.err.println(e.getMessage());
        }
    }

    private Thread getQueueThread(Path archivePath) {
        Thread queueMonitor = new Thread(() -> {
            while (true) {
                try {
                    if (!isBusy.get() && !clientQueue.isEmpty()) {
                        Socket nextClient = clientQueue.poll();
                        if (!nextClient.isClosed()) {
                            try {
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(nextClient.getOutputStream()));
                                writer.write("READY\n");
                                writer.flush();
                                isBusy.set(true);
                                handleClient(nextClient, archivePath);
                            } catch (IOException e) {
                                System.err.println("Error sending READY to queued client.");
                                try {
                                    nextClient.close();
                                } catch (IOException ignored) {}
                            }
                        }
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        queueMonitor.setDaemon(true);
        return queueMonitor;
    }

    private Path createUSPDir(String dirName) throws IOException {
        Path path = Paths.get(dirName).toAbsolutePath();
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                throw new IOException("Couldn't create USP directory: " + path);
            }
        }
        return path;
    }

    private void handleClient(Socket clientSocket, Path archivePath) {
        ClientHandler handler = new ClientHandler(clientSocket, syncInterval, archivePath, () -> isBusy.set(false));
        Thread thread = new Thread(handler);
        thread.start();
    }
}
