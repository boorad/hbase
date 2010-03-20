/**
 * Copyright 2009 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.regionserver;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.util.ClassSize;
import org.apache.hadoop.io.Writable;

/**
 * WALEdit: Used in HBase's transaction log (WAL) to represent
 * the collection of edits (KeyValue objects) corresponding to a 
 * single transaction. The class implements "Writable" interface
 * for serializing/deserializing a set of KeyValue items.
 * 
 * Previously, if a transaction contains 3 edits to c1, c2, c3 for a row R,
 * the HLog would have three log entries as follows:
 * 
 *    <logseq1-for-edit1>:<KeyValue-for-edit-c1>
 *    <logseq2-for-edit2>:<KeyValue-for-edit-c2>
 *    <logseq3-for-edit3>:<KeyValue-for-edit-c3>
 * 
 * This presents problems because row level atomicity of transactions
 * was not guaranteed. If we crash after few of the above appends make
 * it, then recovery will restore a partial transaction.
 *
 * In the new world, all the edits for a given transaction are written
 * out as a single record, for example:
 * 
 *   <logseq#-for-entire-txn>:<WALEdit-for-entire-txn>
 * 
 * where, the WALEdit is serialized as:
 *   <-1, # of edits, <KeyValue>, <KeyValue>, ... >
 * For example:
 *   <-1, 3, <Keyvalue-for-edit-c1>, <KeyValue-for-edit-c2>, <KeyValue-for-edit-c3>>
 *   
 * The -1 marker is just a special way of being backward compatible with 
 * an old HLog which would have contained a single <KeyValue>.
 * 
 * The deserializer for WALEdit backward compatibly detects if the record
 * is an old style KeyValue or the new style WALEdit.
 *
 */
public class WALEdit implements Writable {
  
  private final int VERSION_2 = -1;
  
  private List<KeyValue> kvs = new ArrayList<KeyValue>();
  
  public WALEdit() {
  }
  
  public void add(KeyValue kv) {
    this.kvs.add(kv);
  }

  public boolean isEmpty() {
    return kvs.isEmpty();
  }

  public int size() {
    return kvs.size();
  }
  
  public List<KeyValue> getKeyValues() {
    return kvs;
  }

  public void readFields(DataInput in) throws IOException {
    
    // ignore any old state in case caller is recycling an instance of this object.
    kvs = new ArrayList<KeyValue>();

    int versionOrLength = in.readInt();

    if (versionOrLength == VERSION_2) {
      // this is new style HLog entry containing multiple KeyValues.
      int numEdits = in.readInt();
      for (int idx = 0; idx < numEdits; idx++) {
        KeyValue kv = new KeyValue();
        kv.readFields(in);
        this.add(kv);
      }
    } else {
      // this is an old style HLog entry. The int that we just
      // read is actually the length of a single KeyValye.
      KeyValue kv = new KeyValue();
      kv.readFields(versionOrLength, in);
      this.add(kv);
    }

  }

  public void write(DataOutput out) throws IOException {
    out.writeInt(VERSION_2);
    out.writeInt(kvs.size());
    for (KeyValue kv : kvs) {
      kv.write(out);
    }
  }

  public long heapSize() {
    long size = ClassSize.align(ClassSize.OBJECT +
                                ClassSize.REFERENCE + 
                                ClassSize.ARRAYLIST);
    for (KeyValue kv : kvs) {
      size += kv.heapSize();
    }
    
    return size;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("[#edits: " + kvs.size() + " = <");
    for (KeyValue kv : kvs) {
      sb.append(kv.toString());
      sb.append("; ");
    }
    sb.append(">]");
    return sb.toString();
  }
  
}