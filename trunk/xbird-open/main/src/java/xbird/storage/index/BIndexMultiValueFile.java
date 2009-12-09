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
package xbird.storage.index;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import xbird.storage.DbException;
import xbird.util.collections.longs.LongArrayList;
import xbird.util.collections.longs.LongHash.LongLRUMap;
import xbird.util.lang.PrintUtils;
import xbird.util.primitives.Primitives;

/**
 * 
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public final class BIndexMultiValueFile extends BIndexFile {
    private static final Log LOG = LogFactory.getLog(BIndexMultiValueFile.class);

    private final LongLRUMap<MultiPtrs> ptrsCache = new LongLRUMap<MultiPtrs>(512);

    public BIndexMultiValueFile(File file) {
        super(file, false);
        BFileHeader fh = getFileHeader();
        fh.setMultiValue(true);
    }

    public BIndexMultiValueFile(File file, int pageSize, int caches) {
        super(file, pageSize, caches, false);
        BFileHeader fh = getFileHeader();
        fh.setMultiValue(true);
    }

    @Override
    public synchronized long addValue(final Value key, final Value value) throws DbException {
        final long valuePtr = storeValue(value);
        final long ptr = findValue(key);
        if(ptr != KEY_NOT_FOUND) {// key found
            // update the page
            MultiPtrs ptrs = ptrsCache.get(ptr);
            if(ptrs == null) {//TODO concurrent insertion is too slow..
                byte[] ptrTuple = retrieveTuple(ptr);
                ptrs = MultiPtrs.readFrom(ptrTuple);
                ptrsCache.put(ptr, ptrs);
            }
            ptrs.addPointer(valuePtr);
            updateValue(ptr, ptrs);
            return ptr;
        } else {
            // insert a new key           .
            MultiPtrs ptrs = new MultiPtrs(valuePtr);
            long newPtr = storeValue(ptrs);
            addValue(key, newPtr);
            ptrsCache.put(newPtr, ptrs);
            return newPtr;
        }
    }

    @Override
    protected BTreeCallback getHandler(BTreeCallback handler) {
        return new BIndexMultiValueCallback(handler);
    }

    private final class BIndexMultiValueCallback implements BTreeCallback {

        final BTreeCallback handler;

        public BIndexMultiValueCallback(BTreeCallback handler) {
            this.handler = handler;
        }

        public boolean indexInfo(Value key, long pointer) {
            MultiPtrs ptrs = ptrsCache.get(pointer);
            if(ptrs == null) {
                final byte[] ptrTuple;
                try {
                    ptrTuple = retrieveTuple(pointer);
                } catch (DbException e) {
                    throw new IllegalStateException(e);
                }
                ptrs = MultiPtrs.readFrom(ptrTuple);
            }
            final LongArrayList lptrs = ptrs.getPointers();
            final int size = lptrs.size();
            for(int i = 0; i < size; i++) {
                final long lptr = lptrs.get(i);
                final byte[] value;
                try {
                    value = retrieveTuple(lptr);
                } catch (DbException e) {
                    LOG.error(PrintUtils.prettyPrintStackTrace(e));
                    throw new IllegalStateException(e);
                }
                if(!handler.indexInfo(key, value)) {
                    return false;
                }
            }
            return true;
        }

        public boolean indexInfo(Value key, byte[] value) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MultiPtrs extends Value {

        private LongArrayList _ptrs;
        private int _used;
        private int _free;

        public MultiPtrs() {
            super();
        }

        public MultiPtrs(long ptr) {
            super(initData(ptr));
            this._ptrs = new LongArrayList(4);
            _ptrs.add(ptr);
            this._used = 1;
            this._free = 3;
        }

        private MultiPtrs(byte[] b, long[] ptrs, int used, int free) {
            super(b);
            this._ptrs = new LongArrayList(ptrs, used);
            this._used = used;
            this._free = free;
        }

        public LongArrayList getPointers() {
            return _ptrs;
        }

        public void addPointer(final long ptr) {
            final byte[] oldData = _data;
            final int offset = (_used + 1) << 3; //8 + (_used * 8);
            _ptrs.add(ptr);
            _used++;
            if((_free--) > 0) {
                Primitives.putInt(oldData, 0, _used);
                Primitives.putLong(oldData, offset, ptr);
            } else {
                this._free = _used; // doubling spaces
                int newLen = 8 + (_used << 4); //8 + ((_used + _free) << 3);
                final byte[] newData = new byte[newLen];

                System.arraycopy(oldData, 0, newData, 0, oldData.length);
                Primitives.putInt(newData, 0, _used);
                Primitives.putInt(newData, 4, _free);
                Primitives.putLong(newData, offset, ptr);

                this._data = newData;
                this._pos = 0;
                this._len = newLen;
            }
        }

        private static byte[] initData(long ptr) {
            final byte[] b = new byte[40]; // 8 + (8 * (3 + 1))
            Primitives.putInt(b, 0, 1);
            Primitives.putInt(b, 4, 3);
            Primitives.putLong(b, 8, ptr);
            return b;
        }

        public static MultiPtrs readFrom(byte[] b) {
            final int used = Primitives.getInt(b, 0);
            final int free = Primitives.getInt(b, 4);
            final long[] ptrs = new long[(used * 3) / 2];
            int idx = 8;
            for(int i = 0; i < used; i++) {
                ptrs[i] = Primitives.getLong(b, idx);
                idx += 8;
            }
            return new MultiPtrs(b, ptrs, used, free);
        }

    }
}
