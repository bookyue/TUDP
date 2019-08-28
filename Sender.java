import java.io.*;
import java.net.*;
import java.util.Vector;

public class Sender {

    public static void main(String args[]) throws Exception {
        // 获取要通过UDP发送的文件的地址，端口和名称
        final String hostName = args[0];
        final int port = Integer.parseInt(args[1]);
        final String fileName = args[2];

        createAndSend(hostName, port, fileName);
    }

    public static void createAndSend(String hostName, int port, String fileName) throws IOException {
        System.out.println("Sending the file");

        // 创建socket，设置地址并创建要发送的文件
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(hostName);
        File file = new File(fileName); 

        // 创建一个字节数组来存储文件流
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        inFromFile.read(fileByteArray);

        // 启动计时器以计算吞吐量
        StartTime timer = new StartTime(0);

        // 创建flag以表示最后一个message和一个16位序列号
        int sequenceNumber = 0;
        boolean lastMessageFlag = false;

        // 创建flag以表示最后一个确认的message和一个16位序列号
        int ackSequenceNumber = 0;
        int lastAckedSequenceNumber = 0;
        boolean lastAcknowledgedFlag = false;

        // 创建计数器以计算重新传输次数并初始化窗口大小
        int retransmissionCounter = 0;
        int windowSize = 128;

        // 用向量存储已发送的message
        Vector <byte[]> sentMessageList = new Vector <byte[]>();

        for (int i=0; i < fileByteArray.length; i = i+1021 ) {

            // 递增序列号
            sequenceNumber += 1;

            // 创建新的字节数组存储message
            byte[] message = new byte[1024];

            // 将message的第一个和第二个字节设置为序列号
            message[0] = (byte)(sequenceNumber >> 8);
            message[1] = (byte)(sequenceNumber);

            // 如果数据包是最后一个数据包，则将flag设置为1（true），并将其存储在头的第三个字节中
            if ((i+1021) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte)(1);
            } else { // 如果不是最后一个message存储标志为0（false）
                lastMessageFlag = false;
                message[2] = (byte)(0);
            }

            // 将message的字节复制到message数组
            if (!lastMessageFlag) {
                for (int j=0; j != 1021; j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }
            else if (lastMessageFlag) { // 如果是最后的message
                for (int j=0;  j < (fileByteArray.length - i); j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }

            // 打包message
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);

            // 将message添加到已发送的message列表中
            sentMessageList.add(message);

            while (true) {
                // 如果下一个序列号在窗口之外
                if ((sequenceNumber - windowSize) > lastAckedSequenceNumber) {

                    boolean ackRecievedCorrect = false;
                    boolean ackPacketReceived = false;

                    while (!ackRecievedCorrect) {
                        // 检查ack
                        byte[] ack = new byte[2];
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                        try {
                            socket.setSoTimeout(50);
                            socket.receive(ackpack);
                            ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                            ackPacketReceived = true;
                        } catch (SocketTimeoutException e) {
                            ackPacketReceived = false;
                            //System.out.println("Socket timed out while waiting for an acknowledgement");
                            //e.printStackTrace();
                        }

                        if (ackPacketReceived) {
                            if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                                lastAckedSequenceNumber = ackSequenceNumber;
                            }
                            ackRecievedCorrect = true;
                            System.out.println("Ack recieved: Sequence Number = " + ackSequenceNumber);
                            break;  // 如果有ack则中断，以便可以发送下一个数据包
                        } else { // 重发数据包
                            System.out.println("Resending: Sequence Number = " + sequenceNumber);
                            // 在最后一个确认的数据包之后重新发送数据包以及之后的所有数据包（累积确认）
                            for (int y=0; y != (sequenceNumber - lastAckedSequenceNumber); y++) {
                                byte[] resendMessage = new byte[1024];
                                resendMessage = sentMessageList.get(y + lastAckedSequenceNumber);

                                DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                                socket.send(resendPacket);
                                retransmissionCounter += 1;
                            }
                        }
                    }
                } else { // 其他管道未满，break所以我们可以发送message
                    break;
                }
            }

            // 发送数据包
            socket.send(sendPacket);
            System.out.println("Sent: Sequence number = " + sequenceNumber + ", Flag = " + lastMessageFlag);


            // 检查ack
            while (true) {
                boolean ackPacketReceived = false;
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(10);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    //System.out.println("Socket timed out waiting for an ack");
                    ackPacketReceived = false;
                    //e.printStackTrace();
                    break;
                }

                // 记下ack并向前移动窗口
                if (ackPacketReceived) {
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                        System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    }
                }
            }
        }

        // 继续检查并重新发送，直到我们收到final ack
        while (!lastAcknowledgedFlag) {

            boolean ackRecievedCorrect = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // 检查单个ack
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    //System.out.println("Socket timed out waiting for an ack1");
                    ackPacketReceived = false;
                    //e.printStackTrace();
                }

                // 如果是最后一个数据包
                if (lastMessageFlag) {
                    lastAcknowledgedFlag = true;
                    break;
                }   
                // 如果收到确认，可以发送下一个数据包
                    if (ackPacketReceived) {        
                    System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                    }
                    ackRecievedCorrect = true;
                    break; // Break 如果有确认，则可以发送下一个数据包
                } else { // 重发数据包
                    // 在最后一个确认的数据包之后重新发送数据包以及之后的所有数据包（累积确认）
                    for (int j=0; j != (sequenceNumber-lastAckedSequenceNumber); j++) {
                        byte[] resendMessage = new byte[1024];
                        resendMessage = sentMessageList.get(j + lastAckedSequenceNumber);
                        DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                        socket.send(resendPacket);
                        System.out.println("Resending: Sequence Number = " + lastAckedSequenceNumber);

                        // 递增重传计数器
                        retransmissionCounter += 1;
                    }
                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " has been sent");

        // 计算平均吞吐量
        int fileSizeKB = (fileByteArray.length) / 1024;
        int transferTime = timer.getTimeElapsed() / 1000;
        double throughput = (double) fileSizeKB / transferTime;
        System.out.println("File size: " + fileSizeKB + "KB, Transfer time: " + transferTime + " seconds. Throughput: " + throughput + "KBps");
        System.out.println("Number of retransmissions: " + retransmissionCounter);
    }
}
