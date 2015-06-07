package com.example.manik;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by maniksin on 5/30/15.
 */
public class ShareServer {
    private ServerSocket mainSock;
    private int serverPort = 32001;   // should be 0 when discovery is enabled
    private JmDNS jmdns;

    private final String JMDNS_SERVICE_TYPE = "_share._tcp.local.";
    private final String JMDNS_SERVICE_NAME = "myShare";
    private final String JMDNS_SERVICE_DESCRIPTION = "My share service";

    public ShareServer() {
        Socket clientSock;
        Thread t;

        try {
            mainSock = new ServerSocket(serverPort);
            InetAddress ip = mainSock.getInetAddress();
            int port = mainSock.getLocalPort();
            ServiceInfo sinfo = ServiceInfo.create(JMDNS_SERVICE_TYPE, JMDNS_SERVICE_NAME,
                    port, JMDNS_SERVICE_DESCRIPTION);

            jmdns = JmDNS.create(ip);
            jmdns.registerService(sinfo);

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
