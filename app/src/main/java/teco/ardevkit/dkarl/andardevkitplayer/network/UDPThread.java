package teco.ardevkit.dkarl.andardevkitplayer.network;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import teco.ardevkit.dkarl.andardevkitplayer.ARELViewActivity;
import teco.ardevkit.dkarl.andardevkitplayer.R;

/**
 * Created by dkarl on 29.01.15.
 */
public class UDPThread extends Thread {
    private DatagramSocket udpSocket;
    private DatagramPacket incoming;
    private int port = ARELViewActivity.getContext().getResources().getInteger(R.integer.networkPort);
    private byte[] buffer = new byte[65536];
    public volatile boolean running = true;
    private String logTag = "ardevkit-udp";

    public UDPThread() {
        super();
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
        if (udpSocket!=null)
            udpSocket.close();
    }

    @Override
    public void run() {
        while (running) {
            try {
                udpSocket.receive(incoming);
                byte[] data = incoming.getData();
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
