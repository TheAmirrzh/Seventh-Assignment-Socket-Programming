package Client;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * ClientReceiver handles incoming messages from the server
 * Runs in a separate thread to continuously listen for new messages
 */
public class ClientReceiver implements Runnable {
    private BufferedReader input;
    private volatile boolean running = true;

    public ClientReceiver(BufferedReader input) {
        this.input = input;
    }

    @Override
    public void run() {
        try {
            while (running && input != null) {
                try {
                    // Listen for new messages from server
                    String message = input.readLine();

                    if (message == null) {
                        // Server closed connection
                        break;
                    }

                    // Print the new message in CLI
                    System.out.println(message);

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error receiving message: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("ClientReceiver error: " + e.getMessage());
            }
        }
    }

    /**
     * Stop the receiver thread gracefully
     */
    public void stop() {
        running = false;
    }

    /**
     * Check if the receiver is still running
     */
    public boolean isRunning() {
        return running;
    }
}