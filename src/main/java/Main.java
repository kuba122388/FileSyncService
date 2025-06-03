import client.Client;
import server.MulticastResponder;
import server.Server;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Options:\n[1] Server\n[2] Client\n[3] Exit\nStart as: ");

            int choice = readInt(scanner, "Choose an option: ");
            switch (choice) {
                case 1 -> {
                    startServer(scanner);
                    return;
                }
                case 2 -> {
                    startClient(scanner);
                    return;
                }
                case 3 -> {
                    System.out.println("Exiting...");
                    return;
                }
                default -> System.out.println("Option does not exist.\n");
            }
        }
    }

    private static void startServer(Scanner scanner) {
        int port = readInt(scanner, "Enter TCP port for server: ");
        int syncInterval = readInt(scanner, "Enter synchronization frequency (in minutes): ");
        Server.startServer(port, syncInterval);
        new Thread(new MulticastResponder(port)).start();
    }

    private static void startClient(Scanner scanner) {
        System.out.println("Connect to USP server:\n[1] Automatically\n[2] Manually");
        int option = readInt(scanner, "Your choice: ");
        boolean auto = option == 1;
        new Thread(new Client(auto)).start();
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Try again.\n");
            }
        }
    }
}
