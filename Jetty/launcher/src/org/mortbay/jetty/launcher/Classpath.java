// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.jetty.launcher;

import java.util.Vector;
import java.io.IOException;
import java.io.File;
import java.util.Arrays;    // 1.2
import java.net.URLClassLoader;
import java.net.URL;
import java.util.StringTokenizer;
import java.io.FilenameFilter;
import java.net.MalformedURLException;


/**
 * Class to handle CLASSPATH construction
 * @author Jan Hlavat�
 */
public class Classpath {

    Vector _elements = new Vector();    

    public Classpath()
    {}    

    public Classpath(String initial)
    {
        addClasspath(initial);
    }
        
    public boolean addComponent(String component)
    {
        if ((component != null)&&(component.length()>0)) {
            try {
                File f = new File(component);
                if (f.exists())
                {
                    File key = f.getCanonicalFile();
                    if (!_elements.contains(key))
                    {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (IOException e) {}
        }
        return false;
    }
    
    public boolean addComponent(File component)
    {
        if (component != null) {
            try {
                if (component.exists()) {
                    File key = component.getCanonicalFile();
                    if (!_elements.contains(key)) {
                        _elements.add(key);
                        return true;
                    }
                }
            } catch (IOException e) {}
        }
        return false;
    }

    public void addClasspath(String s)
    {
        if (s != null)
        {
            StringTokenizer t = new StringTokenizer(s, File.pathSeparator);
            while (t.hasMoreTokens())
            {
                addComponent(t.nextToken());
            }
        }
    }    
    
    public String toString()
    {
        StringBuffer cp = new StringBuffer(1024);
        int cnt = _elements.size();
        if (cnt >= 1) {
            cp.append( ((File)(_elements.elementAt(0))).getPath() );
        }
        for (int i=1; i < cnt; i++) {
            cp.append(File.pathSeparatorChar);
            cp.append( ((File)(_elements.elementAt(i))).getPath() );
        }
        return cp.toString();
    }
    
    public ClassLoader getClassLoader() {
        int cnt = _elements.size();
        URL[] urls = new URL[cnt];
        for (int i=0; i < cnt; i++) {
            try {
                urls[i] = ((File)(_elements.elementAt(i))).toURL();
            } catch (MalformedURLException e) {}
        }
        
        ClassLoader old_classloader = Thread.currentThread().getContextClassLoader();
        if (old_classloader == null) {
            old_classloader = Classpath.class.getClassLoader();
        }
        if (old_classloader == null) {
            old_classloader = ClassLoader.getSystemClassLoader();
        }
        return new URLClassLoader(urls, old_classloader);
    }
}
