package com.example.manik;

import java.nio.ByteBuffer;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;

/**
 * Created by maniksin on 5/30/15.
 */
public class WorkerThread implements Runnable {
    private Socket sock;

    // Messages exchanged with the peer
    // Keep in sync with Peer's project
    private final int NETWORK_MSG_STRING_ID = 1;
    private final int NETWORK_MSG_ACK_ID = 2;
    private final int NETWORK_MSG_FILE_PUT_START = 3;
    private final int NETWORK_MSG_FILE_PUT_START_ACK = 4;
    private final int NETWORK_MSG_FILE_DATA = 5;
    private final int NETWORK_MSG_FILE_DATA_ACK = 6;
    private final int NETWORK_MSG_FILE_PUT_END = 7;
    private final int NETWORK_MSG_FILE_PUT_END_ACK = 8;
    private final int NETWORK_MSG_FILE_TRANSFER_CANCEL = 9;

    // File download context
    private FileOutputStream fo = null;

    public WorkerThread(Socket s) {
        sock = s;
    }


    private byte[] handle_file_operations(int msg_id, int payload_len, byte[] payload) {

        byte[] response = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(response);

        if (msg_id == NETWORK_MSG_FILE_PUT_START) {
            if (fo != null) {
                // New file download start while some download is already running
                try {
                    fo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    fo = null;
                    return null;
                }
            }

            String str = new String(payload);
            String target = Paths.get(str).getFileName().toString();
            System.out.println("Request received to download file: " + target);
            File fp = new File(target);
            try {
                fo = new FileOutputStream(fp);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                fo = null;
                return null;
            }

            bb.putInt(NETWORK_MSG_FILE_PUT_START_ACK);
            bb.putInt(0);  // 0 length payload
            return response;

        }

        if (msg_id == NETWORK_MSG_FILE_DATA) {
            if (fo == null) {
                // No download requested...
                return null;
            }

            try {
                fo.write(payload);
            } catch (IOException e) {
                e.printStackTrace();
                fo = null;
                return null;
            }

            //System.out.println("File data write: " + payload_len + " bytes");
            bb.putInt(NETWORK_MSG_FILE_DATA_ACK);
            bb.putInt(0);
            return response;

        }

        if (msg_id == NETWORK_MSG_FILE_PUT_END) {
            if (fo == null) {
                // No download going on
                return null;
            }

            try {
                fo.flush();
                fo.close();
            } catch (IOException e) {
                e.printStackTrace();
                fo = null;
                return null;
            }

            System.out.println("File download complete...");
            bb.putInt(NETWORK_MSG_FILE_PUT_END_ACK);
            bb.putInt(0);
            return response;
        }

        if (msg_id == NETWORK_MSG_FILE_TRANSFER_CANCEL) {
            if (fo != null) {
                System.out.println("Transfer cancelled by the peer");
                fo = null;
            }
        }

        return null;

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
                //System.out.println("Advertised: " + len + ", Received: " + actual_len);
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

                } else {
                    switch (msg_id) {
                        case NETWORK_MSG_FILE_DATA:
                        case NETWORK_MSG_FILE_PUT_START:
                        case NETWORK_MSG_FILE_PUT_END:
                        case NETWORK_MSG_FILE_TRANSFER_CANCEL:
                            byte[] out_msg = handle_file_operations(msg_id, actual_len, data_bytes);
                            if (out_msg != null) {
                                OutputStream out = sock.getOutputStream();
                                out.write(out_msg);
                            }
                            break;
                        default:
                            System.out.println("Invalid message: " + msg_id + " received!!");
                            // no action, drop it
                    }
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
