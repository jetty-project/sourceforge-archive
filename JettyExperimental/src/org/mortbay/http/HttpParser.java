package org.mortbay.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mortbay.io.Buffer;
import org.mortbay.io.Portable;

/* ------------------------------------------------------------------------------- */
/** 
 * 
 * @version $Revision$
 * @author gregw
 */
public abstract class HttpParser
{
    private static Log log= LogFactory.getLog(HttpParser.class);

    // Terminal symbols.
    static final byte COLON= (byte)':';
    static final byte SEMI_COLON= (byte)';';
    static final byte SPACE= 0x20;
    static final byte CARRIAGE_RETURN= 0x0D;
    static final byte LINE_FEED= 0x0A;
    static final byte TAB= 0x09;

    // States
    public static final int STATE_START= -11;
    public static final int STATE_FIELD0= -10;
    public static final int STATE_SPACE1= -9;
    public static final int STATE_FIELD1= -8;
    public static final int STATE_SPACE2= -7;
    public static final int STATE_END0= -6;
    public static final int STATE_END1= -5;
    public static final int STATE_FIELD2= -4;
    public static final int STATE_HEADER= -3;
    public static final int STATE_HEADER_NAME= -2;
    public static final int STATE_HEADER_VALUE= -1;
    public static final int STATE_END= 0;
    public static final int STATE_EOF_CONTENT= 1;
    public static final int STATE_CONTENT= 2;
    public static final int STATE_CHUNKED_CONTENT= 3;
    public static final int STATE_CHUNK_SIZE= 4;
    public static final int STATE_CHUNK_PARAMS= 5;
    public static final int STATE_CHUNK= 6;

    public static final int CHUNKED_CONTENT= -2;
    public static final int EOF_CONTENT= -1;
    public static final int NO_CONTENT= 0;

    /* ------------------------------------------------------------------------------- */
    protected int state= STATE_START;
    protected byte eol;
    protected int length;
    protected int contentLength;
    protected int contentPosition;
    protected int chunkLength;
    protected int chunkPosition;

    /* ------------------------------------------------------------------------------- */
    /** Constructor. 
     */
    public HttpParser()
    {}

    /* ------------------------------------------------------------------------------- */
    public int getState()
    {
        return state;
    }

    /* ------------------------------------------------------------------------------- */
    public String toString(Buffer buf)
    {
        return "state=" + state + " length=" + length + " buf=" + buf.hashCode();
    }

    /* ------------------------------------------------------------------------------- */
    /** parse.
     * @param handler
     * @param source
     * @return parser state
     */
    public void parse(Buffer source)
    {
        state= STATE_START;

        // continue parsing
        while (state != STATE_END)
            parseNext(source);
    }

