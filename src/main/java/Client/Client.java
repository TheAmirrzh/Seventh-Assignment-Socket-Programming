package Client;

import Shared.Message;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.Scanner;

/**
 * Client class for connecting to the CS Music Room server
 * Supports login, chat, file upload, and file download
 */
public class Client {
    private static Socket socket;
    private static ObjectOutputStream objectOutput;
    private static ObjectInputStream objectInput;
    private static PrintWriter textOutput;
    private static BufferedReader textInput;
    private static String username;
    private static ClientReceiver receiver;

    public static void main(String[] args) throws Exception {
        try {
            socket = new Socket("localhost", 12345);

            // Initialize streams
            objectOutput = new ObjectOutputStream(socket.getOutputStream());
            objectInput = new ObjectInputStream(socket.getInputStream());
            textOutput = new PrintWriter(socket.getOutputStream(), true);
            textInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Scanner scanner = new Scanner(System.in);

            // --- LOGIN PHASE ---
            System.out.println("===== Welcome to CS Music Room =====");

            boolean loggedIn = false;
            while (!loggedIn) {
                System.out.print("Username: ");
                String inputUsername = scanner.nextLine();
                System.out.print("Password: ");
                String password = scanner.nextLine();

                sendLoginRequest(inputUsername, password);

                // Receive and check the server's login response
                try {
                    Object response = objectInput.readObject();
                    if (response instanceof Message) {
                        Message loginResponse = (Message) response;
                        if (loginResponse.type == Message.LOGIN_RESPONSE) {
                            if (loginResponse.success) {
                                username = inputUsername;
                                loggedIn = true;
                                System.out.println("✓ " + loginResponse.content);

                                // Create user directory if it doesn't exist
                                createUserDirectory();

                            } else {
                                System.out.println("✗ " + loginResponse.content);
                                System.out.println("Please try again.\n");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error receiving login response: " + e.getMessage());
                }
            }

            // --- ACTION MENU LOOP ---
            while (true) {
                printMenu();
                System.out.print("Enter choice: ");
                String choice = scanner.nextLine();

                switch (choice) {
                    case "1" -> enterChat(scanner);
                    case "2" -> uploadFile(scanner);
                    case "3" -> requestDownload(scanner);
                    case "0" -> {
                        System.out.println("Exiting...");
                        // Send disconnect message
                        Message disconnect = new Message(Message.DISCONNECT);
                        objectOutput.writeObject(disconnect);
                        return;
                    }
                    default -> System.out.println("Invalid choice.");
                }
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private static void printMenu() {
        System.out.println("\n--- Main Menu ---");
        System.out.println("1. Enter chat box");
        System.out.println("2. Upload a file");
        System.out.println("3. Download a file");
        System.out.println("0. Exit");
    }

    private static void sendLoginRequest(String username, String password) {
        try {
            Message loginMsg = Message.loginRequest(username, password);
            objectOutput.writeObject(loginMsg);
        } catch (IOException e) {
            System.err.println("Error sending login request: " + e.getMessage());
        }
    }

    private static void enterChat(Scanner scanner) throws IOException {
        System.out.println("You have entered the chat room. Type '/exit' to leave.");
        System.out.println("----------------------------------------");

        // Create and start ClientReceiver thread to continuously get new messages from server
        receiver = new ClientReceiver(textInput);
        Thread receiverThread = new Thread(receiver);
        receiverThread.setDaemon(true); // Dies when main thread dies
        receiverThread.start();

        String messageString = "";
        while (!messageString.equalsIgnoreCase("/exit")) {
            messageString = scanner.nextLine();

            if (!messageString.equalsIgnoreCase("/exit")) {
                sendChatMessage(messageString);
            }
        }

        // Stop the receiver when exiting chat
        if (receiver != null) {
            receiver.stop();
        }

        System.out.println("You left the chat room.");
    }

    private static void sendChatMessage(String messageToSend) throws IOException {
        try {
            Message chatMsg = Message.chatMessage(username, messageToSend);
            objectOutput.writeObject(chatMsg);
        } catch (IOException e) {
            System.err.println("Error sending chat message: " + e.getMessage());
            throw e;
        }
    }

    private static void uploadFile(Scanner scanner) throws IOException {
        // List all files in the resources/Client/<username> folder
        String userDir = "resources/Client/" + username + "/";
        File directory = new File(userDir);

        if (!directory.exists()) {
            System.out.println("User directory not found: " + userDir);
            return;
        }

        File[] files = directory.listFiles((dir, name) -> !name.startsWith("."));
        if (files == null || files.length == 0) {
            System.out.println("No files to upload in " + userDir);
            return;
        }

        // Show available files
        System.out.println("Select a file to upload:");
        for (int i = 0; i < files.length; i++) {
            System.out.println((i + 1) + ". " + files[i].getName() +
                    " (" + files[i].length() + " bytes)");
        }

        System.out.print("Enter file number: ");
        int choice;
        try {
            choice = Integer.parseInt(scanner.nextLine()) - 1;
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }

        if (choice < 0 || choice >= files.length) {
            System.out.println("Invalid choice.");
            return;
        }

        File selectedFile = files[choice];

        try {
            // Notify the server that a file upload is starting
            Message uploadRequest = Message.fileUploadRequest(selectedFile.getName(), selectedFile.length());
            objectOutput.writeObject(uploadRequest);

            // Read the file into a byte array and send it over the socket
            byte[] fileData = Files.readAllBytes(selectedFile.toPath());
            DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream());
            dataOutput.write(fileData);
            dataOutput.flush();

            // Wait for server response
            Object response = objectInput.readObject();
            if (response instanceof Message) {
                Message uploadResponse = (Message) response;
                if (uploadResponse.success) {
                    System.out.println("✓ File uploaded successfully: " + selectedFile.getName());
                } else {
                    System.out.println("✗ Upload failed: " + uploadResponse.content);
                }
            }

        } catch (Exception e) {
            System.err.println("Error uploading file: " + e.getMessage());
        }
    }

    private static void requestDownload(Scanner scanner) throws IOException {
        try {
            // Send a request to the server to retrieve the list of available files
            Message fileListRequest = Message.fileListRequest();
            objectOutput.writeObject(fileListRequest);

            // Receive file list response
            Object response = objectInput.readObject();
            if (!(response instanceof Message)) {
                System.out.println("Invalid response from server");
                return;
            }

            Message fileListResponse = (Message) response;
            if (fileListResponse.type != Message.FILE_LIST_RESPONSE) {
                System.out.println("Unexpected response type");
                return;
            }

            String[] files = fileListResponse.fileList;
            if (files == null || files.length == 0) {
                System.out.println("No files available for download.");
                return;
            }

            // Display the file names and prompt the user to select one
            System.out.println("Available files for download:");
            for (int i = 0; i < files.length; i++) {
                System.out.println((i + 1) + ". " + files[i]);
            }

            System.out.print("Enter file number to download: ");
            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine()) - 1;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input.");
                return;
            }

            if (choice < 0 || choice >= files.length) {
                System.out.println("Invalid choice.");
                return;
            }

            String selectedFile = files[choice];

            // Send download request
            Message downloadRequest = Message.fileDownloadRequest(selectedFile);
            objectOutput.writeObject(downloadRequest);

            // Receive file data
            Object downloadResponse = objectInput.readObject();
            if (downloadResponse instanceof Message) {
                Message fileMsg = (Message) downloadResponse;

                if (fileMsg.type == Message.LOGIN_RESPONSE && !fileMsg.success) {
                    System.out.println("✗ Download failed: " + fileMsg.content);
                    return;
                }

                if (fileMsg.type == Message.FILE_DOWNLOAD_DATA) {
                    // Read file data
                    byte[] fileData = new byte[(int) fileMsg.fileSize];
                    DataInputStream dataInput = new DataInputStream(socket.getInputStream());
                    dataInput.readFully(fileData);

                    // Save file to user's directory
                    String userDir = "resources/Client/" + username + "/";
                    String downloadPath = userDir + fileMsg.fileName;
                    Files.write(Paths.get(downloadPath), fileData);

                    System.out.println("✓ File downloaded successfully: " + downloadPath +
                            " (" + fileData.length + " bytes)");
                }
            }

        } catch (Exception e) {
            System.err.println("Error downloading file: " + e.getMessage());
        }
    }

    /**
     * Create user directory if it doesn't exist
     */
    private static void createUserDirectory() {
        try {
            String userDir = "resources/Client/" + username + "/";
            Files.createDirectories(Paths.get(userDir));
        } catch (IOException e) {
            System.err.println("Error creating user directory: " + e.getMessage());
        }
    }

    /**
     * Clean up resources when exiting
     */
    private static void cleanup() {
        try {
            if (receiver != null) {
                receiver.stop();
            }
            if (objectInput != null) objectInput.close();
            if (objectOutput != null) objectOutput.close();
            if (textInput != null) textInput.close();
            if (textOutput != null) textOutput.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}