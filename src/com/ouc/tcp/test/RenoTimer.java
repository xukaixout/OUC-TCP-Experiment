package com.ouc.tcp.test;

import java.util.TimerTask;

public class RenoTimer extends TimerTask {

    private TCP_Sender client;

    public RenoTimer(TCP_Sender cli) {
        super();
        client = cli;
    }

    @Override
    public void run() {
        System.out.println("Packet Time Out!");
        client.resend();
    }

}