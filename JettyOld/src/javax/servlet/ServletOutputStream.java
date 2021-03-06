/*
 * $Id$
 * 
 * Copyright (c) 1995-1999 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * This software is the confidential and proprietary information of Sun
 * Microsystems, Inc. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Sun.
 * 
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF THE
 * SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING
 * THIS SOFTWARE OR ITS DERIVATIVES.
 * 
 * CopyrightVersion 1.0
 */

package javax.servlet;

import java.io.OutputStream;
import java.io.IOException;
import java.io.CharConversionException;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Provides an output stream for sending binary data to the
 * client. You can access an object of this class
 * by using the {@link ServletResponse.getOutputStream}
 * method.
 *
 * <p>This class is abstract and the servlet engine usually
 * extends and defines it. If you subclass this class,
 * you must implement the <code>java.io.OutputStream#write(int)</code>
 * method.
 *
 * 
 * @author 	Various
 * @version 	$Version$
 *
 * @see 	ServletResponse
 *
 */

public abstract class ServletOutputStream extends OutputStream {

    private static final String LSTRING_FILE = "javax.servlet.LocalStrings";
    private static ResourceBundle lStrings =
	ResourceBundle.getBundle(LSTRING_FILE);


    
    /**
     *
     * Does nothing, because this is an abstract class.
     *
     */

    protected ServletOutputStream () { }


    /**
     * Writes a <code>String</code> to the client, 
     * without a carriage return-line feed (CRLF) 
     * character at the end.
     *
     *
     * @param s			the <code>String</code to send to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void print(String s) throws IOException {
	if (s==null) s="null";
	int len = s.length();
	for (int i = 0; i < len; i++) {
	    char c = s.charAt (i);

	    //
	    // XXX NOTE:  This is clearly incorrect for many strings,
	    // but is the only consistent approach within the current
	    // servlet framework.  It must suffice until servlet output
	    // streams properly encode their output.
	    //
	    if ((c & 0xff00) != 0) {	// high order byte must be zero
		String errMsg = lStrings.getString("err.not_iso8859_1");
		Object[] errArgs = new Object[1];
		errArgs[0] = new Character(c);
		errMsg = MessageFormat.format(errMsg, errArgs);
		throw new CharConversionException(errMsg);
	    }
	    write (c);
	}
    }



    /**
     * Writes a <code>boolean</code> value to the client,
     * with no carriage return-line feed (CRLF) 
     * character at the end.
     *
     * @param b			the </code>boolean</code> value 
     *				to send to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void print(boolean b) throws IOException {
	String msg;
	if (b) {
	    msg = lStrings.getString("value.true");
	} else {
	    msg = lStrings.getString("value.false");
	}
	print(msg);
    }



    /**
     * Writes a character to the client,
     * with no carriage return-line feed (CRLF) 
     * character at the end.
     *
     * @param c			the character to send to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void print(char c) throws IOException {
	print(String.valueOf(c));
    }




    /**
     *
     * Writes an integer to the client,
     * with no carriage return-line feed (CRLF) 
     * character at the end.
     *
     * @param i			the integer to send to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */  

    public void print(int i) throws IOException {
	print(String.valueOf(i));
    }



 
    /**
     * 
     * Writes a <code>long</code> value to the client,
     * with no carriage return-line feed (CRLF) character 
     * at the end.
     *
     * @param l			the <code>long</code> value 
     *				to send to the client
     *
     * @exception IOException 	if an input or output exception 
     *				occurred
     * 
     */

    public void print(long l) throws IOException {
	print(String.valueOf(l));
    }



    /**
     *
     * Writes a <code>float</code> value to the client,
     * with no carriage return-line feed (CRLF) character 
     * at the end.
     *
     * @param f			the <code>float</code> value
     *				to send to the client
     *
     * @exception IOException	if an input or output exception occurred
     *
     *
     */

    public void print(float f) throws IOException {
	print(String.valueOf(f));
    }



    /**
     *
     * Writes a <code>double</code> value to the client,
     * with no carriage return-line feed (CRLF) character 
     * at the end.
     * 
     * @param d			the <code>double</code> value
     *				to send to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void print(double d) throws IOException {
	print(String.valueOf(d));
    }



    /**
     * Writes a carriage return-line feed (CRLF)
     * character to the client.
     *
     *
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println() throws IOException {
	print("\r\n");
    }



    /**
     * Writes a <code>String</code> to the client, 
     * followed by a carriage return-line feed (CRLF) 
     * character.
     *
     *
     * @param s			the </code>String</code> to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println(String s) throws IOException {
	print(s);
	println();
    }




    /**
     *
     * Writes a <code>boolean</code> value to the client, 
     * followed by a 
     * carriage return-line feed (CRLF) character.
     *
     *
     * @param b			the <code>boolean</code> value 
     *				to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println(boolean b) throws IOException {
	print(b);
	println();
    }



    /**
     *
     * Writes a character to the client, followed by a carriage
     * return-line feed (CRLF) character.
     *
     * @param c			the character to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println(char c) throws IOException {
	print(c);
	println();
    }



    /**
     *
     * Writes an integer to the client, followed by a 
     * carriage return-line feed (CRLF) character.
     *
     *
     * @param i			the integer to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println(int i) throws IOException {
	print(i);
	println();
    }



    /**  
     *
     * Writes a <code>long</code> value to the client, followed by a 
     * carriage return-line feed (CRLF) character.
     *
     *
     * @param l			the <code>long</code> value to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */  

    public void println(long l) throws IOException {
	print(l);
	println();
    }



    /**
     *
     * Writes a <code>float</code> value to the client, 
     * followed by a carriage return-line feed (CRLF) character.
     *
     * @param f			the <code>float</code> value 
     *				to write to the client
     *
     *
     * @exception IOException 	if an input or output exception 
     *				occurred
     *
     */

    public void println(float f) throws IOException {
	print(f);
	println();
    }



    /**
     *
     * Writes a <code>double</code> value to the client, 
     * followed by a carriage return-line feed (CRLF) character.
     *
     *
     * @param d			the <code>double</code> value
     *				to write to the client
     *
     * @exception IOException 	if an input or output exception occurred
     *
     */

    public void println(double d) throws IOException {
	print(d);
	println();
    }
}
