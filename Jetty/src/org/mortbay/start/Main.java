// ========================================================================
// Copyright (c) 2002 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.mortbay.start;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;




/*-------------------------------------------*/
/** Main start class.
 * This class is intended to be the main class listed in the MANIFEST.MF  of
 * the start.jar archive. It allows an application to be started with the
 * command "java -jar start.jar".
 *
 * The behaviour of Main is controlled by the "org/mortbay/start/start.config"
 * file obtained as a resource.  The format of each line is this file
 * is:<PRE>
 *  SUBJECT [ [!] CONDITION [AND|OR] ]*
 * </PRE>
 * where SUBJECT:<PRE> 
 *   ends with ".class" is the Main class to run.
 *   ends with ".xml" is a configuration file for the command line
 *   ends with "/" is a directory from which add all jar and zip files from. 
 *   ends with "/*" is a directory from which add all unconsidered jar and zip files from.
 *   Containing = are used to assign system properties.
 *   all other subjects are treated as files to be added to the classpath.
 * </PRE>
 * Subjects may include system properties with $(propertyname) syntax. 
 * File subjects starting with "/" are considered absolute, all others are relative to
 * the home directory.
 * <P>
 * CONDITION is one of:<PRE>
 *   always
 *   never
 *   available package.class
 *   java OPERATOR n.n
 *   nargs OPERATOR n
 *   OPERATOR := one of "<",">","<=",">=","==","!="
 * </PRE>
 * CONTITIONS can be combined with AND OR or !, with AND being the assume
 * operator for a list of CONDITIONS.
 * Classpath operations are evaluated on the fly, so once a class or jar is
 * added to the classpath, subsequent available conditions will see that class.
 *
 *
 * @author Jan Hlavaty (hlavac@code.cz)
 * @author Greg Wilkins
 * @version $Revision$
 */
 
public class Main
{
    private String _classname = null;
    private File _home_dir = null;
    private Classpath _classpath = new Classpath();
    private boolean _debug = System.getProperty("DEBUG",null)!=null;
    private ArrayList _xml = new ArrayList();
       
