// ===========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------
package org.mortbay.util;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.lang.reflect.Method;

/* ------------------------------------------------------------ */
/** Abstract resource class.
 *
 * @version $Id$
 * @author Nuno Pregui�a
 * @author Greg Wilkins (gregw)
 */
public abstract class Resource
{
    Object _associate;
    
    /* ------------------------------------------------------------ */
    public static Resource newResource(URL url)
        throws IOException
    {
        if (url==null)
            return null;

        String urls=url.toExternalForm();
        if( urls.startsWith( "file:"))
        {
            try
            {
                URLConnection connection=url.openConnection();
                Permission perm = connection.getPermission();
                if (!(perm instanceof java.io.FilePermission) && Code.debug())
                    Code.warning("Caution: File resource without FilePermission:"+url);
                
                File file =new File(perm.getName());
                FileResource fileResource= new FileResource(url,connection,file);
                if (fileResource.getAlias()!=null)
                    return fileResource.getAlias();
                return fileResource;
            }
            catch(Exception e)
            {
                Code.debug(e);
                return new BadResource(url,e.toString());
            }
        }
        else if( urls.startsWith( "jar:file:"))
        {
            return new JarFileResource(url);
        }
        else if( urls.startsWith( "jar:"))
        {
            return new JarResource(url);
        }

        return new URLResource(url,null);
    }

    /* ------------------------------------------------------------ */
    /** Construct a resource from a string.
     * @param resource A URL or filename.
     * @return A Resource object.
     */
    public static Resource newResource(String resource)
        throws MalformedURLException, IOException
    {
        URL url=null;
        try
        {
            // Try to format as a URL?
            url = new URL(resource);
        }
        catch(MalformedURLException e)
        {
            if(!resource.startsWith("ftp:") &&
               !resource.startsWith("file:") &&
               !resource.startsWith("jar:"))
            {
                try
                {
                    // It's a file.
                    if (resource.startsWith("./"))
                        resource=resource.substring(2);
                    
                    File file=new File(resource);
                    if (resource.indexOf("..")>=0)
                        file=new File(file.getCanonicalPath());
                    url=file.toURL();
                    URLConnection connection=url.openConnection();
                    FileResource fileResource= new FileResource(url,connection,file);
                    if (fileResource.getAlias()!=null)
                        return fileResource.getAlias();
                    return fileResource;
                }
                catch(Exception e2)
                {
                    Code.debug(e2);
                    throw e;
                }
            }
            else
            {
                Code.warning("Bad Resource: "+resource);
                throw e;
            }
        }

        String nurl=url.toString();
        if (nurl.length()>0 &&
            nurl.charAt(nurl.length()-1)!=
            resource.charAt(resource.length()-1))
        {
            if ((nurl.charAt(nurl.length()-1)!='/' ||
                 nurl.charAt(nurl.length()-2)!=resource.charAt(resource.length()-1))
                &&
                (resource.charAt(resource.length()-1)!='/' ||
                 resource.charAt(resource.length()-2)!=nurl.charAt(nurl.length()-1)
                 ))
            {
                return new BadResource(url,"Trailing special characters stripped by URL in "+resource);
            }
        }
        return newResource(url);
    }

    /* ------------------------------------------------------------ */
    /** Construct a system resource from a string.
     * The resource is tried as classloader resource before being
     * treated as a normal resource.
     */
    public static Resource newSystemResource(String resource)
        throws IOException
    {
        URL url=null;
        // Try to format as a URL?
        ClassLoader
            loader=Thread.currentThread().getContextClassLoader();
        if (loader!=null)
        {
            url=loader.getResource(resource);
            if (url==null && resource.startsWith("/"))
                url=loader.getResource(resource.substring(1));
        }
        if (url==null)
        {
            loader=Resource.class.getClassLoader();
            if (loader!=null)
            {
                url=loader.getResource(resource);
                if (url==null && resource.startsWith("/"))
                    url=loader.getResource(resource.substring(1));
            }
        }
        
        if (url==null)
        {
            url=ClassLoader.getSystemResource(resource);
            if (url==null && resource.startsWith("/"))
                url=loader.getResource(resource.substring(1));
        }
        
        if (url==null)
            return null;
        return newResource(url);
    }

    /* ------------------------------------------------------------ */
    protected void finalize()
    {
        release();
    }

    /* ------------------------------------------------------------ */
    /** Release any resources held by the resource.
     */
    public abstract void release();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns true if the respresened resource exists.
     */
    public abstract boolean exists();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns true if the respresenetd resource is a container/directory.
     * If the resource is not a file, resources ending with "/" are
     * considered directories.
     */
    public abstract boolean isDirectory();

    /* ------------------------------------------------------------ */
    /**
     * Returns the last modified time
     */
    public abstract long lastModified();


    /* ------------------------------------------------------------ */
    /**
     * Return the length of the resource
     */
    public abstract long length();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns an URL representing the given resource
     */
    public abstract URL getURL();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns an File representing the given resource or NULL if this
     * is not possible.
     */
    public abstract File getFile()
        throws IOException;
    

    /* ------------------------------------------------------------ */
    /**
     * Returns the name of the resource
     */
    public abstract String getName();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns an input stream to the resource
     */
    public abstract InputStream getInputStream()
        throws java.io.IOException;

    /* ------------------------------------------------------------ */
    /**
     * Returns an output stream to the resource
     */
    public abstract OutputStream getOutputStream()
        throws java.io.IOException, SecurityException;
    
    /* ------------------------------------------------------------ */
    /**
     * Deletes the given resource
     */
    public abstract boolean delete()
        throws SecurityException;
    
    /* ------------------------------------------------------------ */
    /**
     * Rename the given resource
     */
    public abstract boolean renameTo( Resource dest)
        throws SecurityException;
    
    /* ------------------------------------------------------------ */
    /**
     * Returns a list of resource names contained in the given resource
     */
    public abstract String[] list();
    

    /* ------------------------------------------------------------ */
    /**
     * Returns the resource contained inside the current resource with the
     * given name
     */
    public abstract Resource addPath(String path)
        throws IOException,MalformedURLException;
    

    /* ------------------------------------------------------------ */
    public Object getAssociate()
    {
        return _associate;
    }

    /* ------------------------------------------------------------ */
    public void setAssociate(Object o)
    {
        _associate=o;
    }
    
    /* ------------------------------------------------------------ */
    public CachedResource cache()
        throws IOException
    {
        return new CachedResource(this);
    }
    
}
