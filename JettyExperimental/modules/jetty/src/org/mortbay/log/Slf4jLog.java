//========================================================================
//$Id$
//Copyright 2004 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.mortbay.log;

import java.lang.reflect.Method;

class Slf4jLog implements Logger
{
    private static final String LOGGER="org.slf4j.Logger";
    private static final String LOGGERFACTORY="org.slf4j.LoggerFactory";
    private Method infoSOO;
    private Method debugSOO;
    private Method debugST;
    private Method debugEnabled;
    private Method warnSOO;
    private Method warnST;
    private Object logger;

    Slf4jLog() throws Exception
    {
        this("org.mortbay.log");
    }
    
    Slf4jLog(String name) throws Exception
    {
        Class slf4j = Thread.currentThread().getContextClassLoader()==null?Class.forName(LOGGER):Thread.currentThread().getContextClassLoader().loadClass(LOGGER);
        infoSOO = slf4j.getMethod("info", new Class[]{String.class,Object.class,Object.class});
        debugSOO = slf4j.getMethod("debug", new Class[]{String.class,Object.class,Object.class});
        debugST = slf4j.getMethod("debug", new Class[]{String.class,Throwable.class});
        debugEnabled = slf4j.getMethod("isDebugEnabled", new Class[]{});
        warnSOO = slf4j.getMethod("warn", new Class[]{String.class,Object.class,Object.class});
        warnST = slf4j.getMethod("warn", new Class[]{String.class,Throwable.class});
        
        Class slf4jf = Thread.currentThread().getContextClassLoader()==null?Class.forName(LOGGERFACTORY):Thread.currentThread().getContextClassLoader().loadClass(LOGGERFACTORY);
        Method getLogger = slf4jf.getMethod("getLogger", new Class[]{String.class});
        logger=getLogger.invoke(null, new Object[]{name});
    }
    
    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doDebug(java.lang.String, java.lang.Object, java.lang.Object)
     */
    public void debug(String msg, Object arg0, Object arg1)
    {
        try{debugSOO.invoke(logger, new Object[]{msg,arg0,arg1});}
        catch (Exception e) {e.printStackTrace();}
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doDebug(java.lang.String, java.lang.Throwable)
     */
    public void debug(String msg, Throwable th)
    {
        try{debugST.invoke(logger, new Object[]{msg,th});}
        catch (Exception e) {e.printStackTrace();}
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doDebugEnabled()
     */
    public boolean isDebugEnabled()
    {
        try{return ((Boolean)debugEnabled.invoke(logger, new Object[]{})).booleanValue();}
        catch (Exception e) {e.printStackTrace();return true;}
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doInfo(java.lang.String, java.lang.Object, java.lang.Object)
     */
    public void info(String msg, Object arg0, Object arg1)
    {
        try{infoSOO.invoke(logger, new Object[]{msg,arg0,arg1});}
        catch (Exception e) {e.printStackTrace();}
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doWarn(java.lang.String, java.lang.Object, java.lang.Object)
     */
    public void warn(String msg, Object arg0, Object arg1)
    {
        try{warnSOO.invoke(logger, new Object[]{msg,arg0,arg1});}
        catch (Exception e) {e.printStackTrace();}
    }

    /* ------------------------------------------------------------ */
    /* 
     * @see org.mortbay.log.Log#doWarn(java.lang.String, java.lang.Throwable)
     */
    public void warn(String msg, Throwable th)
    {
        try{warnST.invoke(logger, new Object[]{msg,th});}
        catch (Exception e) {e.printStackTrace();}
    }

    /* ------------------------------------------------------------ */
    public Logger getLogger(String name)
    {
        try
        {
            return new Slf4jLog(name);
        }
        catch (Exception e)
        {
            Log.warn(e);
            return this;
        }
    }
    
    public String toString()
    {
        return logger.toString();
    }
}