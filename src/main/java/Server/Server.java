package Server;

import Shared.User;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Server class that handles multiple client connections
 * Supports chat messaging and file upload/download operations
 */
public class Server {
    private static final int PORT = 12345;
    private static final String SERVER_FILES_DIR = "resources/Server.Files/";
    private static final String CLIENT_FILES_DIR = "resources/Client/";

    // Predefined users for authentication
    private static final User[] users = {
            new User("user1", "1234"),
            new User("user2", "1234"),
            new User("user3", "1234"),
            new User("user4", "1234"),
            new User("user5", "1234"),
    };

    // List of currently connected clients
    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // Initialize server directories
        initializeDirectories();

        // Create ServerSocket listening on port 12345
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("===== CS Music Room Server Started =====");
        System.out.println("Server listening on port " + PORT);
        System.out.println("Waiting for client connections...");

        try {
            // Accept incoming client connections in a loop
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Create a new ClientHandler object
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients);

                // Add it to the 'clients' list
                synchronized (clients) {
                    clients.add(clientHandler);
                }

                // Start a new thread to handle communication
                Thread clientThread = new Thread(clientHandler);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            serverSocket.close();
        }
    }

    /**
     * Initialize required directories for server and client files
     */
    private static void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(SERVER_FILES_DIR));
            Files.createDirectories(Paths.get(CLIENT_FILES_DIR));

            // Create user directories
            for (User user : users) {
                Files.createDirectories(Paths.get(CLIENT_FILES_DIR + user.getUsername()));
            }

            System.out.println("Server directories initialized");
        } catch (IOException e) {
            System.err.println("Failed to create directories: " + e.getMessage());
        }
    }

    /**
     * Authenticate user credentials
     */
    public static boolean authenticate(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get list of files in server directory
     */
    public static String[] getServerFileList() {
        try {
            File serverDir = new File(SERVER_FILES_DIR);
            if (!serverDir.exists()) {
                return new String[0];
            }

            File[] files = serverDir.listFiles((dir, name) -> !name.startsWith("."));
            if (files == null) {
                return new String[0];
            }

            String[] fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = files[i].getName();
            }
            return fileNames;
        } catch (Exception e) {
            System.err.println("Error getting server file list: " + e.getMessage());
            return new String[0];
        }
    }

    /**
     * Remove client from the clients list
     */
    public static void removeClient(ClientHandler client) {
        synchronized (clients) {
            clients.remove(client);
            System.out.println("Client removed. Total clients: " + clients.size());
        }
    }

    /**
     * Broadcast message to all connected clients except sender
     */
    public static void broadcastMessage(String message, ClientHandler sender) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender && client.isAuthenticated()) {
                    client.sendMessage(message);
                }
            }
        }
    }

    /**
     * Broadcast user joined/left notifications
     */
    public static void broadcastUserStatus(String username, boolean joined) {
        String message = joined ? username + " joined the chat" : username + " left the chat";
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client.isAuthenticated() && !client.getUsername().equals(username)) {
                    client.sendMessage("[SERVER] " + message);
                }
            }
        }
    }
}