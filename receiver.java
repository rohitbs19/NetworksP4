//import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import java.math.BigInteger;
import java.util.Timer;
import java.util.TimerTask;
import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;


/*
*
*
* THINGS THAT THE RECEIVER SHOULD DO WITH THE PACKETS SENT BY THE SENDER:
* 1: the receiver should basically write the contents of the packets sent by the sender
* 2: send an ack acknowledging the packet.
* 3: if the packet is out of order that is if the nextseqnum is not equal to the syn of the packet arriving inwards
* 4: as fast retransmit is enabled the receiver has to send a dup ack.
* 5: if the packet is in order then receiver has to send a normal ack increment its seqnum and store the contents into
* 5-2: its local file
* 6: Three if statements have to be there, first to check if the packet being received is corrupted?
* 6-2: 2: if the packet is in order or not
* 6-3:    if yes, then send a normal ack
* 6-4:    if no, then send a dup ack
*
* SPECIAL MECHANISM FOR CUMULATIVE ACKS:
*
* 1: should keep a global counter that counts the number of consecutive dup acks were sent
* 2: this var has to reset every time a normal ack is sent.
* 3: if the counter exceeds the >=3 and has a chance to send normal ack then it has to go through its buffer
* 4: looking for packets which have not been acked and not in order, then send an ack acking all four of the packets.
*
*
*
* */

/*
*
*
* assumptions from the sender:
* 1: first the syn pack comes from the sender to initiate the 3 way handshake
* 2: the receiver has to send a syn+ack in response to that.
* 3: so in a way the receiver has to wait till the syn from the sender is received in order to send it response
* 4: after sending syn+ack it should sleep until an ack is received from the sender.
* 5: once the ack is received it should enter the while loop which loops around until the file that is being sent
* 5-2: is exhausted that is till the last packet form the sender.
* 6: once the last packet arrives indicating it is last packet then the receiver should issue teardown
* 7: and the connection should end based on that.
*
* */
public class receiver{




    /*
    * fields
    * */
    static int PACKETSIZE =1024;
    Timer timer;
    Semaphore s;
    int destPortForTimer;
    DatagramSocket socket_1, socket_2;
    int timeoutVal =1000;
    int windowSize = 10;



    public byte[] copyOfRange(byte[] srcArr, int start, int end){
        int length = (end > srcArr.length)? srcArr.length-start: end-start;
        byte[] destArr = new byte[length];
        System.arraycopy(srcArr, start, destArr, 0, length);
        return destArr;
    }

    public void print(String str) {
        System.out.println(str);

    }

    /*
    *
    * helper class of a packet or we could make the packet definition in the sender a package level class
    *
    *
    * */

    /*
     * constructor is key for this because the code which does the majority of the work of the receiver
     * goes into the constructor
     * */
    public void setTimer(boolean isNewTimer){
        if (timer != null) timer.cancel();
        if (isNewTimer){
            timer = new Timer();
            timer.schedule(new receiver.Timeout(), 1000);
        }
    }

    public class Timeout extends TimerTask {
        public void run(){
            try{
                s.acquire();
                System.out.println("receiver: Timeout!");
                Packet SYNACK = new Packet(0, null, 'Q', 1);
                ByteBuffer synack = SYNACK.createPacket();
                socket_2.send(new DatagramPacket(synack.array(), synack.array().length,InetAddress.getByName("10.0.1.101"),destPortForTimer));
                s.release();
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public String addBinary(String p1, String p2) {

        // Initialize result
        String result = "";

        // Initialize digit sum
        int s = 0;

        // Travers both strings starting
        // from last characters
        int i = p1.length() - 1, j = p2.length() - 1;
        while (i >= 0 || j >= 0 || s == 1)
        {

            // Comput sum of last
            // digits and carry
            s += ((i >= 0)? p1.charAt(i) - '0': 0);
            s += ((j >= 0)? p2.charAt(j) - '0': 0);

            // If current digit sum is
            // 1 or 3, add 1 to result
            result = (char)(s % 2 + '0') + result;

            // Compute carry
            s /= 2;

            // Move to next digits
            i--; j--;
        }

        return result;

    }
    public String invert(String binary) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < binary.length(); i++) {
            if(binary.charAt(i) == '0') {
                sb.append('1');
            }
            else {
                sb.append('0');
            }
        }
        return sb.toString();

    }
    /*
     *
     * helper function for validating if the received packet is of intial syn type
     *
     * */
    public class Packet{

