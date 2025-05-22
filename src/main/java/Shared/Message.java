package Shared;

import java.io.Serializable;

/**
 * Message class for structured communication between Client and Server
 * Supports various message types for chat, file operations, and authentication
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // Message type constants
    public static final int LOGIN_REQUEST = 0;
    public static final int LOGIN_RESPONSE = 1;
    public static final int CHAT_MESSAGE = 2;
    public static final int FILE_LIST_REQUEST = 3;
    public static final int FILE_LIST_RESPONSE = 4;
    public static final int FILE_UPLOAD_REQUEST = 5;
    public static final int FILE_UPLOAD_DATA = 6;
    public static final int FILE_DOWNLOAD_REQUEST = 7;
    public static final int FILE_DOWNLOAD_DATA = 8;
    public static final int USER_JOINED = 9;
    public static final int USER_LEFT = 10;
    public static final int DISCONNECT = 11;

    public int type;
    public String sender;
    public String content;
    public String fileName;
    public long fileSize;
    public byte[] fileData;
    public String[] fileList;
    public boolean success;

    public Message() {
    }

    public Message(int type) {
        this.type = type;
    }

    public Message(int type, String content) {
        this.type = type;
        this.content = content;
    }

    public Message(int type, String sender, String content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    // Static factory methods for convenience
    public static Message loginRequest(String username, String password) {
        Message msg = new Message(LOGIN_REQUEST);
        msg.sender = username;
        msg.content = password;
        return msg;
    }

    public static Message loginResponse(boolean success, String message) {
        Message msg = new Message(LOGIN_RESPONSE);
        msg.success = success;
        msg.content = message;
        return msg;
    }

    public static Message chatMessage(String sender, String content) {
        return new Message(CHAT_MESSAGE, sender, content);
    }

    public static Message fileListRequest() {
        return new Message(FILE_LIST_REQUEST);
    }

    public static Message fileListResponse(String[] files) {
        Message msg = new Message(FILE_LIST_RESPONSE);
        msg.fileList = files;
        return msg;
    }

    public static Message fileUploadRequest(String fileName, long fileSize) {
        Message msg = new Message(FILE_UPLOAD_REQUEST);
        msg.fileName = fileName;
        msg.fileSize = fileSize;
        return msg;
    }

    public static Message fileDownloadRequest(String fileName) {
        Message msg = new Message(FILE_DOWNLOAD_REQUEST);
        msg.fileName = fileName;
        return msg;
    }

    public static Message userJoined(String username) {
        return new Message(USER_JOINED, username, username + " joined the chat");
    }

    public static Message userLeft(String username) {
        return new Message(USER_LEFT, username, username + " left the chat");
    }

    @Override
    public String toString() {
        return String.format("Message{type=%d, sender='%s', content='%s', fileName='%s'}",
                type, sender, content, fileName);
    }
}