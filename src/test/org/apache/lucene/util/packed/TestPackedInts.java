package org.apache.lucene.util.packed;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.store.*;
import org.apache.lucene.util.LuceneTestCase;

import java.util.*;
import java.io.IOException;

public class TestPackedInts extends LuceneTestCase {

  private Random rnd;

  public void testBitsRequired() throws Exception {
    assertEquals(61, PackedInts.bitsRequired((long)Math.pow(2, 61)-1));
    assertEquals(61, PackedInts.bitsRequired(0x1FFFFFFFFFFFFFFFL));
    assertEquals(62, PackedInts.bitsRequired(0x3FFFFFFFFFFFFFFFL));
    assertEquals(63, PackedInts.bitsRequired(0x7FFFFFFFFFFFFFFFL));
  }

  public void testMaxValues() throws Exception {
    assertEquals("1 bit -> max == 1",
            1, PackedInts.maxValue(1));
    assertEquals("2 bit -> max == 3",
            3, PackedInts.maxValue(2));
    assertEquals("8 bit -> max == 255",
            255, PackedInts.maxValue(8));
    assertEquals("63 bit -> max == Long.MAX_VALUE",
            Long.MAX_VALUE, PackedInts.maxValue(63));
    assertEquals("64 bit -> max == Long.MAX_VALUE (same as for 63 bit)", 
            Long.MAX_VALUE, PackedInts.maxValue(64));
  }

  public void testPackedInts() throws IOException {
    rnd = newRandom();
    for(int iter=0;iter<5;iter++) {
      long ceil = 2;
      for(int nbits=1;nbits<63;nbits++) {
        final int valueCount = 100+rnd.nextInt(500);
        final Directory d = new MockRAMDirectory();

        IndexOutput out = d.createOutput("out.bin");
        PackedInts.Writer w = PackedInts.getWriter(
                out, valueCount, nbits);

        final long[] values = new long[valueCount];
        for(int i=0;i<valueCount;i++) {
          long v = rnd.nextLong() % ceil;
          if (v < 0) {
            v = -v;
          }
          values[i] = v;
          w.add(values[i]);
        }
        w.finish();
        out.close();

        IndexInput in = d.openInput("out.bin");
        PackedInts.Reader r = PackedInts.getReader(in);
        for(int i=0;i<valueCount;i++) {
          assertEquals("index=" + i + " ceil=" + ceil + " valueCount="
                  + valueCount + " nbits=" + nbits + " for "
                  + r.getClass().getSimpleName(), values[i], r.get(i));
        }
        in.close();
        ceil *= 2;
      }
    }
  }

  public void testControlledEquality() {
    final int VALUE_COUNT = 255;
    final int BITS_PER_VALUE = 8;

    List<PackedInts.Mutable> packedInts =
            createPackedInts(VALUE_COUNT, BITS_PER_VALUE);
    for (PackedInts.Mutable packedInt: packedInts) {
      for (int i = 0 ; i < packedInt.size() ; i++) {
        packedInt.set(i, i+1);
      }
    }
    assertListEquality(packedInts);
  }

  public void testSpecificBugWithSet() throws IOException {
    PackedInts.Mutable mutable = PackedInts.getMutable(26, 5);
    mutable.set(24, 31);
    assertEquals("The value #24 should be correct", 31, mutable.get(24));
    mutable.set(4, 16);
    assertEquals("The value #24 should remain unchanged", 31, mutable.get(24));
  }

  // Prompted by a problem developing LUCENE-2335
  public void test7BitMutables() throws IOException {
    int VALUES = 26;
    int BITS = PackedInts.bitsRequired(VALUES);
    long END = PackedInts.maxValue(BITS);

    PackedInts.Mutable mutable = new Packed32(VALUES, BITS);//PackedInts.getMutable(VALUES, BITS);
    List<Integer> plain = new ArrayList<Integer>(VALUES);

    // Fill with END
    for (int i = 0 ; i < VALUES ; i++) {
      mutable.set(i, END);
      plain.add((int)END);
    }

    // Check END assignment
    for (int i = 0 ; i < VALUES ; i++) {
      assertEquals("Mutable at index " + i + " should contain END",
          END, mutable.get(i));
      assertEquals("Plain at index " + i + " should contain END",
          (int)END, (int)plain.get(i));
    }

    // Generate values to assign in random order
    Random random = newRandom(87);
    List<Integer> indexes = new ArrayList<Integer>(VALUES);
    List<Integer> assignments = new ArrayList<Integer>(VALUES);
    for (int i = 0 ; i < VALUES ; i++) {
      indexes.add(i);
      assignments.add(i);
    }
    Collections.shuffle(indexes, random);
    Collections.shuffle(assignments, random);

    // Assign values
    for (int i = 0 ; i < VALUES ; i++) {
      plain.set(indexes.get(i), assignments.get(i));
      mutable.set(indexes.get(i), assignments.get(i));
      assertEquals("Equality check at assignment #" + i,
          plain, mutable);
    }

    // Compare for equality
    assertEquals("Final equality check", plain, mutable);
  }

