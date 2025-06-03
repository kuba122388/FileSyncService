package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TCPServer implements Runnable {
    private final int port;
    private final int syncInterval;

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
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("! New connection established: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort() + '\n');

                ClientHandler clientHandler = new ClientHandler(clientSocket, syncInterval, archivePath);
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();

                try {
                    clientThread.join();
                } catch (InterruptedException e) {
                    System.out.println("Client unexpectedly closed connection.");
                }

                System.out.println("Client served, waiting for the next one...");
            }
        } catch (IOException e) {
            System.out.println("Encountered problem when opening server socket on port: " + port);
            System.err.println(e.getMessage());
        }
    }

    private Path createUSPDir(String dirName) throws IOException {
        Path path = Paths.get(dirName).toAbsolutePath();
        if(!Files.exists(path))
            try{
                Files.createDirectory(path);
            } catch (IOException e){
                throw new IOException("Couldn't create USP directory: " + path);
            }
        return path;
    }

}
