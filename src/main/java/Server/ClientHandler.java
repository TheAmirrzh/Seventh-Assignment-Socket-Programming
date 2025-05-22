package Server;

import Shared.Message;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.List;

/**
 * ClientHandler manages communication with a single client
 * Handles authentication, chat messages, and file operations
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream objectInput;
    private ObjectOutputStream objectOutput;
    private BufferedReader textInput;
    private PrintWriter textOutput;
    private List<ClientHandler> allClients;
    private String username;
    private boolean isAuthenticated = false;
    private boolean isInChat = false;

    public ClientHandler(Socket socket, List<ClientHandler> allClients) {
        this.socket = socket;
        this.allClients = allClients;

        try {
            // Initialize streams - Object streams for structured messages
            this.objectOutput = new ObjectOutputStream(socket.getOutputStream());
            this.objectInput = new ObjectInputStream(socket.getInputStream());

            // Text streams for simple string communication
            this.textOutput = new PrintWriter(socket.getOutputStream(), true);
            this.textInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            System.err.println("Error initializing client streams: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("ClientHandler started for: " + socket.getInetAddress());

            while (true && !socket.isClosed()) {
                try {
                    // Try to read as a Message object first
                    Object received = objectInput.readObject();

                    if (received instanceof Message) {
                        Message message = (Message) received;
                        processMessage(message);
                    } else if (received instanceof String) {
                        // Handle string messages (for compatibility)
                        String textMessage = (String) received;
                        processTextMessage(textMessage);
                    }

                } catch (EOFException e) {
                    // Client disconnected
                    break;
                } catch (ClassNotFoundException e) {
                    System.err.println("Unknown message type received: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Client communication error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Process structured Message objects
     */
    private void processMessage(Message message) throws IOException {
        switch (message.type) {
            case Message.LOGIN_REQUEST:
                handleLogin(message.sender, message.content);
                break;

            case Message.CHAT_MESSAGE:
                if (isAuthenticated) {
                    handleChatMessage(message);
                }
                break;

            case Message.FILE_LIST_REQUEST:
                if (isAuthenticated) {
                    sendFileList();
                }
                break;

            case Message.FILE_UPLOAD_REQUEST:
                if (isAuthenticated) {
                    handleFileUploadRequest(message);
                }
                break;

            case Message.FILE_DOWNLOAD_REQUEST:
                if (isAuthenticated) {
                    sendFile(message.fileName);
                }
                break;

            case Message.DISCONNECT:
                cleanup();
                break;

            default:
                System.out.println("Unknown message type: " + message.type);
        }
    }

    /**
     * Process simple text messages (for backward compatibility)
     */
    private void processTextMessage(String textMessage) throws IOException {
        if (!isAuthenticated) {
            // Handle login in format "username:password"
            String[] parts = textMessage.split(":", 2);
            if (parts.length == 2) {
                handleLogin(parts[0], parts[1]);
            }
        } else {
            // Handle chat message
            Message chatMsg = Message.chatMessage(username, textMessage);
            handleChatMessage(chatMsg);
        }
    }

    /**
     * Handle chat messages and broadcast them
     */
    private void handleChatMessage(Message message) throws IOException {
        String chatMessage = "[" + message.sender + "]: " + message.content;
        System.out.println("Chat message: " + chatMessage);

        // Broadcast to all other clients
        broadcast(chatMessage);
    }

    /**
     * Handle file upload request
     */
    private void handleFileUploadRequest(Message message) throws IOException {
        try {
            System.out.println("File upload request: " + message.fileName + " (" + message.fileSize + " bytes)");

            // Read file data
            byte[] fileData = new byte[(int) message.fileSize];
            DataInputStream dataInput = new DataInputStream(socket.getInputStream());
            dataInput.readFully(fileData);

            // Save the file
            saveUploadedFile(message.fileName, fileData);

            // Send success response
            Message response = Message.loginResponse(true, "File uploaded successfully");
            objectOutput.writeObject(response);

        } catch (IOException e) {
            System.err.println("Error handling file upload: " + e.getMessage());
            Message response = Message.loginResponse(false, "File upload failed: " + e.getMessage());
            objectOutput.writeObject(response);
        }
    }

    /**
     * Send message to this client
     */
    public void sendMessage(String msg) {
        try {
            textOutput.println(msg);
        } catch (Exception e) {
            System.err.println("Error sending message to client: " + e.getMessage());
        }
    }

    /**
     * Broadcast message to all other clients
     */
    private void broadcast(String msg) throws IOException {
        synchronized (allClients) {
            for (ClientHandler client : allClients) {
                if (client != this && client.isAuthenticated()) {
                    client.sendMessage(msg);
                }
            }
        }
    }

    /**
     * Send list of available files to client
     */
    private void sendFileList() {
        try {
            String[] files = Server.getServerFileList();
            Message response = Message.fileListResponse(files);
            objectOutput.writeObject(response);

        } catch (IOException e) {
            System.err.println("Error sending file list: " + e.getMessage());
        }
    }

    /**
     * Send file to client
     */
    private void sendFile(String fileName) {
        try {
            String filePath = "resources/Server.Files/" + fileName;
            File file = new File(filePath);

            if (!file.exists()) {
                Message response = Message.loginResponse(false, "File not found: " + fileName);
                objectOutput.writeObject(response);
                return;
            }

            // Read file data
            byte[] fileData = Files.readAllBytes(file.toPath());

            // Send file metadata
            Message fileMsg = new Message(Message.FILE_DOWNLOAD_DATA);
            fileMsg.fileName = fileName;
            fileMsg.fileSize = file.length();
            objectOutput.writeObject(fileMsg);

            // Send file data
            DataOutputStream dataOutput = new DataOutputStream(socket.getOutputStream());
            dataOutput.write(fileData);
            dataOutput.flush();

            System.out.println("File sent: " + fileName + " (" + file.length() + " bytes)");

        } catch (IOException e) {
            System.err.println("Error sending file: " + e.getMessage());
            try {
                Message response = Message.loginResponse(false, "Error sending file: " + e.getMessage());
                objectOutput.writeObject(response);
            } catch (IOException ex) {
                System.err.println("Error sending error response: " + ex.getMessage());
            }
        }
    }

    /**
     * Save uploaded file to server directory
     */
    private void saveUploadedFile(String filename, byte[] data) throws IOException {
        String serverFilePath = "resources/Server.Files/" + filename;
        Files.write(Paths.get(serverFilePath), data);
        System.out.println("File saved: " + serverFilePath + " (" + data.length + " bytes)");
    }

    /**
     * Handle user login
     */
    private void handleLogin(String username, String password) throws IOException {
        try {
            boolean authResult = Server.authenticate(username, password);

            if (authResult) {
                this.username = username;
                this.isAuthenticated = true;

                // Send success response
                Message response = Message.loginResponse(true, "Login successful");
                objectOutput.writeObject(response);

                // Notify other clients that user joined
                Server.broadcastUserStatus(username, true);

                System.out.println("User authenticated: " + username);

            } else {
                // Send failure response
                Message response = Message.loginResponse(false, "Invalid credentials");
                objectOutput.writeObject(response);

                System.out.println("Authentication failed for: " + username);
            }

        } catch (IOException e) {
            System.err.println("Error handling login: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Clean up when client disconnects
     */
    private void cleanup() {
        try {
            if (isAuthenticated && username != null) {
                // Notify other clients that user left
                Server.broadcastUserStatus(username, false);
                System.out.println("User disconnected: " + username);
            }

            // Remove from client list
            Server.removeClient(this);

            // Close streams and socket
            if (objectInput != null) objectInput.close();
            if (objectOutput != null) objectOutput.close();
            if (textInput != null) textInput.close();
            if (textOutput != null) textOutput.close();
            if (socket != null && !socket.isClosed()) socket.close();

        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    // Getters
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public String getUsername() {
        return username;
    }

    public boolean isInChat() {
        return isInChat;
    }

    public void setInChat(boolean inChat) {
        this.isInChat = inChat;
    }
}