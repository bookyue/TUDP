import java.io.*;
import java.net.*;

public class Receiver {

    public static void main(String args[]) throws Exception {
        System.out.println("Ready to receive the file!");

        //获取用UDP发送的文件的地址，端口和名称
        final int port = Integer.parseInt(args[0]);
        final String fileName = args[1];

        receiveAndCreate(port, fileName);
    }

    public static void receiveAndCreate(int port, String fileName) throws IOException {
        // 创建socket，设置地址并创建要发送的文件
        DatagramSocket socket = new DatagramSocket(port);
        InetAddress address;
        File file = new File(fileName);
        FileOutputStream outToFile = new FileOutputStream(file);

        // 创建一个标志以指示最后一条message
        boolean lastMessageFlag = false;
		
        // 存储序列号
        int sequenceNumber = 0;
        int lastSequenceNumber = 0;

        while (!lastMessageFlag) {
            // Create byte array for full message and another for file data without header
			//完整的message字节数组，没有header的文件数据字节数组
            byte[] message = new byte[1024];
            byte[] fileByteArray = new byte[1021];

            // 接收数据包并检索message
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            socket.setSoTimeout(0);
            socket.receive(receivedPacket);
            message = receivedPacket.getData();

            // 获取发送确认的端口和地址
            address = receivedPacket.getAddress();
            port = receivedPacket.getPort();

            // 检索序列号
            sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

            // 检索最后一条message标志
            if ((message[2] & 0xff) == 1) {
                lastMessageFlag = true;
            } else {
                lastMessageFlag = false;
            }

            if (sequenceNumber == (lastSequenceNumber + 1)) {

                // 更新最新的序列号
                lastSequenceNumber = sequenceNumber;

                // 从message中检索数据
                for (int i=3; i < 1024 ; i++) {
                    fileByteArray[i-3] = message[i];
                }

                // 将message写入文件
                outToFile.write(fileByteArray);
                System.out.println("Received: Sequence number = " + sequenceNumber +", Flag = " + lastMessageFlag);

                // 发送ack
                sendAck(lastSequenceNumber, socket, address, port);

                // 检查最后一条message
                if (lastMessageFlag) {
                    outToFile.close();
                } 
            } else {
                // 如果已收到数据包，再次发送该数据包的确认
                if (sequenceNumber < (lastSequenceNumber + 1)) {
                    // 给已收到的数据包发送ack
                    sendAck(sequenceNumber, socket, address, port);
                } else {
                    // 重新发送最后收到的数据包的ack
                    sendAck(lastSequenceNumber, socket, address, port);
                }
            }
        }
        
        socket.close();
        System.out.println("File " + fileName + " has been received.");
	}

    public static void sendAck(int lastSequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
        // 重新发送ack
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte)(lastSequenceNumber >> 8);
        ackPacket[1] = (byte)(lastSequenceNumber);
        DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);
        System.out.println("Sent ack: Sequence Number = " + lastSequenceNumber);
    }
}