    public static void main(String[] args)
    {
        try
        {
            new Main().run(args);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    
    static File getDirectory(String name)
    {
        try
        {
            if (name != null)
            {
                File dir = new File(name).getCanonicalFile();
                if (dir.isDirectory())
                {
                    return dir;
                }
            }
        } catch (IOException e) { }
        return null;
    }
    

    boolean isAvailable(String classname)
    {
        try
        {
            Class.forName(classname);
            return true;
        }
        catch (ClassNotFoundException e)
        {}
        
        ClassLoader loader=_classpath.getClassLoader();
        try
        {
            loader.loadClass(classname);
            return true;
        }
        catch (ClassNotFoundException e)
        {}

        return false;
    }

    public static void invokeMain(ClassLoader classloader, String classname, String[] args)
         throws IllegalAccessException,
                InvocationTargetException,
                NoSuchMethodException,
                ClassNotFoundException
    {
        Class invoked_class = null;
        invoked_class = classloader.loadClass(classname);
        Class[] method_param_types = new Class[1];
        method_param_types[0] = args.getClass();
        Method main = null;
        main = invoked_class.getDeclaredMethod("main",method_param_types);
        Object[] method_params = new Object[1];
        method_params[0] = args;
        main.invoke(null,method_params);
    }
    
    /* ------------------------------------------------------------ */
    String expand(String s)
    {
        int i1=0;
        int i2=0;

        while (s!=null)
        {
            i1=s.indexOf("$(",i2);
            if (i1<0)
                break;
            i2=s.indexOf(")",i1+2);
            if (i2<0)
                break;

            String property=System.getProperty(s.substring(i1+2,i2),"");
            s=s.substring(0,i1)+property+s.substring(i2+1);
        }
        return s;
    }
    
    
    /* ------------------------------------------------------------ */
    void configure(InputStream config,String[] args)
        throws Exception
    {
        BufferedReader cfg = new BufferedReader(new InputStreamReader(config,"ISO-8859-1"));
        Version java_version = new Version(System.getProperty("java.version"));
        Version ver = new Version();
        
        // JAR's already processed
        java.util.Hashtable done = new Hashtable();
        
        
        // Handle line by line
        String line = null;
        while (true)
        {
            line=cfg.readLine();
            if (line==null)
                break;
            if (line.length()==0 || line.startsWith("#"))
                continue;

            try
            {
                StringTokenizer st = new StringTokenizer(line);
                String subject = st.nextToken();
                boolean expression = true;
                boolean not = false;
                String condition=null;

                // Evaluate all conditions
                while (st.hasMoreTokens())
                {
                    condition = st.nextToken();
                    if (condition.equalsIgnoreCase("!"))
                    {
                        not=true;
                        continue;
                    }
                    if (condition.equalsIgnoreCase("OR"))
                    {
                        if (expression)
                            break;
                        expression=true;
                        continue;
                    }

                    if (condition.equalsIgnoreCase("AND"))
                    {
                        if (!expression)
                            break;
                        continue;
                    }

                    boolean eval=true;
                    
                    if (condition.equals("true") || condition.equals("always"))
                    {
                        eval=true;
                    }
                    else if (condition.equals("false") ||condition.equals("never"))
                    {
                        eval=false;
                    }
                    else if (condition.equals("available"))
                    {
                        String class_to_check = st.nextToken();
                        eval= isAvailable(class_to_check);
                    }
                    else if (condition.equals("exists"))
                    {
                        try
                        {
                            eval=false;
                            File file = new File(expand(st.nextToken()));
                            eval=file.exists();
                        }
                        catch(Exception e)
                        {
                            if (_debug) e.printStackTrace();
                        }
                    }
                    else if (condition.equals("java"))
                    {
                        String operator = st.nextToken();
                        String version = st.nextToken();
                        ver.parse(version);
                        eval =
                            (operator.equals("<") && java_version.compare(ver)<0) ||
                            (operator.equals(">") && java_version.compare(ver)>0) ||
                            (operator.equals("<=") && java_version.compare(ver)<=0) ||
                            (operator.equals("=<") && java_version.compare(ver)<=0) ||
                            (operator.equals("=>") && java_version.compare(ver)>=0) ||
                            (operator.equals(">=") && java_version.compare(ver)>=0) ||
                            (operator.equals("==") && java_version.compare(ver)==0) ||
                            (operator.equals("!=") && java_version.compare(ver)!=0);
                    }
                    else if (condition.equals("nargs"))
                    {
                        String operator = st.nextToken();
                        int number = Integer.parseInt(st.nextToken());
                        eval=
                            (operator.equals("<") && args.length<number) ||
                            (operator.equals(">") && args.length>number) ||
                            (operator.equals("<=") && args.length<=number) ||
                            (operator.equals("=<") && args.length<=number) ||
                            (operator.equals("=>") && args.length>=number) ||
                            (operator.equals(">=") && args.length>=number) ||
                            (operator.equals("==") && args.length==number) ||
                            (operator.equals("!=") && args.length!=number);
                    }
                    else
                    {
                        System.err.println("ERROR: Unknown condition: "+condition);
                        eval=false;
                    }

                    expression &= not?!eval:eval;
                    not=false;
                }
                        
                String file=expand(subject).replace('/',File.separatorChar);
                
                if (_debug)
                    System.err.println((expression?"T ":"F ")+line);
                
                if (!expression)
                {
                    done.put(file,file);
                    continue;
                }

                // Handle the subject
                if (subject.indexOf("=")>0)
                {
                    int i = file.indexOf("=");
                    String property=file.substring(0,i);
                    String value=file.substring(i+1);
                    if (_debug) System.err.println("  "+property+"="+value);
                    System.setProperty(property,value);
                }
                else if (subject.endsWith("/*"))
                {
                    // directory of JAR files
                    File extdir = new File(file.substring(0,file.length()-1));
                    File[] jars = extdir.listFiles(new FilenameFilter()
                        {
                            public boolean accept(File dir, String name)
                            {
                                String namelc = name.toLowerCase();
                                return namelc.endsWith(".jar") || name.endsWith(".zip");
                                
                            }
                        } );
                    
                    
                    for (int i=0; i<jars.length; i++)
                    {
                        String jar = jars[i].getCanonicalPath();
                        if (!done.containsKey(jar))
                        {
                            done.put(jar,jar);
                            boolean added=_classpath.addComponent(jar);
                            if (_debug)
                                System.err.println((added?"  CLASSPATH+=":"  !")+jar);
                        }
                    }
                }
                else if (subject.endsWith("/"))
                {
                    // class directory
                    File cd = new File(file);
                    String d = cd.getCanonicalPath();
                    if (!done.containsKey(d))
                    {
                        done.put(d,d);
                        boolean added=_classpath.addComponent(d);
                        if (_debug)
                            System.err.println((added?"  CLASSPATH+=":"  !")+d);
                    }
                }
                else if (subject.toLowerCase().endsWith(".xml"))
                {
                    // Config file
                    File f = new File(file);                        
                    if (f.exists())
                        _xml.add(f.getCanonicalPath());
                    if (_debug) System.err.println("  ARGS+="+f);
                }
                else if (subject.toLowerCase().endsWith(".class"))
                {
                    // Class
                    _classname = subject.substring(0,subject.length()-6);
                    if (_debug) System.err.println("  CLASS="+_classname);
                }
                else
                {
                    // single JAR file
                    File f = new File(file);                        
                    String d = f.getCanonicalPath();
                    if (!done.containsKey(d))
                    {
                        done.put(d,d);
                        
                        boolean added=_classpath.addComponent(d);

                        if (!added)
                        {
                            added=_classpath.addClasspath(expand(subject));
                            if (_debug)
                                System.err.println((added?"  CLASSPATH+=":"  !")+d);
                        }
                        else if (_debug)
                            System.err.println((added?"  CLASSPATH+=":"  !")+d);
                    }
                }
            }
            catch (Exception e)
            {
                System.err.println("on line: '"+line+"'");
                e.printStackTrace();
            }
        }
    }
    
    
    /* ------------------------------------------------------------ */
    public void run(String[] args)
    {    
        // set up classpath:
        
        try
        {
            InputStream cpcfg =getClass().getClassLoader()
                .getResourceAsStream("org/mortbay/start/start.config");
            configure(cpcfg,args);
            cpcfg.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        
        // okay, classpath complete.
        System.setProperty("java.class.path",_classpath.toString());
        ClassLoader cl = _classpath.getClassLoader();

        if (_debug) System.err.println("jetty.home="+System.getProperty("jetty.home"));
        if (_debug) System.err.println("java.io.tmpdir="+System.getProperty("java.io.tmpdir"));
        if (_debug) System.err.println("java.class.path="+_classpath.toString());
        

        // Invoke main(args) using new classloader.
        Thread.currentThread().setContextClassLoader(cl);
            
        try
        {
            if (_xml.size()>0)
            {
                for (int i=0;i<args.length;i++)
                    _xml.add(args[i]);
                args=(String[])_xml.toArray(args);
            }
                
            invokeMain(cl,_classname,args);            
        }
        catch (Exception e)
        {
            e.printStackTrace();
        } 
    }
}
