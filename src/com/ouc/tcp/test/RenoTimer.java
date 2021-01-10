package com.ouc.tcp.test;

import java.util.Queue;
import java.util.TimerTask;

import com.ouc.tcp.message.TCP_PACKET;

public class RenoTimer extends TimerTask {

    private TCP_Sender client;
    private Queue<TCP_PACKET> dataQueue;

    public RenoTimer(TCP_Sender cli, Queue<TCP_PACKET> pkts) {
        super();
        client = cli;
        dataQueue = pkts;
    }

    @Override
    public void run() {
        System.out.println("Packet Time Out!");
        client.resend(dataQueue.peek());
    }
    
}