package client;

import common.json.JsonUtils;
import common.model.Message;

import java.io.IOException;
import java.net.*;

public class MulticastDiscovery implements Runnable {
    private final String multicastAddr = "224.0.0.2";
    private int multicastPort = 5000;
    private InetAddress ip;
    private int serverUSPPort;

    private final Object lock;

    private volatile boolean paused = true;

    MulticastDiscovery(Object lock) {
        this.lock = lock;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getServerUSPPort() {
        return serverUSPPort;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }


    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(multicastPort)) {
            InetAddress group = InetAddress.getByName(multicastAddr);
            socket.joinGroup(group);
            socket.setSoTimeout(5000);

            while (true) {
                while (paused);
                System.out.println("Waiting for server response on multicast...");

                Message discoverMsg = new Message("DISCOVER", multicastPort);
                byte[] msgJson = JsonUtils.toJson(discoverMsg).getBytes();
                DatagramPacket discoverPacket = new DatagramPacket(msgJson, msgJson.length, group, multicastPort);
                socket.send(discoverPacket);

                try {
                    byte[] buf = new byte[256];
                    DatagramPacket responsePacket = new DatagramPacket(buf, buf.length);
                    socket.receive(responsePacket);

                    Message response = getMessageFromPacket(responsePacket, socket);

                    while (!"OFFER".equals(response.type())) {
                        response = getMessageFromPacket(responsePacket, socket);
                    }

                    System.out.println("Received OFFER: " + response);
                    ip = responsePacket.getAddress();
                    serverUSPPort = response.port();
                    paused = true;
                    synchronized (lock) {
                        lock.notify();
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("No answer within 5 seconds.");
                    Thread.sleep(10000);
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Message getMessageFromPacket(DatagramPacket packet, DatagramSocket socket) throws IOException {
        socket.receive(packet);
        String json = new String(packet.getData(), 0, packet.getLength());
        return JsonUtils.fromJson(json, Message.class);
    }

}
