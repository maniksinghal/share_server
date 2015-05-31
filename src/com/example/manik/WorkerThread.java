package com.example.manik;

import java.io.*;
import java.net.Socket;

/**
 * Created by maniksin on 5/30/15.
 */
public class WorkerThread implements Runnable {
    private Socket sock;

    // Messages exchanged with the peer
    // Keep in sync with Peer's project
    private final int NETWORK_MSG_STRING_ID = 1;
    private final int NETWORK_MSG_ACK_ID = 2;

    public WorkerThread(Socket s) {
        sock = s;
    }

    public void run() {
        // Reflect the packets for now
        int msg_id;
        int len;
        int actual_len;
        byte[] data_bytes;

        while (true) {
            try {
                InputStream in = sock.getInputStream();
                DataInputStream din = new DataInputStream(in);
                msg_id = din.readInt();
                len = din.readInt();
                data_bytes = new byte[len];
                actual_len = din.read(data_bytes);
                System.out.println("Advertised: " + len + ", Received: " + actual_len);
                if (actual_len < 0) {
                    actual_len = 0;
                }

                if (msg_id == NETWORK_MSG_STRING_ID) {
                    OutputStream out = sock.getOutputStream();
                    DataOutputStream dout = new DataOutputStream(out);
                    msg_id = NETWORK_MSG_ACK_ID;
                    dout.writeInt(msg_id);
                    dout.writeInt(actual_len);
                    dout.write(data_bytes);

                }

            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        try {
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }

    }
}
