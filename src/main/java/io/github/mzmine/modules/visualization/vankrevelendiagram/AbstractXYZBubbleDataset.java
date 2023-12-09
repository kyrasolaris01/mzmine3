package io.github.mzmine.modules.visualization.vankrevelendiagram;

import org.jfree.data.xy.AbstractXYZDataset;

public abstract class AbstractXYZBubbleDataset extends AbstractXYZDataset {

  public double getBubbleSizeValue(int series, int item) {
    double result = Double.NaN;
    Number bubbleSize = getBubbleSizeValue(series, item);
    if (bubbleSize != null) {
      result = bubbleSize.doubleValue();
    }
    return result;
  }

}
