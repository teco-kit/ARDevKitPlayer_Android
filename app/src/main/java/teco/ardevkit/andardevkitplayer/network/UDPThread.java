package teco.ardevkit.andardevkitplayer.network;

import android.app.Activity;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import teco.ardevkit.andardevkitplayer.PausableARELActivity;
import teco.ardevkit.andardevkitplayer.R;

/**
 * Created by dkarl on 29.01.15.
 */
public class UDPThread extends Thread {
    private DatagramSocket udpSocket;
    private DatagramPacket incoming;
    private int port;
    private byte[] buffer = new byte[65536];
    public volatile boolean running = true;
    private String logTag = "ardevkit-udp";
    private Activity parent;

    public UDPThread(PausableARELActivity parent) {
        super();
        this.parent = parent;
        this.port = parent.getResources().getInteger(R.integer.networkPort);
        Log.d(logTag, "UDP thread created");
        try {
            udpSocket = new DatagramSocket(port);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        incoming = new DatagramPacket(buffer, buffer.length);
    }

    @Override
    public void interrupt() {
        super.interrupt();
        if (udpSocket != null)
            udpSocket.close();
    }

    @Override
    public void run() {
        while (running) {
            try {
                udpSocket.receive(incoming);
                String s = new String("OK: " + port);
                //TODO: LOG incoming packet
                DatagramPacket dp = new DatagramPacket(s.getBytes(), s.getBytes().length,
                        incoming.getAddress(), incoming.getPort());
                udpSocket.send(dp);
                //   Thread.sleep(100);
            } catch (IOException e) {
                e.printStackTrace();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
        }
        Log.d(logTag, "UDP thread closed");
        udpSocket.close();
    }
}
