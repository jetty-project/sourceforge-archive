/**
 * 
 */
package org.mortbay.jetty.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;

/**
 * Scanner
 * 
 * Scans a list of files and directories on a periodic basis to detect changes.
 * If a change in any of the watched files is found, then the target LifeCycle
 * objects are stopped and restarted.
 * 
 *  This is used by the Jetty Maven plugin to watch the classes, dependencies 
 *  and web.xml file of a web application and to restart the webapp if any
 *  of the above changes.
 *  
 * @author janb
 *
 */
public class Scanner extends Thread
{
	
	private int scanInterval;
	
	private List roots;

	private Map scanInfo = Collections.EMPTY_MAP;
	
	private List listeners;
	
	private Log log;
	
	
	public interface Listener
	{
		public void changeDetected();
	}
	
	
	public Scanner ()
	{	
	}
	
	
	/**
	 * The files and directory roots to watch. Directories will be
	 * recursively scanned.
	 * 
	 * @return Returns the roots.
	 */
	public List getRoots()
	{
		return this.roots;
	}

	/**
	 * @param roots The roots to set.
	 */
	public void setRoots(List roots)
	{
		this.roots = roots;
	}

	/**
	 * 
	 * @return Returns the scanInterval.
	 */
	public int getScanInterval()
	{
		return this.scanInterval;
	}

	/**
	 * 
	 * @param scanInterval The scanInterval in seconds to set.
	 */
	public void setScanInterval(int scanInterval)
	{
		this.scanInterval = scanInterval;
	}
	

	/**
	 * List of Scanner.Listener implementations.
	 * @return Returns the listeners.
	 */
	public List getListeners()
	{
		return this.listeners;
	}


	/**
	 * @param listeners The listeners to set.
	 */
	public void setListeners(List listeners)
	{
		this.listeners = listeners;
	}
	
    /**
     * Log file to use for debug/info/error messages.
     * @param log The Maven plugin Log implementation.
     */
    public void setLog (Log log)
    {
        this.log = log;       
    }
    
    public Log getLog ()
    {
        return this.log;
    }
    
	
	/**
	 * Loop every scanInterval seconds until interrupted, checking to see if
	 * any of the watched files have changed. If they have, stop and restart
	 * the LifeCycle targets.
	 *  
	 * @see java.lang.Runnable#run()
	 */
	public void run ()
	{
		//do preliminary scan to get last modified times
		scanInfo = scan ();
		
		//sleep for our interval
		long sleepMillis = getScanInterval()*1000L;
		boolean running = true;
		while (running)
		{
			try
			{
				//wake up and scan the files
				Thread.currentThread().sleep(sleepMillis);
				Map latestScanInfo = scan();
				
				if (!latestScanInfo.equals(scanInfo))
				{
					if ((getListeners() != null) && (!getListeners().isEmpty()))
					{
						try
						{
							getLog().info("Calling scanner listeners ...");
							
							for (int i=0; i<getListeners().size();i++)
								((Scanner.Listener)getListeners().get(i)).changeDetected();
							
							getLog().info("Listeners completed.");
						}
						catch (Exception e)
						{
							log.error("Error doing stop/start", e);
						}
					}
				}				
				scanInfo = latestScanInfo;
			}
			catch (InterruptedException e)
			{
				running = false;	
			}
		}
	}
	
	
	
	
	/**
	 * Scan the files and directories.
	 * 
	 * @return
	 */
	private Map scan ()
	{
		getLog().debug("Recursively scanning roots ...");
		List roots = getRoots();
		if ((roots == null) || (roots.isEmpty()))
			return Collections.EMPTY_MAP;
		
		HashMap scanInfoMap = new HashMap();	
		Iterator itor = roots.iterator();
		while (itor.hasNext())
		{
			File f = (File)itor.next();
			scan (f, scanInfoMap);
		}
		
		if  (getLog().isDebugEnabled())
		{
			itor = scanInfo.entrySet().iterator();
			while (itor.hasNext())
			{
				Map.Entry e = (Map.Entry)itor.next();
				getLog().debug("Scanned "+e.getKey()+" : "+e.getValue());
			}
		}
		
		getLog().debug("Scan complete.");
		return scanInfoMap;
	}
	
	
	/**
	 * Scan the file, or recurse into it if it is a directory.
	 * @param f
	 * @param scanInfoMap
	 */
	private void scan (File f, Map scanInfoMap)
	{
		try
		{
			if (f.isFile())
			{
				String name = f.getCanonicalPath();
				long lastModified = f.lastModified();
				scanInfoMap.put(name, new Long(lastModified));
			}
			else
			{
				File[] files = f.listFiles();
				for (int i=0;i<files.length;i++)
					scan(files[i], scanInfoMap);
			}
		}
		catch (IOException e)
		{
			getLog().error("Error scanning watched files", e);
		}
	}


	
}