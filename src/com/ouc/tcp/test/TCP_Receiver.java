package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.TCP_PACKET;

public class TCP_Receiver extends TCP_Receiver_ADT {

	private TCP_PACKET ackPack;
	int nextSequence;
	Queue<Integer> sequenceQueue;

	public TCP_Receiver() {
		super();
		super.initTCP_Receiver(this);
		sequenceQueue = new PriorityBlockingQueue<Integer>();
		nextSequence = 1;
	}

	@Override
	public void deliver_data() {
		File fw = new File("recvData.txt");
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(fw, true));
			while (!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();
				for (int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				writer.flush();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void rdt_recv(TCP_PACKET recvPack) {
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			int sequence = recvPack.getTcpH().getTh_seq();
			System.out.println("Receive seq: " + sequence);
			if (sequence < nextSequence) {
				tcpH.setTh_ack(nextSequence - 1);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				reply(ackPack);
			} else {
				recvBuffer.put(sequence, recvPack.getTcpS().getData());
				sequenceQueue.offer(sequence);
				while (sequenceQueue.size() > 0 && sequenceQueue.peek() == nextSequence) {
					while (sequenceQueue.size() > 0 && sequenceQueue.peek() == nextSequence) {
						sequenceQueue.poll();
					}
					dataQueue.add(recvBuffer.get(nextSequence));
					nextSequence++;
				}
				deliver_data();
				tcpH.setTh_ack(nextSequence - 1);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				reply(ackPack);
			}
		} else {
			System.out.println("pkt CheckSum Error!");
		}

	}

	@Override
	public void reply(TCP_PACKET replyPack) {
		tcpH.setTh_eflag((byte) 7);
		client.send(replyPack);
	}

}