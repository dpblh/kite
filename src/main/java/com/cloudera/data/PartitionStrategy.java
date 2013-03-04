package com.cloudera.data;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.cloudera.data.impl.Accessor;
import com.cloudera.data.impl.PartitionKey;
import org.apache.avro.generic.GenericRecord;

import com.cloudera.data.partition.HashFieldPartitioner;
import com.cloudera.data.partition.IdentityFieldPartitioner;
import com.cloudera.data.partition.IntRangeFieldPartitioner;
import com.cloudera.data.partition.RangeFieldPartitioner;
import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

public class PartitionStrategy {

  private List<FieldPartitioner> fieldPartitioners;

  static {
    Accessor.setDefault(new AccessorImpl());
  }

  protected PartitionStrategy() {
    fieldPartitioners = Lists.newArrayList();
  }

  public PartitionStrategy(FieldPartitioner... partitioners) {
    this();

    for (FieldPartitioner fieldPartitioner : partitioners) {
      addFieldPartitioner(fieldPartitioner);
    }
  }

  public PartitionStrategy(List<FieldPartitioner> partitioners) {
    this();

    fieldPartitioners.addAll(partitioners);
  }

  private void addFieldPartitioner(FieldPartitioner fieldPartitioner) {
    fieldPartitioners.add(fieldPartitioner);
  }

  public List<FieldPartitioner> getFieldPartitioners() {
    return fieldPartitioners;
  }

  public int getCardinality() {
    int cardinality = 1;
    for (FieldPartitioner fieldPartitioner : fieldPartitioners) {
      cardinality *= fieldPartitioner.getCardinality();
    }
    return cardinality;
  }

  /**
   * Returns a key that represents the value of the partition.
   */
  PartitionKey getPartitionKey(Object entity) {
    Object[] values = new Object[fieldPartitioners.size()]; // TODO: reuse
    for (int i = 0; i < fieldPartitioners.size(); i++) {
      FieldPartitioner fp = fieldPartitioners.get(i);
      String name = fp.getName();
      Object value;
      if (entity instanceof GenericRecord) {
        value = ((GenericRecord) entity).get(name);
      } else {
        try {
          PropertyDescriptor propertyDescriptor = new PropertyDescriptor(
              name, entity.getClass());
          value = propertyDescriptor.getReadMethod().invoke(entity);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Cannot read property " + name
              + " from " + entity, e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException("Cannot read property " + name
              + " from " + entity, e);
        } catch (IntrospectionException e) {
          throw new RuntimeException("Cannot read property " + name
              + " from " + entity, e);
        }
      }
      values[i] = fp.apply(value);
    }
    return new PartitionKey(values);
  }

  /**
   * Return a {@link PartitionStrategy} for subpartitions starting at the given
   * index.
   */
  PartitionStrategy getSubpartitionStrategy(int startIndex) {
    if (startIndex == 0) {
      return this;
    }
    if (startIndex == fieldPartitioners.size()) {
      return null;
    }
    return new PartitionStrategy(fieldPartitioners.subList(startIndex,
        fieldPartitioners.size()));
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("fieldPartitioners", fieldPartitioners).toString();
  }

  public static class Builder implements Supplier<PartitionStrategy> {

    private PartitionStrategy partitionStrategy = new PartitionStrategy();

    public Builder hash(String name, int buckets) {
      partitionStrategy.addFieldPartitioner(new HashFieldPartitioner(name,
          buckets));
      return this;
    }

    public Builder identity(String name, int buckets) {
      partitionStrategy.addFieldPartitioner(new IdentityFieldPartitioner(name,
          buckets));
      return this;
    }

    public Builder range(String name, int... upperBounds) {
      partitionStrategy.addFieldPartitioner(new IntRangeFieldPartitioner(name,
          upperBounds));
      return this;
    }

    public Builder range(String name, Comparable<?>... upperBounds) {
      partitionStrategy.addFieldPartitioner(new RangeFieldPartitioner(name,
          upperBounds));
      return this;
    }

    @Override
    public PartitionStrategy get() {
      return partitionStrategy;
    }

  }

}