        /*
        *
        * fields
        *
        * */
        int seqNumber;
        byte[] dataSeg;
        char flag;
        int ackNum;
        int checksum;
        long timeStamp;
        //packet fields copy from sender


        /*
         * constructor
         * */
        //copy from sender
        public Packet() {

        }
        public Packet(int seqNumber, byte [] dataSeg, char flag, int ackNum/* with fields*/) {
            this.seqNumber = seqNumber;
            this.dataSeg = dataSeg;
            this.flag = flag;
            this.ackNum = ackNum;
            this.timeStamp = System.nanoTime();
        }


        public boolean isValidSyn() {


            if (this.flag == 'S' && this.checksum == computeChecksum()) {
                return true;
            }
            return false;
        }

        public boolean isValidAck() {

            if (this.flag == 'A' && this.checksum == computeChecksum()) {
                return true;
            }
            return false;
        }

        /*
        * generate packet from the datagram packet received to make it of packet type
        * set the fields of the packet using this function
        *
        * */

        public void generatePacketFromDatagramPacket(byte[] inData) {
            byte[] sequenceNumber = copyOfRange(inData, 0, 4);
            this.seqNumber = ByteBuffer.wrap(sequenceNumber).getInt();


            byte[] ackNumber = copyOfRange(inData, 4, 8);
            this.ackNum = ByteBuffer.wrap(ackNumber).getInt();
            //print("ackNUM " +this.ackNum);
            byte[] timeStamp = copyOfRange(inData, 8, 16);
            this.timeStamp = ByteBuffer.wrap(timeStamp).getLong();

            byte[] lengthField = copyOfRange(inData, 16, 20);
            int length = ByteBuffer.wrap(lengthField).getInt();

           /* int shiftedLength = length << 3;
            if ((shiftedLength + 4)==length) {
                this.flag = 'S';
            } else if ((shiftedLength + 2) == length) {
                this.flag='F';
            } else if ((shiftedLength + 1) == length) {
                this.flag = 'A';
            }*/
            int i =0;
            String extractFlagBitsMask = "00000000000000000000000000001111";
            int extractMaskValue = new BigInteger(extractFlagBitsMask, 2).intValue();
            int shiftedLength = extractMaskValue & length;
            /*
             *
             * so the possibilities are 1000, 0100, 0010, 0001
             * */
            String SynMask = "00000000000000000000000000000111";
            int synMaskValue = new BigInteger(SynMask, 2).intValue();
            String FinMask = "00000000000000000000000000001011";
            int finMaskValue = new BigInteger(FinMask, 2).intValue();
            String AckMask = "00000000000000000000000000001101";
            int ackMaskValue = new BigInteger(AckMask, 2).intValue();
            String DMask = "00000000000000000000000000001110";
            int dMaskValue = new BigInteger(DMask, 2).intValue();
            String synackMask = "00000000000000000000000000000101";
            int synackValue = new BigInteger(synackMask, 2).intValue();

            if ((shiftedLength & synMaskValue) == 0) {
                print("PACKET TYPE IS --> SYN1");
                this.flag = 'S';
            }else if((shiftedLength & ackMaskValue) == 0){
                print("PACKET TYPE IS --> ACK1");
                this.flag = 'A';
            } else if ((shiftedLength & finMaskValue) == 0) {
                print("PACKET TYPE IS --> FIN1");
                this.flag = 'F';
            } else if ((shiftedLength & dMaskValue) == 0) {
                print("PACKET TYPE IS --> DATA1");
                this.flag = 'D';
            } else if ((shiftedLength & synackValue) == 0) {
                print("PACKET TYPE IS --> SYN + ACK");
                this.flag = 'Q';
            }
            //byte[] type = copyOfRange(inData, 12, 14);
            //print("type = " + type);
            //this.flag = ByteBuffer.wrap(type).getChar();
            //System.out.println("INSIDE GENERATEPACKET FROM DATAGRAM FLAG:  " + this.flag);
            byte[] checksumComputed = copyOfRange(inData, 20, 24);
            this.checksum = ByteBuffer.wrap(checksumComputed).getInt();
            byte[] dataField = copyOfRange(inData, 24, inData.length);
            this.dataSeg = dataField;
        }
        /*
        *
        * responsible for checksum validation of the the packet
        * */
        public boolean isValidPacket() {

            if (this.checksum == computeChecksum()) {
                return true;
            }

            return false;
        }

