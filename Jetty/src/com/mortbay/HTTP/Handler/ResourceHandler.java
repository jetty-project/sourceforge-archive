package com.mortbay.HTTP.Handler;

import com.mortbay.HTTP.ChunkableOutputStream;
import com.mortbay.HTTP.HandlerContext;
import com.mortbay.HTTP.HttpException;
import com.mortbay.HTTP.HttpFields;
import com.mortbay.HTTP.HttpRequest;
import com.mortbay.HTTP.HttpResponse;
import com.mortbay.HTTP.PathMap;
import com.mortbay.Util.Code;
import com.mortbay.Util.Log;
import com.mortbay.Util.Resource;
import com.mortbay.Util.StringUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* ------------------------------------------------------------ */
/** Resource Handler
 *
 * Serves files from a given resource URL base and implements
 * the GET, HEAD, DELETE, OPTIONS, MOVE methods and the
 * IfModifiedSince and IfUnmodifiedSince header fields.
 * A simple memory cache is also provided to reduce file I/O.
 * 
 * @version $Id$
 * @author Nuno Pregui�a 
 * @author Greg Wilkins
 */
public class ResourceHandler extends NullHandler
{
    /* ----------------------------------------------------------------- */
    private String _allowHeader = null;
    private CachedFile[] _cache=null;
    private int _nextIn=0;
    private Map _cacheMap=null;
    private boolean _dirAllowed =true;
    private boolean _putAllowed =false;
    private boolean _delAllowed=false;
    private int _maxCachedFiles =64;
    private int _maxCachedFileSize =40960;
    private Resource _resourceBase=null;
 
    /* ------------------------------------------------------------ */
    List _indexFiles =new ArrayList(2);
    {
        _indexFiles.add("index.html");
        _indexFiles.add("index.htm");
    }
 
    /* ------------------------------------------------------------ */
    public boolean isDirAllowed()
    {
        return _dirAllowed;
    }
    /* ------------------------------------------------------------ */
    public void setDirAllowed(boolean dirAllowed)
    {
        _dirAllowed = dirAllowed;
    }
 
    /* ------------------------------------------------------------ */
    public boolean isPutAllowed()
    {
        return _putAllowed;
    }
    /* ------------------------------------------------------------ */
    public void setPutAllowed(boolean putAllowed)
    {
        _putAllowed = putAllowed;
    }

    /* ------------------------------------------------------------ */
    public boolean isDelAllowed()
    {
        return _delAllowed;
    }
    /* ------------------------------------------------------------ */
    public void setDelAllowed(boolean delAllowed)
    {
        _delAllowed = delAllowed;
    }
 
    /* ------------------------------------------------------------ */
    public List getIndexFiles()
    {
        return _indexFiles;
    }
 
    /* ------------------------------------------------------------ */
    public void setIndexFiles(List indexFiles)
    {
        if (indexFiles==null)
            _indexFiles=new ArrayList(5);
        else
            _indexFiles = indexFiles;
    }
 
    /* ------------------------------------------------------------ */
    public void addIndexFile(String indexFile)
    {
        _indexFiles.add(indexFile);
    }
 
    /* ------------------------------------------------------------ */
    public int getMaxCachedFiles()
    {
        return _maxCachedFiles;
    }

    /* ------------------------------------------------------------ */
    public void setMaxCachedFiles(int maxCachedFiles_)
    {
        _maxCachedFiles = maxCachedFiles_;
    }
 
    /* ------------------------------------------------------------ */
    public int getMaxCachedFileSize()
    {
        return _maxCachedFileSize;
    }
 
    /* ------------------------------------------------------------ */
    public void setMaxCachedFileSize(int maxCachedFileSize)
    {
        _maxCachedFileSize = maxCachedFileSize;
    }


    /* ----------------------------------------------------------------- */
    /** Construct a ResourceHandler.
     */
    public ResourceHandler()
    {}

 
    /* ----------------------------------------------------------------- */
    public void start()
    {
        try
        {
            _resourceBase=getHandlerContext().getResourceBase();

            Log.event("ResourceHandler started in "+ _resourceBase);
            if (_maxCachedFiles>0 && _maxCachedFileSize>0 && _cache==null)
            {
                _cache=new CachedFile[_maxCachedFiles];
                _cacheMap=new HashMap();
            }
            super.start();
        }
        catch(Exception e)
        {
            Code.warning("XXX",e);
            throw new Error(e.toString());
        }
    }
 
