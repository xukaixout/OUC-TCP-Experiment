package com.ouc.tcp.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.TCP_Receiver_ADT;
import com.ouc.tcp.message.*;

public class TCP_Receiver extends TCP_Receiver_ADT {

	private TCP_PACKET ackPack; // 回复的ACK报文段
	private ReceiverSlidingWindow window = new ReceiverSlidingWindow();

	/* 构造函数 */
	public TCP_Receiver() {
		super(); // 调用超类构造函数
		super.initTCP_Receiver(this); // 初始化TCP接收端
	}

	@Override
	// 接收到数据报：检查校验和，设置回复的ACK报文段
	public void rdt_recv(TCP_PACKET recvPack) {
		// 检查校验码，生成ACK
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			int ACKSequence = -1;
			try {
				ACKSequence = window.recv_pkt(recvPack.clone());
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			if (ACKSequence != -1) {
				tcpH.setTh_ack(ACKSequence * 100 + 1);
				ackPack = new TCP_PACKET(tcpH, tcpS, recvPack.getSourceAddr());
				tcpH.setTh_sum(CheckSum.computeChkSum(ackPack));
				reply(ackPack);
			}
		}
		System.out.println();

	}

	@Override
	// 交付数据（将数据写入文件）；不需要修改
	public void deliver_data() {
	}

	@Override
	// 回复ACK报文段
	public void reply(TCP_PACKET replyPack) {
		// 设置错误控制标志
		tcpH.setTh_eflag((byte) 7); // eFlag=0，信道无错误

		// 发送数据报
		client.send(replyPack);
	}

}

class ReceiverSlidingWindow {
	private int size = 16;
	private int base = 0;
	private TCP_PACKET[] packets = new TCP_PACKET[size];
	Queue<int[]> dataQueue = new LinkedBlockingQueue();

	public ReceiverSlidingWindow() { }

	public int recv_pkt(TCP_PACKET packet) {
		int currentSequence = (packet.getTcpH().getTh_seq() - 1) / 100;
		if (currentSequence < base) {
			int l = base - size, r = base - 1;
			l = Math.max(l, 1);
			if (l <= currentSequence && currentSequence <= r)
				return currentSequence;
		} else if (base <= currentSequence && currentSequence < base + size) {
			packets[currentSequence - base] = packet;
			if (currentSequence == base)
				slid_window();
			return currentSequence;
		}
		return -1;
	}

	private void slid_window() {
		int maxIndex = 0;
		while (maxIndex + 1 < size && packets[maxIndex + 1] != null)
			maxIndex++;
		for (int i = 0; i < maxIndex + 1; i++)
			dataQueue.add(packets[i].getTcpS().getData());
		for (int i = 0; maxIndex + 1 + i < this.size; i++)
			packets[i] = packets[maxIndex + 1 + i];
		for (int i = size - maxIndex - 1; i < size; i++)
			packets[i] = null;
		base += maxIndex + 1;
		if (dataQueue.size() >= 20 || base == 1000)
			deliver_data();
	}

	public void deliver_data() {
		try {
			File file = new File("recvData.txt");
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

			while (!dataQueue.isEmpty()) {
				int[] data = dataQueue.poll();

				// 将数据写入文件
				for (int i = 0; i < data.length; i++) {
					writer.write(data[i] + "\n");
				}
				writer.flush(); // 清空输出缓存
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}