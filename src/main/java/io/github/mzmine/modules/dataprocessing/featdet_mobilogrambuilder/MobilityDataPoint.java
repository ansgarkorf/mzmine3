package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import io.github.mzmine.datamodel.DataPoint;

public class MobilityDataPoint implements DataPoint {

  private final double mz;
  private final double intensity;
  private final double mobility;
  private final int scanNum;

  public MobilityDataPoint(double mz, double intensity, double mobility, int scanNum) {
    this.mz = mz;
    this.intensity = intensity;
    this.mobility = mobility;
    this.scanNum = scanNum;
  }

  public double getMobility() {
    return mobility;
  }

  @Override
  public double getMZ() {
    return mz;
  }

  @Override
  public double getIntensity() {
    return intensity;
  }

  public int getScanNum() {
    return scanNum;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    long temp;
    temp = Double.doubleToLongBits(intensity);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(mobility);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(mz);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + scanNum;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MobilityDataPoint other = (MobilityDataPoint) obj;
    if (Double.doubleToLongBits(intensity) != Double.doubleToLongBits(other.intensity))
      return false;
    if (Double.doubleToLongBits(mobility) != Double.doubleToLongBits(other.mobility))
      return false;
    if (Double.doubleToLongBits(mz) != Double.doubleToLongBits(other.mz))
      return false;
    if (scanNum != other.scanNum)
      return false;
    return true;
  }

}
