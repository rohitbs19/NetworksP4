//import com.sun.corba.se.impl.encoding.ByteBufferWithInfo;
import java.util.concurrent.TimeUnit;
import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;


public class HostA{



    //static int data_size = 988;			// (checksum:8, seqNum:4, data<=988) Bytes : 1000 Bytes total
    static int win_size; //= 10;
    static int timeoutVal = 300;		// 120ms until timeout

    int base;					// base sequence number of window
    int nextSeqNum;				// next sequence number in window
    String path;				// path of file to be sent
    String fileName;			// filename to be saved by receiver
    Vector<Packet> packetsList;	// list of generated packets
    Timer timer;				// for timeouts
    Semaphore s;				// guard CS for base, nextSeqNum
    boolean bufferEndLoopExit;// if receiver has completely received the file [INITIALIZE THIS WITH FALSE IN THE START]
    int PACKETSIZE; // init to with mtu in constructor
    int ACKSIZE = 24;
    int numRetransmitions =0;
    int numDataPacketSent =0;
    int dupAckCounter =0;
    int toberetrans = 0;
    /*
     *
     * GLOBALS FOR THE TIMEOUT COMPUTATION
     *
     * */
    double ERTT=0;
    double EEDEV=0;
    double TO=0;
    double SRTT=0;
    double SDEV =0;
    //another class for packet
    /*
    *
    * defunct for the sender
    * */
    public void print(String s) {
        System.out.println(s);
    }
    public void setTimer(boolean isNewTimer){
        if (timer != null) timer.cancel();
        if (isNewTimer){
            timer = new Timer();
            timer.schedule(new Timeout(), timeoutVal);
        }
    }

    public class Timeout extends TimerTask{

