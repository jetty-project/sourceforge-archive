// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.http;
import java.io.IOException;
import java.net.Socket;
import org.mortbay.util.Code;
import org.mortbay.util.InetAddrPort;
import org.mortbay.util.Log;
import org.mortbay.util.ThreadPool;
import org.mortbay.util.ThreadedServer;


/* ------------------------------------------------------------ */
/** Socket HTTP Listener.
 * The behaviour of the listener can be controlled with the
 * attributues of the ThreadedServer and ThreadPool from which it is
 * derived. Specifically: <PRE>
 * MinThreads    - Minumum threads waiting to service requests.
 * MaxThread     - Maximum thread that will service requests.
 * MaxIdleTimeMs - Time for an idle thread to wait for a request.
 * MaxReadTimeMs - Time that a read on a request can block.
 * LowResourcePersistTimeMs - time in ms that connections will persist if listener is
 *                            low on resources. 
 * </PRE>
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public class SocketListener
    extends ThreadedServer
    implements HttpListener
{
    /* ------------------------------------------------------------------- */
    private HttpServer _server;
    private int _lowResourcePersistTimeMs=2000;
    private int _throttled=0;
    private boolean _lastLow=false;
    private boolean _lastOut=false;
    private String _scheme=HttpMessage.__SCHEME;
    
    /* ------------------------------------------------------------------- */
    public SocketListener()
    {}

    /* ------------------------------------------------------------------- */
    public SocketListener(InetAddrPort address)
        throws IOException
    {
        super(address);
    }

    /* ------------------------------------------------------------ */
    public void setHttpServer(HttpServer server)
    {
        Code.assertTrue(_server==null || _server==server,
                        "Cannot share listeners");
        _server=server;
    }

    /* ------------------------------------------------------------ */
    public HttpServer getHttpServer()
    {
        return _server;
    }

    /* --------------------------------------------------------------- */
    public void setDefaultScheme(String scheme)
    {
        _scheme=scheme;
    }
    
    /* --------------------------------------------------------------- */
    public String getDefaultScheme()
    {
        return _scheme;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return time in ms that connections will persist if listener is
     * low on resources.
     */
    public int getLowResourcePersistTimeMs()
    {
        return _lowResourcePersistTimeMs;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @param ms time in ms that connections will persist if listener is
     * low on resources. 
     */
    public void setLowResourcePersistTimeMs(int ms)
    {
        _lowResourcePersistTimeMs=ms;
    }
    
    /* --------------------------------------------------------------- */
    public void start()
        throws Exception
    {
        super.start();
        Log.event("Started SocketListener on "+getInetAddrPort());
    }

    /* --------------------------------------------------------------- */
    public void stop()
        throws InterruptedException
    {
        super.stop();
        Log.event("Stopped SocketListener on "+getInetAddrPort());
    }

    /* ------------------------------------------------------------ */
    /** Handle Job.
     * Implementation of ThreadPool.handle(), calls handleConnection.
     * @param job A Connection.
     */
    public void handleConnection(Socket socket)
        throws IOException
    {
        socket.setTcpNoDelay(true);
        
        HttpConnection connection =
            new HttpConnection(this,
                               socket.getInetAddress(),
                               socket.getInputStream(),
                               socket.getOutputStream(),
                               socket);
        
        try
        {
            if (_lowResourcePersistTimeMs>0 && isLowOnResources())
            {
                _throttled++;
                socket.setSoTimeout(_lowResourcePersistTimeMs);
            }
            else
                socket.setSoTimeout(getMaxIdleTimeMs());
        }
        catch(Exception e)
        {
            Code.warning(e);
        }
        connection.handle();
    }

    /* ------------------------------------------------------------ */
    /** Customize the request from connection.
     * This method extracts the socket from the connection and calls
     * the customizeRequest(Socket,HttpRequest) method.
     * @param request
     */
    public void customizeRequest(HttpConnection connection,
                                 HttpRequest request)
    {
        Socket socket=(Socket)(connection.getConnection());
        customizeRequest(socket,request);
    }

    /* ------------------------------------------------------------ */
    /** Customize request from socket.
     * Derived versions of SocketListener may specialize this method
     * to customize the request with attributes of the socket used (eg
     * SSL session ids).
     * This version resets the SoTimeout if it has been reduced due to
     * low resources.  Derived implementations should call
     * super.customizeRequest(socket,request) unless persistConnection
     * has also been overridden and not called.
     * @param request
     */
    protected void customizeRequest(Socket socket,
                                    HttpRequest request)
    {
        try
        {
            if (socket.getSoTimeout()!=getMaxReadTimeMs())
            {
                if (_throttled>0)
                    _throttled--;
                socket.setSoTimeout(getMaxReadTimeMs());
            }
        }
        catch(Exception e)
        {
            Code.ignore(e);
        }
    }

    /* ------------------------------------------------------------ */
    /** Persist the connection.
     * If the listener is low on resources, the connection read
     * timeout is set to lowResourcePersistTimeMs.  The
     * customizeRequest method is used to reset this to the normal
     * value after a request has been read.
     * @param connection.
     */
    public void persistConnection(HttpConnection connection)
    {
        try
        {
            if (_lowResourcePersistTimeMs>0 && isLowOnResources())
            {
                _throttled++;
                Socket socket=(Socket)(connection.getConnection());
                socket.setSoTimeout(_lowResourcePersistTimeMs);
            }
            else
            {
                Socket socket=(Socket)(connection.getConnection());
                socket.setSoTimeout(getMaxIdleTimeMs());
            }
        }
        catch(Exception e)
        {
            Code.ignore(e);
        }
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return True if low on idle threads. 
     */
    public boolean isLowOnResources()
    {
        boolean low =
            getThreads()==getMaxThreads() &&
            getIdleThreads()<getMinThreads();
        if (low && !_lastLow)
            Log.event("LOW ON THREADS: "+this);
        else if (!low && _lastLow)
        {
            Log.event("OK on threads: "+this);
            _lastOut=false;
        }
        _lastLow=low;
        return low;
    }

    /* ------------------------------------------------------------ */
    /** 
     * @return True if out of resources. 
     */
    public boolean isOutOfResources()
    {
        boolean out =
            getThreads()==getMaxThreads() &&
            getIdleThreads()==0;
        if (out && !_lastOut)
            Code.warning("OUT OF THREADS: "+this);
            
        _lastOut=out;
        return out;
    }
    
}






