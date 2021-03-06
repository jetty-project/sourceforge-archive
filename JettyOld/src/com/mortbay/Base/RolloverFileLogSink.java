// ===========================================================================
//	RolloverFileLogSink.java
// ===========================================================================

/*
** (c) Copyright V. Lipovetsky, 1998-2000
** www.fuib.com 
** E-mail: vit@fuib.com, lipov99@yahoo.com
** Modified for use with Jetty by Kent Johnson <KJohnson@transparent.com>
** Multiple log file modification by Jonathon Parker, jparker@transparent.com
*/

package com.mortbay.Base;


/* ------------------------------------------------------------ */
/** Rollover File Log Sink.
 * This implementation of Log Sink writes logs to a file. Files
 * are rolled over every day and old files are deleted.
 *
 * The default constructor looks for these System properties:
 * ROLLOVER_LOG_DIR	    The path to the directory containing the logs
 * ROLLOVER_LOG_RETAIN_DAYS The number of days to retain logs
 * ROLLOVER_LOG_EXTENSION   The file extension for log files
 * ROLLOVER_LOG_STOP_TIMEOUT How long to wait to kill the cleanup thread
 * ROLLOVER_LOG_TIMER_INTERVAL How long the cleanup thread sleeps
 * ROLLOVER_LOG_MULT_DAY If true, Jetty will keep multiple log files for same day,
 *                       if server is halted and restored.  Useful for debugging 
 *                       server crashes.
 *
 * @version 1.0 Tue May 23 2000
 * @author V. Lipovetsky
 * @author Kent Johnson
 */

