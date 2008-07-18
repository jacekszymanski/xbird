/*
 * @(#)$Id: BTree.java 3619 2008-03-26 07:23:03Z yui $
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
 *     Makoto YUI - ported from Apache Xindice with some modification
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
 */
package xbird.storage.index;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import xbird.storage.DbException;
import xbird.storage.indexer.BasicIndexQuery;
import xbird.storage.indexer.IndexQuery;
import xbird.util.codec.VariableByteCodec;
import xbird.util.collections.LongHash;
import xbird.util.collections.ObservableLongLRUMap;
import xbird.util.collections.LongHash.BucketEntry;
import xbird.util.collections.LongHash.Cleaner;
import xbird.util.io.FastMultiByteArrayOutputStream;
import xbird.util.lang.ArrayUtils;

/**
 * BTree represents a Variable Magnitude Simple-Prefix B+Tree File.
 * <DIV lang="en"></DIV>
 * <DIV lang="ja"></DIV>
 * 
 * @author Makoto YUI (yuin405+xbird@gmail.com)
 */
public class BTree extends Paged {
    private static final Log LOG = LogFactory.getLog(BTree.class);

    /** If page size is 4k, 2m (4k * 512) cache */
    public static final int DEFAULT_IN_MEMORY_NODES = 512;
    public static final int KEY_NOT_FOUND = -1;

    private static final int LEAST_KEYS = 5;

    private static final byte[] EmptyBytes = new byte[0];
    private static final Value EmptyValue = new Value(EmptyBytes);

    protected static final byte LEAF = 1;
    protected static final byte BRANCH = 2;

    /**
     * Cache of the recently used tree nodes.
     *
     * Cache contains weak references to the BTreeNode objects, keys are page numbers (Long objects).
     * Access synchronized by this map itself.
     */
    private final LongHash<BTreeNode> _cache;

    private final BTreeFileHeader _fileHeader;

    private BTreeRootInfo _rootInfo;
    private BTreeNode _rootNode;

    public BTree(File file) {
        this(file, true);
    }

    public BTree(File file, boolean duplicateAllowed) {
        this(file, DEFAULT_PAGESIZE, DEFAULT_IN_MEMORY_NODES, duplicateAllowed);
    }

    public BTree(File file, int pageSize, int caches, boolean duplicateAllowed) {
        super(file, pageSize);
        BTreeFileHeader fh = getFileHeader();
        fh.incrTotalPageCount(); // for root page
        fh._duplicateAllowed = duplicateAllowed;
        this._fileHeader = fh;
        final Synchronizer sync = new Synchronizer();
        final int purgeSize = Math.max(caches >>> 2, 16); // perge 1/4 pages or 16 pages at a time
        this._cache = new ObservableLongLRUMap<BTreeNode>(caches, purgeSize, sync);
    }

    public void init(boolean bulkload) throws DbException {
        if(!exists()) {
            boolean created = create(false);
            if(!created) {
                throw new IllegalStateException("create B+Tree file failed: "
                        + _file.getAbsolutePath());
            }
        } else {
            open();
        }
    }

    private static final class Synchronizer implements Cleaner<BTreeNode> {

        Synchronizer() {}