  private void assertEquals(
      String message, List<Integer> plain, PackedInts.Mutable mutable) {
    assertEquals("The arrays should be of equal length",
        plain.size(), mutable.size());
    for (int i = 0 ; i < plain.size() ; i++) {
      assertEquals(message + " The values at index " + i + " should be the same"
          + " for a Mutable of type " + mutable.getClass().getSimpleName(),
          (long)plain.get(i), mutable.get(i));
    }
  }

  public void testRandomEquality() {
    final int[] VALUE_COUNTS = new int[]{0, 1, 5, 8, 100, 500};
    final int MIN_BITS_PER_VALUE = 1;
    final int MAX_BITS_PER_VALUE = 64;

    rnd = newRandom();

    for (int valueCount: VALUE_COUNTS) {
      for (int bitsPerValue = MIN_BITS_PER_VALUE ;
           bitsPerValue <= MAX_BITS_PER_VALUE ;
           bitsPerValue++) {
        assertRandomEquality(valueCount, bitsPerValue, rnd.nextLong());
      }
    }
  }

  private void assertRandomEquality(int valueCount, int bitsPerValue, long randomSeed) {
    List<PackedInts.Mutable> packedInts = createPackedInts(valueCount, bitsPerValue);
    for (PackedInts.Mutable packedInt: packedInts) {
      try {
        fill(packedInt, (long)(Math.pow(2, bitsPerValue)-1), randomSeed);
      } catch (Exception e) {
        e.printStackTrace(System.err);
        fail(String.format(
                "Exception while filling %s: valueCount=%d, bitsPerValue=%s",
                packedInt.getClass().getSimpleName(),
                valueCount, bitsPerValue));
      }
    }
    assertListEquality(packedInts);
  }

  private List<PackedInts.Mutable> createPackedInts(
          int valueCount, int bitsPerValue) {
    List<PackedInts.Mutable> packedInts = new ArrayList<PackedInts.Mutable>();
    if (bitsPerValue <= 8) {
      packedInts.add(new Direct8(valueCount));
    }
    if (bitsPerValue <= 16) {
      packedInts.add(new Direct16(valueCount));
    }
    if (bitsPerValue <= 31) {
      packedInts.add(new Packed32(valueCount, bitsPerValue));
    }
    if (bitsPerValue <= 32) {
      packedInts.add(new Direct32(valueCount));
    }
    if (bitsPerValue <= 63) {
      packedInts.add(new Packed64(valueCount, bitsPerValue));
    }
    packedInts.add(new Direct64(valueCount));
    return packedInts;
  }

  private void fill(PackedInts.Mutable packedInt, long maxValue, long randomSeed) {
    Random rnd2 = new Random(randomSeed);
    maxValue++;
    for (int i = 0 ; i < packedInt.size() ; i++) {
      long value = Math.abs(rnd2.nextLong() % maxValue);
      packedInt.set(i, value);
      assertEquals(String.format(
              "The set/get of the value at index %d should match for %s",
              i, packedInt.getClass().getSimpleName()),
              value, packedInt.get(i));
    }
  }

  private void assertListEquality(
          List<? extends PackedInts.Reader> packedInts) {
    assertListEquality("", packedInts);
  }

  private void assertListEquality(
            String message, List<? extends PackedInts.Reader> packedInts) {
    if (packedInts.size() == 0) {
      return;
    }
    PackedInts.Reader base = packedInts.get(0);
    int valueCount = base.size();
    for (PackedInts.Reader packedInt: packedInts) {
      assertEquals(message + ". The number of values should be the same ",
              valueCount, packedInt.size());
    }
    for (int i = 0 ; i < valueCount ; i++) {
      for (int j = 1 ; j < packedInts.size() ; j++) {
        assertEquals(String.format(
                "%s. The value at index %d should be the same for %s and %s",
                message, i, base.getClass().getSimpleName(),
                packedInts.get(j).getClass().getSimpleName()),
                base.get(i), packedInts.get(j).get(i));
      }
    }
  }
}