    /* ----------------------------------------------------------------- */
    public void stop()
    {
        super.stop();
    }
 
    /* ----------------------------------------------------------------- */
    public void destroy()
    {
        _cache=null;
        if( _cacheMap != null)
            _cacheMap.clear();
        super.destroy();
    }

    /* ------------------------------------------------------------ */
    /** Translate path to a real file path.
     * @param pathSpec 
     * @param path 
     * @return 
     */
    private Resource makeresource(String pathSpec,String path)
        throws MalformedURLException,IOException
    {
        Resource resourceBase=getHandlerContext().getResourceBase();
        if (resourceBase==null)
            return null;
        String info=PathMap.pathInfo(pathSpec,path);
        if (info==null)
            info=path;
        
        return resourceBase.addPath(info);
    }
 
    /* ------------------------------------------------------------ */
    public void handle(String pathInContext,
                       HttpRequest request,
                       HttpResponse response)
        throws HttpException, IOException
    {
        // Extract and check filename
        if (pathInContext.indexOf("..")>=0)
            throw new HttpException(HttpResponse.__403_Forbidden);
        
        Resource resourceBase=getHandlerContext().getResourceBase();
        if (resourceBase==null)
            return;
        
        Resource resource = resourceBase.addPath(pathInContext);
        
        try
        {
            Code.debug("\nPATH=",pathInContext,
                       "\nRESOURCE=",resource);
            
            // check filename
            boolean endsWithSlash= resource.toString().endsWith("/");
            
            String method=request.getMethod();
            if (method.equals(HttpRequest.__GET) ||
                method.equals(HttpRequest.__HEAD))
                handleGet(request, response, pathInContext, resource, endsWithSlash);  
            else if (method.equals(HttpRequest.__PUT))
                handlePut(request, response, pathInContext, resource);
            else if (method.equals(HttpRequest.__DELETE))
                handleDelete(request, response, pathInContext, resource);
            else if (method.equals(HttpRequest.__OPTIONS))
                handleOptions(response);
            else if (method.equals(HttpRequest.__MOVE))
                handleMove(request, response, pathInContext, resource);
            else
            {
                Code.debug("Unknown action:"+method);
                // anything else...
                try{
                    if (resource.exists())
                        response.sendError(response.__501_Not_Implemented);
                }
                catch(Exception e) {Code.ignore(e);}
            }
        }
        finally
        {
            if (resource!=null)
                resource.release();
        }
    }

