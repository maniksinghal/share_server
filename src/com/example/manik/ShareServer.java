package com.example.manik;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by maniksin on 5/30/15.
 */
public class ShareServer {
    private ServerSocket mainSock;
    private int serverPort = 32001;   // should be 0 when discovery is enabled

    public ShareServer() {
        Socket clientSock;
        Thread t;

        try {
            mainSock = new ServerSocket(serverPort);

            System.out.println("Reading on socket");
            while (true) {
                clientSock = mainSock.accept();
                System.out.println("Client request...");
                t = new Thread(new WorkerThread(clientSock));
                t.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