        public void cleanup(long key, BTreeNode node) {
            if(!node.dirty) {
                return;
            }
            try {
                node.write();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (DbException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    @Override
    public boolean open() throws DbException {
        if(super.open()) {
            final long p = _fileHeader.getRootPage();
            this._rootInfo = new BTreeRootInfo(p);
            this._rootNode = getBTreeNode(_rootInfo, p, null);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean create(boolean close) throws DbException {
        if(super.create(false)) {
            // Don't call this.open() as it will try to read rootNode from the disk
            super.open();
            // Initialize root node
            long p = _fileHeader.getRootPage();
            this._rootInfo = new BTreeRootInfo(p);
            this._rootNode = new BTreeNode(_rootInfo, getPage(p));
            _rootNode.ph.setStatus(LEAF);
            _rootNode.set(new Value[0], new long[0]);
            try {
                _rootNode.write();
            } catch (IOException e) {
                throw new DbException(e);
            }
            synchronized(_cache) {
                _cache.put(_rootNode.page.getPageNum(), _rootNode);
            }
            if(close) {
                close();
            }
            return true;
        }
        return false;
    }

    protected final boolean isDuplicateAllowed() {
        return _fileHeader._duplicateAllowed;
    }

    /**
     * addValue adds a Value to the BTree and associates a pointer with
     * it.  The pointer can be used for referencing any type of data, it
     * just so happens that Xindice uses it for referencing pages of
     * associated data in the BTree file or other files.
     *
     * @param value The Value to add
     * @param pointer The pointer to associate with it
     * @return The previous value for the pointer (or -1)
     */
    public long addValue(Value value, long pointer) throws DbException {
        try {
            return getRootNode().addValue(value, pointer);
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    /**
     * addValue adds a Value to the BTree and associates a pointer with
     * it.  The pointer can be used for referencing any type of data, it
     * just so happens that Xindice uses it for referencing pages of
     * associated data in the BTree file or other files.
     *
     * @param root The BTree's root information (for nested trees)
     * @param value The Value to add
     * @param pointer The pointer to associate with it
     * @return The previous value for the pointer (or -1)
     */
    public long addValue(BTreeRootInfo root, Value value, long pointer) throws DbException {
        try {
            return getRootNode(root).addValue(value, pointer);
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    /**
     * removeValue removes a Value from the BTree and returns the
     * associated pointer for it.
     *
     * @param value The Value to remove
     * @return The pointer that was associated with it
     */
    public long removeValue(Value value) throws DbException {
        try {
            return getRootNode().removeValue(value);
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    public long[] removeValue(Value value, long pointer) throws DbException {
        try {
            return getRootNode().removeValue(value, pointer);
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    /**
     * removeValue removes a Value from the BTree and returns the
     * associated pointer for it.
     *
     * @param root The BTree's root information (for nested trees)
     * @param value The Value to remove
     * @return The pointer that was associated with it
     */
    public long removeValue(BTreeRootInfo root, Value value) throws DbException {
        try {
            return getRootNode(root).removeValue(value);
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    /**
     * findValue finds a Value in the BTree and returns the associated
     * pointer for it.
     *
     * @param value The Value to find
     * @return The pointer that was associated with it
     */
    public long findValue(Value value) throws DbException {
        return getRootNode().findValue(value);
    }

    /**
     * findValue finds a Value in the BTree and returns the associated
     * pointer for it.
     *
     * @param root The BTree's root information (for nested trees)
     * @param value The Value to find
     * @return The pointer that was associated with it
     */
    @Deprecated
    public long findValue(BTreeRootInfo root, Value value) throws DbException {
        return getRootNode(root).findValue(value);
    }

    public enum SearchType {
        LEFT_MOST, LEFT /* normal */, RIGHT, RIGHT_MOST
    }

    /**
     * query performs a query against the BTree and performs callback
     * operations to report the search results.
     *
     * @param query The IndexQuery to use
     * @param callback The callback instance
     */
    public void search(IndexQuery query, BTreeCallback callback) throws DbException {
        if(query == null) {
            throw new IllegalArgumentException();
        }
        BTreeNode root = getRootNode();
        Value[] keys = query.getOperands();
        assert (keys.length >= 1) : keys.length;
        final int op = query.getOperator();
        try {
            switch(op) {
                case BasicIndexQuery.EQ: {
                    if(isDuplicateAllowed()) {
                        BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                        BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[0]);
                        scanRange(left, right, query, callback);
                    } else {
                        BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                        left.scanLeaf(query, callback, true);
                    }
                    break;
                }
                case BasicIndexQuery.GT:
                case BasicIndexQuery.GE: {
                    BTreeNode right = root.getLeafNode(SearchType.LEFT, keys[keys.length - 1]);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    scanRange(right, rightmost, query, callback);
                    break;
                }
                case BasicIndexQuery.LE:
                case BasicIndexQuery.LT: {
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    scanRange(leftmost, left, query, callback);
                    break;
                }
                case BasicIndexQuery.NE:
                case BasicIndexQuery.NBW:
                case BasicIndexQuery.NOT_IN:
                case BasicIndexQuery.NOT_START_WITH:
                case BasicIndexQuery.NBWX: {
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[keys.length - 1]);
                    scanRange(leftmost, left, query, callback);
                    long lp = left.page.getPageNum(), rp = right.page.getPageNum();
                    if(lp != rp) {
                        scanRange(right, rightmost, query, callback);
                    }
                    break;
                }
                case BasicIndexQuery.BW:
                case BasicIndexQuery.START_WITH:
                case BasicIndexQuery.IN:
                case BasicIndexQuery.BWX: {
                    BTreeNode left = root.getLeafNode(SearchType.LEFT, keys[0]);
                    BTreeNode right = root.getLeafNode(SearchType.RIGHT, keys[keys.length - 1]);
                    scanRange(left, right, query, callback);
                    break;
                }
                default: {
                    BTreeNode leftmost = root.getLeafNode(SearchType.LEFT_MOST, null);
                    BTreeNode rightmost = root.getLeafNode(SearchType.RIGHT_MOST, null);
                    scanRange(leftmost, rightmost, query, callback);
                    break;
                }
            }
        } catch (IOException e) {
            throw new DbException(e);
        }
    }

    private final void scanRange(BTreeNode left, BTreeNode right, IndexQuery query, BTreeCallback callback)
            throws DbException {
        final long rightmostPageNum = right.page.getPageNum();
        if(LOG.isInfoEnabled()) {
            LOG.info("scan range [" + left.page.getPageNum() + ", " + rightmostPageNum + "] start");
        }
        BTreeNode cur = left;
        int scaned = 0;
        while(true) {
            long curPageNum = cur.page.getPageNum();
            if(curPageNum == rightmostPageNum) {
                cur.scanLeaf(query, callback, true);
                ++scaned;
                break;
            } else {
                cur.scanLeaf(query, callback, scaned == 0);
                ++scaned;
            }
            long next = cur._next;
            if(next == curPageNum) {
                throw new IllegalStateException("detected a cyclic link at page#" + curPageNum);
            } else if(next == -1L) {
                throw new IllegalStateException("range scan failed... bug?");
            }
            cur = getBTreeNode(_rootInfo, next, null);
        }
        if(LOG.isInfoEnabled()) {
            LOG.info("scan range end. total scaned pages: " + scaned);
        }
    }

    public FileHeader createFileHeader(int pageSize) {
        return new BTreeFileHeader(pageSize);
    }

    public PageHeader createPageHeader() {
        return new BTreePageHeader();
    }

    @Override
    public BTreeFileHeader getFileHeader() {
        return (BTreeFileHeader) super.getFileHeader();
    }

    /**
     * getRootNode retreives the BTree node for the file's root.
     */
    protected final BTreeNode getRootNode() {
        return _rootNode;
    }

    /**
     * getRootNode retreives the BTree node for the specified
     * root object.
     *
     * @param root The root object to retrieve with
     * @return The root node
     */
    protected final BTreeNode getRootNode(BTreeRootInfo root) throws DbException {
        if(root.page == _rootInfo.page) {
            return _rootNode;
        } else {
            return getBTreeNode(root, root.getPage(), null);
        }
    }

    private final BTreeNode getBTreeNode(BTreeRootInfo root, long page, BTreeNode parent)
            throws DbException {
        BTreeNode node;
        synchronized(_cache) {
            node = _cache.get(page);
            if(node == null) {
                node = new BTreeNode(root, getPage(page), parent);
                try {
                    node.read();
                } catch (IOException e) {
                    throw new DbException("failed to read page#" + page, e);
                }
                if(LOG.isDebugEnabled()) {
                    LOG.debug("read node page#" + page + ", keys: " + node.keys.length);
                }
                _cache.put(node.page.getPageNum(), node);
            } else {
                node.parent = parent;
            }
        }
        return node;
    }

    private final BTreeNode createBTreeNode(BTreeRootInfo root, byte status, BTreeNode parent)
            throws DbException {
        Page p = getFreePage();
        BTreeNode node = new BTreeNode(root, p, parent);
        //node.set(new Value[0], new long[0]);
        node.ph.setStatus(status);
        synchronized(_cache) {
            _cache.put(p.getPageNum(), node);
        }
        return node;
    }

    public void flush(boolean purge, boolean clear) throws DbException {
        if(purge) {
            for(BucketEntry<BTreeNode> e : _cache) {
                BTreeNode node = e.getValue();
                if(node == null) {
                    continue;
                }
                try {
                    node.write();
                } catch (IOException ioe) {
                    throw new DbException(ioe);
                }
            }
        }
        if(clear) {
            _cache.clear();
        }
        super.flush();
    }

    private static final class BTreeRootInfo {

        private final BTreeRootInfo parent;
        private final Value name;
        private final long page;

        private BTreeRootInfo(final long page) {
            this.parent = null;
            this.name = null;
            this.page = page;
        }

        public Value getName() {
            return name;
        }

        public long getPage() {
            return page;
        }

        public BTreeRootInfo getParent() {
            return parent;
        }
    }

    private final class BTreeNode {

        private final BTreeRootInfo root;
        private final Page page;
        private final BTreePageHeader ph;
        private BTreeNode parent;

        private Value[] keys;
        private long[] ptrs;
        private long _next = -1;
        private long _prev = -1; // internal entry for debugging
        private Value prefix = null;

        private boolean loaded = false;
        private int currentDataLen = -1;
        private boolean dirty = false;

        //--------------------------------------------

        protected BTreeNode(final BTreeRootInfo root, final Page page, final BTreeNode parent) {
            this.root = root;
            this.page = page;
            this.ph = (BTreePageHeader) page.getPageHeader();
            this.parent = parent;
        }

        protected BTreeNode(final BTreeRootInfo root, final Page page) {
            this(root, page, null);
        }

        private synchronized long addValue(Value value, long pointer) throws IOException,
                DbException {
            if(value == null) {
                throw new IllegalArgumentException("Can't add a null Value");
            }
            int idx = searchRightmostKey(keys, value, keys.length);
            switch(ph.getStatus()) {
                case BRANCH:
                    idx = idx < 0 ? -(idx + 1) : idx + 1;
                    return getChildNode(idx).addValue(value, pointer);
                case LEAF:
                    final boolean found = idx >= 0;
                    final long oldPtr;
                    if(found) {
                        if(!isDuplicateAllowed()) {
                            throw new BTreeCorruptException("Attempt to add duplicate key to the unique index: "
                                    + value);
                        }
                        oldPtr = ptrs[idx];
                        value = keys[idx];
                        idx = idx + 1;
                    } else {
                        oldPtr = -1;
                        idx = -(idx + 1);
                    }
                    set(ArrayUtils.<Value> insert(keys, idx, value), ArrayUtils.insert(ptrs, idx, pointer));
                    incrDataLength(value, pointer);

                    // Check to see if we've exhausted the block
                    if(needSplit()) {
                        split();
                    }
                    return oldPtr;
                default:
                    throw new BTreeCorruptException("Invalid Page Type '" + ph.getStatus()
                            + "' was detected for page#" + page.getPageNum());
            }
        }

        /** search the leftmost key for duplicate allowed index */
        private int searchLeftmostKey(final Value[] ary, final Value key, final int to) {
            if(!_fileHeader._duplicateAllowed) {
                return Arrays.binarySearch(keys, 0, to, key);
            }
            int low = 0;
            int high = to - 1;
            while(low <= high) {
                int mid = (low + high) >>> 1;
                Value midVal = ary[mid];
                int cmp = midVal.compareTo(key);
                if(cmp < 0) {
                    low = mid + 1;
                } else if(cmp > 0) {
                    high = mid - 1;
                } else {
                    for(int i = mid - 1; i >= 0; i--) {
                        Value nxtVal = ary[i];
                        cmp = midVal.compareTo(nxtVal);
                        if(cmp != 0) {
                            break;
                        }
                        mid = i;
                    }
                    return mid; // key found
                }
            }
            return -(low + 1); // key not found.
        }

        /** search the rightmost key for duplicate allowed index */
        private int searchRightmostKey(final Value[] ary, final Value key, final int to) {
            if(!_fileHeader._duplicateAllowed) {
                return Arrays.binarySearch(keys, 0, to, key);
            }
            int low = 0;
            int high = to - 1;
            while(low <= high) {
                int mid = (low + high) >>> 1;
                Value midVal = ary[mid];
                int cmp = midVal.compareTo(key);
                if(cmp < 0) {
                    low = mid + 1;
                } else if(cmp > 0) {
                    high = mid - 1;
                } else {
                    for(int i = mid + 1; i <= high; i++) {
                        Value nxtVal = ary[i];
                        cmp = midVal.compareTo(nxtVal);
                        if(cmp != 0) {
                            break;
                        }
                        mid = i;
                    }
                    return mid; // key found
                }
            }
            return -(low + 1); // key not found.
        }

        /** @return pointer of left-most matched item */
        private synchronized long removeValue(Value value) throws IOException, DbException {
            int leftIdx = searchLeftmostKey(keys, value, keys.length);
            switch(ph.getStatus()) {
                case BRANCH:
                    leftIdx = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
                    return getChildNode(leftIdx).removeValue(value);
                case LEAF:
                    if(leftIdx < 0) {
                        return KEY_NOT_FOUND;
                    } else {
                        long oldPtr = ptrs[leftIdx];
                        set(ArrayUtils.remove(keys, leftIdx), ArrayUtils.remove(ptrs, leftIdx));
                        decrDataLength(value);
                        return oldPtr;
                    }
                default:
                    throw new BTreeCorruptException("Invalid page type '" + ph.getStatus()
                            + "' in removeValue");
            }
        }

        /** @return pointer of matched items */
        @Deprecated
        private synchronized long[] removeValue(Value value, long pointer) throws IOException,
                DbException {
            int leftIdx = searchLeftmostKey(keys, value, keys.length);
            int rightIdx = isDuplicateAllowed() ? searchRightmostKey(keys, value, keys.length)
                    : leftIdx;
            switch(ph.getStatus()) {
                case BRANCH:
                    leftIdx = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
                    //FIXME keys may be separated nodes
                    return getChildNode(leftIdx).removeValue(value, pointer);
                case LEAF:
                    if(leftIdx < 0) {
                        return new long[0];
                    } else {
                        int founds = 0;
                        long[] matched = new long[rightIdx - leftIdx + 1];
                        for(int i = leftIdx; i < rightIdx; i++) {
                            long p = ptrs[i];
                            if(p == pointer) {
                                set(ArrayUtils.remove(keys, i), ArrayUtils.remove(ptrs, i));
                                decrDataLength(value);
                                matched[founds++] = p;
                            }
                        }
                        if(founds == 0) {
                            return new long[0];
                        }
                        return (founds == matched.length) ? matched
                                : ArrayUtils.copyOfRange(matched, 0, founds);
                    }
                default:
                    throw new BTreeCorruptException("Invalid page type '" + ph.getStatus()
                            + "' in removeValue");
            }
        }

        /**
         * Internal (to the BTreeNode) method.
         * Because this method is called only by BTreeNode itself,
         * no synchronization done inside of this method.
         */
        private BTreeNode getChildNode(final int idx) throws DbException {
            if(ph.getStatus() == BRANCH && idx >= 0 && idx < ptrs.length) {
                return getBTreeNode(root, ptrs[idx], this);
            }
            return null;
        }

        /**
         * Need to split this node after adding one more value?
         * 
         * @see #write()
         */
        private boolean needSplit() {
            int afterKeysLength = keys.length + 1;
            if(afterKeysLength < LEAST_KEYS) {// at least 5 elements in a node
                return false;
            }
            if(afterKeysLength > Short.MAX_VALUE) {
                return true;
            }
            assert (prefix != null);
            // CurrLength + one Long pointer + value length + one int (for value length)
            // actual datalen is smaller than this datalen, because prefix is used.
            int datalen = calculateDataLength();
            int worksize = _fileHeader.getWorkSize();
            return datalen > worksize;
        }

        /**
         * Internal to the BTreeNode method
         */
        private void split() throws IOException, DbException {
            final Value[] leftVals;
            final Value[] rightVals;
            final long[] leftPtrs;
            final long[] rightPtrs;
            final Value separator;

            final short vc = ph.getValueCount();
            int pivot = vc / 2;

            // Split the node into two nodes
            final byte pageType = ph.getStatus();
            int leftLookup = 0;
            switch(pageType) {
                case BRANCH:
                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length + 1];
                    rightVals = new Value[vc - (pivot + 1)];
                    rightPtrs = new long[rightVals.length + 1];

                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length + 1, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

                    separator = keys[leftVals.length];
                    break;
                case LEAF:
                    Value pivotLeft = keys[pivot - 1];
                    Value pivotRight = keys[pivot];
                    if(pivotLeft.equals(pivotRight)) {
                        int leftmost = searchLeftmostKey(keys, pivotLeft, pivot - 1);
                        int diff = pivot - leftmost;
                        if(diff < 0 || diff > Short.MAX_VALUE) {
                            throw new IllegalStateException("pivot: " + pivot + ", leftmost: "
                                    + leftmost + "\nkeys: " + Arrays.toString(keys));
                        }
                        leftLookup = diff;
                    }

                    leftVals = new Value[pivot];
                    leftPtrs = new long[leftVals.length];
                    rightVals = new Value[vc - pivot];
                    rightPtrs = new long[rightVals.length];

                    System.arraycopy(keys, 0, leftVals, 0, leftVals.length);
                    System.arraycopy(ptrs, 0, leftPtrs, 0, leftPtrs.length);
                    System.arraycopy(keys, leftVals.length, rightVals, 0, rightVals.length);
                    System.arraycopy(ptrs, leftPtrs.length, rightPtrs, 0, rightPtrs.length);

                    separator = getSeparator(leftVals[leftVals.length - 1], rightVals[0]);
                    break;
                default:
                    throw new BTreeCorruptException("Invalid page type in split: " + pageType);
            }

            // Promote the pivot to the parent branch
            if(parent == null) {
                // This can only happen if this is the root     
                final BTreeNode lNode = createBTreeNode(root, pageType, this);
                lNode.set(leftVals, leftPtrs);
                lNode.calculateDataLength();

                final BTreeNode rNode = createBTreeNode(root, pageType, this);
                rNode.set(rightVals, rightPtrs);
                rNode.calculateDataLength();

                if(pageType == LEAF) {
                    setLeavesLinked(lNode, rNode);
                }

                ph.setStatus(BRANCH);
                set(new Value[] { separator }, new long[] { lNode.page.getPageNum(),
                        rNode.page.getPageNum() });
                calculateDataLength();

            } else {
                set(leftVals, leftPtrs);
                calculateDataLength();

                final BTreeNode rNode = createBTreeNode(root, pageType, parent);
                rNode.set(rightVals, rightPtrs);
                rNode.calculateDataLength();

                if(pageType == LEAF) {
                    setLeavesLinked(this, rNode);
                    if(leftLookup > 0) {
                        rNode.ph.setLeftLookup((short) leftLookup);
                    }
                }

                final long leftPtr = page.getPageNum();
                parent.promoteValue(separator, rNode.page.getPageNum(), leftPtr);
            }
        }

        /** Set leaves linked 
         * @throws DbException */
        private void setLeavesLinked(final BTreeNode left, final BTreeNode right)
                throws DbException {
            final long leftPageNum = left.page.getPageNum();
            final long rightPageNum = right.page.getPageNum();
            final long origNext = left._next;
            if(origNext != -1L) {
                right._next = origNext;
                BTreeNode origNextNode = getBTreeNode(root, origNext, parent);
                origNextNode._prev = rightPageNum;
                origNextNode.setDirty(true);
            }
            left._next = rightPageNum;
            right._prev = leftPageNum;
        }

        private void promoteValue(final Value key, final long rightPointer, final long leftPtr)
                throws IOException, DbException {
            final int leftIdx = searchRightmostKey(keys, key, keys.length);
            int insertPoint = (leftIdx < 0) ? -(leftIdx + 1) : leftIdx + 1;
            boolean found = false;
            for(int i = insertPoint; i >= 0; i--) {
                final long ptr = ptrs[i];
                if(ptr == leftPtr) {
                    insertPoint = i;
                    found = true;
                    break;
                } else {
                    continue; // just for debugging
                }
            }
            if(!found) {
                throw new IllegalStateException("page#" + page.getPageNum() + ", insertPoint: "
                        + insertPoint + ", leftPtr: " + leftPtr + ", ptrs: "
                        + Arrays.toString(ptrs));
            }
            set(ArrayUtils.<Value> insert(keys, insertPoint, key), ArrayUtils.insert(ptrs, insertPoint + 1, rightPointer));
            incrDataLength(key, rightPointer);

            // Check to see if we've exhausted the block
            if(needSplit()) {
                split();
            }
        }

        /** Gets shortest-possible separator for the pivot */
        private Value getSeparator(final Value value1, final Value value2) {
            int idx = value1.compareTo(value2);
            if(idx == 0) {
                return value1.clone();
            }
            byte[] b = new byte[Math.abs(idx)];
            value2.copyTo(b, 0, b.length);
            return new Value(b);
        }

        /**
         * Sets values and pointers.
         * Internal (to the BTreeNode) method, not synchronized.
         */
        private void set(final Value[] values, final long[] ptrs) {
            final int vlen = values.length;
            if(vlen > Short.MAX_VALUE) {
                throw new IllegalArgumentException("entries exceeds limit: " + vlen);
            }
            this.keys = values;
            this.ptrs = ptrs;
            this.ph.setValueCount((short) vlen);
            if(vlen > 1) {
                final int prevPreixLen = ph.getPrefixLength();
                this.prefix = getPrefix(values[0], values[vlen - 1]);
                final int prefixLen = prefix.getLength();
                assert (prefixLen <= Short.MAX_VALUE) : prefixLen;
                if(prefixLen != prevPreixLen) {
                    final int diff = prefixLen - prevPreixLen;
                    currentDataLen += diff;
                    ph.setPrefixLength((short) prefixLen);
                }
            } else {
                this.prefix = EmptyValue;
                ph.setPrefixLength((short) 0);
            }
            setDirty(true);
            _cache.put(page.getPageNum(), this); // required? REVIEWME
        }

        private void setDirty(final boolean dirt) {
            this.dirty = dirt;
        }

        private Value getPrefix(final Value v1, final Value v2) {
            final int idx = Math.abs(v1.compareTo(v2)) - 1;
            if(idx > 0) {
                final byte[] d2 = v2.getData();
                return new Value(d2, v2.getPosition(), idx);
            } else {
                return EmptyValue;
            }
        }

        /**
         * Reads node only if it is not loaded yet
         */
        private synchronized void read() throws IOException, DbException {
            if(!this.loaded) {
                Value v = readValue(page);
                DataInputStream in = new DataInputStream(v.getInputStream());
                // Read in the common prefix (if any)
                final short pfxLen = ph.getPrefixLength();
                final byte[] pfxBytes;
                if(pfxLen > 0) {
                    pfxBytes = new byte[pfxLen];
                    in.read(pfxBytes);
                    this.prefix = new Value(pfxBytes);
                } else {
                    pfxBytes = EmptyBytes;
                    this.prefix = EmptyValue;
                }
                // Read in the Values
                Value prevKey = null;
                final int keyslen = ph.getValueCount();
                keys = new Value[keyslen];
                for(int i = 0; i < keyslen; i++) {
                    final int valSize = in.readInt();
                    if(valSize == -1) {
                        prevKey.incrRefCount();
                        keys[i] = prevKey;
                    } else {
                        byte[] b = new byte[pfxLen + valSize];
                        if(pfxLen > 0) {
                            System.arraycopy(pfxBytes, 0, b, 0, pfxLen);
                        }
                        if(valSize > 0) {
                            in.read(b, pfxLen, valSize);
                        }
                        prevKey = new Value(b);
                        keys[i] = prevKey;
                    }
                }
                // Read in the pointers
                final int ptrslen = ph.getPointerCount();
                ptrs = new long[ptrslen];
                for(int i = 0; i < ptrslen; i++) {
                    ptrs[i] = VariableByteCodec.decodeLong(in);
                }
                // Read in the links if current node is a leaf
                if(ph.getStatus() == LEAF) {
                    this._prev = in.readLong();
                    this._next = in.readLong();
                }
                this.currentDataLen = v.getLength();
                this.loaded = true;
            }
        }

        private synchronized void write() throws IOException, DbException {
            if(!dirty) {
                return;
            }
            if(LOG.isTraceEnabled()) {
                LOG.trace((ph.getStatus() == LEAF ? "Leaf " : "Branch ") + "Node#"
                        + page.getPageNum() + " - " + Arrays.toString(keys));
            }
            final FastMultiByteArrayOutputStream bos = new FastMultiByteArrayOutputStream(_fileHeader.getWorkSize());
            final DataOutputStream os = new DataOutputStream(bos);

            // write out the prefix
            final short prefixlen = ph.getPrefixLength();
            if(prefixlen > 0) {
                prefix.writeTo(os);
            }
            // Write out the Values
            Value prevKey = null;
            for(int i = 0; i < keys.length; i++) {
                final Value v = keys[i];
                if(v == prevKey) {
                    os.writeInt(-1);
                } else {
                    final int len = v.getLength();
                    final int size = len - prefixlen;
                    os.writeInt(size);
                    if(size > 0) {
                        v.writeTo(os, prefixlen, size);
                    }
                }
                prevKey = v;
            }
            // Write out the pointers
            for(int i = 0; i < ptrs.length; i++) {
                VariableByteCodec.encodeLong(ptrs[i], os);
            }
            // Write out link if current node is a leaf
            if(ph.getStatus() == LEAF) {
                os.writeLong(_prev);
                os.writeLong(_next);
            }

            writeValue(page, new Value(bos.toByteArray()));
            setDirty(false);
        }

        private int calculateDataLength() {
            if(currentDataLen > 0) {
                return currentDataLen;
            }
            final int vlen = keys.length;
            final short prefixlen = ph.getPrefixLength();
            int datalen = prefixlen + (vlen >>> 2) /* key size */;
            Value prevValue = null;
            for(int i = 0; i < vlen; i++) {
                final long ptr = ptrs[i];
                datalen += VariableByteCodec.requiredBytes(ptr);
                final Value v = keys[i];
                if(v == prevValue) {
                    continue;
                }
                final int keylen = v.getLength();
                final int actkeylen = keylen - prefixlen; /* actual keys length */
                datalen += actkeylen;
                prevValue = v;
            }
            if(ph.getStatus() == LEAF) {
                datalen += 16;
            }
            this.currentDataLen = datalen;
            return datalen;
        }

        private void incrDataLength(final Value value, final long ptr) {
            int datalen = currentDataLen;
            if(datalen == -1) {
                datalen = calculateDataLength();
            }
            final int refcnt = value.incrRefCount();
            if(refcnt == 1) {
                datalen += value.getLength();
            }
            datalen += VariableByteCodec.requiredBytes(ptr);
            datalen += 4 /* key size */;
            this.currentDataLen = datalen;
        }

        private void decrDataLength(final Value value) {
            int datalen = currentDataLen;
            final int refcnt = value.decrRefCount();
            if(refcnt == 0) {
                datalen -= value.getLength();
            }
            datalen -= (4 + 8);
            this.currentDataLen = datalen;
        }

        /** find lest-most value which matches to the key */
        private synchronized long findValue(Value value) throws DbException {
            if(value == null) {
                throw new DbException("Can't search on null Value");
            }
            int idx = searchLeftmostKey(keys, value, keys.length);
            switch(ph.getStatus()) {
                case BRANCH:
                    idx = idx < 0 ? -(idx + 1) : idx + 1;
                    return getChildNode(idx).findValue(value);
                case LEAF:
                    if(idx < 0) {
                        return KEY_NOT_FOUND;
                    } else {
                        int lookup = ph.getLeftLookup();
                        if(lookup > 0) {
                            BTreeNode leftmostNode = this;
                            while(true) {
                                final BTreeNode prevNode = getBTreeNode(root, leftmostNode._prev, parent);
                                final int prevLookup = prevNode.ph.getLeftLookup();
                                if(prevLookup == 0) {
                                    break;
                                }
                                leftmostNode = prevNode;
                                lookup = prevLookup;
                            }
                            final long[] leftmostPtrs = leftmostNode.ptrs;
                            final int offset = leftmostPtrs.length - lookup;
                            return leftmostPtrs[offset];
                        } else {
                            return ptrs[idx];
                        }
                    }
                default:
                    throw new BTreeCorruptException("Invalid page type '" + ph.getStatus()
                            + "' in findValue");
            }
        }

        /** 
         * Scan the leaf node. 
         * Note that keys might be shortest-possible value.
         */
        private void scanLeaf(IndexQuery query, BTreeCallback callback, boolean edge) {
            assert (ph.getStatus() == LEAF) : LEAF;
            Value[] conds = query.getOperands();
            switch(query.getOperator()) {
                case BasicIndexQuery.EQ: {
                    if(!edge) {
                        for(int i = 0; i < keys.length; i++) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                        return;
                    }
                    final int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if(leftIdx >= 0) {
                        assert (isDuplicateAllowed());
                        final int rightIdx = searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                        for(int i = leftIdx; i <= rightIdx; i++) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.NE: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    int rightIdx = isDuplicateAllowed() ? searchRightmostKey(keys, conds[conds.length - 1], keys.length)
                            : leftIdx;
                    for(int i = 0; i < ptrs.length; i++) {
                        if(i < leftIdx || i > rightIdx) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.BWX:
                case BasicIndexQuery.BW:
                case BasicIndexQuery.START_WITH:
                case BasicIndexQuery.IN: {
                    if(!edge) {
                        for(int i = 0; i < keys.length; i++) {
                            if(query.testValue(keys[i])) {
                                callback.indexInfo(keys[i], ptrs[i]);
                            }
                        }
                        return;
                    }
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if(leftIdx < 0) {
                        leftIdx = -(leftIdx + 1);
                    }
                    int rightIdx = searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                    if(rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for(int i = leftIdx; i < ptrs.length; i++) {
                        if(i <= rightIdx && query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.NBWX:
                case BasicIndexQuery.NBW:
                case BasicIndexQuery.NOT_START_WITH: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if(leftIdx < 0) {
                        leftIdx = -(leftIdx + 1);
                    }
                    int rightIdx = searchRightmostKey(keys, conds[conds.length - 1], keys.length);
                    if(rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for(int i = 0; i < ptrs.length; i++) {
                        if((i <= leftIdx || i >= rightIdx) && query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
                }
                case BasicIndexQuery.LT: {
                    int leftIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if(leftIdx < 0) {
                        leftIdx = -(leftIdx + 1); // insertion point
                    }
                    for(int i = 0; i < leftIdx; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.LE: {
                    int leftIdx = searchRightmostKey(keys, conds[0], keys.length);
                    if(leftIdx < 0) {
                        leftIdx = -(leftIdx + 1); // insertion point
                    }
                    if(leftIdx >= ptrs.length) {
                        leftIdx = ptrs.length - 1;
                    }
                    for(int i = 0; i <= leftIdx; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.GT: {
                    int rightIdx = searchRightmostKey(keys, conds[0], keys.length);
                    if(rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for(int i = rightIdx + 1; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.GE: {
                    int rightIdx = searchLeftmostKey(keys, conds[0], keys.length);
                    if(rightIdx < 0) {
                        rightIdx = -(rightIdx + 1);
                    }
                    for(int i = rightIdx; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                }
                case BasicIndexQuery.ANY:
                    for(int i = 0; i < ptrs.length; i++) {
                        callback.indexInfo(keys[i], ptrs[i]);
                    }
                    break;
                case BasicIndexQuery.NOT_IN:
                default:
                    for(int i = 0; i < ptrs.length; i++) {
                        if(query.testValue(keys[i])) {
                            callback.indexInfo(keys[i], ptrs[i]);
                        }
                    }
                    break;
            }
        }

        private BTreeNode getLeafNode(SearchType searchType, Value key) throws IOException,
                DbException {
            final byte nodeType = ph.getStatus();
            switch(nodeType) {
                case BRANCH:
                    switch(searchType) {
                        case LEFT: {
                            int leftIdx = searchLeftmostKey(keys, key, keys.length);
                            leftIdx = leftIdx < 0 ? -(leftIdx + 1) : leftIdx + 1;
                            return getChildNode(leftIdx).getLeafNode(searchType, key);
                        }
                        case RIGHT: {
                            int rightIdx = searchRightmostKey(keys, key, keys.length);
                            rightIdx = rightIdx < 0 ? -(rightIdx + 1) : rightIdx + 1;
                            return getChildNode(rightIdx).getLeafNode(searchType, key);
                        }
                        case LEFT_MOST:
                            return getChildNode(0).getLeafNode(searchType, key);
                        case RIGHT_MOST:
                            int rightIdx = ptrs.length - 1;
                            assert (rightIdx >= 0);
                            return getChildNode(rightIdx).getLeafNode(searchType, key);
                        default:
                            throw new IllegalStateException();
                    }
                case LEAF:
                    switch(searchType) {
                        case LEFT: {
                            BTreeNode leftmostNode = this;
                            if(keys[0].equals(key)) {
                                int lookup = ph.getLeftLookup();
                                while(lookup > 0) {
                                    leftmostNode = getBTreeNode(root, leftmostNode._prev, parent);
                                    int keylen = leftmostNode.keys.length;
                                    if(lookup < keylen) {
                                        break;
                                    }
                                    lookup = leftmostNode.ph.getLeftLookup();
                                    if(lookup == 0) {
                                        break;
                                    }
                                    Value firstKey = leftmostNode.keys[0];
                                    if(!firstKey.equals(key)) {
                                        break;
                                    }
                                }
                            }
                            return leftmostNode;
                        }
                        case RIGHT_MOST:
                            if(_next != -1L) {
                                BTreeNode nextNode = getBTreeNode(root, _next, parent);
                                throw new IllegalStateException("next=" + _next + ".. more leaf ["
                                        + nextNode + "] exists on the right side of leaf ["
                                        + this.toString() + "]\n parent-ptrs: "
                                        + Arrays.toString(parent.ptrs));
                            }
                            break;
                        case LEFT_MOST:
                            if(_prev != -1L) {
                                BTreeNode prevNode = getBTreeNode(root, _prev, parent);
                                throw new IllegalStateException("prev=" + _prev + ".. more leaf ["
                                        + prevNode + "] exists on the left side of leaf ["
                                        + this.toString() + "]\n parent-ptrs: "
                                        + Arrays.toString(parent.ptrs));
                            }
                            break;
                        default:
                            break;
                    }
                    return this;
                default:
                    throw new BTreeCorruptException("Invalid page type in query: " + nodeType);
            }
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            final long rootPage = root.getPage();
            BTreeNode pn = this;
            while(true) {
                final long curPageNum = pn.page.getPageNum();
                buf.append(curPageNum);
                pn = pn.parent;
                if(pn == null) {
                    if(curPageNum != rootPage) {
                        buf.append("<-?");
                    }
                    break;
                } else {
                    buf.append("<-");
                }
            }
            return buf.toString();
        }
    }

    protected class BTreeFileHeader extends FileHeader {

        private long _rootPage = 0;
        private boolean _duplicateAllowed = true;

        public BTreeFileHeader(int pageSize) {
            super(pageSize);
        }

        @Override
        public synchronized void read(RandomAccessFile raf) throws IOException {
            super.read(raf);
            this._duplicateAllowed = raf.readBoolean();
            this._rootPage = raf.readLong();
        }

        @Override
        public synchronized void write(RandomAccessFile raf) throws IOException {
            super.write(raf);
            raf.writeBoolean(_duplicateAllowed);
            raf.writeLong(_rootPage);
        }

        /** The root page of the storage tree */
        @Deprecated
        public final void setRootPage(long rootPage) {
            this._rootPage = rootPage;
            setDirty(true);
        }

        /** The root page of the storage tree */
        public final long getRootPage() {
            return _rootPage;
        }
    }

    protected class BTreePageHeader extends PageHeader {

        private short valueCount = 0;
        private short prefixLength = 0;
        private short leftLookup = 0;

        public BTreePageHeader() {
            super();
        }

        @Deprecated
        public BTreePageHeader(ByteBuffer buf) {
            super(buf);
        }

        @Override
        public synchronized void read(ByteBuffer buf) {
            super.read(buf);
            if(getStatus() == UNUSED) {
                return;
            }
            valueCount = buf.getShort();
            prefixLength = buf.getShort();
            leftLookup = buf.getShort();
        }

        @Override
        public synchronized void write(ByteBuffer buf) {
            super.write(buf);
            buf.putShort(valueCount);
            buf.putShort(prefixLength);
            buf.putShort(leftLookup);
        }

        /** The number of values stored by this page */
        public final void setValueCount(short valueCount) {
            this.valueCount = valueCount;
        }

        /** The number of values stored by this page */
        public final short getValueCount() {
            return valueCount;
        }

        /** The number of pointers stored by this page */
        public final int getPointerCount() {
            if(getStatus() == BRANCH) {
                return valueCount + 1;
            } else {
                return valueCount;
            }
        }

        public final short getPrefixLength() {
            return prefixLength;
        }

        public final void setPrefixLength(short prefixLength) {
            this.prefixLength = prefixLength;
        }

        public final short getLeftLookup() {
            return leftLookup;
        }

        public final void setLeftLookup(short leftLookup) {
            this.leftLookup = leftLookup;
        }
    }

    public static final class BTreeCorruptException extends RuntimeException {
        private static final long serialVersionUID = 5609947858701765326L;

        public BTreeCorruptException(String message) {
            super(message);
        }

        public BTreeCorruptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}