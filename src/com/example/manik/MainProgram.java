package com.example.manik;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by maniksin on 5/30/15.
 */
public class MainProgram {

    public static void main(String[] args) {
        //System.setProperty("java.net.preferIPv4Stack", "true");  // Moved to VM options in Program configuration
        ShareServer s = new ShareServer();
    }
}
