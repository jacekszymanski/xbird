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
package xbird.util.concurrent.counter;

import sun.misc.Unsafe;
import xbird.util.hashes.HashUtils;
import xbird.util.lang.UnsafeUtils;

/**
 * 
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public final class StripeAtomicIntCounter2 implements ICounter {
    private static final long serialVersionUID = -9202959371917424737L;

    private static final Unsafe _unsafe = UnsafeUtils.getUnsafe();
    private static final int _base = _unsafe.arrayBaseOffset(int[].class);
    private static final int _scale = _unsafe.arrayIndexScale(int[].class);
    private final int[] _array;
    private volatile int _macguffin;

    private final int _size;
    private final int _mask;

    private volatile int _fuzzy_sum_cache;
    private volatile long _fuzzy_time;

    public StripeAtomicIntCounter2(int nstripe) {
        assert (nstripe > 0) : nstripe;
        final int power2 = HashUtils.nextPowerOfTwo(nstripe);
        this._size = power2;
        this._mask = power2 - 1;

        this._array = new int[power2];
        // must perform at least one volatile write to conform to JMM
        this._macguffin = 0;
    }

    public int get() {
        int sum = getVolatile(0); // force volatile read
        final int[] ary = _array;
        for(int i = 1; i < _size; i++) {
            sum += ary[i];
        }
        return sum;
    }

    public int estimateGet() {
        if(_size < 64) {
            return get();
        }
        final long millis = System.currentTimeMillis();
        if(_fuzzy_time != millis) {
            _fuzzy_sum_cache = get();
            _fuzzy_time = millis; // Indicate freshness of cached value
        }
        return _fuzzy_sum_cache;
    }

    public void add(final int x) {
        int idx = HashUtils.hash(Thread.currentThread(), _size - 1);
        addAndGet(idx, x);
    }

    public void add2(final int x) {
        int hash = HashUtils.hash(Thread.currentThread());
        int idx = hash & _mask;
        addAndGet(idx, x);
    }

    public int getAndAdd(final int x) {
        int l = get();
        add(x);
        return l;
    }

    public int addAndGet(final int x) {
        add(x);
        return get();
    }

    public int incrementAndGet() {
        return addAndGet(1);
    }

    public int decrementAndGet() {
        return addAndGet(-1);
    }

    public int getAndIncrement() {
        return getAndAdd(1);
    }

    public int getAndDecrement() {
        return getAndAdd(-1);
    }

    private int addAndGet(final int i, final int delta) {
        while(true) {
            int current = getVolatile(i);
            int next = current + delta;
            if(compareAndSet(i, current, next)) {
                return next;
            }
        }
    }

    private int getVolatile(final int i) {
        return _array[i + _macguffin];
    }

    private boolean compareAndSet(final int i, final int expect, final int update) {
        return _unsafe.compareAndSwapInt(_array, rawIndex(i), expect, update);
    }

    private long rawIndex(final int i) {
        assert (i >= 0 && i < _array.length) : "Illegal index: " + i;
        return _base + i * _scale;
    }

}
