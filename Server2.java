import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class Server2 {

    private static final int PORT = 8080;
    private static final String DIRECTORY = "./server_files/";
    private static final Map<Socket, Integer> clientThreadCountMap = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Thread t1 = new Thread(new ClientHandler(serverSocket.accept()));
                t1.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            synchronized (clientThreadCountMap) {
                if (!clientThreadCountMap.containsKey(socket)) {
                    clientThreadCountMap.put(socket, 1);
                } else {
                    int currentCount = clientThreadCountMap.get(socket);
                    clientThreadCountMap.put(socket, currentCount + 1);
                }
            }

            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                synchronized (clientThreadCountMap) {
                    System.out.println("Client : " + socket.getPort());
                    int threadCountForClient = clientThreadCountMap.get(socket);
                    System.out.println("Threads connected to this client: " + threadCountForClient);
                }

                File[] files = new File(DIRECTORY).listFiles();
                StringBuilder fileList = new StringBuilder();
                for (File file : files) {
                    fileList.append(file.getName()).append("\n");
                }
                out.writeUTF(fileList.toString());

                String requestedFile = in.readUTF();
                File fileToSend = new File(DIRECTORY + requestedFile);
                if (fileToSend.exists()) {
                    out.writeLong(fileToSend.length());

                    // Start time for sending the file
                    long classicstartTime = System.currentTimeMillis();

                    try (FileChannel fileChannel = new FileInputStream(fileToSend).getChannel()) {
                        ByteBuffer buffer = ByteBuffer.allocate(4096);
                        long totalBytesSent = 0;
                        while (totalBytesSent < fileToSend.length()) {
                            int bytesRead = fileChannel.read(buffer);
                            if (bytesRead == -1) {
                                break;
                            }
                            buffer.flip();
                            out.write(buffer.array(), 0, bytesRead);
                            buffer.clear();
                            totalBytesSent += bytesRead;
                        }
                    }

                    // End time for sending the file
                    long classicendTime = System.currentTimeMillis();

                    long classicElapsedTime = classicendTime - classicstartTime;
                    System.out.println("Classic download time: " + classicElapsedTime + " ms");
                    
                    
                // Start time for sending the file (Zero-Copy)
                long zeroCopyStartTime = System.currentTimeMillis();

                try (FileChannel fileChannel = new FileInputStream(fileToSend).getChannel()) {
                    long totalBytesSent = 0;
                    long fileSize = fileToSend.length();

                    while (totalBytesSent < fileSize) {
                        long bytesTransferred = fileChannel.transferTo(totalBytesSent, fileSize - totalBytesSent, Channels.newChannel(socket.getOutputStream()));
                        if (bytesTransferred == 0) {
                            break;
                        }
                        totalBytesSent += bytesTransferred;
                    }
                }

                // End time for sending the file (Zero-Copy)
                long zeroCopyEndTime = System.currentTimeMillis();

                long zeroCopyElapsedTime = zeroCopyEndTime - zeroCopyStartTime;
                System.out.println("Zero-Copy download time: " + zeroCopyElapsedTime + " ms");    
                    
                    
                } else {
                    out.writeLong(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (clientThreadCountMap) {
                    int currentCount = clientThreadCountMap.get(socket);
                    if (currentCount > 1) {
                        clientThreadCountMap.put(socket, currentCount - 1);
                    } else {
                        clientThreadCountMap.remove(socket);
                        System.out.println("Client disconnected: " + socket);
                    }
                }
            }
        }
    }
}
