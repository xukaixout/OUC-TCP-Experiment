package com.ouc.tcp.test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.ouc.tcp.client.TCP_Sender_ADT;
import com.ouc.tcp.client.UDT_Timer;
import com.ouc.tcp.message.TCP_PACKET;

public class TCP_Sender extends TCP_Sender_ADT {

	private TCP_PACKET tcpPack;
	private TCP_PACKET rcvPack;
	private UDT_Timer timer;
	private volatile int nextSequence;
	private volatile short cwnd = 1;
	private short cwnd_cnt = 0;
	private short ssthresh = 16;
	private Queue<TCP_PACKET> pkt_queue;
	private volatile int queueHead;
	private int ACKcount;
	private RenoTimer task;
	private int taskTimeLen = 300;

	public TCP_Sender() {
		super();
		super.initTCP_Sender(this);
		timer = new UDT_Timer();
		pkt_queue = new LinkedBlockingQueue<TCP_PACKET>();
		nextSequence = 1;
		queueHead = 1;
		ACKcount = 0;
	}

	@Override
	public void rdt_send(int dataIndex, int[] appData) {
		while (nextSequence >= queueHead + cwnd)
			;
		tcpS.setData(appData);
		tcpPack = new TCP_PACKET(tcpH, tcpS, destinAddr);
		tcpH.setTh_seq(nextSequence);
		tcpH.setTh_sum(CheckSum.computeChkSum(tcpPack));
		tcpPack.setTcpH(tcpH);
		try {
			if (pkt_queue.size() < cwnd)
				pkt_queue.offer(tcpPack.clone());
			else
				System.out.println("Queue is full!");
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		udt_send(tcpPack);
		if (queueHead == nextSequence) {
			task = new RenoTimer(this);
			timer.schedule(task, taskTimeLen, taskTimeLen);
		}
		nextSequence++;
	}

	@Override
	public void recv(TCP_PACKET recvPack) {
		System.out.println("Receive ACK Number: " + recvPack.getTcpH().getTh_ack());
		if (CheckSum.computeChkSum(recvPack) == recvPack.getTcpH().getTh_sum()) {
			rcvPack = recvPack;
			waitACK();
		} else {
			System.out.println("ACK Checksum Error!");
		}
	}

	@Override
	public void udt_send(TCP_PACKET stcpPack) {
		tcpH.setTh_eflag((byte) 7);
		client.send(stcpPack);
	}

	@Override
	public void waitACK() {
		int nowACK = rcvPack.getTcpH().getTh_ack();
		if (nowACK == queueHead - 1) {
			if (++ACKcount == 4) {
				TCP_PACKET pkt = pkt_queue.peek();
				System.out.println("3-ACK, resending pkt: " + pkt.getTcpH().getTh_seq());
				udt_send(pkt);
				cwnd = (short) (cwnd / 2);
				cwnd_cnt = 0;
				ssthresh = (short) Math.max(ssthresh / 2, 2);
			}
		} else if (nowACK >= queueHead) {
			for (int i = 0; i < nowACK - queueHead + 1; ++i)
				pkt_queue.poll();
			ACKcount = 1;
			queueHead = nowACK + 1;
			ackQueue.add(rcvPack.getTcpH().getTh_ack());
			task.cancel();
			if (queueHead != nextSequence) {
				task = new RenoTimer(this);
				timer.schedule(task, taskTimeLen, taskTimeLen);
			}
			if (cwnd >= ssthresh) {
				cwnd_cnt++;
				if (cwnd_cnt == cwnd) {
					cwnd++;
					cwnd_cnt = 0;
				}
			} else {
				cwnd++;
			}
		} else {
			System.out.println("Outdated ACK received!");
		}
	}

	public void resend() {
		ssthresh = (short) (cwnd / 2);
		cwnd = 1;
		if (pkt_queue.size() > 0)
			udt_send(pkt_queue.peek());
	}

}