        public int computeChecksum() {
            String seqNumInBinary = null;
            if(this.flag=='A' && this.ackNum>=0)
            seqNumInBinary= Integer.toBinaryString(this.ackNum);
            else
                seqNumInBinary = Integer.toBinaryString(this.seqNumber);

            String temp = null;

            if (seqNumInBinary.length() != 16) {

                int offset = 16 - seqNumInBinary.length();
                StringBuilder stringB = new StringBuilder();

                for(int i =0; i< offset; i++) {
                    stringB.append('0');
                }
                stringB.append(seqNumInBinary);
                temp = stringB.toString();
            }

            String part1 = temp.substring(0, 8);
            String part2 = temp.substring(8);

            String addition = addBinary(part1, part2);

            addition = invert(addition);

            //this.checksum = Short.parseShort(addition);
            return Integer.parseInt(addition);
        }
        public ByteBuffer createPacket() {
            // sequence number

            byte[] seqNumberBb = ByteBuffer.allocate(4).putInt(this.seqNumber).array();

            // timestamp
            //byte[] timestamp = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array();

            byte[] ackNumBB = ByteBuffer.allocate(4).putInt(this.ackNum).array();

            // length

            int length=0;
            if (this.dataSeg != null) {
                length = this.dataSeg.length;
            }else{
                length=0;
            }

            int shiftedLength = length << 4;
            String mask;
            if(flag=='S') {
                mask = "00000000000000000000000000001000";
            } else if(flag == 'F') {
                mask = "00000000000000000000000000000100";
            } else if(flag=='A') {
                mask = "00000000000000000000000000000010";
            }else if(flag=='D'){
                mask = "00000000000000000000000000000001";
            }else if(flag=='Q'){
                mask = "00000000000000000000000000001010";
            }
            else{
                mask = "00000000000000000000000000000000";
            }
            int value = new BigInteger(mask, 2).intValue();
            length = value | shiftedLength;

            //print("LENGTH FIELD:   " + Integer.toBinaryString(length));
            //  convert to byte array
            //int i =0;
            this.checksum = computeChecksum();
            byte[] checksumBB = ByteBuffer.allocate(4).putInt(this.checksum).array();
            byte[] dataLength = ByteBuffer.allocate(4).putInt(length).array();
           // byte[] type = ByteBuffer.allocate(2).putChar(this.flag).array();
            byte[] data =null;
            int lengthDataSegment=0;
            if(this.dataSeg!=null) {
                data = ByteBuffer.allocate(this.dataSeg.length).put(this.dataSeg).array();
                lengthDataSegment = this.dataSeg.length;
            }
            byte [] timeStampBB = ByteBuffer.allocate(8).putLong(this.timeStamp).array();


            // cumulative packet containing all data
            ByteBuffer packet = ByteBuffer.allocate(4 + 4 + 8 + 4 + 4 + lengthDataSegment);
            packet.put(seqNumberBb);
            packet.put(ackNumBB);
            packet.put(timeStampBB);
            packet.put(dataLength);
            packet.put(checksumBB);
            //packet.put(type);
            if (this.dataSeg != null) {
                packet.put(data);
            }// handling data
            return packet;
        }

        public void packetString() {
            System.out.println("***************PACKET*******************");
            System.out.println("SEQ NUM: "+this.seqNumber);
            System.out.println("ACK NUM: "+this.ackNum);
            System.out.println("PACKET TYPE: "+this.flag);
            if(this.dataSeg!=null) {
                System.out.println("DATA LENGTH: " + this.dataSeg.length);
            }
            System.out.println("***************PACKET*******************");
        }
    }


