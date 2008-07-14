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
package xbird.util.io;

import java.io.*;

/**
 * 
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405 AT gmail.com)
 */
public final class NoopWriter extends Writer {

    public NoopWriter() {}

    public NoopWriter(Object lock) {
        super(lock);
    }

    @Override
    public Writer append(char c) throws IOException {
        return super.append(c);
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) throws IOException {
        return super.append(csq, start, end);
    }

    @Override
    public Writer append(CharSequence csq) throws IOException {
        return super.append(csq);
    }

    @Override
    public void write(char[] cbuf) throws IOException {
        super.write(cbuf);
    }

    @Override
    public void write(int c) throws IOException {
        super.write(c);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        super.write(str, off, len);
    }

    @Override
    public void write(String str) throws IOException {
        super.write(str);
    }

    @Override
    public void close() throws IOException {}

    @Override
    public void flush() throws IOException {}

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {}

}
