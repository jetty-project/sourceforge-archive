// ========================================================================
// Copyright (c) 2003 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.start;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/*-------------------------------------------*/
/** Monitor thread.
 * This thread listens on the port specified by the STOP.PORT system parameter
 * (defaults to 8079) for request authenticated with the key given by the STOP.KEY
 * system parameter (defaults to "mortbay") for admin requests. Commands "stop" and
 * "status" are currently supported.
 */
public class Monitor extends Thread
{
    private int _port = Integer.getInteger("STOP.PORT",8079).intValue();
    private String _key = System.getProperty("STOP.KEY","mortbay");

    ServerSocket _socket;
    
    Monitor()
    {
        try
        {
            if(_port<0)
                return;
            setDaemon(true);
            _socket=new ServerSocket(_port,1,InetAddress.getByName("127.0.0.1"));
            if (_port==0)
            {
                _port=_socket.getLocalPort();
                System.out.println(_port);
            }
            if (!"mortbay".equals(_key))
            {
                _key=Long.toString((long)(Long.MAX_VALUE*Math.random()),36);
                System.out.println(_key);
            }
        }
        catch(Exception e)
        {
            if (Main._debug)
                e.printStackTrace();
            else
                System.err.println(e.toString());
        }
        if (_socket!=null)
            this.start();
        else
            System.err.println("WARN: Not listening on monitor port: "+_port);
    }
    
    public void run()
    {
        while (true)
        {
            Socket socket=null;
            try{
                socket=_socket.accept();
                
                LineNumberReader lin=
                    new LineNumberReader(new InputStreamReader(socket.getInputStream()));
                String key=lin.readLine();
                if (!_key.equals(key))
                    continue;
                
                String cmd=lin.readLine();
                if (Main._debug) System.err.println("command="+cmd);
                if ("stop".equals(cmd))
                    System.exit(0);
                if ("status".equals(cmd))
                {
                    socket.getOutputStream().write("OK\r\n".getBytes());
                    socket.getOutputStream().flush();
                    }
            }
            catch(Exception e)
            {
                if (Main._debug)
                    e.printStackTrace();
                else
                    System.err.println(e.toString());
            }
            finally
            {
                if (socket!=null)
                {
                    try{socket.close();}catch(Exception e){}
                }
                socket=null;
            }
        }
    }

    /** Start a Monitor.
     * This static method starts a monitor that listens for admin requests.
     */
    public static void monitor()
    {
        new Monitor();
    }
 
}
