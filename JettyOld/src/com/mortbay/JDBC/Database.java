// ========================================================================
// Copyright (c) 1996 Mort Bay Consulting Pty. Ltd., Sydney
// $Id$
// ========================================================================

package com.mortbay.JDBC;
import com.mortbay.Base.*;
import java.sql.*;
import java.util.*;

/* ------------------------------------------------------------ */
/** JDBC Database wrapper
 * This class wraps the detail of creating JDBC drivers and
 * connections to a particular database.
 * <p>
 * This class takes a DbAdaptor instance, a JDBC database URL
 * and an optional properties object and manages a pool
 * of JDBC connections to that database.
 * <p>
 * The DbAdaptor instance is used to handle table creation
 * from meta data and formatting values for the particular
 * underlying database.
 * <p>
 * <h3>Notes</h3>
 * The connection pool is managed by wrapper classes for
 * ResultSet and Connection. (Really needs weak references :-)
 *
 * @see Table, DbAdaptor
 * @version $Revision$ $Date$
 * @author Greg Wilkins (gregw)
 */
public class Database
{
    /* ------------------------------------------------------------ */
    private static final Hashtable adaptorCache = new Hashtable();
    private static final Hashtable driverCache = new Hashtable();

    /* ------------------------------------------------------------ */
    private final Properties properties=new Properties();
    private final Stack connections = new Stack();
    private DbAdaptor adaptor;
    private String url;

    /* ------------------------------------------------------------ */
    /** Default Constructor.
     * Creates testing DB for MSQL adaptor with null URL
     */
    public Database()
    {
	try{
	    init("com.mortbay.JDBC.MsqlAdaptor",null);
	}
	catch(Exception e)
	{
	    Code.fail(e);
	}
    }
    
    
    /* ------------------------------------------------------------ */
    /** Database constructor
     * @param properties A hashtable of connection properties, which must
     *                   at least contain DbAdaptor and DbUrl.
     *
     * @exception ClassNotFoundException Could not find DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception InstantiationException Could not create DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception IllegalAccessException Other problem with DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception SQLException JDBC driver manager could not open connection
     */
    public Database(Hashtable properties)
	 throws SQLException,
	     ClassNotFoundException,
	     InstantiationException,
	     IllegalAccessException
    {
	String dbAdaptor = (String) properties.get("DbAdaptor");
	String dbUrl = (String) properties.get("DbUrl");
	Code.assert(dbAdaptor!=null && dbUrl!=null,
		    "Can't find DbAdaptor or DbUrl");

	Enumeration e = properties.keys();
	while (e.hasMoreElements())
	{
	    String k=e.nextElement().toString();
	    if ("DbAdaptor".equals(k) || "DbUrl".equals(k))
		continue;
	    this.properties.put(k,properties.get(k).toString());
	}
	init(dbAdaptor,dbUrl);
    }
    
    /* ------------------------------------------------------------ */
    /** Database constructor
     * @param dbAdaptor The full package and class name of the JDBC
     *                  DbAdaptor type to use for this database.
     * @param dbUrl    The url of this database
     * @param properties A hashtable of connection properties (or null)
     *
     * @exception ClassNotFoundException Could not find DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception InstantiationException Could not create DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception IllegalAccessException Other problem with DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception SQLException JDBC driver manager could not open connection
     */
    public Database(String dbAdaptor,
		    String dbUrl,
		    Hashtable properties)
	 throws SQLException,
			ClassNotFoundException,
			InstantiationException,
			IllegalAccessException
    {
	if (properties!=null)
	{
	    Enumeration e = properties.keys();
	    while (e.hasMoreElements())
	    {
		String k=e.nextElement().toString();
		if ("DbDriver".equals(k) || "DbUrl".equals(k))
		    continue;
		this.properties.put(k,properties.get(k).toString());
	    }
	}
	init(dbAdaptor,dbUrl);
    }

    /* ------------------------------------------------------------ */
    /** Database constructor
     * @param dbAdaptor The full package and class name of the JDBC
     *                  DbAdaptor type to use for this database.
     * @param dbUrl    The url of this database
     *
     * @exception ClassNotFoundException Could not find DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception InstantiationException Could not create DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception IllegalAccessException Other problem with DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception SQLException JDBC driver manager could not open connection
     */
    public Database(String dbAdaptor,
		    String dbUrl)
	 throws SQLException,
		ClassNotFoundException,
		InstantiationException,
		IllegalAccessException
    {
	this(dbAdaptor,dbUrl,null);
    }
	
    /* ------------------------------------------------------------ */
    /** Initialize database
     * @param dbAdaptor The full package and class name of the JDBC
     *                  DbAdaptor type to use for this database.
     * @param dbUrl The database URL
     *
     * @exception ClassNotFoundException Could not find DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception InstantiationException Could not create DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception IllegalAccessException Other problem with DbAdaptor or
                  the JDBC driver specified by the adaptor
     * @exception SQLException JDBC driver manager could not open connection
     */
    private void init(String dbAdaptor, String dbUrl)
	throws SQLException,
	       ClassNotFoundException,
	       InstantiationException,
	       IllegalAccessException
    {
	Code.debug ("Created database: "+dbUrl);
	
	// Check if adaptor has been constructed before
	synchronized(adaptorCache)
	{
	    adaptor = (DbAdaptor) adaptorCache.get(dbAdaptor);
	    if (adaptor==null)
	    {
		Code.debug("Instantiate JDBC DbAdaptor: "+dbAdaptor);
		Object o = Class.forName(dbAdaptor).newInstance();
		if (o instanceof java.sql.Driver)
		    Code.fail(dbAdaptor +
			      " is a JDBC driver! Database needs a com.mortbay.JDBC.DbAdaptor class");

		if (!(o instanceof com.mortbay.JDBC.DbAdaptor))
		    Code.fail(dbAdaptor +
			      " is not a com.mortbay.JDBC.DbAdaptor class");

		adaptor=(DbAdaptor)o;
		adaptorCache.put(dbAdaptor,adaptor);
	    }
	}

	Code.assert(adaptor!=null,"Must have adaptor");
	
	// Check if driver has been constructed before
	synchronized(driverCache)
	{
	    String dbDriver = adaptor.getJdbcDriver();
	    if (!driverCache.containsKey(dbDriver))
	    {
		Code.debug("Instantiate JDBC driver: "+dbDriver);
		driverCache.put(dbDriver,
			      Class.forName(dbDriver).newInstance());
	    }
	}
	
	url=dbUrl;
    }
    
