package com.ouc.tcp.test;

import com.ouc.tcp.client.Client;
import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_RetransTask;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.*;

public class TCP_Sender extends TCP_Sender_ADT {

	private TCP_PACKET tcpPack; // 待发送的TCP数据报
	private volatile int flag = 1;

	private SenderSlidingWindow window = new SenderSlidingWindow(client);

	/* 构造函数 */
	public TCP_Sender() {
		super(); // 调用超类构造函数
		super.initTCP_Sender(this); // 初始化TCP发送端
	}

	@Override
	// 可靠发送（应用层调用）：封装应用层数据，产生TCP数据报；需要修改
	public void rdt_send(int dataIndex, int[] appData) {

		// 生成TCP数据报（设置序号和数据字段/校验和),注意打包的顺序
		tcpH.setTh_seq(dataIndex * appData.length + 1);// 包序号设置为字节流号：
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);

		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		if (window.isFull()) {
			System.out.println();
			System.out.println("Waiting for Sliding Window...");
			System.out.println();
			flag = 0;
		}
		while (flag == 0)
			;
		try {
			window.insert_pkt(tcpPack.clone());
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		// 发送TCP数据报
		udt_send(tcpPack);
	}

	@Override
	// 不可靠发送：将打包好的TCP数据报通过不可靠传输信道发送；仅需修改错误标志
	public void udt_send(TCP_PACKET stcpPack) {
		// 设置错误控制标志
		tcpH.setTh_eflag((byte) 7);
		// System.out.println("to send: "+stcpPack.getTcpH().getTh_seq());
		// 发送数据报
		client.send(stcpPack);
	}

	@Override
	// 需要修改
	public void waitACK() {
	}

	@Override
	// 接收到ACK报文：检查校验和，将确认号插入ack队列;NACK的确认号为－1；不需要修改
	public void recv(TCP_PACKET recvPack) {
		System.out.println();
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			System.out.println("Receive ACK Number： " + recvPack.getTcpH().getTh_ack());
			window.recv_ACK((recvPack.getTcpH().getTh_ack() - 1) / 100);
			if (!window.isFull())
				flag = 1;
		}
		// 处理ACK报文
		waitACK();
	}

}

class SenderSlidingWindow {
	private Client client;
	private int size = 16;
	private int base = 0;
	private int nextIndex = 0;
	private TCP_PACKET[] packets = new TCP_PACKET[size];
	private UDT_Timer[] timers = new UDT_Timer[size];

	public SenderSlidingWindow(Client cli) {
		client = cli;
	}

	public boolean isFull() {
		return size <= nextIndex;
	}

	public void insert_pkt(TCP_PACKET packet) {
		packets[nextIndex] = packet;
		timers[nextIndex] = new UDT_Timer();
		timers[nextIndex].schedule(new UDT_RetransTask(client, packet), 500, 500);
		nextIndex++;
	}

	public void recv_ACK(int currentSequence) {
		if (base <= currentSequence && currentSequence < base + size) {
			if (timers[currentSequence - base] == null)
				return;
			timers[currentSequence - base].cancel();
			timers[currentSequence - base] = null;
		}
		if (currentSequence == base) {
			int maxACKedIndex = 0;
			while (maxACKedIndex + 1 < nextIndex && timers[maxACKedIndex + 1] == null)
				maxACKedIndex++;

			for (int i = 0; maxACKedIndex + 1 + i < size; i++) {
				packets[i] = packets[maxACKedIndex + 1 + i];
				timers[i] = timers[maxACKedIndex + 1 + i];
			}

			for (int i = size - (maxACKedIndex + 1); i < size; i++) {
				packets[i] = null;
				timers[i] = null;
			}

			base += maxACKedIndex + 1;
			nextIndex -= maxACKedIndex + 1;
		}
	}
}