        public void run(){
            try{
                s.acquire();
                System.out.println("Sender: Timeout!");
                print("next seq num prior val: " + nextSeqNum);
                print("base val rn: " + base);
		nextSeqNum = base;
		++numRetransmitions;
                s.release();
            } catch(InterruptedException e){
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
            int h=0;
            // If current digit sum is
            // 1 or 3, add 1 to result
            result = (char)(s % 2 + '0') + result;

            // Compute carry
            s /= 2;

            // Move to next digits
            i--;

            j--;
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


    public class Packet{

        //fields
        int seqNumber;
        byte[] dataSeg;
        char flag;
        int ackNum;
        int checksum;
        long timestamp;
        Timer timer;

        //constructor
        public Packet() {

        }
        public Packet(int seqNumber, byte[] dataSeg, char flag, int ackNum) {
            this.seqNumber = seqNumber;
            this.dataSeg = dataSeg;
            this.flag = flag;
            this.ackNum = ackNum;
            this.timestamp = System.nanoTime();
            this.timer = new Timer();

        }
        public void generatePacketFromDatagramPacket(byte[] inData) {
            byte[] sequenceNumber = copyOfRange(inData, 0, 4);
            this.seqNumber = ByteBuffer.wrap(sequenceNumber).getInt();


            byte[] ackNumber = copyOfRange(inData, 4, 8);
            this.ackNum = ByteBuffer.wrap(ackNumber).getInt();
            //print("ackNUM " +this.ackNum);

            byte[] timeStampBB = copyOfRange(inData, 8, 16);
            this.timestamp = ByteBuffer.wrap(timeStampBB).getLong();

            byte[] lengthField = copyOfRange(inData, 16, 20);
            int length = ByteBuffer.wrap(lengthField).getInt();



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
                //print("PACKET TYPE IS --> SYN");
                this.flag = 'S';
            }else if((shiftedLength & ackMaskValue) == 0){
                // print("PACKET TYPE IS --> ACK");
                this.flag = 'A';
            } else if ((shiftedLength & finMaskValue) == 0) {
                //print("PACKET TYPE IS --> FIN");
                this.flag = 'F';
            } else if ((shiftedLength & dMaskValue) == 0) {
                //print("PACKET TYPE IS --> DATA");
                this.flag = 'D';
            } else if ((shiftedLength & synackValue) == 0) {
                //print("PACKET TYPE IS --> SYN + ACK");
                this.flag = 'Q';
            }


            int i=0;

            //System.out.println("INSIDE GENERATEPACKET FROM DATAGRAM FLAG:  " + this.flag);
            byte[] checksumComputed = copyOfRange(inData, 20, 24);
            this.checksum = ByteBuffer.wrap(checksumComputed).getInt();
            byte[] dataField = copyOfRange(inData, 24, inData.length);
            this.dataSeg = dataField;
        }
        public int computeChecksum() {
            String seqNumInBinary = null;
            if(this.flag=='A')
                seqNumInBinary= Integer.toBinaryString(this.ackNum);
            else
                seqNumInBinary = Integer.toBinaryString(this.seqNumber);

            String temp = null;

            if (seqNumInBinary.length() != 32) {
                int h=0;
                int offset = 32 - seqNumInBinary.length();
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
            return Integer.parseInt(addition,2);
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
                int q=0;
                mask = "00000000000000000000000000000000";
            }
            int h=0;
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
            byte[] timeStampBB = ByteBuffer.allocate(8).putLong(this.timestamp).array();
            ByteBuffer packet = null;
            // cumulative packet containing all data
            byte[] fileNameBB = null;
            byte[] BBFileName = null;
            byte[] fileLength = null;
            if (this.flag == 'S') {
                 fileNameBB = fileName.getBytes();
                 BBFileName = ByteBuffer.allocate(fileNameBB.length).put(fileNameBB).array();
                 fileLength = ByteBuffer.allocate(4).putInt(fileNameBB.length).array();

            }
            if(this.flag=='S'){
                packet = ByteBuffer.allocate(4 + 4 + 8 + 4 + 4 + 4 + fileNameBB.length);
            }else {
                 packet = ByteBuffer.allocate(4 + 4 + 8 + 4 + 4 + lengthDataSegment);
            }
            packet.put(seqNumberBb);
            packet.put(ackNumBB);
            packet.put(timeStampBB);
            packet.put(dataLength);
            packet.put(checksumBB);
            if(this.flag=='S') {
                packet.put(fileLength);
                packet.put(BBFileName);
            }
            /*
             *
             * do the timer stuff
             *
             * */
            if(this.flag != 'S' && this.flag!='A' && this.seqNumber != 0) {
                this.timer.schedule(new Timeout(), (long) TO);
               // print("TIMER INSTALLED TO SYN # " + this.seqNumber + " TIMER VAL: " + 1000);
            }
            else {
                this.timer.schedule(new Timeout(), (long) 5000);
                //print("TIMER INSTALLED TO SYN # " + this.seqNumber + " TIMER VAL: 5000");
            }
            /*
             * TIMER STUFF ENDS
             * */
            //packet.put(type);

            if (this.dataSeg != null) {

                packet.put(data);
            }// handling data
            return packet;
        }

        public boolean isSynack() {
            if (this.flag == 'Q' && this.checksum==computeChecksum()) {
                return true;
            }
            return false;
        }

        public byte [] getDataSeg() {

            return this.dataSeg;
        }

        public void setDataSeg(byte [] fileData) {

            this.dataSeg = fileData;
        }

        public Timer getTimer() {

            return this.timer;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public void packetString() {
            System.out.println("***************PACKET1*******************");
            System.out.println("SEQ NUM: "+this.seqNumber);
            System.out.println("ACK NUM: "+this.ackNum);
            System.out.println("PACKET TYPE: "+this.flag);
            if (dataSeg != null) {
                System.out.println("DATA LENGTH: " + this.dataSeg.length);
            }
            System.out.println("***************PACKET1*******************");
        }

    }

    //send thread class

    public class senderThread extends Thread{

        //fields fir sender Thread
        private DatagramSocket out;
        private int dest_port;
        private InetAddress dest_addr;
        private int recv_port;
        private String IP;
        //construct for sender Thread
        public senderThread(DatagramSocket socket_out, int dest_port, int recv_port, String remoteIP) {
            this.out = socket_out;
            this.dest_port = dest_port;
            this.recv_port = recv_port;
            this.IP = remoteIP;
        }

        //generate packet function used as helper function


        // main run method from Thread class
        //override


        public void run() {
            try {
                print("IP value --> " + IP);
                print("dest port--> " + dest_port);

                dest_addr = InetAddress.getByName(IP);
                boolean isTransmitted_handshake = false; // ensures 16 retransmissions, else reports error
                int numRetransmissions = 0;
                FileInputStream fileInStream = new FileInputStream(new File(fileName));

                while (!isTransmitted_handshake && numRetransmissions < 16) {
                    try {

                        // send the first syn packet
                        //first it should send a syn packet to the receiver and wait
                        //Thread.sleep()
                        // if the catch block gets the apt interrupt and this thread wakes up
                        // then send an Ack to the Syn + ACK received in the ReceiveAckThread
                        // once this process ends set the flag for sending data packets to the receiver
                        //this might cause null pointer error
                        Packet pkt_instance = new Packet(0, null, 'S', 0);
                        ByteBuffer syn_pkt = pkt_instance.createPacket();
                        pkt_instance.packetString();
                        /*
                         *
                         * as this is the first ever packet to be sent from the senders side
                         * */
                        TO = 5000;


                        out.send(new DatagramPacket(syn_pkt.array(), syn_pkt.array().length,
                                dest_addr, dest_port));
                        System.out.println("sent the first syn Packet; SYN PACKET #" + pkt_instance.seqNumber);
                        /*print("snd " + pkt_instance.getTimestamp() + " " + pkt_instance.flag + " " +
                                pkt_instance.seqNumber + " " + "0" + " "
                                + pkt_instance.ackNum);*/

                        // THREE-WAY HANDSHAKE
                        try {
                            sleep(5000);
                            numRetransmissions++;

                            // if the thread is interruputed, this means that
                            // we have received an SYN+ACK from the receiver. Hence.
                            // we can continue with normal execution i.e send an ACK
                            // and start transmitting data.

                        } catch (InterruptedException ex) {

                            System.out.println("IS INTERRUPTED");

                            //now that we received the SYN + ACK from the receiver
                            //send an ACK to the Receiver with ACK number = Receive SYN + 1
                            //Here this will be 1 as the Receive SYN =0
                            boolean ackHandShake = false;

                            while (!ackHandShake) {


                                try {

                                    isTransmitted_handshake = true; // successfully transmitted
                                    Packet ackPkt_instance = new Packet(0, null, 'A', 1);
                                    ByteBuffer ackPkt = ackPkt_instance.createPacket();
                                    out.send(new DatagramPacket(ackPkt.array(), ackPkt.array().length, dest_addr, dest_port));
                                    System.out.println("the final ack for the handshake has been sent");
                                   /* print("snd " + ackPkt_instance.getTimestamp() + " " + ackPkt_instance.flag + " " +
                                            ackPkt_instance.seqNumber + " " + "0" + " "
                                            + ackPkt_instance.ackNum);*/
                                    //Thread.sleep(10000);
                                    print("going to sleep");
                                    sleep(10000);
                                    print("going to sleep");
                                    ackHandShake = true;
                                    //Three way handshake done!
                                    bufferEndLoopExit = false;
                                    // now keep a loop which run until the given file is retransmitted
                                    int terminationFlag = 0;    // terminates the loop when reading the file is done!
                                    while (!bufferEndLoopExit) {
                                        // print("NEXTSEQNUM: " + nextSeqNum);

                                        /*
                                         * 1: Till this point the three way handshake is done and the connection is established
                                         * 2: Now, There have to be two conditions:
                                         * 2-1: if --> base (first byte un-acked or the start of the send window/buffer),
                                         * 2-1: base < nextseqnum (this is end pointer of the buffer till where it is populated) then
                                         * aside: the timeout should equal the nextseqnum = base; as the base is first unacked byte
                                         * 2-1: retransmit the packet from the packet list (buffer) at nextseqNum (base)
                                         * 2-2: else that is if the nextSeqNum is > than pkt size
                                         * 2-2: then generate the next slice of the data and send to the receiver.
                                         *
                                         * */
                                        // empty data seg that is to be initialized with required data to be sent
                                        if(nextSeqNum < base + (win_size)) {
                                            s.acquire();


                                            byte[] dataRead = new byte[PACKETSIZE-24];
                                            Packet newDataPktInstance = null;
                                            /*
                                             * NOTE:	that I have changed the packetlist to contain elements of Packet type
                                             * */
                                            if ((nextSeqNum) < packetsList.size()) {
                                  		print("**************RETRANSMITTING SYN #"+ (nextSeqNum) + " ********************"); 
				               if(nextSeqNum>0) {
                                                    newDataPktInstance = packetsList.get(nextSeqNum-1);//-PACKETSIZE);
                                                }else{
                                                    newDataPktInstance = packetsList.get(nextSeqNum );
                                                }
                                                newDataPktInstance.timer = new Timer();


                                                print("RETRANSMITTING PACKET WITH SYN #" + newDataPktInstance.seqNumber);
                                            }
                                            // if normal case and not retransmission
                                            else {
                                                // read the slice of the file (set as 1024 for convenience now but has to be
                                                // changed to MTU and be taken an command line arg)
                                                terminationFlag = fileInStream.read(dataRead);
                                                print("TERMINATIONS FLAG: " + terminationFlag + " AT NEXTSEQNUM " + nextSeqNum);
                                                if (terminationFlag == -1) {

                                                    newDataPktInstance = new Packet(nextSeqNum, new byte[0], 'F', -2);
                                                
						}
                                                //CAUTION flag is 'n'
                                                else {
                                                    newDataPktInstance = new Packet(nextSeqNum, dataRead, 'D', 0);
                                                }// add the new packet to be sent into the unacked packets buffer
                                                packetsList.add(newDataPktInstance);
                                                // this might cause null pointer
                                            }

                                            ByteBuffer normalPkt = newDataPktInstance.createPacket();
                                            newDataPktInstance.packetString();
                                            out.send(new DatagramPacket(normalPkt.array(), normalPkt.array().length, dest_addr, dest_port));

                                            ++numDataPacketSent;

                                            if (!bufferEndLoopExit) {
                                                nextSeqNum = nextSeqNum+1;
                                            }
                                            s.release();

                                        }
                                        print("WAITING FOR TIMEOUT");
                                        Thread.sleep(5);
                                    }

                                } catch (InterruptedException ip) {
                                    /*
                                    *
                                    * MAJOR QUERY WARNING: 1001001
                                    * */
                                    bufferEndLoopExit = true;
                                    print("interrupt OCCURED INDICATING THE ARRIVAL OF RESENT SYN + ACK PACKET");
                                }
                            }
                        }


                    } catch (Exception exc) {
                        exc.printStackTrace();
                    }
                }

            } catch (Exception exc) {
                exc.printStackTrace();
            }

        }

    }


    public char[] convertStringToCharArray(String str) {
        String stri = new String(str);
        char[] returnVal = stri.toCharArray();
        return returnVal;
    }
    public byte[] copyOfRange(byte[] srcArr, int start, int end){
        int length = (end > srcArr.length)? srcArr.length-start: end-start;
        byte[] destArr = new byte[length];
        System.arraycopy(srcArr, start, destArr, 0, length);
        return destArr;
    }



    //receive thread class
    public class receiveAckThread extends Thread{

        //fields
        private DatagramSocket in;
        senderThread ThreadSender;
        //constructor
        public receiveAckThread(DatagramSocket in, senderThread ThreadSender ) {
            this.in = in;
            this.ThreadSender = ThreadSender;

        }


        //generate packet function used as helper function
        // see if you can make use of the existing packet class
        ///actually we cannot and we need to unwrap the byte array we receive from the receiver
        public int ackNumExtract(byte[] receivedPacket) {
            byte[] ackBytes = copyOfRange(receivedPacket, 4, 8);
            return ByteBuffer.wrap(ackBytes).getInt(); // returns -1 if the checksum computation for the received ack fails
            // return -2 if the ack indicated teardown
        }

        //run method override
        public double adaptiveTimeOutCompute(int S, Packet currPacket) {

            if (S == 0) {

                ERTT = 2500;
                EEDEV = 0;
                TO = 2 * ERTT;
                //print("IN THE FORMULA COMPUTATION PART: " + TO);

            }else{
               // print("nano time: --> : " + System.nanoTime() + " timestamp: --> " + currPacket.timestamp);

                SRTT = Math.abs(System.nanoTime() - currPacket.getTimestamp()) + 200000;
               // print("SRTT BEFORE CONVERSION " + SRTT/1000000);

                SRTT = SRTT / 1000000;//TimeUnit.MILLISECONDS.convert((long)SRTT, TimeUnit.NANOSECONDS);
                //print("ERTT --> " + ERTT + " SRTT--> " + SRTT);
                SDEV = Math.abs(SRTT - ERTT);
                ERTT = (0.875*ERTT) + (0.125*SRTT);
                EEDEV = (0.75 * EEDEV) + (0.25 * SDEV);
                TO = ERTT + (4 * SDEV);
                //print("DECIDED/COMPUTED TO VAL: " + TO);

            }

            return TO;

        }
        public void run() {
            try {

                byte[] receivedAckData = new byte[ACKSIZE];
                DatagramPacket receivedPacket = new DatagramPacket(receivedAckData, receivedAckData.length);

                try {

                    while (!bufferEndLoopExit) {
                        in.receive(receivedPacket);
                        Packet receivedAck = new Packet();
                        receivedAck.generatePacketFromDatagramPacket(receivedAckData);
                        print("*****************ACK RECEIVED*********************");
                        receivedAck.packetString();


                        int ackNumberFromPkt = ackNumExtract(receivedAckData);

                        if (receivedAck.isSynack()) {
                            //wake the sleeping thread as the required packet has arrived
                            this.ThreadSender.interrupt();

                        }
                        /*
                         *
                         * 1: check if dup ack
                         * 1-2: if dup ack THEN increment dup ack counter;
                         * 2: if dup counter reached retransmit the packet : that retransmit condition nextseqnum = base;
                         * 3: check if the ack is normal [THIS INCLUDES should take check for teardown signal into consideration]
                         * 3-1: if ack is normal equate the ack = base;
                         * 3-2:
                         *
                         *
                         * */
                        else {
                            if (ackNumberFromPkt != -1) {
                                //dup ack detected
                                if (base == ackNumberFromPkt+1) {

                                    print("DETECTED DUP ACK AT #" + ackNumberFromPkt);
                                    print("PACKET WITH ACKNUMBER MENTIONE ABOVE IS LOST");
                                    //then retransmit condition that is
                                    s.acquire();
                                    packetsList.get(ackNumberFromPkt).getTimer().cancel();
                                    ++dupAckCounter;

                                    if (dupAckCounter >= 4) {
                                        dupAckCounter =0;
					
                                        toberetrans = ackNumberFromPkt;
                                        nextSeqNum = base;
                                    }
                                    ++numRetransmitions;
                                    s.release();
                                } else if (ackNumberFromPkt == -2) {
                                    //probably have to do complete fin shake
                                    bufferEndLoopExit = true;
                                    print("connection closed");

                                    System.exit(0);
                                } else {
                                    base = ackNumberFromPkt +1;
                                    s.acquire();

                                    adaptiveTimeOutCompute(receivedAck.ackNum, receivedAck);
                                    print("PACKET WITH ACK #" + ackNumberFromPkt + " WAS RECEIVED IN THE SENDER");
                                    if (packetsList.get(ackNumberFromPkt) != null) {
                                        packetsList.get(ackNumberFromPkt).getTimer().cancel();
                                    }
                                    s.release();
                                    //refresh the timer for this
                                }

                            }
                        }
                        print("*****************ACK RECEIVED*********************");
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


    }

    //constructor for the main class
    public HostA(int sk4_dst_port, String remoteIP, int sk1_dst_port, String fileName /* only path has to be taken*/
    , int mtu, int cws
    ) {
        base = 0;
        nextSeqNum = 0;

        /*
        *
        * init all the global vars with the passed on args
        *
        * */
        PACKETSIZE = mtu;   //given by the args mentioned through TCPEnd
        win_size = cws; //given by the args mentioned through TCPEnd


        this.fileName = fileName;
        packetsList = new Vector<Packet>(win_size);
        bufferEndLoopExit = false;
        DatagramSocket sk1, sk4;
        s = new Semaphore(1);
        System.out.println("Sender: sk1_dst_port=" + sk1_dst_port +
                ", sk4_dst_port=" + sk4_dst_port  + ", outputFileName=" + fileName);

        try {
            // create sockets
           // sk1 = new DatagramSocket();				// outgoing channel
            sk4 = new DatagramSocket(sk4_dst_port);	// incoming channel

            // create threads to process data

            senderThread th_out = new senderThread(sk4, sk1_dst_port, sk4_dst_port, remoteIP);
            receiveAckThread th_in = new receiveAckThread(sk4, th_out);
            th_in.start();
            th_out.start();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    //main method for the main class
   /* public static void main(String args[]) {
        if (args.length != 4) {
            System.err.println("Usage: java Sender sk1_dst_port, sk4_dst_port, inputFilePath, outputFileName");
            System.exit(-1);
        }
        else new HostA(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2], args[3]);
    }*/


}

