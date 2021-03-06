// ========================================================================
// $Id$
// Copyright 1997-2004 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.html;

import junit.framework.TestSuite;


/* ------------------------------------------------------------ */
/** Util meta JUnitTestHarness.
 * @version $Id$
 * @author Juancarlo A�ez <juancarlo@modelistica.com>
 */
public class Tests extends junit.framework.TestCase
{
    public Tests(String name)
    {
      super(name);
    }

    public static junit.framework.Test suite() {
      return new TestSuite(Tests.class);
    }

    /* ------------------------------------------------------------ */
    /** main.
     */
    public static void main(String[] args)
    {
      junit.textui.TestRunner.run(suite());
    }

    public void testPlaceHolder()
    {
      assertTrue(true);
    }
}
