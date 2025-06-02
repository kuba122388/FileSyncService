package server;

public class Server {
    public static void startServer(int port, int syncInterval){
        new Thread(new TCPServer(port, syncInterval)).start();
    }
}
