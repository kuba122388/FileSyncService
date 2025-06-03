import client.Client;
import server.Server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int choice;

        while (true) {
            System.out.println("Options:\n[1] Server\n[2] Client\n[3] Exit\nStart as: ");

            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Wrong input.\n");
                continue;
            }

            if (choice == 1) {
                // Server
                try {
                    System.out.print("Enter TCP port for server: ");
                    int port = Integer.parseInt(scanner.nextLine());

                    System.out.print("Enter synchronization frequency (in minutes): ");
                    int syncInterval = Integer.parseInt(scanner.nextLine());

                    Server.startServer(port, syncInterval);
                    break;

                } catch (NumberFormatException e) {
                    System.out.println("Invalid number.\n");
                }

            } else if (choice == 2) {
                // Client
                Client.startClient();
            } else if (choice == 3) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Option does not exist.\n");
            }
        }
    }
}
