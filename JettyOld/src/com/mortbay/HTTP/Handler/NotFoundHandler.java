// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package com.mortbay.HTTP.Handler;
import com.mortbay.Base.*;
import com.mortbay.HTTP.*;
import java.io.*;
import java.util.*;
import javax.servlet.http.*;
import javax.servlet.*;


/* --------------------------------------------------------------------- */
/** NotFound Handler
 * <p>This handler is a terminating handler of a handler stack.
 * Any request passed to this handler receives a SC_NOT_FOUND
 * response.
 *
 * @see Interface.HttpHandler
 * @version $Id$
 * @author Greg Wilkins
 */
public class NotFoundHandler extends NullHandler
{
    /* ----------------------------------------------------------------- */
    public void handle(HttpRequest request,
			 HttpResponse response)
	 throws Exception
    {
	response.setContentType("text/html");
	response.setStatus(response.SC_NOT_FOUND,"Not Found");
	
	PrintWriter out = new PrintWriter(response.getOutputStream());
	out.println("<HTML>\n<HEAD><TITLE>Not Found</TITLE>");
	out.println("<BODY>\n<H2>Error: "+
			HttpResponse.SC_NOT_FOUND +
			" Not Found</H2><br><br>");		
	out.println("</BODY>\n</HTML>");
	out.flush();
    }    
}


