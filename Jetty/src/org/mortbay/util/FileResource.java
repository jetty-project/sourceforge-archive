// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------
package org.mortbay.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URISyntaxException;
import java.security.Permission;


/* ------------------------------------------------------------ */
/** File Resource.
 *
 * Handle resources of implied or explicit file type.
 * This class can check for aliasing in the filesystem (eg case
 * insensitivity).  By default this is turned on if the platform does
 * not have the "/" path separator, or it can be controlled with the
 * "org.mortbay.util.FileResource.checkAliases" system parameter.
 *
 * If alias checking is turned on, then aliased resources are
 * treated as if they do not exist, nor can they be created.
 *
 * @version $Revision$
 * @author Greg Wilkins (gregw)
 */
class FileResource extends URLResource
{
    private static boolean __checkAliases;
    static
    {
        __checkAliases=
            "true".equalsIgnoreCase
            (System.getProperty("org.mortbay.util.FileResource.checkAliases","true"));
 
       if (__checkAliases)
            Code.debug("Checking Resource aliases");
    }
    
    /* ------------------------------------------------------------------------------- */
    /** setCheckAliases.
     * @param checkAliases True of resource aliases are to be checked for (eg case insensitivity or 8.3 short names) and treated as not found.
     */
    public static void setCheckAliases(boolean checkAliases)
    {
        __checkAliases=checkAliases;
    }

    /* ------------------------------------------------------------------------------- */
    /** getCheckAliases.
     * @return True of resource aliases are to be checked for (eg case insensitivity or 8.3 short names) and treated as not found.
     */
    public static boolean getCheckAliases()
    {
        return __checkAliases;
    }
    
    /* ------------------------------------------------------------ */
    private File _file;
    private transient URL _alias=null;
    private transient boolean _aliasChecked=false;
    
    /* -------------------------------------------------------- */
    FileResource(URL url)
        throws IOException, URISyntaxException
    {
        super(url,null);

        try
        {
            // Try standard API to convert URL to file.
            _file =new File(new URI(url.toString()));
        }
        catch (Exception e)
        {
            Code.ignore(e);
            try
            {
                // Assume that File.toURL produced unencoded chars. So try
                // encoding them.
                String urls=
                    "file:"+org.mortbay.util.URI.encodePath(url.toString().substring(5));
                _file =new File(new URI(urls));
            }
            catch (Exception e2)
            {
                Code.ignore(e2);

                // Still can't get the file.  Doh! try good old hack!
                checkConnection();
                Permission perm = _connection.getPermission();
                _file = new File(perm==null?url.getFile():perm.getName());
            }
        }
    }
    
    /* -------------------------------------------------------- */
    FileResource(URL url, URLConnection connection, File file)
    {
        super(url,connection);
        _file=file;
    }
    
    /* -------------------------------------------------------- */
    public Resource addPath(String path)
        throws IOException,MalformedURLException
    {
        FileResource r=null;

        if (!isDirectory())
        {
            r=(FileResource)super.addPath(path);
        }
        else
        {
            path = org.mortbay.util.URI.canonicalPath(path);
            
            // treat all paths being added as relative
            if (path.startsWith("/"))
                path = path.substring(1);
            
            File newFile = new File(_file,path);
            
            r=new FileResource(newFile.toURI().toURL(),null,newFile);
        }
                                  
        return r;
    }
   
    
    /* ------------------------------------------------------------ */
    public URL getAlias()
    {
        if (__checkAliases && !_aliasChecked)
        {
            try
            {    
                String abs=_file.getAbsolutePath();
                String can=_file.getCanonicalPath();
                
                if (abs.length()!=can.length() || !abs.equals(can))
                    _alias=new File(can).toURI().toURL();
                
                if (_alias!=null && Code.debug())
                {
                    Code.debug("ALIAS abs=",abs);
                    Code.debug("ALIAS can=",can);
                }
            }
            catch(Exception e)
            {
                Code.warning(e);
                return getURL();
            }                
        }
        return _alias;
    }
    
    /* -------------------------------------------------------- */
    /**
     * Returns true if the resource exists.
     */
    public boolean exists()
    {
        return _file.exists();
    }
        
    /* -------------------------------------------------------- */
    /**
     * Returns the last modified time
     */
    public long lastModified()
    {
        return _file.lastModified();
    }

    /* -------------------------------------------------------- */
    /**
     * Returns true if the respresenetd resource is a container/directory.
     */
    public boolean isDirectory()
    {
        return _file.isDirectory();
    }

    /* --------------------------------------------------------- */
    /**
     * Return the length of the resource
     */
    public long length()
    {
        return _file.length();
    }
        

    /* --------------------------------------------------------- */
    /**
     * Returns the name of the resource
     */
    public String getName()
    {
        return _file.getAbsolutePath();
    }
        
    /* ------------------------------------------------------------ */
    /**
     * Returns an File representing the given resource or NULL if this
     * is not possible.
     */
    public File getFile()
    {
        return _file;
    }
        
    /* --------------------------------------------------------- */
    /**
     * Returns an input stream to the resource
     */
    public InputStream getInputStream() throws IOException
    {
        return new FileInputStream(_file);
    }
        
    /* --------------------------------------------------------- */
    /**
     * Returns an output stream to the resource
     */
    public OutputStream getOutputStream()
        throws java.io.IOException, SecurityException
    {
        return new FileOutputStream(_file);
    }
        
    /* --------------------------------------------------------- */
    /**
     * Deletes the given resource
     */
    public boolean delete()
        throws SecurityException
    {
        return _file.delete();
    }

    /* --------------------------------------------------------- */
    /**
     * Rename the given resource
     */
    public boolean renameTo( Resource dest)
        throws SecurityException
    {
        if( dest instanceof FileResource)
            return _file.renameTo( ((FileResource)dest)._file);
        else
            return false;
    }

    /* --------------------------------------------------------- */
    /**
     * Returns a list of resources contained in the given resource
     */
    public String[] list()
    {
        String[] list =_file.list();
        if (list==null)
            return null;
        for (int i=list.length;i-->0;)
        {
            if (new File(_file,list[i]).isDirectory() &&
                !list[i].endsWith("/"))
                list[i]+="/";
        }
        return list;
    }
         
    /* ------------------------------------------------------------ */
    /** Encode according to this resource type.
     * File URIs are encoded.
     * @param uri URI to encode.
     * @return The uri unchanged.
     */
    public String encode(String uri)
    {
        return uri;
    }
    
    /* ------------------------------------------------------------ */
    /** 
     * @param o
     * @return 
     */
    public boolean equals( Object o)
    {
        if (this == o)
            return true;

        if (null == o || ! (o instanceof FileResource))
            return false;

        FileResource f=(FileResource)o;
        return f._file == _file || (null != _file && _file.equals(f._file));
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the hashcode.
     */
    public int hashCode()
    {
       return null == _file ? super.hashCode() : _file.hashCode();
    }
}
