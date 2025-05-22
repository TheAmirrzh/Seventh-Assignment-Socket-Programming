package Client;

import Shared.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;
import java.util.Optional;

/**
 * GUI Chat Client using JavaFX (Bonus Implementation)
 * Provides a graphical interface for the chat application
 */
public class ChatClientFX extends Application {
    private Socket socket;
    private ObjectOutputStream objectOutput;
    private ObjectInputStream objectInput;
    private PrintWriter textOutput;
    private BufferedReader textInput;
    private String username;

    // GUI Components
    private TextArea chatArea;
    private TextField messageField;
    private ListView<String> userList;
    private Button sendButton;
    private Button connectButton;
    private Button disconnectButton;

    // Connection status
    private boolean connected = false;
    private Thread messageReceiver;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("CS Music Room - Chat Client");

        // Create main layout
        BorderPane root = new BorderPane();

        // Top: Connection controls
        HBox topBox = createConnectionControls();
        root.setTop(topBox);

        // Center: Chat area and user list
        HBox centerBox = createChatArea();
        root.setCenter(centerBox);

        // Bottom: Message input
        HBox bottomBox = createMessageInput();
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Handle close request
        primaryStage.setOnCloseRequest(e -> {
            disconnect();
            Platform.exit();
        });
    }

    private HBox createConnectionControls() {
        HBox box = new HBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #f0f0f0;");

        connectButton = new Button("Connect");
        disconnectButton = new Button("Disconnect");
        Label statusLabel = new Label("Disconnected");

        connectButton.setOnAction(e -> showLoginDialog());
        disconnectButton.setOnAction(e -> disconnect());
        disconnectButton.setDisable(true);

        box.getChildren().addAll(connectButton, disconnectButton,
                new Separator(), statusLabel);

        return box;
    }

    private HBox createChatArea() {
        HBox box = new HBox(10);
        box.setPadding(new Insets(10));

        // Chat messages area
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefWidth(600);

        // Users list
        userList = new ListView<>();
        userList.setPrefWidth(180);
        userList.getItems().add("Connected Users:");

        VBox chatBox = new VBox(5);
        chatBox.getChildren().addAll(new Label("Chat Messages"), chatArea);

        VBox usersBox = new VBox(5);
        usersBox.getChildren().addAll(new Label("Online Users"), userList);

        box.getChildren().addAll(chatBox, usersBox);
        HBox.setHgrow(chatBox, Priority.ALWAYS);

        return box;
    }

    private HBox createMessageInput() {
        HBox box = new HBox(10);
        box.setPadding(new Insets(10));

        messageField = new TextField();
        messageField.setPromptText("Type your message here...");
        messageField.setDisable(true);

        sendButton = new Button("Send");
        sendButton.setDisable(true);

        // Send message on Enter key or button click
        messageField.setOnAction(e -> sendMessage());
        sendButton.setOnAction(e -> sendMessage());

        box.getChildren().addAll(messageField, sendButton);
        HBox.setHgrow(messageField, Priority.ALWAYS);

        return box;
    }

    private void showLoginDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your credentials");

        // Create login form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);

        // Focus on username field
        Platform.runLater(() -> usernameField.requestFocus());

        // Convert result when login button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new String[]{usernameField.getText(), passwordField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(credentials -> {
            if (!credentials[0].trim().isEmpty() && !credentials[1].trim().isEmpty()) {
                connect(credentials[0], credentials[1]);
            }
        });
    }

    private void connect(String username, String password) {
        try {
            socket = new Socket("localhost", 12345);

            // Initialize streams
            objectOutput = new ObjectOutputStream(socket.getOutputStream());
            objectInput = new ObjectInputStream(socket.getInputStream());
            textOutput = new PrintWriter(socket.getOutputStream(), true);
            textInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send login request
            Message loginMsg = Message.loginRequest(username, password);
            objectOutput.writeObject(loginMsg);

            // Wait for response
            Object response = objectInput.readObject();
            if (response instanceof Message) {
                Message loginResponse = (Message) response;
                if (loginResponse.type == Message.LOGIN_RESPONSE && loginResponse.success) {
                    this.username = username;
                    connected = true;

                    // Update GUI
                    Platform.runLater(() -> {
                        connectButton.setDisable(true);
                        disconnectButton.setDisable(false);
                        messageField.setDisable(false);
                        sendButton.setDisable(false);
                        chatArea.appendText("✓ Connected as " + username + "\n");
                        userList.getItems().add(username + " (You)");
                    });

                    // Start message receiver thread
                    startMessageReceiver();

                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Login Failed");
                        alert.setHeaderText("Authentication Error");
                        alert.setContentText(loginResponse.content);
                        alert.showAndWait();
                    });
                    socket.close();
                }
            }

        } catch (Exception e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Connection Error");
                alert.setHeaderText("Failed to connect to server");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void startMessageReceiver() {
        messageReceiver = new Thread(() -> {
            try {
                while (connected && !socket.isClosed()) {
                    String message = textInput.readLine();
                    if (message == null) break;

                    Platform.runLater(() -> {
                        chatArea.appendText(message + "\n");

                        // Auto-scroll to bottom
                        chatArea.setScrollTop(Double.MAX_VALUE);

                        // Update user list if it's a user joined/left message
                        if (message.contains("joined the chat")) {
                            String joinedUser = message.substring(message.indexOf("]") + 2,
                                    message.indexOf(" joined"));
                            if (!userList.getItems().contains(joinedUser)) {
                                userList.getItems().add(joinedUser);
                            }
                        } else if (message.contains("left the chat")) {
                            String leftUser = message.substring(message.indexOf("]") + 2,
                                    message.indexOf(" left"));
                            userList.getItems().remove(leftUser);
                        }
                    });
                }
            } catch (IOException e) {
                if (connected) {
                    Platform.runLater(() -> {
                        chatArea.appendText("Connection lost: " + e.getMessage() + "\n");
                        disconnect();
                    });
                }
            }
        });
        messageReceiver.setDaemon(true);
        messageReceiver.start();
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty() || !connected) return;

        try {
            Message chatMsg = Message.chatMessage(username, message);
            objectOutput.writeObject(chatMsg);

            // Clear message field
            messageField.clear();

            // Show own message in chat area
            chatArea.appendText("[" + username + "]: " + message + "\n");
            chatArea.setScrollTop(Double.MAX_VALUE);

        } catch (IOException e) {
            chatArea.appendText("Error sending message: " + e.getMessage() + "\n");
        }
    }

    private void disconnect() {
        if (connected) {
            try {
                connected = false;

                if (objectOutput != null) {
                    Message disconnect = new Message(Message.DISCONNECT);
                    objectOutput.writeObject(disconnect);
                }

                if (socket != null) socket.close();

            } catch (IOException e) {
                System.err.println("Error disconnecting: " + e.getMessage());
            }
        }

        // Update GUI
        Platform.runLater(() -> {
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            messageField.setDisable(true);
            sendButton.setDisable(true);
            chatArea.appendText("✗ Disconnected from server\n");
            userList.getItems().clear();
            userList.getItems().add("Connected Users:");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}