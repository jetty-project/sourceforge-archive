// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.mortbay.webapps.jetty;
import org.mortbay.util.Code;
import org.mortbay.util.Log;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public  class DemoFilter implements Filter
{
    public void init(FilterConfig filterConfig)
        throws ServletException
    {
        Code.debug("init:",filterConfig);
    }
    
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
	throws IOException, ServletException
    {
        Code.debug("doFilter:",request);
        chain.doFilter(request, response);
    }

    public void destroy()
    {
        Code.debug("destroy");
    }
}
