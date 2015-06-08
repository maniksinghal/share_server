package com.example.manik;

import java.nio.ByteBuffer;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by maniksin on 5/30/15.
 */
public class WorkerThread implements Runnable,Serializable {
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
    private final int NETWORK_MSG_DIRECTORY_TIME_GET = 10;
    private final int NETWORK_MSG_DIRECTORY_TIME_ACK = 11;
    private final int NETWORK_MSG_DIRECTORY_TIME_SET = 12;
    private final int NETWORK_MSG_FILE_PUT_START_NACK = 13;

    // Hash-map storages
    private final String HASHMAP_STORE_FILES = ".file_info";
    private final String HASHMAP_STORE_DIRECTORIES = ".directory_info";

    // File download context
    private FileOutputStream fo = null;
    private String currentFile = null;
    private long currentFile_ts = 0;
    private HashMap<String,String> file_info = null;
    private HashMap<String,String> directory_info = null;
    private String mAndroidId = null;

    public WorkerThread(Socket s) {
        sock = s;
        //file_info = load_hashmap(HASHMAP_STORE_FILES);
        //directory_info = load_hashmap(HASHMAP_STORE_DIRECTORIES);
    }


    private HashMap<String,String> load_hashmap(String path) {
        String full_path = mAndroidId + "/" + path;
        try
        {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(full_path));
            Object result = ois.readObject();
            ois.close();
            return (HashMap<String, String>)result;
        }
        catch(Exception e)
        {
            // May be hash not created yet.
            System.out.println("Could not find hashmap file " + path + ". Creating new hash");
            HashMap<String, String> hash = new HashMap<>();
            return hash;
        }
    }

    private void update_hashmap(HashMap<String, String> hash, String path) {
        String full_path = mAndroidId + "/" + path;
        try {
            FileOutputStream fout = new FileOutputStream(full_path);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(hash);
            oos.close();
            System.out.println("Hashmap updated to " + full_path);
        } catch (Exception e) {
            System.out.println("Updating hashmap to file " + full_path + " Failed");
            System.out.println(e.getMessage() + " " + e.getLocalizedMessage());
        }
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

             /*
                * NETWORK_MSG_FILE_PUT_START
                * 4 byte msg-id
                * 4 byte payload-len
                * 8 byte file last modified time
                * 4 byte file length
                * <file-name>
                * 4 byte android-id.length
                * <android-id string>
                 */
            bb = ByteBuffer.wrap(payload);
            currentFile_ts = bb.getLong();
            int file_len = bb.getInt();
            byte[] file = new byte[file_len];
            bb.get(file);
            int android_id_len = bb.getInt();
            byte[] android_id = new byte[android_id_len];
            bb.get(android_id);

            mAndroidId = new String(android_id);
            currentFile = new String(file);

            bb = ByteBuffer.wrap(response);

            File device = new File(mAndroidId);
            if (device == null || !device.isDirectory()) {
                // Create it
                device.mkdir();
            }
            file_info = load_hashmap(HASHMAP_STORE_FILES);


            // Check if file already exists
            String stored_ts = file_info.get(currentFile);
            if (stored_ts != null && Long.valueOf(stored_ts) == currentFile_ts) {
                // File already exists
                System.out.println("File " + currentFile + " already exists");
                bb.putInt(NETWORK_MSG_FILE_PUT_START_NACK);
                bb.putInt(0);
                return response;
            }
            String target = mAndroidId + "/" + Paths.get(currentFile).getFileName().toString();
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

            String file_ts = Long.toString(currentFile_ts);
            file_info.put(currentFile, file_ts);
            update_hashmap(file_info, HASHMAP_STORE_FILES);

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

        if (msg_id == NETWORK_MSG_DIRECTORY_TIME_GET) {

            bb = ByteBuffer.wrap(payload);
            int file_len = bb.getInt();
            byte[] file_name = new byte[file_len];
            bb.get(file_name, 0, file_len);
            int android_id_len = bb.getInt();
            byte[] android_id = new byte[android_id_len];
            bb.get(android_id);

            mAndroidId = new String(android_id);
            String str = new String(file_name);

            File device = new File(mAndroidId);
            if (device == null || !device.isDirectory()) {
                // Create it
                device.mkdir();
            }
            directory_info = load_hashmap(HASHMAP_STORE_DIRECTORIES);

            String file_ts = directory_info.get(str);
            long ts = 0;
            if (file_ts != null) {
                ts = Long.valueOf(file_ts);
            }
            System.out.println("Read timestamp " + ts + " for directory " + str);

            byte[] rsp = new byte[16];
            bb = ByteBuffer.wrap(rsp);
            bb.putInt(NETWORK_MSG_DIRECTORY_TIME_ACK);
            bb.putInt(8);
            bb.putLong(ts);
            return rsp;
        }

        if (msg_id == NETWORK_MSG_DIRECTORY_TIME_SET) {

             /*
                * Directory sync message:
                * 4 byte msg-id
                * 4 byte payload-len
                * 4 byte directory-string length
                * <directory-string>
                * 8 byte modified time
                * 4 byte android_id.length
                * <android-id>
                 */
            bb = ByteBuffer.wrap(payload);
            int file_len = bb.getInt();
            byte[] file_name = new byte[file_len];
            bb.get(file_name, 0, file_len);
            String dir_ts = Long.toString(bb.getLong());
            int android_id_len = bb.getInt();
            byte[] android_id = new byte[android_id_len];
            bb.get(android_id);

            mAndroidId = new String(android_id);
            String str = new String(file_name);

            File device = new File(mAndroidId);
            if (device == null || !device.isDirectory()) {
                // Create it
                device.mkdir();
            }
            directory_info = load_hashmap(HASHMAP_STORE_DIRECTORIES);
            file_info = load_hashmap(HASHMAP_STORE_FILES);




            directory_info.put(str, dir_ts);

            // We can remove all files from file_info hashmap which have lesser ts than the
            // directory ts, as the peer is not going to ask for checking those files.
            List<String> files_to_remove = new ArrayList<String>();
            for (String key: file_info.keySet()) {
                if (key.contains(str)) {
                    // File name has the directory name
                    String file_ts = file_info.get(key);
                    if (Long.valueOf(file_ts) < Long.valueOf(dir_ts)) {
                        files_to_remove.add(key);
                    }
                }

            }

            while (!files_to_remove.isEmpty()) {
                String file = files_to_remove.remove(0);
                System.out.println("Removing " + file + " from hashmap on directory timestamp update");
                file_info.remove(file);
            }

            update_hashmap(directory_info, HASHMAP_STORE_DIRECTORIES);
            update_hashmap(file_info, HASHMAP_STORE_FILES);

            System.out.println("Set timestamp " + dir_ts + " for directory " + str);

            bb = ByteBuffer.wrap(response);
            bb.putInt(NETWORK_MSG_DIRECTORY_TIME_ACK);
            bb.putInt(0); // 0 payload length
            return response;
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
                        case NETWORK_MSG_DIRECTORY_TIME_GET:
                        case NETWORK_MSG_DIRECTORY_TIME_SET:
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
