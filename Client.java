import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;

public class Client {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Receive list of files
            String fileList = in.readUTF();
            System.out.println("Available files:\n" + fileList);

            System.out.println("Enter the name of the file you want to download:");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String requestedFile = reader.readLine();
            out.writeUTF(requestedFile);

            // Receive file size
            long fileSize = in.readLong();
            if (fileSize > 0) {
                System.out.println("File downloading...");

                long classicElapsedTime = downloadClassicFile(in, requestedFile, fileSize);
                System.out.println("Classic download time: " + classicElapsedTime + " ms");

                long zeroCopyElapsedTime = downloadZeroCopyFile(in, requestedFile, fileSize);
                System.out.println("Zero-Copy download time: " + zeroCopyElapsedTime + " ms");
            } else {
                System.out.println("File not found on server.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

   private static long downloadClassicFile(DataInputStream in, String requestedFile, long fileSize) throws IOException {
    String receivedFileName = "./client_files_classic/" + requestedFile;

    long startTime = System.currentTimeMillis();

    try (FileOutputStream fos = new FileOutputStream(receivedFileName)) {
        byte[] buffer = new byte[6 * 1024 * 1024];  // 64KB buffer
        int bytesRead;
        long totalBytesReceived = 0;

        while (totalBytesReceived < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesReceived))) != -1) {
            fos.write(buffer, 0, bytesRead);
            totalBytesReceived += bytesRead;
        }
    }

    long endTime = System.currentTimeMillis();
    return endTime - startTime;
}

    private static long downloadZeroCopyFile(DataInputStream in, String requestedFile, long fileSize) throws IOException {
        String receivedFileName = "./client_files_zero_copy/" + requestedFile;

        long zeroCopyStartTime = System.currentTimeMillis();
        long zeroCopyEndTime;

        try (FileOutputStream fos = new FileOutputStream(receivedFileName)) {
            FileChannel fileChannel = fos.getChannel();
            long totalBytesReceived = 0;
            while (totalBytesReceived < fileSize) {
                long bytesTransferred = fileChannel.transferFrom(Channels.newChannel(in), totalBytesReceived, fileSize - totalBytesReceived);
                if (bytesTransferred == 0) {
                    break;
                }
                totalBytesReceived += bytesTransferred;
            }
            zeroCopyEndTime = System.currentTimeMillis();
        }

        return zeroCopyEndTime - zeroCopyStartTime;
    }
}