package com.example.manik;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by maniksin on 5/30/15.
 */
public class MainProgram {

    public static void main(String[] args) {
        //Path p = Paths.get("/sdcard/hello/how/are/you.jpg");
        //System.out.println("Got path " + p.getFileName().toString() + ".");
        System.setProperty("java.net.preferIPv4Stack", "true");  // else JmDNS picks ipv6 interface as default
        ShareServer s = new ShareServer();
    }
}
