package server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer implements Runnable{
    private final int port;
    private final int syncInterval;

    public TCPServer(int port, int syncInterval) {
        this.port = port;
        this.syncInterval = syncInterval;
    }

    @Override
    public void run(){
        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Server TCP started! Waiting for connections...");
            while(true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("! New connection established: " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());

                ClientHandler clientHandler = new ClientHandler(clientSocket, syncInterval);
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();

                try {
                    clientThread.join();
                } catch (InterruptedException e) {
                    System.out.println("Wątek został przerwany");
                }

                System.out.println("Client served, waiting for the next one...");
            }
        } catch (IOException e){
            System.out.println("Encountered problem when opening server socker on port: " + port);
        }
    }

}
