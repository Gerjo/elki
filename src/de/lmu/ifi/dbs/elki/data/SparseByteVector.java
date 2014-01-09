package de.lmu.ifi.dbs.elki.data;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import gnu.trove.iterator.TIntDoubleIterator;
import gnu.trove.map.TIntDoubleMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import de.lmu.ifi.dbs.elki.math.linearalgebra.Vector;
import de.lmu.ifi.dbs.elki.persistent.ByteArrayUtil;
import de.lmu.ifi.dbs.elki.persistent.ByteBufferSerializer;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.ArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;

/**
 * <p>
 * A SparseByteVector is to store real values as double values.
 * </p>
 * 
 * A SparseByteVector only requires storage for those attribute values that
 * are non-zero.
 * 
 * @author Arthur Zimek
 */
public class SparseByteVector extends AbstractNumberVector<Byte> implements SparseNumberVector<Byte> {
  /**
   * Static instance.
   */
  public static final SparseByteVector.Factory FACTORY = new SparseByteVector.Factory();

  /**
   * Serializer using varint encoding.
   */
  public static final ByteBufferSerializer<SparseByteVector> VARIABLE_SERIALIZER = new VariableSerializer();

  /**
   * Indexes of values.
   */
  private final int[] indexes;

  /**
   * Stored values.
   */
  private final byte[] values;

  /**
   * The dimensionality of this feature vector.
   */
  private int dimensionality;

  /**
   * Direct constructor.
   * 
   * @param indexes Indexes Must be sorted!
   * @param values Associated value.
   * @param dimensionality "true" dimensionality
   */
  public SparseByteVector(int[] indexes, byte[] values, int dimensionality) {
    super();
    this.indexes = indexes;
    this.values = values;
    this.dimensionality = dimensionality;
  }

  /**
   * Provides a SparseByteVector consisting of double values according to the
   * specified mapping of indices and values.
   * 
   * @param values the values to be set as values of the real vector
   * @param dimensionality the dimensionality of this feature vector
   * @throws IllegalArgumentException if the given dimensionality is too small
   *         to cover the given values (i.e., the maximum index of any value not
   *         zero is bigger than the given dimensionality)
   */
  public SparseByteVector(TIntDoubleMap values, int dimensionality) throws IllegalArgumentException {
    if(values.size() > dimensionality) {
      throw new IllegalArgumentException("values.size() > dimensionality!");
    }

    this.indexes = new int[values.size()];
    this.values = new byte[values.size()];
    // Import and sort the indexes
    {
      TIntDoubleIterator iter = values.iterator();
      for(int i = 0; iter.hasNext(); i++) {
        iter.advance();
        this.indexes[i] = iter.key();
      }
      Arrays.sort(this.indexes);
    }
    // Import the values accordingly
    {
      for(int i = 0; i < values.size(); i++) {
        this.values[i] = (byte) values.get(this.indexes[i]);
      }
    }
    this.dimensionality = dimensionality;
    final int maxdim = getMaxDim();
    if(maxdim > dimensionality) {
      throw new IllegalArgumentException("Given dimensionality " + dimensionality + " is too small w.r.t. the given values (occurring maximum: " + maxdim + ").");
    }
  }

  /**
   * Get the maximum dimensionality.
   * 
   * @return the maximum dimensionality seen
   */
  private int getMaxDim() {
    if(this.indexes.length == 0) {
      return 0;
    }
    else {
      return this.indexes[this.indexes.length - 1];
    }
  }

  /**
   * Provides a SparseByteVector consisting of double values according to the
   * specified mapping of indices and values.
   * 
   * @param values the values to be set as values of the real vector
   * @throws IllegalArgumentException if the given dimensionality is too small
   *         to cover the given values (i.e., the maximum index of any value not
   *         zero is bigger than the given dimensionality)
   */
  public SparseByteVector(byte[] values) throws IllegalArgumentException {
    this.dimensionality = values.length;

    // Count the number of non-zero entries
    int size = 0;
    {
      for(int i = 0; i < values.length; i++) {
        if(values[i] != 0) {
          size++;
        }
      }
    }
    this.indexes = new int[size];
    this.values = new byte[size];

    // Copy the values
    {
      int pos = 0;
      for(int i = 0; i < values.length; i++) {
        byte value = values[i];
        if(value != 0) {
          this.indexes[pos] = i;
          this.values[pos] = value;
          pos++;
        }
      }
    }
  }

