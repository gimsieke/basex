package org.basex.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import org.basex.util.Array;

/**
 * This class stores the table on disk and reads it block-wise.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Tim Petrowsky
 */
public final class TableDiskAccess extends TableAccess {
  /** Max entries per block. */
  private static final int ENTRIES = (1 << IO.BLOCKPOWER) >>> IO.NODEPOWER;
  /** Entries per new block. */
  private static final int NEWENTRIES = (int) (IO.BLOCKFILL * ENTRIES);

  /** File storing all blocks. */
  private final RandomAccessFile file;
  /** Name of the database. */
  private final String db;
  /** Filename. */
  private final String fn;

  /** The current block buffer. */
  private final byte[] buffer;
  /** Number of the current block. */
  private int block = -1;

  /** Index array storing the FirstPre values. this one is sorted ascending. */
  private int[] firstPres;
  /** Index array storing the BlockNumbers. */
  private int[] blocks;

  /** Number of the first entry in the current block. */
  private int firstPre = -1;
  /** FirstPre of the next block. */
  private int nextPre = -1;

  /** Number of entries in the index (used blocks). */
  private int indexSize;
  /** Number of blocks in the data file (including unused). */
  private int nrBlocks;
  /** Index number of the current block, referencing indexBlockNumber array. */
  private int index = -1;
  /** Number of entries in the storage. */
  private int count;

  /** Whether the index is dirty. */
  private boolean indexdirty;
  /** Dirty flag. */
  private boolean dirty;

  /**
   * Constructor.
   * @param nm name of the database
   * @param f prefix for all files (no ending)
   * @throws IOException in case files cannot be read
   */
  public TableDiskAccess(final String nm, final String f) throws IOException {
    db = nm;
    fn = f;

    // READ INFO FILE
    final DataInput info = new DataInput(nm, f + 'i');
    nrBlocks = info.readInt();
    indexSize = info.readInt();
    count = info.readInt();
    info.close();

    // INITIALIZE CURRENT BLOCK
    buffer = new byte[1 << IO.BLOCKPOWER];

    // READ INDEX
    firstPres = new int[indexSize];
    blocks = new int[indexSize];

    final DataInput in = new DataInput(nm, f + 'x');
    for(int i = 0; i < indexSize; i++) {
      firstPres[i] = in.readInt();
      blocks[i] = in.readInt();
    }
    in.close();

    // INITIALIZE FILE
    file = new RandomAccessFile(IO.dbfile(nm, f), "rw");

    // READ FIRST BLOCK
    readBlock(0, 0, indexSize > 1 ? firstPres[1] : count);
  }

  /**
   * Searches for the block containing the entry for that pre. then it
   * reads the block and returns it's offset inside the block.
   * @param pre pre of the entry to search for
   * @return offset of the entry in currentBlock
   */
  public synchronized int cursor(final int pre) {
    int fp = firstPre;
    int np = nextPre;
    if(pre >= fp && pre < np) return (pre - fp) << IO.NODEPOWER;

    final int last = indexSize - 1;
    int low = 0;
    int high = last;
    int mid = index;
    while(low <= high) {
      if(pre < fp) high = mid - 1;
      else if(pre >= np) low = mid + 1;
      else break;
      mid = (high + low) >>> 1;
      fp = firstPres[mid];
      np = mid == last ? fp + ENTRIES : firstPres[mid + 1];
    }
    if(low > high) {
      throw new RuntimeException("Possible Bug in Table DiskAccess:\n" +
          "IndexSize: " + indexSize + ", Access: " + mid + " (" + low + ")");
      //BaseX.notexpected();
    }
    
    readBlock(mid, fp, np);
    return (pre - firstPre) << IO.NODEPOWER;
  }

