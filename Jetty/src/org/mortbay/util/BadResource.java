// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------
package org.mortbay.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;


/* ------------------------------------------------------------ */
/** Bad Resource.
 *
 * A Resource that is returned for a bade URL.  Acts as a resource
 * that does not exist and throws appropriate exceptions.
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
class BadResource extends Resource
{
    /* ------------------------------------------------------------ */
    private String _message=null;
        
    /* -------------------------------------------------------- */
    BadResource(URL url,  String message)
    {
        super(url,null);
        _message=message;
    }
    

    /* -------------------------------------------------------- */
    public boolean exists()
    {
        return false;
    }
        
    /* -------------------------------------------------------- */
    public long lastModified()
    {
        return -1;
    }

    /* -------------------------------------------------------- */
    public boolean isDirectory()
    {
        return false;
    }

    /* --------------------------------------------------------- */
    public long length()
    {
        return -1;
    }
        
        
    /* ------------------------------------------------------------ */
    public File getFile()
    {
        return null;
    }
        
    /* --------------------------------------------------------- */
    public InputStream getInputStream() throws IOException
    {
        throw new FileNotFoundException(_message);
    }
        
    /* --------------------------------------------------------- */
    public OutputStream getOutputStream()
        throws java.io.IOException, SecurityException
    {
        throw new FileNotFoundException(_message);
    }
        
    /* --------------------------------------------------------- */
    public boolean delete()
        throws SecurityException
    {
        throw new SecurityException(_message);
    }

    /* --------------------------------------------------------- */
    public boolean renameTo( Resource dest)
        throws SecurityException
    {
        throw new SecurityException(_message);
    }

    /* --------------------------------------------------------- */
    public String[] list()
    {
        return null;
    }

    /* ------------------------------------------------------------ */
    public String toString()
    {
        return super.toString()+"; BadResource: "+_message;
    }
    
}