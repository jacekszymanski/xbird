/*
 * @(#)$Id: XQTSTest.template 946 2006-09-14 03:31:56Z yui $
 *
 * Copyright 2006-2008 Makoto YUI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Contributors:
 *     Makoto YUI - initial implementation
 */
package xqts.expressions.operators.compexpr.valcomp.durationdatetimeop.yearmonthdurationlt;

import junit.framework.TestCase;
import xqts.XQTSTestBase;

public class YearMonthDurationLTTest extends TestCase {

    private static final String TEST_PATH = "((//ns:test-group[count(child::ns:test-group)=0])[57]//ns:test-case)";
    private static final String TARGET_XQTS_VERSION = "1.0.2";
    
    private final XQTSTestBase xqts;

    public YearMonthDurationLTTest() {
    	super(YearMonthDurationLTTest.class.getName());
        this.xqts = new XQTSTestBase(YearMonthDurationLTTest.class.getName(), TARGET_XQTS_VERSION);
    }
    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args1() throws Exception {
        xqts.invokeTest(TEST_PATH + "[1]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args2() throws Exception {
        xqts.invokeTest(TEST_PATH + "[2]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args3() throws Exception {
        xqts.invokeTest(TEST_PATH + "[3]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args4() throws Exception {
        xqts.invokeTest(TEST_PATH + "[4]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args5() throws Exception {
        xqts.invokeTest(TEST_PATH + "[5]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args6() throws Exception {
        xqts.invokeTest(TEST_PATH + "[6]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2argsNew7() throws Exception {
        xqts.invokeTest(TEST_PATH + "[7]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args8() throws Exception {
        xqts.invokeTest(TEST_PATH + "[8]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args9() throws Exception {
        xqts.invokeTest(TEST_PATH + "[9]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan2args10() throws Exception {
        xqts.invokeTest(TEST_PATH + "[10]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan3() throws Exception {
        xqts.invokeTest(TEST_PATH + "[11]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan4() throws Exception {
        xqts.invokeTest(TEST_PATH + "[12]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan5() throws Exception {
        xqts.invokeTest(TEST_PATH + "[13]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan6() throws Exception {
        xqts.invokeTest(TEST_PATH + "[14]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThanNew7() throws Exception {
        xqts.invokeTest(TEST_PATH + "[15]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan8() throws Exception {
        xqts.invokeTest(TEST_PATH + "[16]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan9() throws Exception {
        xqts.invokeTest(TEST_PATH + "[17]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan10() throws Exception {
        xqts.invokeTest(TEST_PATH + "[18]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan11() throws Exception {
        xqts.invokeTest(TEST_PATH + "[19]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan12() throws Exception {
        xqts.invokeTest(TEST_PATH + "[20]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan13() throws Exception {
        xqts.invokeTest(TEST_PATH + "[21]");
    }

    @org.junit.Test(timeout=300000)
    public void testOpYearMonthDurationLessThan14() throws Exception {
        xqts.invokeTest(TEST_PATH + "[22]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT1() throws Exception {
        xqts.invokeTest(TEST_PATH + "[23]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT2() throws Exception {
        xqts.invokeTest(TEST_PATH + "[24]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT3() throws Exception {
        xqts.invokeTest(TEST_PATH + "[25]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT4() throws Exception {
        xqts.invokeTest(TEST_PATH + "[26]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT5() throws Exception {
        xqts.invokeTest(TEST_PATH + "[27]");
    }

    @org.junit.Test(timeout=300000)
    public void testKYearMonthDurationLT6() throws Exception {
        xqts.invokeTest(TEST_PATH + "[28]");
    }

}