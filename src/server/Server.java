package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class Server implements SendingObject {

    private Map<Message, Long> messages;
    private DatagramSocket datagramSocket;

    private List<Thread> threads;

    public Server(DatagramSocket datagramSocket) {
        messages = new HashMap<>();
        this.datagramSocket = datagramSocket;
        threads = new LinkedList<>();
    }

    public void loadDataFile(String dataFilePath) {

        try {
            BufferedReader reader = new BufferedReader(new FileReader(dataFilePath));

            String line;

            while ((line = reader.readLine()) != null) {

                String[] args = line.split(" ");

                if (args.length >= 4) {

                    StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 3; i < args.length; i++) {
                        stringBuilder.append(args[i]);
                        if (i + 1 < args.length) {
                            stringBuilder.append(' ');
                        }
                    }

                    String address = args[0];

                    int port = Integer.parseInt(args[1]);

                    long time = Long.parseLong(args[2]);

                    String data = new String(stringBuilder);

                    InetAddress inetAddress = InetAddress.getByName(address);

                    Message message = new Message(inetAddress, port, data);

                    messages.put(message, time);
                }
            }
            reader.close();
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
    }

    public void process() {

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        threads.clear();

        Map<Long, List<Message>> longListMap = new HashMap<>();

        for (Message message : messages.keySet()) {

            long time = messages.get(message);

            List<Message> messageList = longListMap.getOrDefault(time, new ArrayList<>());
            messageList.add(message);

            longListMap.put(time, messageList);
        }

        for (Long time : longListMap.keySet()) {
            threads.add(new ThreadMessage(longListMap.get(time), time, this));
        }

        for (Thread thread : threads) {
            thread.start();
        }
    }


    public void stop() {

        for (Thread thread : threads) {
            thread.interrupt();
        }

        threads.clear();
    }

    public void clear() {
        messages.clear();
    }

    public synchronized void sendMessage(Message message) throws IOException {

        byte[] data = message.getData().getBytes();

        DatagramPacket datagramPacket = new DatagramPacket(data, 0, data.length, message.getInetAddress(), message.getPort());

        datagramSocket.send(datagramPacket);
    }

    private static class ThreadMessage extends Thread {

        private final SendingObject sendingObject;
        private final List<Message> messages;
        private long time;


        private ThreadMessage(List<Message> messages, long time, SendingObject sendingObject) {
            this.messages = messages;
            this.time = time;
            this.sendingObject = sendingObject;
        }

        private static void printSendMessageLog(Message message) {

            StringBuilder stringBuilder = new StringBuilder();

            stringBuilder.append('[');
            stringBuilder.append(new Date());
            stringBuilder.append("] ");
            stringBuilder.append(message.getInetAddress().getHostName());
            stringBuilder.append(':');
            stringBuilder.append(message.getPort());
            stringBuilder.append(" ==> ");
            stringBuilder.append(message.getData());

            System.out.println(stringBuilder);
        }

        @Override
        public void run() {

            while (!isInterrupted()) {


                List<Message> removedMessage = new LinkedList<>();

                for (Message message : messages) {
                    try {
                        sendingObject.sendMessage(message);
                        printSendMessageLog(message);
                    } catch (IOException e) {
                        removedMessage.add(message);
                        e.printStackTrace();
                    }
                }


                messages.removeAll(removedMessage);

                if (messages.isEmpty()) {
                    interrupt();
                }

                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }
}