    /* ------------------------------------------------------------ */
    /* Constructor.
     * Driver must have already been loaded
     * @param dbUrl 
     */
    private Database(String dbUrl)
	 throws SQLException
    {
	url=dbUrl;
    }

    /* ------------------------------------------------------------ */
    /** Get the db adaptor
     * @return DbAdaptor
     */
    public  DbAdaptor getAdaptor()
    {
	return adaptor;
    }

    /* ------------------------------------------------------------ */
    /** Create a new transaction instance
     * @return The transaction instance.
     * @exception SQLException 
     */
    public com.mortbay.JDBC.Transaction newTransaction()
	throws SQLException
    {
	return new Transaction(this);
    }
    
    /* ------------------------------------------------------------ */
    /** Get a connection
     * Get either a new or recycled connection.  The real JDBC
     * connection is wrapped in a com.mortbay.JDBC.Connection
     * instance, which returns the connection when it is finalized
     * or an attempt is made to close the connection.
     *
     * @return The connection instance
     * @exception SQLException 
     */
    public synchronized java.sql.Connection getConnection()
	 throws SQLException
    {
	com.mortbay.JDBC.Connection connection = null;
	
	if (connections.size()>0)
	{
	    connection = (com.mortbay.JDBC.Connection)connections.pop();
	    Code.debug("New Connection:"+connection);
	}
	else
	{
	    connection =
		new com.mortbay.JDBC
		.Connection(DriverManager.getConnection(url,properties),
			    this);
	    Code.debug("Recycled Connection:"+connection);
	}
	return connection;
    }

    /* ------------------------------------------------------------ */
    /** Recycle a connection
     * Called by com.mortbay.JDBC.Connection and
     * com.mortbay.JDBC.Transaction to return a connection to the
     * free pool.
     * @param c 
     */
    synchronized void recycleConnection(java.sql.Connection c)
    {
	boolean closed=true;
	try{
	    if (c!=null)
		closed=c.isClosed();
	}
	catch(SQLException sqle)
	{
	    closed=true;
	}
	
	if (!closed)
	{
	    if (c instanceof com.mortbay.JDBC.Connection)
		connections.push(c);
	    else
		connections.push(new com.mortbay.JDBC.Connection(c,this));
	}
    }
    
    /* ------------------------------------------------------------ */
    /** Send an SQL query to the database.
     * When the returned ResultSet is emptied, closed or finalized, the
     * underlying connection is recycled.
     * @param update SQL query clause
     * @return ResultSet containing results of the query.
     */
    public synchronized java.sql.ResultSet query(String query)
	 throws SQLException
    {
	return query(null,query);
    }

    /* ------------------------------------------------------------ */
    /**  Send an SQL query to the database in a transaction.
     * 
     * @param tx The transaction object or null if no transaction
     * @param query SQL query clause
     * @return ResultSet containing results of the query.
     * @exception SQLException 
     */
    public synchronized java.sql.ResultSet query(Transaction tx,
						 String query)
	 throws SQLException
    {
	Code.debug("Query: "+query);
	com.mortbay.JDBC.Connection connection = null;
	if (tx==null)
	    connection = (com.mortbay.JDBC.Connection)getConnection();
	else
	    connection = (com.mortbay.JDBC.Connection)tx.getConnection();
	
	java.sql.Statement s = connection.createStatement();
	java.sql.ResultSet rs= s.executeQuery(query);

	com.mortbay.JDBC.ResultSet result =
	    new com.mortbay.JDBC.ResultSet(rs,connection);
	
	return result;
    }
    

    /* ------------------------------------------------------------ */
    /** Send an update SQL request to the database
     * @param update SQL update clause
     * @return int number of rows updated
     */
    public int update(String update)
	 throws SQLException
    {
	return update(null,update);
    }


    /* ------------------------------------------------------------ */
    /** Send an update SQL request to the database in transaction
     * @param tx The transaction object or null if no transaction
     * @param update SQL update string
     * @return number of rows updated
     * @exception SQLException 
     */
    public int update(Transaction tx,String update)
	 throws SQLException
    {
	Code.debug("Update: "+update);
	
	com.mortbay.JDBC.Connection connection = null;
	if (tx==null)
	    connection = (com.mortbay.JDBC.Connection)getConnection();
	else
	    connection = (com.mortbay.JDBC.Connection)tx.getConnection();
	Statement s = connection.createStatement();
	int rows = s.executeUpdate(update);
	Code.debug("Rows="+rows);
	s.close();
	connection.recycle();
	return rows;
    }
    
    
    /* ------------------------------------------------------------ */
    /** Quote a string for an SQL statement
     * Prepare a string for inclusion in a SQL clause with the quoting
     * conventions of the underlying database.
     */
    public String quote(String s)
    {
	return adaptor.quote(s);
    }
};












