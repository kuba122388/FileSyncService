import client.Client;
import server.MulticastResponder;
import server.Server;

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
                try {
                    System.out.print("Enter TCP port for server: ");
                    int port = Integer.parseInt(scanner.nextLine());

                    System.out.print("Enter synchronization frequency (in minutes): ");
                    int syncInterval = Integer.parseInt(scanner.nextLine());

                    Server.startServer(port, syncInterval);
                    new Thread(new MulticastResponder(port)).start();
                    break;

                } catch (NumberFormatException e) {
                    System.out.println("Invalid number.\n");
                }
            } else if (choice == 2) {
                System.out.println("How would you like to connect to USP server?\n[1] Automatically\n[2] Manually");

                int option = Integer.parseInt(scanner.nextLine());
                if (option == 1) {
                    new Thread(new Client(true)).start();
                    break;
                } else if (option == 2) {
                    new Thread(new Client(true)).start();
                    break;
                }
            } else if (choice == 3) {
                System.out.println("Exiting...");
                break;
            } else {
                System.out.println("Option does not exist.\n");
            }
        }
    }
}
