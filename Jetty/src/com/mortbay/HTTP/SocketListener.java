// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package com.mortbay.HTTP;
import com.mortbay.Util.*;

import java.io.*;
import java.net.*;
import java.util.*;

/* ------------------------------------------------------------ */
/** Socket HTTP Listener.
 *
 * Temparary implementation, just to get the ball rolling.
 * 
 * @version 1.0 Fri Oct  8 1999
 * @author Greg Wilkins (gregw)
 */
public class SocketListener 
    extends ThreadedServer
    implements HttpListener
{
    /* ------------------------------------------------------------------- */
    private HttpServer _server;
    
    /* ------------------------------------------------------------------- */
    SocketListener(HttpServer server, InetAddrPort address)
        throws IOException
    {
	// XXX NEED TO CONFIGURE THREAD LIMITS AND TIMEOUTS
        super(address,3,255,30000,5000);
        _server=server;
    }

    /* ------------------------------------------------------------ */
    public HttpServer getServer()
    {
        return _server;
    }
    
    /* --------------------------------------------------------------- */
    public String getDefaultScheme()
    {
        return "http";
    }

    /* --------------------------------------------------------------- */
    public String getHost()
    {
        return super.getInetAddress().getHostName();
    }
    
    /* --------------------------------------------------------------- */
    public int getPort()
    {
        return super.getPort();
    }

    /* --------------------------------------------------------------- */
    public void start()
    {
        super.start();
        Log.event("Started SocketListener on "+getInetAddrPort());
    }
    
    /* --------------------------------------------------------------- */
    public void stop()
        throws InterruptedException
    {
        Log.event("Stopping SocketListener on "+getInetAddrPort());
        super.stop();
    }
    
    /* --------------------------------------------------------------- */
    public void destroy()
    {
        Log.event("Destroy SocketListener on "+getInetAddrPort());
        super.destroy();
    }
    
    /* ------------------------------------------------------------ */
    /** Handle Job.
     * Implementation of ThreadPool.handle(), calls handleConnection.
     * @param job A Connection.
     */
    public final void handleConnection(Socket socket)
    {
        try
        {
            Code.debug("ACCEPT:",socket);
            HttpConnection connection =
                new HttpConnection(this,socket.getInetAddress(),
				   socket.getInputStream(),
				   socket.getOutputStream());
            connection.handle();
        }
        catch ( Exception e ){
            Code.warning("Connection problem",e);
        }
        finally
        {
	    Code.debug("CLOSE: "+socket);
            try {socket.close();}
            catch ( Exception e ){Code.warning("Connection problem",e);}
            socket=null;
        }
    }
}