  @Override
  public int getDimensionality() {
    return dimensionality;
  }

  /**
   * Sets the dimensionality to the new value.
   * 
   * 
   * @param dimensionality the new dimensionality
   * @throws IllegalArgumentException if the given dimensionality is too small
   *         to cover the given values (i.e., the maximum index of any value not
   *         zero is bigger than the given dimensionality)
   */
  @Override
  public void setDimensionality(int dimensionality) throws IllegalArgumentException {
    final int maxdim = getMaxDim();
    if(maxdim > dimensionality) {
      throw new IllegalArgumentException("Given dimensionality " + dimensionality + " is too small w.r.t. the given values (occurring maximum: " + maxdim + ").");
    }
    this.dimensionality = dimensionality;
  }

  @Override
  @Deprecated
  public Byte getValue(int dimension) {
    int pos = Arrays.binarySearch(this.indexes, dimension);
    if(pos >= 0) {
      return values[pos];
    }
    else {
      return 0;
    }
  }

  @Override
  @Deprecated
  public double doubleValue(int dimension) {
    int pos = Arrays.binarySearch(this.indexes, dimension);
    if(pos >= 0) {
      return values[pos];
    }
    else {
      return 0.;
    }
  }

  @Override
  @Deprecated
  public long longValue(int dimension) {
    int pos = Arrays.binarySearch(this.indexes, dimension);
    if(pos >= 0) {
      return (long) values[pos];
    }
    else {
      return 0;
    }
  }

  @Override
  public byte byteValue(int dimension) {
    int pos = Arrays.binarySearch(this.indexes, dimension);
    if(pos >= 0) {
      return values[pos];
    }
    else {
      return 0;
    }
  }

  @Override
  public Vector getColumnVector() {
    return new Vector(getValues());
  }

  /**
   * <p>
   * Provides a String representation of this SparseByteVector as suitable
   * for
   * {@link de.lmu.ifi.dbs.elki.datasource.parser.SparseNumberVectorLabelParser}
   * .
   * </p>
   * 
   * <p>
   * The returned String is a single line with entries separated by
   * {@link AbstractNumberVector#ATTRIBUTE_SEPARATOR}. The first entry gives the
   * number of values actually not zero. Following entries are pairs of Byte
   * and Byte where the Byte gives the index of the dimensionality and the
   * Byte gives the corresponding value.
   * </p>
   * 
   * <p>
   * Example: a vector (0,1.2,1.3,0)<sup>T</sup> would result in the String<br>
   * <code>2 2 1.2 3 1.3</code><br>
   * </p>
   * 
   * @return a String representation of this SparseByteVector
   */
  @Override
  public String toString() {
    StringBuilder featureLine = new StringBuilder();
    featureLine.append(this.indexes.length);
    for(int i = 0; i < this.indexes.length; i++) {
      featureLine.append(ATTRIBUTE_SEPARATOR);
      featureLine.append(this.indexes[i]);
      featureLine.append(ATTRIBUTE_SEPARATOR);
      featureLine.append(this.values[i]);
    }

    return featureLine.toString();
  }

  /**
   * Returns an array consisting of the values of this feature vector.
   * 
   * @return an array consisting of the values of this feature vector
   */
  private double[] getValues() {
    double[] vals = new double[dimensionality];
    for(int i = 0; i < indexes.length; i++) {
      vals[this.indexes[i]] = this.values[i];
    }
    return vals;
  }

  @Override
  public int iter() {
    return 0;
  }

  @Override
  public int iterDim(int iter) {
    return indexes[iter];
  }

  @Override
  public int iterAdvance(int iter) {
    return iter + 1;
  }

