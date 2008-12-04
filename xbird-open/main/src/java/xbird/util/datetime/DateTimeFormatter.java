/*
 * @(#)$Id$
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
package xbird.util.datetime;

/**
 * 
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public final class DateTimeFormatter {

    private DateTimeFormatter() {}

    public static String formatTime(long t) {
        final StringBuilder buf = new StringBuilder();
        long hour = t / 3600000;
        if(hour > 0) {
            buf.append(hour + "h ");
            t = t % 3600000;
        }
        long min = t / 60000;
        if(min > 0) {
            buf.append(min + "m ");
            t = t % 60000;
        }
        long sec = t / 1000;
        if(sec > 0) {
            buf.append(sec + "s ");
            t = t % 1000;
        }
        if(t > 0) {
            buf.append(t + "ms");
        }
        return buf.toString();
    }

    public static String formatNanoTime(final long t) {
        final long ms = t / 1000000;
        return formatTime(ms);
    }

}