    public receiver(int socket_1_destPort, int socket_2_destPort, String pathOfFile){

        /*
         * initialize the respective fields here
         * */
        destPortForTimer = socket_2_destPort;
        int prevSeqNum = -1;
        int lastByteRead =0;
        int nextByteExpected=0;
        boolean endFlag = false;
        s = new Semaphore(1);
        Timer timer2;
        try {
            /*
             *
             * the majority of the code fragment
             *
             * */
            socket_1 = new DatagramSocket(socket_1_destPort); //incoming channel
            socket_2 = new DatagramSocket(); //outgoing channel


            try {
                /*
                * keep a while loop which exits the moment a valid syn is provided from the sender
                * the first packet that the receiver has to send should be a syn packet.
                * */
                byte[] data = new byte[PACKETSIZE];

                DatagramPacket inPkt = new DatagramPacket(data, data.length);

                InetAddress destAddr = InetAddress.getByName("10.0.1.101");

                FileOutputStream fileOutStream = null;


                /*
                *
                *   make potential modifications to the String Path var to accommodate the incoming file
                *
                * */
                File filePath = new File(pathOfFile);
                if (!filePath.exists()) {
                    filePath.mkdir();
                }
                File file = new File(pathOfFile + "/output.txt");
                if (!file.exists()) {
                    file.createNewFile();
                }
                fileOutStream = new FileOutputStream(file);

                boolean ThreeWayContinue = true;
                boolean connectionSetupFlag = false;
                boolean transferComplete = false;
                int counterForThreeWay = 16;
                while (ThreeWayContinue) {
                    System.out.println("ENTERS THE HANDSHAKE WHILE");

                    --counterForThreeWay;

                    socket_1.receive(inPkt);

                    Packet ourPacketFormat = new Packet();

                    ourPacketFormat.generatePacketFromDatagramPacket(data);

                    ourPacketFormat.packetString();

                    //System.out.println("is Valid SYN  = " + ourPacketFormat.isValidSyn());
                    if (ourPacketFormat.isValidSyn()) {

                        ThreeWayContinue=false;
                        boolean lastAck = false;

                        while (!lastAck) {


                            Packet synack = new Packet(0,null, 'Q', 1);
                            //synack.packetString();
                            ByteBuffer synackPkt = synack.createPacket();


                            socket_2.send(new DatagramPacket(synackPkt.array(),
                                    synackPkt.array().length,
                                    destAddr,
                                    socket_2_destPort));


                            System.out.println("SENT A SYN + ACK");

                            // now the second packet that the receiver has to receive is an ack
                            /*
                            *
                            * in future a time has to set to check if the hasn't received yet..
                            * if the ack doesn't come in time then the syn + ack has to transmitted again
                            *
                            * */

                            socket_1.receive(inPkt);

                            Packet ackPacket = new Packet();
                            ackPacket.generatePacketFromDatagramPacket(data);
                            /*print("rcv " + ackPacket.timeStamp + " " + ackPacket.flag + " " +
                                    ackPacket.seqNumber + " " + "0" + " "
                                    + ackPacket.ackNum);*/
                            ackPacket.packetString();
                            if (ackPacket.isValidAck()) {
                                setTimer(false);
                                System.out.println("RECEIVED THE EXPECTED ACK!");
                                lastAck = true;
                                connectionSetupFlag = true;
                            }

                        }
                    } else if (counterForThreeWay >= 0) {

                        continue;

                    }else{

                        ThreeWayContinue = false;
                        System.out.println("DID NOT receive the first Syn in the handshake");
                        System.exit(-1);

                    }

                }

                if (connectionSetupFlag) {
                    /*
                    * TODO: special mechanism for cumulative ack [do not need this would make sender more complex]
                    *
                    * */
                    while (!transferComplete) {


                            
                            socket_1.receive(inPkt);
                            lastByteRead++;
                            Packet dataPacket = new Packet();
                            dataPacket.generatePacketFromDatagramPacket(data);


                            /*
                             *
                             * follow the steps from the first major comment
                             *
                             *
                             * */
                            print("DATA PACKET RECEIVED");
                            if (dataPacket.isValidPacket()) {

                                /*
                                 * check if the it is the right order
                                 * in other words is it the packet the receiver is expecting
                                 * */
                                if (dataPacket.seqNumber == nextByteExpected) {
                                    /*
                                     * if final packet then issue teardown
                                     * */
                                    /*
                                     *
                                     * assumption is being made prob:4-66
                                     * with regard to the length of packet
                                     * */
                                    if (inPkt.getLength() == 24 && dataPacket.flag == 'F') {
                                        /*
                                         * prob: 5-66 [args problem]
                                         * */
                                        Packet tearDown = new Packet(0, null, 'F', -2);
                                        ByteBuffer finPkt = tearDown.createPacket();

                                        /*
                                         * assumption that teardown is indicated by -2 check sender and agree with this convention
                                         * prob: 6-66
                                         * */
                                        socket_2.send(new DatagramPacket(finPkt.array(), finPkt.array().length, destAddr, socket_2_destPort));

                                        transferComplete = true;
                                        System.out.println("closing connection phase has been reached");
                                        continue;
                                    } // time for normal ACK
                                    else {

                                        /*
                                         * [args problem] prob: 7-66
                                         * */
                                        Packet normalAck = new Packet(0, null, 'A', dataPacket.seqNumber);

                                        ByteBuffer normalAckPkt = normalAck.createPacket();
                                        normalAck.packetString();

                                        socket_2.send(new DatagramPacket(normalAckPkt.array(), normalAckPkt.array().length, destAddr, socket_2_destPort));

                                        System.out.println("sent a normal ack with ack #" + dataPacket.seqNumber);
                                    }

                                    fileOutStream.write(data, 24, inPkt.getLength() - 24);
                                    ++nextByteExpected;
                                    //print("seqNUMBER CONTAINED IN THE DATA PACKET RECEIVED " + dataPacket.seqNumber);



                                } else {
                                    /*
                                     * send a dup ack as fast retransmit is enabled for this mode.
                                     * */
                                    /*
                                     * prob: not all args present for the time being 2-66
                                     * an Assumption is also being made with regard to nextseqNum prob: 3-66
                                     * */
                                    Packet dupAck = new Packet(0, null, 'A', nextByteExpected);
                                    print("*********************DUP ACK SENT*************************");
                                    print("***********************************************************");
                                    print("***************THE REQUESTED PACKET # " + nextByteExpected);
                                    print("***********************************************************");
                                    print("*********************DUP ACK SENT*************************");                                    print("*********************DUP ACK SENT*************************");
                                    dupAck.packetString();
                                    print("***********************************************************");
                                    print("*********************DUP ACK SENT*************************");
                                    ByteBuffer dupAckPkt = dupAck.createPacket();
                                    socket_2.send(new DatagramPacket(dupAckPkt.array(), dupAckPkt.array().length, destAddr, socket_2_destPort));

                                    System.out.println("dup ack sent ack # " + nextByteExpected);
                                }

                            } else {
                                System.out.println("in-valid checksum");

                                Packet dupAck = new Packet(0, null, 'A', nextByteExpected);
                                print("*********************DUP ACK SENT*************************");
                                dupAck.packetString();
                                print("*********************DUP ACK SENT*************************");
                                ByteBuffer dupAckPkt = dupAck.createPacket();
                                socket_2.send(new DatagramPacket(dupAckPkt.array(), dupAckPkt.array().length, destAddr, socket_2_destPort));


                            }


                    }
                    /*
                    * close the file system stream
                    * */
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }finally {
                /*
                * close the sockets
                * */
                System.out.println("the connection is closed");

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }


    }

    /*
    *
    * the main method which will make an instance of the receiver class and lead to the
    * triggering of the receiver class constructor
    *
    * */

    public static void main(String args[]) {
        if (args.length != 3) {
            System.err.println("Usage: java Receiver sk2_dst_port, sk3_dst_port, outputFolderPath");
            System.exit(-1);
        }
        else new receiver(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2]);
    }

}