  /**
   * Fetches the requested block into blockNumber and set firstPre and
   * blockSize.
   * @param ind index number of the block to fetch
   * @param first first entry in that block
   * @param next first entry in the next block
   */
  private synchronized void readBlock(final int ind, final int first,
      final int next) {

    try {
      final int b = blocks[ind];
      writeBlock();
      file.seek((long) b << IO.BLOCKPOWER);
      file.read(buffer);
      block = b;
      index = ind;
      firstPre = first;
      nextPre = next;
    } catch(final IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * Checks whether the current block needs to be written and write it.
   * @throws IOException in case of problems
   */
  private synchronized void writeBlock() throws IOException {
    if(dirty) {
      file.seek((long) block << IO.BLOCKPOWER);
      file.write(buffer);
      dirty = false;
    }
  }

  /**
   * Fetches next block.
   */
  private synchronized void nextBlock() {
    readBlock(index + 1, nextPre, index + 2 >= indexSize ? count :
      firstPres[index + 2]);
  }

  @Override
  public synchronized void flush() throws IOException {
    writeBlock();
    if(indexdirty) {
      DataOutput out = new DataOutput(db, fn + 'x');
      for(int i = 0; i < indexSize; i++) {
        out.writeInt(firstPres[i]);
        out.writeInt(blocks[i]);
      }
      out.close();
      out =  new DataOutput(db, fn + 'i');
      out.writeInt(nrBlocks);
      out.writeInt(indexSize);
      out.writeInt(count);
      out.close();
      indexdirty = false;
    }
  }

  @Override
  public synchronized void close() throws IOException {
    flush();
    file.close();
  }

  @Override
  public synchronized int read1(final int pos, final int off) {
    final int o = off + cursor(pos);
    return buffer[o] & 0xFF;
  }

  @Override
  public synchronized int read2(final int pos, final int off) {
    final int o = off + cursor(pos);
    return ((buffer[o] & 0xFF) << 8) + (buffer[o + 1] & 0xFF);
  }

  @Override
  public synchronized int read4(final int pos, final int off) {
    final int o = off + cursor(pos);
    return ((buffer[o] & 0xFF) << 24)
        + ((buffer[o + 1] & 0xFF) << 16)
        + ((buffer[o + 2] & 0xFF) << 8) + (buffer[o + 3] & 0xFF);
  }

  @Override
  public synchronized long read5(final int pos, final int off) {
    final int o = off + cursor(pos);
    return ((long) (buffer[o] & 0xFF) << 32)
        + ((long) (buffer[o + 1] & 0xFF) << 24)
        + ((buffer[o + 2] & 0xFF) << 16)
        + ((buffer[o + 3] & 0xFF) << 8) + (buffer[o + 4] & 0xFF);
  }

  @Override
  public synchronized void write1(final int pos, final int off, final int v) {
    final int o = off + cursor(pos);
    buffer[o] = (byte) v;
    dirty = true;
  }

  @Override
  public synchronized void write2(final int pos, final int off, final int v) {
    final int o = off + cursor(pos);
    buffer[o] = (byte) (v >>> 8);
    buffer[o + 1] = (byte) v;
    dirty = true;
  }

  @Override
  public synchronized void write4(final int pos, final int off, final int v) {
    final int o = off + cursor(pos);
    buffer[o]     = (byte) (v >>> 24);
    buffer[o + 1] = (byte) (v >>> 16);
    buffer[o + 2] = (byte) (v >>> 8);
    buffer[o + 3] = (byte) v;
    dirty = true;
  }

  @Override
  public synchronized void write5(final int pos, final int off, final long v) {
    final int o = off + cursor(pos);
    buffer[o]     = (byte) (v >>> 32);
    buffer[o + 1] = (byte) (v >>> 24);
    buffer[o + 2] = (byte) (v >>> 16);
    buffer[o + 3] = (byte) (v >>> 8);
    buffer[o + 4] = (byte) v;
    dirty = true;
  }

  /* Note to delete method: Freed blocks are currently ignored. */
  
  @Override
  public synchronized void delete(final int first, final int nr) {
    // mark index as dirty and get first block
    indexdirty = true;
    cursor(first);

    // some useful variables to make code more readable
    int from = first - firstPre;
    final int last = first + nr - 1;

    // check if all entries are in current block => handle and return
    if(last < nextPre) {
      dirty = true;
      copy(buffer, from + nr, buffer, from, nextPre - last - 1);

      updatePre(nr);

      // if whole block was deleted, remove it from the index
      if(nextPre == firstPre) {
        Array.move(firstPres, index + 1, -1, indexSize - index - 1);
        Array.move(blocks, index + 1, -1, indexSize - index - 1);
        indexSize--;
        readBlock(index, firstPre, index + 2 > indexSize ? count :
          firstPres[index + 1]);
      }
      return;
    }

    // handle blocks whose entries are to be deleted entirely

    // first count them
    int unused = 0;
    while(nextPre <= last) {
      if(from == 0) unused++;
      nextBlock();
      from = 0;
    }
    // now remove them from the index
    if(unused > 0) {
      Array.move(firstPres, index, -unused, indexSize - index);
      Array.move(blocks, index, -unused, indexSize - index);
      indexSize -= unused;
      index -= unused;
    }

    // delete entries at beginning of current (last) block
    dirty = true;
    copy(buffer, last - firstPre + 1, buffer, 0, nextPre - last - 1);

    // update index entry for this block
    firstPres[index] = first;
    firstPre = first;
    updatePre(nr);
  }
  
  /**
   * Updates the firstPre index entries.
   * @param nr number of entries to move
   */
  private synchronized void updatePre(final int nr) {
    // update index entries for all following blocks and reduce counter
    for(int i = index + 1; i < indexSize; i++) firstPres[i] -= nr;
    count -= nr;
    nextPre = index + 1 >= indexSize ? count : firstPres[index + 1];
  }

  @Override
  public synchronized void insert(final int pre, final byte[] entries) {
    indexdirty = true;
    final int nr = entries.length >>> IO.NODEPOWER;
    count += nr;
    cursor(pre);

    final int ins = pre - firstPre + 1;

    // all entries fit in current block
    if(nr < ENTRIES - nextPre + firstPre) {
      // shift following entries forward and insert next entries
      dirty = true;
      copy(buffer, ins, buffer, ins + nr, nextPre - pre);
      copy(entries, 0, buffer, ins, nr);

      // update index entries
      for(int i = index + 1; i < indexSize; i++) firstPres[i] += nr;
      nextPre += nr;
      return;
    }

    // we need to reorganize entries

    // save entries to be added into new block after inserted blocks
    final int move = nextPre - pre - 1;
    final byte[] rest = new byte[move << IO.NODEPOWER];

    copy(buffer, ins, rest, 0, move);

    // make room in index for new blocks
    int newBlocks = (int) Math.ceil((double) nr / NEWENTRIES) + 1;
    // in case we insert at block boundary
    if(pre == nextPre - 1) newBlocks--;
    
    // resize the index
    firstPres = Array.resize(firstPres, nrBlocks, nrBlocks + newBlocks);
    blocks = Array.resize(blocks, nrBlocks, nrBlocks + newBlocks);
    
    Array.move(firstPres, index + 1, newBlocks, indexSize - index - 1);
    Array.move(blocks, index + 1, newBlocks, indexSize - index - 1);

    // add blocks for new entries
    int remain = nr;
    int pos = 0;
    while(remain > 0) {
      newBlock();
      copy(entries, pos, buffer, 0, Math.min(remain, NEWENTRIES));

      firstPres[++index] = nr - remain + pre + 1;
      blocks[index] = block;
      indexSize++;
      remain -= NEWENTRIES;
      pos += NEWENTRIES;
    }

    // add remaining part of split block
    if(rest.length > 0) {
      newBlock();
      copy(rest, 0, buffer, 0, move);

      firstPres[++index] = pre + nr + 1;
      blocks[index] = block;
      indexSize++;
    }

    // update index entries
    for(int i = index + 1; i < indexSize; i++) firstPres[i] += nr;

    // update cached variables
    firstPre = pre + 1;
    if(rest.length > 0) firstPre += nr;
    nextPre = index + 1 >= indexSize ? count : firstPres[index + 1];
  }

  /**
   * Creates a new, empty block.
   */
  private synchronized void newBlock() {
    try {
      writeBlock();
    } catch(final IOException e) {
      e.printStackTrace();
    }
    block = nrBlocks++;
    dirty = true;
  }

  /**
   * Convenience method for copying blocks.
   * @param s source array
   * @param sp source position
   * @param d destination array
   * @param dp destination position
   * @param l source length
   */
  private synchronized void copy(final Object s, final int sp, final Object d,
      final int dp, final int l) {
    System.arraycopy(s, sp << IO.NODEPOWER, d, dp << IO.NODEPOWER,
        l << IO.NODEPOWER);
  }

  /**
   * Return the entryCount.
   * @return number of entries in storage.
   */
  public synchronized int size() {
    return count;
  }

  /**
   * Return the number of used blocks.
   * @return number of used blocks.
   */
  public synchronized int blocks() {
    return indexSize;
  }
}
