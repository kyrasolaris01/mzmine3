/*
 * Copyright (c) 2004-2024 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataanalysis.pca_new;

import io.github.mzmine.datamodel.AbundanceMeasure;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularDataModel;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public class PCACalculator {

  private static final Logger logger = Logger.getLogger(PCACalculator.class.getName());
  private final RealMatrix data;
  private SingularValueDecomposition svd;
  private RealMatrix loadings;
  private RealMatrix projectData;
  private boolean computed = false;
  private RealMatrix principalComponentMatrix;

  public PCACalculator(RealMatrix data) {
    this.data = data;
  }

  public static RealMatrix performMeanCenter(RealMatrix data, boolean inPlace) {

    RealMatrix result = inPlace ? data
        : new Array2DRowRealMatrix(data.getRowDimension(), data.getColumnDimension());

    for (int col = 0; col < data.getColumnDimension(); col++) {
      final RealVector columnVector = data.getColumnVector(col);
      double sum = 0;
      for (int row = 0; row < columnVector.getDimension(); row++) {
        sum += columnVector.getEntry(row);
      }
      final double mean = sum / columnVector.getDimension();

      var resultVector = result.getColumnVector(col);
      for (int row = 0; row < columnVector.getDimension(); row++) {
        resultVector.setEntry(row, columnVector.getEntry(row) - mean);
      }
      result.setColumnVector(col, resultVector);
    }
    return result;
  }

  public static RealMatrix createDatasetFromRows(List<FeatureListRow> rows,
      List<RawDataFile> allFiles, AbundanceMeasure measure) {

    // colums = features, rows = raw files
    //        f1  f2  f3  f4
    // file1  5   0   2   3
    // file2  2   2   1   1
    // file3  3   4   4   5

    final RealMatrix data = new Array2DRowRealMatrix(rows.size(), allFiles.size());
    for (int fileIndex = 0; fileIndex < allFiles.size(); fileIndex++) {
      final RawDataFile file = allFiles.get(fileIndex);
      for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
        final FeatureListRow row = rows.get(rowIndex);
        final Feature feature = row.getFeature(file);

        final double abundance;
        if (feature != null) {
          abundance = measure.get((ModularDataModel) feature);
        } else {
          abundance = 0.0d;
        }
        data.setEntry(fileIndex, rowIndex, abundance);
      }
    }

    return data;
  }

  public void performPCA(int numComponents) {

    logger.finest(() -> "Performing mean centering");
    final RealMatrix centeredMatrix = performMeanCenter(data, false);

    logger.finest(() -> "Performing singular value decomposition");
    svd = new SingularValueDecomposition(centeredMatrix);

    // Get principal components (columns of the orthogonal matrix U)
    principalComponentMatrix = svd.getU();
    final RealMatrix principalComponents = principalComponentMatrix.getSubMatrix(0,
        principalComponentMatrix.getRowDimension() - 1, 0, numComponents);

    // Get loadings (right singular vectors, columns of the orthogonal matrix V)
    loadings = svd.getV();

    setComputed(true);
  }

  public RealMatrix getPrincipalComponentMatrix() {
    if (!isComputed()) {
      throw new PcaNotComputedException();
    }
    return principalComponentMatrix;
  }

  private void setPrincipalComponentMatrix(RealMatrix principalComponentMatrix) {
    this.principalComponentMatrix = principalComponentMatrix;
  }

  public RealMatrix getPrincipalComponents(int num) {
    if (!isComputed()) {
      throw new PcaNotComputedException();
    }

    return principalComponentMatrix.getSubMatrix(0, principalComponentMatrix.getRowDimension() - 1,
        0, num);
  }

  public SingularValueDecomposition getSvd() {
    if (!isComputed()) {
      throw new PcaNotComputedException();
    }
    return svd;
  }

  private void setSvd(SingularValueDecomposition svd) {
    this.svd = svd;
  }

  public RealMatrix getLoadings() {
    if (!isComputed()) {
      throw new PcaNotComputedException();
    }
    return loadings;
  }

  private void setLoadings(RealMatrix loadings) {
    this.loadings = loadings;
  }

  public RealMatrix getProjectData() {
    if (!isComputed()) {
      throw new PcaNotComputedException();
    }
    return projectData;
  }

  private void setProjectData(RealMatrix projectData) {
    this.projectData = projectData;
  }

  public boolean isComputed() {
    return computed;
  }

  private void setComputed(boolean computed) {
    this.computed = computed;
  }
}