    /* ------------------------------------------------------------------- */
    void handleGet(HttpRequest request,
                   HttpResponse response,
                   String path,
                   Resource resource,
                   boolean endsWithSlash)
        throws IOException
    {
        Code.debug("Looking for ",resource);
  
        // Try a cache lookup
        if (_cache!=null && !endsWithSlash)
        {
            byte[] bytes=null;
            synchronized(_cacheMap)
            {
                CachedFile cachedFile=
                    (CachedFile)_cacheMap.get(resource.toString());
                if (cachedFile!=null &&cachedFile.isValid())
                {
                    if (!checkGetHeader(request,response,cachedFile.resource))
                        return;
                    bytes=cachedFile.prepare(response);
                }
            }
            if (bytes!=null)
            {
                Code.debug("Cache hit: "+resource);
                OutputStream out = response.getOutputStream();
                out.write(bytes);
                out.flush();
                return;
            }
        }  
 
        if (resource!=null && resource.exists())
        {
            // Check modified dates
            if (!checkGetHeader(request,response,resource))
                return;
     
            // check if directory
            if (resource.isDirectory())
            {
                if (!endsWithSlash && !path.equals("/"))
                {
                    Code.debug("Redirect to directory/");
                    
                    String q=request.getQuery();
                    StringBuffer buf=request.getRequestURL();
                    buf.append("/");
                    if (q!=null&&q.length()!=0)
                        buf.append("?"+q);
                    response.setField(HttpFields.__Location, buf.toString());
                    response.sendError(301,"Moved Permanently");
                    return;
                }
  
                // See if index file exists
                boolean indexSent=false;
                for (int i=_indexFiles.size();i-->0;)
                {
                    Resource index =
                        resource.addPath((String)_indexFiles.get(i));
      
                    if (index.exists())
                    {
                        // Redirect to the index
                        StringBuffer url=request.getRequestURL();
                        url.append(_indexFiles.get(i));
                        response.sendRedirect(url.toString());
                        indexSent=true;
                        break;
                    }
                }
  
                if (!indexSent)
                    sendDirectory(request,response,resource,
                                  path.length()>1);
            }
            // check if it is a file
            else if (resource.exists())
            {
                if (!endsWithSlash)
                    sendFile(request,response,resource);
            }
            else
                // don't know what it is
                Code.warning("Unknown file type");
        }
    }

 
    /* ------------------------------------------------------------ */
    /* Check modification date headers.
     */
    private boolean checkGetHeader(HttpRequest request,
                                   HttpResponse response,
                                   Resource file)
        throws IOException
    {
        if (!request.getMethod().equals(HttpRequest.__HEAD))
        {
            // check any modified headers.
            long date=0;
            if ((date=request.getDateField(HttpFields.__IfModifiedSince))>0)
            {
                if (file.lastModified() <= date)
                {
                    response.sendError(response.__304_Not_Modified);
                    return false;
                }
            }
   
            if ((date=request.getDateField(HttpFields.__IfUnmodifiedSince))>0)
            {
                if (file.lastModified() > date)
                {
                    response.sendError(response.__412_Precondition_Failed);
                    return false;
                }
            }
        }
        return true;
    }
 
 
    /* ------------------------------------------------------------ */
    void handlePut(HttpRequest request,
                   HttpResponse response,
                   String path,
                   Resource resource)
        throws IOException
    {
        Code.debug("PUT ",path," in ",resource);

        if (!_putAllowed)
            return;
  
        try
        {
            int toRead = request.getIntField(HttpFields.__ContentLength);
            InputStream in = request.getInputStream();
     
            OutputStream fos = resource.getOutputStream();
            final int bufSize = 1024;
            byte bytes[] = new byte[bufSize];
            int read;
            Code.debug(HttpFields.__ContentLength+"="+toRead);
            while (toRead > 0 &&
                   (read = in.read(bytes, 0,
                                   (toRead>bufSize?bufSize:toRead))) > 0)
            {
                toRead -= read;
                fos.write(bytes, 0, read);
                if (Code.debug())
                    Code.debug("Read " + read + "bytes: " + bytes);
            }
            in.close();
            fos.close();
            request.setHandled(true);
            response.sendError(response.__204_No_Content);
        }
        catch (SecurityException sex)
        {
            Code.warning(sex);
            response.sendError(response.__403_Forbidden,
                               sex.getMessage());
        }
        catch (Exception ex)
        {
            Code.warning(ex);
        }
    }

    /* ------------------------------------------------------------ */
    void handleDelete(HttpRequest request,
                      HttpResponse response,
                      String path,
                      Resource resource)
        throws IOException
    {
        Code.debug("DELETE ",path," from ",resource);  
  
 
        if (!resource.exists())
            return;
 
        if (!_delAllowed)
        {
            setAllowHeader(response);
            response.sendError(response.__405_Method_Not_Allowed);
            return;
        }
 
        try
        {
            // delete the file
            resource.delete();

            // flush the cache
            if (_cacheMap!=null)
            {
                CachedFile cachedFile=(CachedFile)_cacheMap.get(resource.toString());
                if (cachedFile!=null)
                    cachedFile.flush();
            }

            // Send response
            request.setHandled(true);
            response.sendError(response.__204_No_Content);
        }
        catch (SecurityException sex)
        {
            Code.warning(sex);
            response.sendError(response.__403_Forbidden, sex.getMessage());
        }
    }

 
    /* ------------------------------------------------------------ */
    void handleMove(HttpRequest request,
                    HttpResponse response,
                    String pathInContext,
                    Resource resource)
        throws IOException
    {
        if (!_delAllowed || !_putAllowed)
            return;  
 
        if (!resource.exists())
        {
            if (_delAllowed && _putAllowed)
                response.sendError(response.__404_Not_Found);
            return;
        }

        if (!_delAllowed || !_putAllowed)
        {
            setAllowHeader(response);
            response.sendError(response.__405_Method_Not_Allowed);
            return;
        }
 
        String newPath = request.getField("New-uri");
        String contextPath = getHandlerContext().getContextPath();
        if (newPath.indexOf("..")>=0)
        {
            response.sendError(response.__405_Method_Not_Allowed,
                               "File contains ..");
            return;
        }

        if (contextPath!=null && !newPath.startsWith(contextPath))
        {
            response.sendError(response.__405_Method_Not_Allowed,
                               "Not in context");
            return;
        }
        

        // Find path
        try
        {
            // XXX - Check this
            String newInfo=newPath;
            if (contextPath!=null)
                newInfo=newInfo.substring(contextPath.length());
            Resource newFile = _resourceBase.addPath(newInfo);
     
            Code.debug("Moving "+resource+" to "+newFile);
            resource.renameTo(newFile);
    
            request.setHandled(true);
            response.sendError(response.__204_No_Content);
        }
        catch (Exception ex)
        {
            Code.warning(ex);
            setAllowHeader(response);
            response.sendError(response.__405_Method_Not_Allowed,
                               "Error:"+ex);
            return;
        }
    }
 
