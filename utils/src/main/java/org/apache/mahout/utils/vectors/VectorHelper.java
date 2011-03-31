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

package org.apache.mahout.utils.vectors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.iterator.FileLineIterator;
import org.apache.mahout.common.iterator.sequencefile.PathType;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileDirIterable;
import org.apache.mahout.math.NamedVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.map.OpenObjectIntHashMap;

public final class VectorHelper {

  private static final Pattern TAB_PATTERN = Pattern.compile("\t");

  
  private VectorHelper() { }

  public static String vectorToCSVString(Vector vector, boolean namesAsComments) throws IOException {
    Appendable bldr = new StringBuilder(2048);
    vectorToCSVString(vector, namesAsComments, bldr);
    return bldr.toString();
  }

  public static void vectorToCSVString(Vector vector,
                                       boolean namesAsComments,
                                       Appendable bldr) throws IOException {
    if (namesAsComments && vector instanceof NamedVector){
      bldr.append("#").append(((NamedVector)vector).getName()).append('\n');
    }
    Iterator<Vector.Element> iter = vector.iterator();
    boolean first = true;
    while (iter.hasNext()) {
      if (first) {
        first = false;
      } else {
        bldr.append(",");
      }
      Vector.Element elt = iter.next();
      bldr.append(String.valueOf(elt.get()));
    }
    bldr.append('\n');
  }


  /**
   * @return a String from a vector that fills in the values with the appropriate value from a dictionary where
   * each the ith entry is the term for the ith vector cell.
   */
  public static String vectorToJSONString(Vector vector, String[] dictionary) {
    StringBuilder bldr = new StringBuilder(2048);
    
    if (vector instanceof NamedVector) {
      bldr.append("name: ").append(((NamedVector) vector).getName()).append('\t');
    }
    
    bldr.append("elts: {");
    Iterator<Vector.Element> iter = vector.iterateNonZero();
    boolean first = true;
    while (iter.hasNext()) {
      if (first) {
        first = false;
      } else {
        bldr.append(", ");
      }
      Vector.Element elt = iter.next();
      if (dictionary != null) {
        bldr.append(dictionary[elt.index()]);
      } else {
        bldr.append(String.valueOf(elt.index()));
      }
      bldr.append(':').append(String.valueOf(elt.get()));
    }
    return bldr.append('}').toString();
  }

  
  /**
   * Read in a dictionary file. Format is:
   * 
   * <pre>
   * term DocFreq Index
   * </pre>
   */
  public static String[] loadTermDictionary(File dictFile) throws IOException {
    return loadTermDictionary(new FileInputStream(dictFile));
  }
  
  /**
   * Read a dictionary in {@link SequenceFile} generated by
   * {@link org.apache.mahout.vectorizer.DictionaryVectorizer}
   *
   * @param filePattern
   *          <PATH TO DICTIONARY>/dictionary.file-*
   */
  public static String[] loadTermDictionary(Configuration conf, String filePattern) {
    OpenObjectIntHashMap<String> dict = new OpenObjectIntHashMap<String>();
    for (Pair<Text,IntWritable> record :
         new SequenceFileDirIterable<Text,IntWritable>(new Path(filePattern), PathType.GLOB, null, null, true, conf)) {
      dict.put(record.getFirst().toString(), record.getSecond().get());
    }
    String[] dictionary = new String[dict.size()];
    for (String feature : dict.keys()) {
      dictionary[dict.get(feature)] = feature;
    }
    return dictionary;
  }
  
  /**
   * Read in a dictionary file. Format is: First line is the number of entries
   * 
   * <pre>
   * term DocFreq Index
   * </pre>
   */
  private static String[] loadTermDictionary(InputStream is) throws IOException {
    FileLineIterator it = new FileLineIterator(is);
    
    int numEntries = Integer.parseInt(it.next());
    String[] result = new String[numEntries];
    
    while (it.hasNext()) {
      String line = it.next();
      if (line.startsWith("#")) {
        continue;
      }
      String[] tokens = VectorHelper.TAB_PATTERN.split(line);
      if (tokens.length < 3) {
        continue;
      }
      int index = Integer.parseInt(tokens[2]); // tokens[1] is the doc freq
      result[index] = tokens[0];
    }
    return result;
  }
}