    /* ------------------------------------------------------------------------------- */
    /**
     * Method parseBuffer.
     * @param handler
     * @param buf
     * @param ctx
     */
    public void parseNext(Buffer source)
    {
		if (log.isTraceEnabled()) log.trace("parseNext s="+state+","+source.toDetailString());
		
        if (state == STATE_END)
            state= STATE_START;

        if (source.remaining() == 0)
        {
            source.compact();
            int filled= source.fill();
            if (filled < 0 && state == STATE_EOF_CONTENT)
            {
                state= STATE_END;
                messageComplete(contentPosition);
                return;
            }
        }

        byte ch;

        // Handler header
        while (state < STATE_END && source.remaining() > 0)
        {
            ch= source.get();
            if (eol == CARRIAGE_RETURN && ch == LINE_FEED)
            {
                eol= LINE_FEED;
                continue;
            }
            eol= 0;

            switch (state)
            {
                case STATE_START :
                    if (ch > SPACE)
                    {
                        source.mark();
                        state= STATE_FIELD0;
                    }
                    break;

                case STATE_FIELD0 :
                    if (ch == SPACE)
                    {
                        foundField0(source.sliceFromMark());
                        state= STATE_SPACE1;
                        return;
                    }
                    else if (ch < SPACE)
                    {
                        throw new RuntimeException(toString(source));
                    }
                    break;

                case STATE_SPACE1 :
                    if (ch > SPACE)
                    {
                        source.mark();
                        state= STATE_FIELD1;
                    }
                    else if (ch < SPACE)
                        throw new RuntimeException(toString(source));
                    break;

                case STATE_FIELD1 :
                    if (ch == SPACE)
                    {
                        foundField1(source.sliceFromMark());
                        state= STATE_SPACE2;
                        return;
                    }
                    else if (ch < SPACE)
                    {
                        // HTTP/0.9
                        foundField1(source.sliceFromMark());
                        headerComplete();
                        state= STATE_END;
                        messageComplete(contentPosition);
                        return;
                    }
                    break;

                case STATE_SPACE2 :
                    if (ch > SPACE)
                    {
                        source.mark();
                        state= STATE_FIELD2;
                    }
                    else if (ch < SPACE)
                    {
                        // HTTP/0.9
                        headerComplete();
                        state= STATE_END;
                        messageComplete(contentPosition);
                        return;
                    }
                    break;

                case STATE_FIELD2 :
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        foundField2(source.sliceFromMark());
                        eol= ch;
                        state= STATE_HEADER;
                        return;
                    }
                    break;

                case STATE_HEADER :
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        headerComplete();
                        contentLength= getContentLength();
                        contentPosition= 0;
                        eol= ch;
                        switch (contentLength)
                        {
                            case HttpParser.EOF_CONTENT :
                                state= STATE_EOF_CONTENT;
                                break;
                            case HttpParser.CHUNKED_CONTENT :
                                state= STATE_CHUNKED_CONTENT;
                                break;
                            case HttpParser.NO_CONTENT :
                                state= STATE_END;
                                messageComplete(contentPosition);
                                break;
                            default :
                                state= STATE_CONTENT;
                                break;
                        }
                        return;
                    }
                    else if (ch == COLON || ch == SPACE || ch == TAB)
                    {
                        length= -1;
                        state= STATE_HEADER_VALUE;
                    }
                    else
                    {
                        length= 1;
                        source.mark();
                        state= STATE_HEADER_NAME;
                    }
                    break;

                case STATE_HEADER_NAME :
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        if (length > 0)
                            foundHttpHeader(source.sliceFromMark(length));
                        eol= ch;
                        state= STATE_HEADER;
                        return;
                    }
                    else if (ch == COLON)
                    {
                        if (length > 0)
                            foundHttpHeader(source.sliceFromMark(length));
                        length= -1;
                        state= STATE_HEADER_VALUE;
                        return;
                    }
                    else if (ch != SPACE && ch != TAB)
                    {
                        if (length == -1)
                            source.mark();
                        length= source.position() - source.markValue();
                    }
                    break;

                case STATE_HEADER_VALUE :
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        if (length > 0)
                            foundHttpValue(source.sliceFromMark(length));

