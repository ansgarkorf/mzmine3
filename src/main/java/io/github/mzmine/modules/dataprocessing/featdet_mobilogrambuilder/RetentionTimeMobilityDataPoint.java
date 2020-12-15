package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import io.github.mzmine.datamodel.DataPoint;

public class RetentionTimeMobilityDataPoint implements DataPoint {

  private final double mobility;
  private final double mz;
  private final Float retentionTime;
  private final double intensity;
  private final int frameNumber;
  private final int scanNumber;

  public RetentionTimeMobilityDataPoint(double mobility, double mz, Float retentionTime,
      double intensity, int frameNumber, int scanNumber) {
    this.mobility = mobility;
    this.mz = mz;
    this.retentionTime = retentionTime;
    this.intensity = intensity;
    this.frameNumber = frameNumber;
    this.scanNumber = scanNumber;
  }

  public double getMobility() {
    return mobility;
  }

  public double getMZ() {
    return mz;
  }

  public Float getRetentionTime() {
    return retentionTime;
  }

  public double getIntensity() {
    return intensity;
  }

  public int getFrameNumber() {
    return frameNumber;
  }

  public int getScanNumber() {
    return scanNumber;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + frameNumber;
    long temp;
    temp = Double.doubleToLongBits(intensity);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(mobility);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(mz);
    result = prime * result + (int) (temp ^ (temp >>> 32));
    result = prime * result + ((retentionTime == null) ? 0 : retentionTime.hashCode());
    result = prime * result + scanNumber;
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
    RetentionTimeMobilityDataPoint other = (RetentionTimeMobilityDataPoint) obj;
    if (frameNumber != other.frameNumber)
      return false;
    if (Double.doubleToLongBits(intensity) != Double.doubleToLongBits(other.intensity))
      return false;
    if (Double.doubleToLongBits(mobility) != Double.doubleToLongBits(other.mobility))
      return false;
    if (Double.doubleToLongBits(mz) != Double.doubleToLongBits(other.mz))
      return false;
    if (retentionTime == null) {
      if (other.retentionTime != null)
        return false;
    } else if (!retentionTime.equals(other.retentionTime))
      return false;
    if (scanNumber != other.scanNumber)
      return false;
    return true;
  }

}
