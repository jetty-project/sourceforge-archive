// ========================================================================
// $Id$
// Copyright 2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.http.bio;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

import junit.framework.TestCase;

/**
 * @author gregw
 *
 */
public class HttpServerTest extends TestCase
{
    /**
     * Constructor for HttpServerTest.
     * @param arg0
     */
    public HttpServerTest(String arg0)
    {
        super(arg0);
    }

    public void testRun() throws Exception
    {
        HttpServer server= new HttpServer();
        server.listener.setPort(8123);
        server.listener.setDaemon(true);
        server.listener.start();

        try
        {

            Thread.sleep(1000);

            Socket socket= new Socket(InetAddress.getLocalHost(), 8123);
            PrintWriter out= new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            InputStreamReader in= new InputStreamReader(socket.getInputStream());

            out.print(
                "GET /r1 HTTP/1.1\r\n"
                    + "Host: localhost:8123\r\n"
                    + "\r\n"
                    + "POST /r2 HTTP/1.0\r\n"
                    + "Connection: Keep-Alive\r\n"
                    + "Content-Length: 10\r\n"
                    + "\r\n"
                    + "12345678\r\n"
                    + "GET /r3\r\n");
            out.flush();

            Thread.sleep(1000);
            char[] buf= new char[8192];
            int l= in.read(buf, 0, 8192);
            String result= new String(buf, 0, l);

            int offset= 0;
            offset= result.indexOf("HTTP/1.1 200 OK", offset);
            assertTrue(offset >= 0);
            offset= result.indexOf("Content-Type: text/html", offset);
            assertTrue(offset > 0);
            offset= result.indexOf("Content-Length:", offset);
            assertTrue(offset > 0);
            offset= result.indexOf("GET /r1 HTTP/1.1", offset);
            assertTrue(offset > 0);
            offset= result.indexOf("HTTP/1.1 200 OK", offset);
            assertTrue(offset > 0);
            result.indexOf("Content-Type: text/html", offset);
            assertTrue(offset > 0);
            result.indexOf("Connection: Keep-Alive", offset);
            assertTrue(offset > 0);
            offset= result.indexOf("POST /r2 HTTP/1.0", offset);
            assertTrue(offset > 0);
            offset= result.indexOf("GET /r3", offset);
            assertTrue(offset > 0);

        }
        finally
        {
            server.listener.stop();
        }

    }

}