  @Override
  public boolean iterValid(int iter) {
    return iter < indexes.length;
  }

  @Override
  public double iterDoubleValue(int iter) {
    return (double) values[iter];
  }

  @Override
  public float iterFloatValue(int iter) {
    return (float) values[iter];
  }

  @Override
  public int iterIntValue(int iter) {
    return (int) values[iter];
  }

  @Override
  public short iterShortValue(int iter) {
    return (short) values[iter];
  }

  @Override
  public long iterLongValue(int iter) {
    return (long) values[iter];
  }

  @Override
  public byte iterByteValue(int iter) {
    return values[iter];
  }

  /**
   * Factory class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.has SparseByteVector
   */
  public static class Factory extends AbstractNumberVector.Factory<SparseByteVector, Byte> implements SparseNumberVector.Factory<SparseByteVector, Byte> {
    @Override
    public <A> SparseByteVector newFeatureVector(A array, ArrayAdapter<Byte, A> adapter) {
      int dim = adapter.size(array);
      byte[] values = new byte[dim];
      for(int i = 0; i < dim; i++) {
        values[i] = adapter.get(array, i);
      }
      // TODO: improve efficiency
      return new SparseByteVector(values);
    }

    @Override
    public <A> SparseByteVector newNumberVector(A array, NumberArrayAdapter<?, ? super A> adapter) {
      int dim = adapter.size(array);
      byte[] values = new byte[dim];
      for(int i = 0; i < dim; i++) {
        values[i] = adapter.getByte(array, i);
      }
      // TODO: improve efficiency
      return new SparseByteVector(values);
    }

    @Override
    public SparseByteVector newNumberVector(TIntDoubleMap values, int maxdim) {
      return new SparseByteVector(values, maxdim);
    }

    @Override
    public ByteBufferSerializer<SparseByteVector> getDefaultSerializer() {
      return VARIABLE_SERIALIZER;
    }

    @Override
    public Class<? super SparseByteVector> getRestrictionClass() {
      return SparseByteVector.class;
    }

    /**
     * Parameterization class.
     * 
     * @author Erich Schubert
     * 
     * @apiviz.exclude
     */
    public static class Parameterizer extends AbstractParameterizer {
      @Override
      protected SparseByteVector.Factory makeInstance() {
        return FACTORY;
      }
    }
  }

  /**
   * Serialization class using VarInt encodings.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.uses SparseByteVector - - «serializes»
   */
  public static class VariableSerializer implements ByteBufferSerializer<SparseByteVector> {
    @Override
    public SparseByteVector fromByteBuffer(ByteBuffer buffer) throws IOException {
      final int dimensionality = ByteArrayUtil.readUnsignedVarint(buffer);
      final int nonzero = ByteArrayUtil.readUnsignedVarint(buffer);
      final int[] dims = new int[nonzero];
      final byte[] values = new byte[nonzero];
      for(int i = 0; i < nonzero; i++) {
        dims[i] = ByteArrayUtil.readUnsignedVarint(buffer);
        values[i] = buffer.get();
      }
      return new SparseByteVector(dims, values, dimensionality);
    }

    @Override
    public void toByteBuffer(ByteBuffer buffer, SparseByteVector vec) throws IOException {
      ByteArrayUtil.writeUnsignedVarint(buffer, vec.dimensionality);
      ByteArrayUtil.writeUnsignedVarint(buffer, vec.values.length);
      for(int i = 0; i < vec.values.length; i++) {
        ByteArrayUtil.writeUnsignedVarint(buffer, vec.indexes[i]);
        buffer.put(vec.values[i]);
      }
    }

    @Override
    public int getByteSize(SparseByteVector vec) {
      int sum = 0;
      sum += ByteArrayUtil.getUnsignedVarintSize(vec.dimensionality);
      sum += ByteArrayUtil.getUnsignedVarintSize(vec.values.length);
      for(int i = 0; i < vec.values.length; i++) {
        sum += ByteArrayUtil.getUnsignedVarintSize(vec.indexes[i]);
        ++sum;
      }
      return sum;
    }
  }
}
