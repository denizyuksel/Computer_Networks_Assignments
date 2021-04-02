import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

/*
 * Sender class for simulating the operation of a Selective Repeat sender
 * over a lossy network layer and the code for transferring a specified file
 * as an application-layer program.
 * Bartu Atabek - 21602229
 * Deniz Yüksel - 21600880
 */
public class Sender {

    public static void main(String[] args) throws Exception {

        if (args.length != 4) {
            System.out.println("Invalid program arguments.");
            System.out.println("Arguments should follow the pattern below.");
            System.out.println("java Sender <file_path> <receiver_port> <window_size_N> <retransmission_timeout>");
            return;
        }

        // Program arguments
        String file_path = args[0];
        int receiver_port = Integer.parseInt(args[1]);
        int window_size = Integer.parseInt(args[2]);
        int retransmission_timeout = Integer.parseInt(args[3]);

        // Divides the file and constructs chunks with size 1022 Bytes
        byte[][] segments = constructSegments(readBytesFromFile(file_path), 1022);

        // Variables
        Map<Integer, Thread> threadMap = new HashMap<>();
        TreeSet<Integer> ACKS = new TreeSet<>();

        for (byte[] segment: segments) {
            int val = ((segment[0] & 0xff) << 8) | (segment[1] & 0xff);
            ACKS.add(val);
        }

        // Create Datagram Socket
        InetAddress IPAddress = InetAddress.getByName("127.0.0.1");
        DatagramSocket socket = new DatagramSocket();
        byte[] receiveData = new byte[2];

        int send_base = 1;
        int nextseqnum = 1;
        boolean transfer = true;

        // RunnableSegment Inner class for sending segments
        class RunnableSegment implements Runnable {

            // DatagramPacket to be sent
            private DatagramPacket packet;

            public RunnableSegment(DatagramPacket packet) {
                this.packet = packet;
            }

            @Override
            public void run() {
                try {
                    while(true) {
                        // Send packet
                        socket.send(packet);

                        // Wait for main thread notification or timeout
                        Thread.sleep(retransmission_timeout);
                    }
                }

                // Stop if main thread interrupts this thread
                catch (InterruptedException | IOException e) {
                }
            }
        }

        System.out.println("Starting transmission...");

        // Loop until all packets are sent and all ACK are received
        while (transfer) {
            for (int segment_no = nextseqnum; segment_no <= send_base + window_size -1 && segment_no <= segments.length; segment_no++) {
                DatagramPacket sendPacket = new DatagramPacket(segments[segment_no - 1],segments[segment_no-1].length, IPAddress, receiver_port);
                Thread thread = new Thread(new RunnableSegment(sendPacket));
                thread.start();
                threadMap.put(segment_no, thread);
                nextseqnum++;
            }

            // Receive ACK
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            int ACKNo = ((receiveData[0] & 0xff) << 8) | (receiveData[1] & 0xff);

            if (ACKNo >= send_base && ACKNo <= send_base + window_size -1) {
                if (threadMap.get(ACKNo) != null) {
                    threadMap.get(ACKNo).interrupt();
                    threadMap.remove(ACKNo);

                    // Advance send_base
                    ACKS.remove(ACKNo);

                    // Send 2 Bytes of 0's to indicate that the transfer is over
                    if (ACKS.isEmpty()) {
                        byte[] endBytes = new byte[2];
                        endBytes[0] = (byte) (0 & 0xFF);
                        endBytes[1] = (byte) (0 & 0xFF);

                        DatagramPacket sendPacket = new DatagramPacket(endBytes, endBytes.length, IPAddress, receiver_port);
                        socket.send(sendPacket);
                        transfer = false;
                    } else {
                        send_base = ACKS.first();
                    }
                }
            }
        }

        System.out.println("Transmission completed.");
    }

    public static byte[] readBytesFromFile(String file_path) {

        FileInputStream fileInputStream = null;
        byte[] bytesArray = null;

        try {

            File file = new File(file_path);
            bytesArray = new byte[(int) file.length()];

            // read file into bytes[]
            fileInputStream = new FileInputStream(file);
            fileInputStream.read(bytesArray);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        return bytesArray;
    }

    public static byte[][] constructSegments(byte[] source, int chunkSize) {

        byte[][] segments = new byte[(int)Math.ceil(source.length / (double) chunkSize)][];

        int start = 0;
        int sequenceNo = 1;

        for (int i = 0; i < segments.length; i++) {
            if(start + chunkSize > source.length) {
                segments[i] = new byte[source.length - start];
                System.arraycopy(source, start, segments[i], 0, source.length - start);
            }
            else {
                segments[i] = new byte[chunkSize];
                System.arraycopy(source, start, segments[i], 0, chunkSize);
            }
            segments[i] = append(segments[i], (byte) (sequenceNo & 0xFF));
            segments[i] = append(segments[i], (byte) ((sequenceNo >> 8) & 0xFF));
            start += chunkSize ;
            sequenceNo++;
        }

        return segments;
    }

    public static byte[] append(byte[] elements, byte element) {
        byte[] newArray = Arrays.copyOf(elements, elements.length + 1);
        newArray[0] = element;
        System.arraycopy(elements, 0, newArray, 1, elements.length);

        return newArray;
    }
}