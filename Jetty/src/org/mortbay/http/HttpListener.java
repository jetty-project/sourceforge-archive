// ========================================================================
// Copyright (c) 1999 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.http;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import org.mortbay.util.LifeCycle;


/* ------------------------------------------------------------ */
/** HTTP Listener.
 *
 * @see HttpConnection
 * @version $Id$
 * @author Greg Wilkins (gregw)
 */
public interface HttpListener extends LifeCycle
{
    public static final String ATTRIBUTE="org.mortbay.http.HttpListener";
        
    public abstract void setHttpServer(HttpServer server);
    public abstract HttpServer getHttpServer();
    public abstract void setHost(String host)
        throws UnknownHostException;
    public abstract String getHost();
    public abstract void setPort(int port);
    public abstract int getPort();

    public abstract String getDefaultScheme();
    
    public abstract void customizeRequest(HttpConnection connection,
                                          HttpRequest request);
    public abstract void persistConnection(HttpConnection connection);
    
    public abstract boolean isLowOnResources();
    public abstract boolean isOutOfResources();

    public abstract boolean isIntegral(HttpConnection connection);
    public abstract String getIntegralScheme();
    public abstract int    getIntegralPort();
    
    public abstract boolean isConfidential(HttpConnection connection);
    public abstract String getConfidentialScheme();
    public abstract int    getConfidentialPort();
    
}