    /* ------------------------------------------------------------ */
    void handleOptions(HttpResponse response)
        throws IOException
    {
        setAllowHeader(response);
        response.commit();
    }
 
    /* ------------------------------------------------------------ */
    void setAllowHeader(HttpResponse response)
    {
        if (_allowHeader == null)
        {
            StringBuffer sb = new StringBuffer(128);
            sb.append(HttpRequest.__GET);
            sb.append(", ");
            sb.append(HttpRequest.__HEAD);
            if (_putAllowed){
                sb.append(", ");
                sb.append(HttpRequest.__PUT);
            }
            if (_delAllowed){
                sb.append(", ");
                sb.append(HttpRequest.__DELETE);
            }
            if (_putAllowed && _delAllowed)
            {
                sb.append(", ");
                sb.append(HttpRequest.__MOVE);
            }
            sb.append(", ");
            sb.append(HttpRequest.__OPTIONS);
            _allowHeader = sb.toString();
        }
        response.setField(HttpFields.__Allow, _allowHeader);
    }
 
    /* ------------------------------------------------------------ */
    void sendFile(HttpRequest request,
                  HttpResponse response,
                  Resource resource)
        throws IOException
    {
        Code.debug("sendFile: ",resource);

        // Can the file be cached?
        if (_cache!=null && resource.length()>0 &&
            resource.length()<_maxCachedFileSize)
        {
            byte[] bytes=null;
            synchronized (_cacheMap)
            {
                CachedFile cachedFile=_cache[_nextIn];
                if (cachedFile==null)
                    cachedFile=_cache[_nextIn]=new CachedFile();
                _nextIn=(_nextIn+1)%_cache.length;
                cachedFile.flush();
                cachedFile.load(resource);
                _cacheMap.put(resource.toString(),cachedFile);
                bytes=cachedFile.prepare(response);
            }
            if (bytes!=null)
            {
                OutputStream out = response.getOutputStream();
                out.write(bytes);
                request.setHandled(true);
                return;
            }
        }
        else
        {
            InputStream in=null;
            int len=0;
            String encoding=getHandlerContext().getMimeByExtension(resource.getName());
            response.setField(HttpFields.__ContentType,encoding);
            len = (int)resource.length();
            response.setIntField(HttpFields.__ContentLength,len);
     
            response.setDateField(HttpFields.__LastModified,
                                  resource.lastModified());
            in = resource.getInputStream();
     
            try
            {
                response.getOutputStream().write(in,len);
            }
            finally
            {
                request.setHandled(true);
                in.close();
            }
        }
    }