public class RolloverFileLogSink 
    extends LogSink implements Runnable
{

    // ---------------------------------------------------------------------------
    //		RolloverFileLogSink
    // ---------------------------------------------------------------------------
    public RolloverFileLogSink()
	throws java.io.IOException
    {
    	String logDir = System.getProperty("ROLLOVER_LOG_DIR");
    	if (logDir != null)
	    setLogDir(logDir);
    		
    	Integer retain = Integer.getInteger("ROLLOVER_LOG_RETAIN_DAYS");
    	if (retain != null)
	    setRetainDays(retain.intValue());
    		
    	String extension = System.getProperty("ROLLOVER_LOG_EXTENSION");
    	if (extension != null)
	    setLogExt(extension);
    		
    	Integer stopTimeout = Integer.getInteger("ROLLOVER_LOG_STOP_TIMEOUT");
    	if (stopTimeout != null)
	    setThreadStopTimeout(stopTimeout.intValue());
    		
    	Integer timerInterval = Integer.getInteger("ROLLOVER_LOG_TIMER_INTERVAL");
    	if (timerInterval != null)
	    setTimerInterval(timerInterval.intValue());
    		
	start();
    }


    /* ------------------------------------------------------------ */
    /** Constructor. 
     * @param newLogDir 
     * @param newRetainDays 
     * @param newLogExt 
     * @param newThreadStopTimeout 
     * @param newTimerInterval 
     * @exception java.io.IOException 
     */
    public RolloverFileLogSink(String newLogDir,
			       int newRetainDays , 
			       String newLogExt,
			       long newThreadStopTimeout,
			       long newTimerInterval)
	throws java.io.IOException
    {
    	setLogDir(newLogDir);
    	setRetainDays(newRetainDays);
    	setLogExt(newLogExt);
	setTimerInterval(newTimerInterval);
	setThreadStopTimeout(newThreadStopTimeout);

	start();
    }
	
	
    // ---------------------------------------------------------------------------
    //		start
    // ---------------------------------------------------------------------------
    private void start()
	throws java.io.IOException
    {
	// Set new log file to name of current date
	setLogNameToDate(new java.util.Date());
	clearOldLogFiles(new java.util.Date());
	startClearThread();

      	setCreated(true);
    }//start
    
    
    // ---------------------------------------------------------------------------
    //		stop
    // ---------------------------------------------------------------------------
    public void stop()
    {
    	cleanup();
    }


    // ---------------------------------------------------------------------------
    //		setLogNameToDate
    // ---------------------------------------------------------------------------
    private synchronized void setLogNameToDate(java.util.Date curDate)
    	throws java.io.IOException
    {
	java.io.File newLogFile = 
	    new java.io.File(logDir, fileDateFormat.format(curDate) + logExt);

	//** If new name eq old do nothing
	if (newLogFile.equals(logFile)) return;
            
	// Make sure we start fresh if multDay option not set
	if (!multDay && newLogFile.exists()){
	    newLogFile.delete();
	}
	// Make additional files appended with _num2, _num3, etc.
	else{
	    int num = 1;
	    while (newLogFile.exists()){
		num++;
		newLogFile = new java.io.File(logDir, 
					      fileDateFormat.format(curDate) + "_num" + num + logExt);
	    }
	}
	logFile = newLogFile;

	//** Open new log file
	java.io.PrintWriter newLogWriter = new java.io.PrintWriter(
								   new java.io.FileWriter(logFile.getPath(), true), true);

	long now = System.currentTimeMillis();
	if (logWriter != null) {
	    synchronized (logWriter) {
				//** Close old log file
		logWriter.close();
		logWriter = newLogWriter;
					
		// This is what sets logWriter to get the log output
		super.setWriter(logWriter);
	    }
	}
	else {
	    logWriter = newLogWriter;
	    super.setWriter(logWriter);
	}
			
    }//setLogNameToDate


    // ---------------------------------------------------------------------------
    //		clearOldLogFiles
    // ---------------------------------------------------------------------------
    private synchronized void clearOldLogFiles(java.util.Date curDate)
    {
	String[] logFileList = logDir.list(
					   new java.io.FilenameFilter() {
						   public boolean accept(java.io.File dir, String n) {
						       return n.indexOf(logExt) != -1;
						   }//accept
					       }//FilenameFilter
					   );

	//** Compute Border date
	java.util.Calendar calendar = java.util.Calendar.getInstance();
	calendar.setTime(curDate);
	calendar.add(java.util.Calendar.DAY_OF_MONTH, -retainDays);

	int borderYear = calendar.get(java.util.Calendar.YEAR);
	int borderMonth = calendar.get(java.util.Calendar.MONTH) + 1;
	int borderDay = calendar.get(java.util.Calendar.DAY_OF_MONTH);
			
	for (int i = 0; i < logFileList.length; i++) {
	    java.io.File logFile = new java.io.File(logDir, logFileList[i]);
	    java.util.StringTokenizer st = 
		new java.util.StringTokenizer(logFile.getName(), "_.");
	    int nYear = Integer.parseInt(st.nextToken());
	    int nMonth = Integer.parseInt(st.nextToken());
	    int nDay = Integer.parseInt(st.nextToken());


	    if (nYear < borderYear ||
		(nYear == borderYear && nMonth < borderMonth) ||
		(nYear == borderYear && nMonth == borderMonth && nDay <= borderDay)) {
		logFile.delete();
	    }//if

	}//for
    }
    
    
    // ---------------------------------------------------------------------------
    //		startClearThread
    // ---------------------------------------------------------------------------
    private synchronized void startClearThread()
    {
	if (clearThread == null) {
	    clearThread = new Thread(this);
	    clearThread.setDaemon(true);
	    clearThread.start();
	}//if
    }


    // ---------------------------------------------------------------------------
    //		stopClearThread
    // ---------------------------------------------------------------------------
    private synchronized void stopClearThread(long timeout)
    {
	if (clearThread != null) {
				//** Send signal about exit from program
	    threadEvent.setOn(true);

				//** wait unitl thread is stopped
	    try {
		clearThread.join(timeout);
	    }
	    catch (java.lang.InterruptedException ignored) { Code.ignore(ignored); }
	    //** if timeout is out time let's interrupt thread
	    if (clearThread.isAlive()) {
        	clearThread.interrupt();
	    }//if
	    clearThread = null;
	}//if
    }


    // ---------------------------------------------------------------------------
    //		run
    // ---------------------------------------------------------------------------
    public void run( )
    {
	try {

	    while(true) {

		synchronized(threadEvent) {
		    threadEvent.wait(timerInterval);

		    if (threadEvent.isOn()) {
			break;
		    }//if

		}//synchronized

		//** Get current datetime and store in member variable
		java.util.Date curTime = new java.util.Date();
        	java.util.Calendar calendar = java.util.Calendar.getInstance();
		calendar.setTime(curTime);

		//logWriter.printTimestamp();
		//logWriter.println("thread in run !");

		if (calendar.get(java.util.Calendar.HOUR_OF_DAY) == 0 
		    && calendar.get(java.util.Calendar.MINUTE) == 0) {
		    setLogNameToDate(curTime);
		    clearOldLogFiles(curTime);
		}//if
	    }//while
	} catch(Exception e) {
				//System.out.println(e);
	    e.printStackTrace();
	}//try

    }//run


    /*
      public static void main(String[] args)
      {
      try {
      RolloverFileLogSink errLog = new RolloverFileLogSink();
      errLog.create(args[0], 2, ".LOG", 20*1000, 20*1000);
      errLog.logWriter.println("Hello, World !");
      } catch(Exception e) {
				//System.out.println(e);
				e.printStackTrace();
				}//try
				}//main
    */


    // ---------------------------------------------------------------------------
    //		cleanup
    // ---------------------------------------------------------------------------
    public synchronized void cleanup()
    {
	if (isCreated()) {
	    stopClearThread(threadStopTimeout);
	    logWriter.close();
	    setCreated(false);
	}//if
    }


    // ---------------------------------------------------------------------------
    private java.io.File logDir = new java.io.File("./");

    public void setLogDir(String newValue)
	throws java.io.IOException
    {
        logDir = new java.io.File(newValue);
        logDir.mkdirs();	// Make sure it exists
    }

    private String logExt = ".log";

    public String getLogExt() {
        return logExt;
    }

    public void setLogExt(String newValue) {
        logExt = newValue;
    }
    private int retainDays = 1;

    public int getRetainDays() {
        return retainDays;
    }

    public void setRetainDays(int newValue)
    {
        retainDays = newValue;
    }

    private long threadStopTimeout = 20*1000;

    public long getThreadStopTimeout() {
        return threadStopTimeout;
    }

    public void setThreadStopTimeout(long newValue) {
        threadStopTimeout = newValue;
    }
    
    private long timerInterval = 20*1000;

    public long getTimerInterval() {
        return timerInterval;
    }

    public void setTimerInterval(long newValue)
    {
        timerInterval = newValue;
    }
    /****************************************
     * data members
     ****************************************/

    public boolean isCreated()
    {
  	return created;
    }

    private void setCreated(boolean newValue)
    {
	created = newValue;
    }

    private boolean created = false;
    // add your data members here
    private java.io.PrintWriter logWriter;
    private java.io.File logFile;
    private Thread clearThread;
    private ThreadEvent threadEvent = new ThreadEvent();
    private java.text.SimpleDateFormat fileDateFormat = 
	new java.text.SimpleDateFormat("yyyy_MM_dd");
    private boolean multDay;


    // ---------------------------------------------------------------------------
    //		ThreadEvent
    // ---------------------------------------------------------------------------
    /** A helper class that is used to signal the cleanup thread to stop. */
    static final private class ThreadEvent 
    {
	private boolean on = false;

	public synchronized boolean isOn() {
	    return on;
	}

	public synchronized void setOn(boolean newValue) {
	    on = newValue;
	    if (on) this.notifyAll();
	}
    }
}
