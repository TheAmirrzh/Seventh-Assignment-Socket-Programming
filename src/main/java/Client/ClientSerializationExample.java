package Client;

import Shared.Message;
import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;

/**
 * Example demonstrating the three different ways to send login messages
 * as discussed in the theoretical questions
 */
public class ClientSerializationExample {

    /**
     * Demonstrates three different approaches to sending login data
     */
    public static void main(String[] args) {
        System.out.println("=== Socket Communication Methods Demo ===\n");

        // Demo data
        String username = "user1";
        String password = "pass123";

        try {
            // Method 1: Plain String Format
            demonstrateStringMethod(username, password);

            // Method 2: Java Serialization
            demonstrateSerializationMethod(username, password);

            // Method 3: JSON Format
            demonstrateJsonMethod(username, password);

        } catch (Exception e) {
            System.err.println("Demo error: " + e.getMessage());
        }
    }

    /**
     * Method 1: Plain String Format
     * Pros: Simple, lightweight, fast
     * Cons: Security issues, delimiter conflicts, limited structure
     */
    private static void demonstrateStringMethod(String username, String password) {
        System.out.println("1. PLAIN STRING METHOD");
        System.out.println("======================");

        // Create plain string message
        String loginMessage = "LOGIN|" + username + "|" + password;
        System.out.println("Message to send: " + loginMessage);

        // Simulate parsing (what server would do)
        String[] parts = loginMessage.split("\\|");
        if (parts.length == 3 && "LOGIN".equals(parts[0])) {
            System.out.println("Parsed - Username: " + parts[1] + ", Password: " + parts[2]);
        }

        // Demonstrate delimiter conflict problem
        String problematicUsername = "user|admin";
        String problematicMessage = "LOGIN|" + problematicUsername + "|" + password;
        System.out.println("Problematic message: " + problematicMessage);
        String[] problematicParts = problematicMessage.split("\\|");
        System.out.println("Split into " + problematicParts.length + " parts instead of 3!");

        System.out.println("Pros: Simple, fast, lightweight");
        System.out.println("Cons: Delimiter conflicts, no structure validation, security issues\n");
    }

    /**
     * Method 2: Java Serialization
     * Pros: Type safety, complex structures, automatic handling
     * Cons: Java-only, platform dependent
     */
    private static void demonstrateSerializationMethod(String username, String password) {
        System.out.println("2. JAVA SERIALIZATION METHOD");
        System.out.println("=============================");

        try {
            // Create Message object
            Message loginRequest = Message.loginRequest(username, password);
            System.out.println("Message object: " + loginRequest);

            // Serialize to bytes (simulate network transmission)
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
            objectStream.writeObject(loginRequest);

            byte[] serializedData = byteStream.toByteArray();
            System.out.println("Serialized size: " + serializedData.length + " bytes");

            // Deserialize (what server would do)
            ByteArrayInputStream inputStream = new ByteArrayInputStream(serializedData);
            ObjectInputStream objectInput = new ObjectInputStream(inputStream);
            Message receivedMessage = (Message) objectInput.readObject();

            System.out.println("Deserialized - Username: " + receivedMessage.sender +
                    ", Password: " + receivedMessage.content);

            objectStream.close();
            objectInput.close();

        } catch (Exception e) {
            System.err.println("Serialization error: " + e.getMessage());
        }

        System.out.println("Pros: Type safety, complex structures, automatic serialization");
        System.out.println("Cons: Java-only, not cross-platform, larger size\n");
    }

    /**
     * Method 3: JSON Format
     * Pros: Cross-platform, human-readable, web-friendly
     * Cons: Slightly larger, requires parsing library
     */
    private static void demonstrateJsonMethod(String username, String password) {
        System.out.println("3. JSON METHOD");
        System.out.println("==============");

        try {
            // Create Message object
            Message loginRequest = Message.loginRequest(username, password);

            // Convert to JSON using Gson
            Gson gson = new Gson();
            String jsonString = gson.toJson(loginRequest);
            System.out.println("JSON message: " + jsonString);
            System.out.println("JSON size: " + jsonString.getBytes().length + " bytes");

            // Parse JSON (what server would do)
            Message receivedMessage = gson.fromJson(jsonString, Message.class);
            System.out.println("Parsed - Username: " + receivedMessage.sender +
                    ", Password: " + receivedMessage.content);

            // Demonstrate flexibility - can add fields without breaking compatibility
            String extendedJson = """
                {
                    "type": 0,
                    "sender": "user1",
                    "content": "pass123",
                    "clientVersion": "1.0",
                    "loginTime": "2024-05-22T10:30:00"
                }
                """;
            System.out.println("Extended JSON with extra fields:");
            System.out.println(extendedJson);

            Message extendedMessage = gson.fromJson(extendedJson, Message.class);
            System.out.println("Still parseable - Username: " + extendedMessage.sender);

        } catch (Exception e) {
            System.err.println("JSON error: " + e.getMessage());
        }

        System.out.println("Pros: Cross-platform, human-readable, web-friendly, flexible");
        System.out.println("Cons: Slightly larger than binary, requires JSON library\n");
    }

    /**
     * Example of how to use these methods with actual socket connection
     */
    public static void demonstrateSocketCommunication() {
        try {
            Socket socket = new Socket("localhost", 12345);

            String username = "user1";
            String password = "pass123";

            // === Method 1: Plain String ===
            PrintWriter stringOut = new PrintWriter(socket.getOutputStream(), true);
            stringOut.println("LOGIN|" + username + "|" + password);

            // === Method 2: Serialized Object ===
            ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
            Message loginRequest = Message.loginRequest(username, password);
            objectOut.writeObject(loginRequest);

            // === Method 3: JSON ===
            Gson gson = new Gson();
            String json = gson.toJson(loginRequest);
            PrintWriter jsonOut = new PrintWriter(socket.getOutputStream(), true);
            jsonOut.println(json);

            socket.close();

        } catch (Exception e) {
            System.err.println("Socket demo error: " + e.getMessage());
        }
    }
}