                        eol= ch;
                        state= STATE_HEADER;
                        return;
                    }
                    else if (ch != SPACE && ch != TAB)
                    {
                        if (length == -1)
                            source.mark();
                        length= source.position() - source.markValue();
                    }
                    break;
            }
        }

        // Handle content
        Buffer chunk;
        while (state > STATE_END && source.remaining() > 0)
        {
            if (eol == CARRIAGE_RETURN && source.peek() == LINE_FEED)
            {
                eol= source.get();
                continue;
            }
            eol= 0;

            switch (state)
            {
                case STATE_EOF_CONTENT :
                    chunk= source.getBuffer(-1);
                    foundContent(contentPosition, chunk);
                    contentPosition += chunk.remaining();
                    return;

                case STATE_CONTENT :
                    {
                        int length= source.remaining();
                        int remaining= contentLength - contentPosition;
                        if (remaining == 0)
                        {
                            state= STATE_END;
                            messageComplete(contentPosition);
                            return;
                        }
                        else if (length > remaining)
                            length= remaining;
                        chunk= source.getBuffer(length);
                        foundContent(contentPosition, chunk);
                        contentPosition += chunk.remaining();
                    }
                    return;

                case STATE_CHUNKED_CONTENT :
                    ch= source.peek();
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                        eol= source.get();
                    else if (ch <= SPACE)
                        source.get();
                    else
                    {
                        chunkLength= 0;
                        chunkPosition= 0;
                        state= STATE_CHUNK_SIZE;
                    }
                    break;

                case STATE_CHUNK_SIZE :
                    ch= source.get();
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        eol= ch;
                        if (chunkLength == 0)
                        {
                            state= STATE_END;
                            messageComplete(contentPosition);
                            return;
                        }
                        else
                            state= STATE_CHUNK;
                    }
                    else if (ch <= SPACE || ch == SEMI_COLON)
                        state= STATE_CHUNK_PARAMS;
                    else if (ch >= '0' && ch <= '9')
                        chunkLength= chunkLength * 16 + (ch - '0');
                    else if (ch >= 'a' && ch <= 'f')
                        chunkLength= chunkLength * 16 + (10 + ch - 'a');
                    else if (ch >= 'A' && ch <= 'F')
                        chunkLength= chunkLength * 16 + (10 + ch - 'A');
                    else
                        Portable.throwRuntime("bad chunk char: " + ch);

                    break;

                case STATE_CHUNK_PARAMS :
                    ch= source.get();
                    if (ch == CARRIAGE_RETURN || ch == LINE_FEED)
                    {
                        eol= ch;
                        if (chunkLength == 0)
                        {
                            state= STATE_END;
                            messageComplete(contentPosition);
                            return;
                        }
                        else
                            state= STATE_CHUNK;
                    }
                    break;

                case STATE_CHUNK :
                    {
                        int length= source.remaining();
                        int remaining= chunkLength - chunkPosition;
                        if (remaining == 0)
                        {
                            state= STATE_CHUNKED_CONTENT;
                            break;
                        }
                        else if (length > remaining)
                            length= remaining;
                        chunk= source.getBuffer(length);
                        foundContent(contentPosition, chunk);
                        contentPosition += chunk.remaining();
                        chunkPosition += chunk.remaining();
                    }
                    return;
            }
        }
    }

    /**
     * This is the method called by parser when the HTTP version is found
     */
    protected void foundField0(Buffer ref)
    {
		if(log.isTraceEnabled()) log.trace("foundField0:" + ref.toDetailString());
    }

    /**
     * This is the method called by parser when HTTP response code is found
     */
    protected void foundField1(Buffer ref)
    {
		if(log.isTraceEnabled()) log.trace("foundField1:" + ref.toDetailString());
    }

    /**
     * This is the method called by parser when HTTP response reason is found
     */
    protected void foundField2(Buffer ref)
    {
		if(log.isTraceEnabled()) log.trace("foundField2:" + ref.toDetailString());
    }

    /**
     * This is the method called by parser when A HTTP Header name is found
     */
    protected void foundHttpHeader(Buffer ref)
    {
		if(log.isTraceEnabled()) log.trace("foundHttpHeader:" + ref.toDetailString());
    }

    /**
     * This is the method called by parser when a HTTP Header value is found
     */
    protected void foundHttpValue(Buffer ref)
    {
		if(log.isTraceEnabled()) log.trace("foundHttpValue:" + ref.toDetailString());
    }

    protected void headerComplete()
    {
		log.trace("headerComplete:");
    }

    protected int getContentLength()
    {
		log.trace("getContentLength:");
    	return 0;
    }

    protected void foundContent(int index, Buffer ref)
    {
        if(log.isTraceEnabled()) log.trace("foundContent:" + index+","+ref.toDetailString());
    }

    protected void messageComplete(int contextLength)
    {
        log.trace("messageComplete:" + contextLength);
    }

}
