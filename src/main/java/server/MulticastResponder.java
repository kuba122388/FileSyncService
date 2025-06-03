package server;

import common.json.JsonUtils;
import common.model.Message;

import java.io.IOException;
import java.net.*;

public class MulticastResponder implements Runnable {
    private final String multicastAddr = "224.0.0.2";
    private final int port = 5000;
    private final int tcpServerPort;

    public MulticastResponder(int port){
        this.tcpServerPort = port;
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(port)) {
            InetAddress group = InetAddress.getByName(multicastAddr);
            socket.joinGroup(group);

            while (true) {
                byte[] buf = new byte[256];
                DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                socket.receive(datagramPacket);

                String json = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
                Message received = JsonUtils.fromJson(json, Message.class);

                if (!received.type().equals("DISCOVER")) continue;

                System.out.println("DISCOVER from: " + datagramPacket.getAddress());

                Message responseMsg = new Message("OFFER", tcpServerPort);
                byte[] msgJson = JsonUtils.toJson(responseMsg).getBytes();

                DatagramPacket responsePacket = new DatagramPacket(msgJson, msgJson.length, group, port);
                socket.send(responsePacket);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
