package org.mortbay.http;

import org.mortbay.util.io.Buffer;
import org.mortbay.util.io.ByteArrayBuffer;

import junit.framework.TestCase;

/**
 * @author gregw
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class HttpParserTest extends TestCase implements HttpParser.Handler
{

    /**
     * Constructor for HttpParserTest.
     * @param arg0
     */
    public HttpParserTest(String arg0)
    {
        super(arg0);
    }

    public static void main(String[] args)
    {
        junit.textui.TestRunner.run(HttpParserTest.class);
    }

    /**
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

	public void testLineParse()
	{
		String http= "POST /foo HTTP/1.0\015\012" +
		"\015\012";
		ByteArrayBuffer buffer = new ByteArrayBuffer(http.getBytes());
		
		contentLength=HttpParser.NO_CONTENT;
		HttpParser.parse(this,buffer);
		assertEquals("POST",f0);
		assertEquals("/foo",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals(-1,h);
	}
	
	public void testHeaderParse()
	{
		String http= "GET / HTTP/1.0\015\012" + 
		"Header1: value1\015\012" + 
		"Header2  :   value 2a  \015\012" + 
		"                    value 2b  \015\012" + 
		"Header3: \015\012" + 
		"Header4 \015\012" + 
		"  value4\015\012" + 
		"\015\012";
		ByteArrayBuffer buffer = new ByteArrayBuffer(http.getBytes());
		
		contentLength=HttpParser.NO_CONTENT;
		HttpParser.parse(this,buffer);
		assertEquals("GET",f0);
		assertEquals("/",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals("Header1",hdr[0]);
		assertEquals("value1",val[0]);
		assertEquals("Header2",hdr[1]);
		assertEquals("value 2a  \015\012                    value 2b",val[1]);
		assertEquals("Header3",hdr[2]);
		assertEquals(null,val[2]);
		assertEquals("Header4",hdr[3]);
		assertEquals("value4",val[3]);
		assertEquals(3,h);
	}
	
    public void testChunkParse()
    {
        String http= "GET / HTTP/1.0\015\012" + 
        "Header1: value1\015\012" + 
		"\015\012"+
		"a;\015\012"+
		"0123456789\015\012"+
		"1a\015\012"+
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ\015\012"+
		"0\015\012";
		ByteArrayBuffer buffer = new ByteArrayBuffer(http.getBytes());
		
		contentLength=HttpParser.CHUNKED_CONTENT;
		HttpParser.parse(this,buffer);
		assertEquals("GET",f0);
		assertEquals("/",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals(0,h);
		assertEquals("Header1",hdr[0]);
		assertEquals("value1",val[0]);
		assertEquals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",content);
    }

	public void testMultiParse()
	{
		String http= "GET / HTTP/1.0\015\012" + 
		"Header1: value1\015\012" + 
		"\015\012"+
		"a;\015\012"+
		"0123456789\015\012"+
		"1a\015\012"+
		"ABCDEFGHIJKLMNOPQRSTUVWXYZ\015\012"+
		"0\015\012" +

		"POST /foo HTTP/1.0\015\012" + 
		"Header2: value2\015\012" + 
		"\015\012"+
		
		"PUT /doodle HTTP/1.0\015\012" + 
	    "Header3: value3\015\012" + 
		"\015\012"+
		"0123456789\015\012";
		
		ByteArrayBuffer buffer = new ByteArrayBuffer(http.getBytes());
		
		contentLength=HttpParser.CHUNKED_CONTENT;
		HttpParser.parse(this,buffer);
		assertEquals("GET",f0);
		assertEquals("/",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals(0,h);
		assertEquals("Header1",hdr[0]);
		assertEquals("value1",val[0]);
		assertEquals("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",content);
		

		contentLength=HttpParser.NO_CONTENT;
		HttpParser.parse(this,buffer);
		assertEquals("POST",f0);
		assertEquals("/foo",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals(0,h);
		assertEquals("Header2",hdr[0]);
		assertEquals("value2",val[0]);
		assertEquals(null,content);
		

		contentLength=10;
		HttpParser.parse(this,buffer);
		assertEquals("PUT",f0);
		assertEquals("/doodle",f1);
		assertEquals("HTTP/1.0",f2);
		assertEquals(0,h);
		assertEquals("Header3",hdr[0]);
		assertEquals("value3",val[0]);
		assertEquals("0123456789",content);
	
	}


   	String content;
	String f0;
	String f1;
	String f2;
	String[] hdr;
	String[] val;
	int h;
	int contentLength;
	
    public void gotContent(int offset, Buffer ref)
    {
    	if (offset==0)
    		content="";
    	content=content.substring(0,offset)+ref;
    	System.out.println("Content["+offset+"]="+ref);
    }

    public void gotMethodOrVersion(Buffer ref)
    {
    	h=-1;
		hdr = new String[9];
		val = new String[9];
    	f0=ref.toString();
    	System.out.print("\n"+ref);
    }

    public void gotUriOrCode(Buffer ref)
    {
		f1=ref.toString();
		System.out.print(" "+ref);
    }

    public void gotVersionOrReason(Buffer ref)
    {
		f2=ref.toString();
		System.out.print(" "+ref);
    }

    public void gotHeader(Buffer name, Buffer value)
    {
		System.out.print("\n"+name+":"+value);
    	hdr[++h]=name.toString();
    	if (value!=null)
	    	val[h]=value.toString();
    }

    public int gotCompleteHeader()
    {
		content=null;
        return contentLength;
    }

    public void gotCompleteMessage(int contentLength)
    {
		System.out.println("Message Complete: "+contentLength);
    }
}