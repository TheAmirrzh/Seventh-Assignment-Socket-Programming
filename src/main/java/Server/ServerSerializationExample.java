package Server;

import Shared.Message;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;

/**
 * Server-side example demonstrating how to handle the three different
 * communication methods discussed in theoretical questions
 */
public class ServerSerializationExample {

    private static final int PORT = 12346; // Different port for demo

    public static void main(String[] args) {
        System.out.println("=== Server Communication Methods Demo ===\n");

        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Demo server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Handle client in a new thread
                Thread clientHandler = new Thread(() -> handleClient(clientSocket));
                clientHandler.start();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Handle a client connection demonstrating all three methods
     */
    private static void handleClient(Socket clientSocket) {
        try {
            // Set up streams for different methods
            BufferedReader textInput = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            ObjectInputStream objectInput = new ObjectInputStream(clientSocket.getInputStream());

            PrintWriter textOutput = new PrintWriter(clientSocket.getOutputStream(), true);
            ObjectOutputStream objectOutput = new ObjectOutputStream(clientSocket.getOutputStream());

            System.out.println("Waiting for messages from client...");

            // This is a simplified example - in reality, you'd need to know
            // which method the client is using

            // Try to read different types of messages
            while (!clientSocket.isClosed()) {
                try {
                    // Method 1: Try reading as plain string
                    if (textInput.ready()) {
                        String message = textInput.readLine();
                        if (message != null) {
                            handleStringMessage(message, textOutput);
                        }
                    }

                    // Method 2: Try reading as serialized object
                    if (objectInput.available() > 0) {
                        Object obj = objectInput.readObject();
                        if (obj instanceof Message) {
                            handleSerializedMessage((Message) obj, objectOutput);
                        }
                    }

                    Thread.sleep(100); // Small delay to prevent busy waiting

                } catch (EOFException e) {
                    // Client disconnected
                    break;
                } catch (Exception e) {
                    System.err.println("Error handling client message: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    /**
     * Handle Method 1: Plain String Messages
     */
    private static void handleStringMessage(String message, PrintWriter output) {
        System.out.println("Received string message: " + message);

        // Parse different message types
        if (message.startsWith("LOGIN|")) {
            handleStringLogin(message, output);
        } else if (message.startsWith("CHAT|")) {
            handleStringChat(message, output);
        } else if (message.startsWith("{") && message.endsWith("}")) {
            // Might be JSON
            handleJsonMessage(message, output);
        } else {
            System.out.println("Unknown string message format: " + message);
            output.println("ERROR|Unknown message format");
        }
    }

    /**
     * Handle string-based login
     */
    private static void handleStringLogin(String message, PrintWriter output) {
        try {
            String[] parts = message.split("\\|");
            if (parts.length >= 3) {
                String username = parts[1];
                String password = parts[2];

                System.out.println("String login attempt - User: " + username);

                // Simulate authentication
                boolean authenticated = authenticateUser(username, password);

                if (authenticated) {
                    output.println("LOGIN_SUCCESS|Welcome " + username);
                    System.out.println("String login successful for: " + username);
                } else {
                    output.println("LOGIN_FAILED|Invalid credentials");
                    System.out.println("String login failed for: " + username);
                }
            } else {
                output.println("LOGIN_FAILED|Invalid message format");
            }
        } catch (Exception e) {
            System.err.println("Error handling string login: " + e.getMessage());
            output.println("LOGIN_FAILED|Server error");
        }
    }

    /**
     * Handle string-based chat message
     */
    private static void handleStringChat(String message, PrintWriter output) {
        try {
            String[] parts = message.split("\\|", 3);
            if (parts.length >= 3) {
                String sender = parts[1];
                String content = parts[2];

                System.out.println("Chat message from " + sender + ": " + content);

                // Echo back (in real implementation, would broadcast to all clients)
                output.println("CHAT_ECHO|" + sender + "|" + content);
            }
        } catch (Exception e) {
            System.err.println("Error handling string chat: " + e.getMessage());
        }
    }

    /**
     * Handle Method 2: Serialized Message Objects
     */
    private static void handleSerializedMessage(Message message, ObjectOutputStream output) {
        System.out.println("Received serialized message: " + message);

        try {
            switch (message.type) {
                case Message.LOGIN_REQUEST:
                    handleSerializedLogin(message, output);
                    break;

                case Message.CHAT_MESSAGE:
                    handleSerializedChat(message, output);
                    break;

                default:
                    System.out.println("Unknown serialized message type: " + message.type);
                    Message errorResponse = new Message(Message.LOGIN_RESPONSE);
                    errorResponse.success = false;
                    errorResponse.content = "Unknown message type";
                    output.writeObject(errorResponse);
            }
        } catch (IOException e) {
            System.err.println("Error handling serialized message: " + e.getMessage());
        }
    }

    /**
     * Handle serialized login
     */
    private static void handleSerializedLogin(Message message, ObjectOutputStream output)
            throws IOException {
        String username = message.sender;
        String password = message.content;

        System.out.println("Serialized login attempt - User: " + username);

        boolean authenticated = authenticateUser(username, password);
        Message response = Message.loginResponse(authenticated,
                authenticated ? "Welcome " + username : "Invalid credentials");

        output.writeObject(response);

        System.out.println("Serialized login " +
                (authenticated ? "successful" : "failed") + " for: " + username);
    }

    /**
     * Handle serialized chat message
     */
    private static void handleSerializedChat(Message message, ObjectOutputStream output)
            throws IOException {
        System.out.println("Chat message from " + message.sender + ": " + message.content);

        // Echo back (in real implementation, would broadcast to all clients)
        Message echo = Message.chatMessage("SERVER",
                "Echo: " + message.sender + " said: " + message.content);
        output.writeObject(echo);
    }

    /**
     * Handle Method 3: JSON Messages
     */
    private static void handleJsonMessage(String jsonMessage, PrintWriter output) {
        System.out.println("Received JSON message: " + jsonMessage);

        try {
            Gson gson = new Gson();
            Message message = gson.fromJson(jsonMessage, Message.class);

            switch (message.type) {
                case Message.LOGIN_REQUEST:
                    handleJsonLogin(message, output, gson);
                    break;

                case Message.CHAT_MESSAGE:
                    handleJsonChat(message, output, gson);
                    break;

                default:
                    System.out.println("Unknown JSON message type: " + message.type);
                    Message errorResponse = Message.loginResponse(false, "Unknown message type");
                    String errorJson = gson.toJson(errorResponse);
                    output.println(errorJson);
            }
        } catch (Exception e) {
            System.err.println("Error parsing JSON message: " + e.getMessage());
            output.println("{\"type\":1,\"success\":false,\"content\":\"Invalid JSON format\"}");
        }
    }

    /**
     * Handle JSON login
     */
    private static void handleJsonLogin(Message message, PrintWriter output, Gson gson) {
        String username = message.sender;
        String password = message.content;

        System.out.println("JSON login attempt - User: " + username);

        boolean authenticated = authenticateUser(username, password);
        Message response = Message.loginResponse(authenticated,
                authenticated ? "Welcome " + username : "Invalid credentials");

        String jsonResponse = gson.toJson(response);
        output.println(jsonResponse);

        System.out.println("JSON login " +
                (authenticated ? "successful" : "failed") + " for: " + username);
    }

    /**
     * Handle JSON chat message
     */
    private static void handleJsonChat(Message message, PrintWriter output, Gson gson) {
        System.out.println("Chat message from " + message.sender + ": " + message.content);

        // Echo back (in real implementation, would broadcast to all clients)
        Message echo = Message.chatMessage("SERVER",
                "Echo: " + message.sender + " said: " + message.content);
        String jsonResponse = gson.toJson(echo);
        output.println(jsonResponse);
    }

    /**
     * Simple authentication method
     */
    private static boolean authenticateUser(String username, String password) {
        // Predefined users for demo
        return ("user1".equals(username) && "1234".equals(password)) ||
                ("user2".equals(username) && "1234".equals(password)) ||
                ("user3".equals(username) && "1234".equals(password));
    }

    /**
     * Demonstrate parsing challenges with different methods
     */
    public static void demonstrateParsingChallenges() {
        System.out.println("=== Parsing Challenges Demo ===\n");

        // Method 1: String parsing issues
        System.out.println("1. String Method - Delimiter Conflicts:");
        String problematicMessage = "LOGIN|user|with|pipes|pass|word";
        String[] parts = problematicMessage.split("\\|");
        System.out.println("Message: " + problematicMessage);
        System.out.println("Split into " + parts.length + " parts: ");
        for (int i = 0; i < parts.length; i++) {
            System.out.println("  [" + i + "]: " + parts[i]);
        }
        System.out.println("Expected 3 parts, got " + parts.length + " - parsing fails!\n");

        // Method 2: Serialization version issues
        System.out.println("2. Serialization Method - Version Compatibility:");
        System.out.println("- If Message class changes (add/remove fields), old clients break");
        System.out.println("- Requires same Java version on client and server");
        System.out.println("- Cannot communicate with non-Java clients\n");

        // Method 3: JSON flexibility
        System.out.println("3. JSON Method - Flexibility:");
        String jsonWithExtra = """
            {
                "type": 0,
                "sender": "user1",
                "content": "pass123",
                "extraField": "ignored",
                "clientVersion": "2.0"
            }
            """;
        System.out.println("JSON with extra fields:");
        System.out.println(jsonWithExtra);

        try {
            Gson gson = new Gson();
            Message parsed = gson.fromJson(jsonWithExtra, Message.class);
            System.out.println("Successfully parsed - Username: " + parsed.sender);
            System.out.println("Extra fields ignored gracefully\n");
        } catch (Exception e) {
            System.err.println("JSON parsing error: " + e.getMessage());
        }
    }
}