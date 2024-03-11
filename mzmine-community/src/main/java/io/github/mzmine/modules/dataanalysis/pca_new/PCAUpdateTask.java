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
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.ColoredXYZDataset;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.DatasetAndRenderer;
import io.github.mzmine.gui.chartbasics.simplechart.datasets.RunOption;
import io.github.mzmine.gui.chartbasics.simplechart.renderers.ColoredXYShapeRenderer;
import io.github.mzmine.javafx.mvci.FxUpdateTask;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.taskcontrol.progress.TotalFinishedItemsProgress;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class PCAUpdateTask extends FxUpdateTask<PCAModel> {

  private final TotalFinishedItemsProgress progressProvider = new TotalFinishedItemsProgress(3);
  private final Integer rangePcIndex;
  private final Integer domainPcIndex;
  private final String metadataColumn;
  private final AbundanceMeasure abundance;
  private final List<FeatureList> flists;
  private final List<DatasetAndRenderer> scoresDatasets = new ArrayList<>();
  private final List<DatasetAndRenderer> loadingsDatasets = new ArrayList<>();
  private final List<Integer> components = new ArrayList<>();
  private PCARowsResult pcaRowsResult;

  protected PCAUpdateTask(@NotNull String taskName, PCAModel model) {
    super(taskName, model);

    domainPcIndex = Objects.requireNonNullElse(model.getDomainPc(), 0) - 1;
    rangePcIndex = Objects.requireNonNullElse(model.getRangePc(), 0) - 1;
    metadataColumn = model.getMetadataColumn();
    flists = model.getFlists();
    abundance = model.getAbundance();
  }

  @Override
  public boolean checkPreConditions() {
    if (rangePcIndex < 0 || domainPcIndex < 0) {
      return false;
    }

    if (metadataColumn != null && MZmineCore.getProjectMetadata().getColumnByName(metadataColumn) == null
        && !metadataColumn.isBlank()) {
      return false;
    }

    if (flists == null || flists.isEmpty() || flists.getFirst() == null) {
      return false;
    }

    if (abundance == null) {
      return false;
    }

    return true;
  }

  @Override
  protected void process() {
    pcaRowsResult = PCAUtils.performPCAOnRows(flists.get(0).getRows(), abundance);
    progressProvider.getAndIncrement();

    final PCAScoresProvider scores = new PCAScoresProvider(pcaRowsResult, "Scores", Color.RED,
        domainPcIndex, rangePcIndex,
        MZmineCore.getProjectMetadata().getColumnByName(metadataColumn));
    final ColoredXYZDataset scoresDS = new ColoredXYZDataset(scores, RunOption.THIS_THREAD);
    progressProvider.getAndIncrement();

    final PCALoadingsProvider loadings = new PCALoadingsProvider(pcaRowsResult, "Loadings", Color.RED,
        domainPcIndex, rangePcIndex);
    final ColoredXYZDataset loadingsDS = new ColoredXYZDataset(loadings, RunOption.THIS_THREAD);
    progressProvider.getAndIncrement();

    loadingsDatasets.add(new DatasetAndRenderer(loadingsDS, new ColoredXYShapeRenderer()));
    scoresDatasets.add(new DatasetAndRenderer(scoresDS, new ColoredXYShapeRenderer()));

    for (int i = 1; i <= pcaRowsResult.pcaResult().principalComponentsMatrix().getRowDimension();
        i++) {
      components.add(i);
    }
  }

  @Override
  protected void updateGuiModel() {
    model.setScoresDatasets(scoresDatasets);
    model.setLoadingsDatasets(loadingsDatasets);
    model.setPcaResult(pcaRowsResult);
    model.getAvailablePCs().setAll(components);

    if (rangePcIndex < components.size()) {
      model.setRangePc(rangePcIndex + 1);
    } else {
      model.setRangePc(components.getLast());
    }
    if (domainPcIndex < components.size()) {
      model.setDomainPc(domainPcIndex + 1);
    } else {
      model.setDomainPc(components.getLast());
    }
  }

  @Override
  public String getTaskDescription() {
    return STR."Computing PCA dataset for \{flists.get(0).getName()}";
  }

  @Override
  public double getFinishedPercentage() {
    return progressProvider.progress();
  }

}