    /* ------------------------------------------------------------------- */
    void sendDirectory(HttpRequest request,
                       HttpResponse response,
                       Resource file,
                       boolean parent)
        throws IOException
    {
        if (_dirAllowed)
        {
            String[] ls = file.list();
            if (ls==null)
            {
                // Just send it as a file and hope that the URL
                // formats the directory
                try{
                    sendFile(request,response,file);
                }
                catch(IOException e)
                {
                    Code.ignore(e);
                    response.sendError(HttpResponse.__403_Forbidden,
                                       "Invalid directory");
                }
                return;
            }

            Code.debug("sendDirectory: "+file);
            String base = request.getPath();
            if (!base.endsWith("/"))
                base+="/";
     
            response.setField(HttpFields.__ContentType,
                              "text/html");
            if (request.getMethod().equals(HttpRequest.__HEAD))
            {
                // Bail out here otherwise we build the page fruitlessly and get
                // hit with a HeadException when we try to write the page...
                response.commit();
                return;
            }
     
            String title = "Directory: "+base;
     
            ChunkableOutputStream out=response.getOutputStream();
     
            out.print("<HTML><HEAD><TITLE>");
            out.print(title);
            out.print("</TITLE></HEAD><BODY>\n<H1>");
            out.print(title);
            out.print("</H1><TABLE BORDER=0>");
     
            if (parent)
            {
                out.print("<TR><TD><A HREF=");
                out.print(padSpaces(base));
                out.print("../>Parent Directory</A></TD><TD></TD><TD></TD></TR>\n");
            }
     
            DateFormat dfmt=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                                                           DateFormat.MEDIUM);
            for (int i=0 ; i< ls.length ; i++)
            {
                Resource item = file.addPath(ls[i]);
  
                out.print("<TR><TD><A HREF=\"");
                String path=base+ls[i];
                if (item.isDirectory() && !path.endsWith("/"))
                    path+="/";
                out.print(padSpaces(path));
                out.print("\">");
                out.print(ls[i]);
                out.print("&nbsp;");
                out.print("</TD><TD ALIGN=right>");
                out.print(""+item.length());
                out.print(" bytes&nbsp;</TD><TD>");
                out.print(dfmt.format(new Date(item.lastModified())));
                out.print("</TD></TR>\n");
            }
            out.println("</TABLE>");
            out.flush();
            request.setHandled(true);
        }
        else
        {
            // directory request not allowed
            response.sendError(HttpResponse.__403_Forbidden,
                               "Directory access not allowed");
        }
    }


 
    /* ------------------------------------------------------------ */
    /**
     * Replaces spaces by %20
     */
    private String padSpaces(String str)
    {
        return StringUtil.replace(str," ","%20");
    }
 

 
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /** Holds a cached file.
     * It is assumed that threads accessing CachedFile have
     * the parents cacheMap locked. 
     */
    private class CachedFile
    {
        Resource resource;
        long lastModified;
        byte[] bytes;
        String encoding;

        /* ------------------------------------------------------------ */
        boolean isValid()
            throws IOException
        {
            if (resource==null)
                return false;

            // check if the file is still there
            if (!resource.exists())
            {
                flush();
                return false;
            }

            // check if the file has changed
            if(lastModified!=resource.lastModified())
            {
                // If it is too big
                if (resource.length()>_maxCachedFileSize)
                {
                    flush();
                    return false;
                }

                // reload the changed file
                load(resource);
            }
 
            return true;
        }
  
        /* ------------------------------------------------------------ */
        byte[] prepare(HttpResponse response)
            throws IOException
        {
            Code.debug("HIT: ",resource);
            response.setField(HttpFields.__ContentType,encoding);
            response.setIntField(HttpFields.__ContentLength,bytes.length);
            response.setDateField(HttpFields.__LastModified,lastModified);
            return bytes;
        }

        /* ------------------------------------------------------------ */
        void load(Resource resource)
            throws IOException
        {
            this.resource=resource;
            lastModified=resource.lastModified();
            bytes = new byte[(int)resource.length()];
            Code.debug("LOAD: ",resource);
     
            InputStream in=resource.getInputStream();
            int read=0;
            while (read<bytes.length)
            {
                int len=in.read(bytes,read,bytes.length-read);
                if (len==-1)
                    throw new IOException("Unexpected EOF: "+resource);
                read+=len;
            }
            in.close();
            encoding=getHandlerContext().getMimeByExtension(resource.getName());
        }
  
        /* ------------------------------------------------------------ */
        void flush()
        {
            if (resource!=null)
            {
                Code.debug("FLUSH: ",resource);
                _cacheMap.remove(resource.toString());
                resource=null;
            }
        }
    }
}



