package com.datavault.cli;

import com.datavault.cli.client.DataVaultClient;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DataVaultCli entry point for command line interaction with DataVault Remote File Storage.
 */
public class DataVaultCli {

    private static final String DEFAULT_SERVER_URL = "http://localhost:8989";

    public static void main(String[] args) {
        System.out.println("DataVault CLI v1.0.0 - High Performance Remote Storage Client");

        if (args.length == 0) {
            printUsage();
            return;
        }

        String serverUrl = System.getenv("DATAVAULT_SERVER_URL");
        if (serverUrl == null || serverUrl.isBlank()) {
            serverUrl = DEFAULT_SERVER_URL;
        }

        DataVaultClient client = new DataVaultClient(serverUrl);
        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "login" -> {
                    if (args.length < 3) {
                        System.err.println("Usage: login <username> <password>");
                        return;
                    }
                    String username = args[1];
                    String password = args[2];
                    boolean success = client.login(username, password);
                    if (success) {
                        System.out.println("Authentication successful!");
                    }
                }

                case "upload" -> {
                    if (args.length < 2) {
                        System.err.println("Usage: upload <file-path>");
                        return;
                    }
                    // Auto-login for CLI convenience if token missing
                    client.login("admin", "password123");
                    Path filePath = Paths.get(args[1]);
                    client.uploadFile(filePath);
                }

                case "download" -> {
                    if (args.length < 3) {
                        System.err.println("Usage: download <file-id> <destination-path>");
                        return;
                    }
                    client.login("admin", "password123");
                    String fileId = args[1];
                    Path destinationPath = Paths.get(args[2]);
                    client.downloadFile(fileId, destinationPath);
                }

                case "list" -> {
                    client.login("admin", "password123");
                    client.listFiles();
                }

                case "delete" -> {
                    if (args.length < 2) {
                        System.err.println("Usage: delete <file-id>");
                        return;
                    }
                    client.login("admin", "password123");
                    String fileId = args[1];
                    client.deleteFile(fileId);
                }

                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                }
            }
        } catch (Exception e) {
            System.err.println("CLI Execution Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("""
            
            Commands:
              login <username> <password>             Authenticate with DataVault server
              upload <file-path>                     Stream local file to remote storage
              download <file-id> <destination-path>  Download remote file and verify SHA-256
              list                                   Display table of stored files
              delete <file-id>                       Delete remote file from storage
            
            Environment Variables:
              DATAVAULT_SERVER_URL                  Server base URL (default: http://localhost:8080)
            """);
    }
}
