// ========================================================================
// $Id$
// Copyright 2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.io.bio;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.mortbay.io.ByteArrayBuffer;
import org.mortbay.io.InBuffer;
import org.mortbay.io.Portable;

/**
 * @author gregw
 * 
 * @deprecated USE BufferBIO
 */
public class InputStreamBuffer extends ByteArrayBuffer implements InBuffer
{
    Socket _socket;
	InputStream _in;
    int _fragment;
	
    public InputStreamBuffer(InputStream in, int bufferSize) 
    {
        super(new byte[bufferSize],0,0);
        _in=in;
        _fragment=bufferSize/4;
    }
    
    public InputStreamBuffer(Socket socket, int bufferSize) 
        throws IOException 
    {
        super(new byte[bufferSize],0,0);
        _in=socket.getInputStream();
        _fragment=bufferSize/4;
        if (bufferSize>3000 && _fragment<1500)
            _fragment=1500;
    }
    
    /**
     * Set the fragment size. If the space available is less than the fragment size, 
     * then fill will compact the buffer.
     * @param fragment
     */
    public void setFragmentSize(int fragment)
    {
        _fragment=fragment;
    }
	
    public int getFragmentSize()
    {
        return _fragment;
    }
    
    /* (non-Javadoc)
     * @see org.mortbay.util.Buffer#fill()
     */
    public int fill() 
        throws IOException
    {
        if (_in==null)
            return 0;
            
    	int space=space();
    	if (space<=_fragment)
        {
            compact();
            space=space();
            if (space<=0)
            {
                if (hasContent())
                    return 0;
                Portable.throwIllegalState("full");
            }
        }
        
	    byte[] bytes = array();
		int n=_in.read(bytes,putIndex(),space);
		if (n>=0)
		{
		  	setPutIndex(putIndex()+n);
			return n;
    	}
    	
    	return -1;
    }

    public boolean isClosed()
    {
        if (_socket!=null)
            return _socket.isClosed() || _socket.isInputShutdown();
        return _in!=null;
    }

    public void close()
        throws IOException
    {
        _in.close();
        _in=null;
    }
}
