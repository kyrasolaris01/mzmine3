/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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

package io.github.mzmine.modules.visualization.network_overview;

import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.modules.visualization.compdb.CompoundDatabaseMatchTab;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableFX;
import io.github.mzmine.modules.visualization.featurelisttable_modular.FeatureTableTab;
import io.github.mzmine.modules.visualization.networking.visual.FeatureNetworkController;
import io.github.mzmine.modules.visualization.spectra.simplespectra.mirrorspectra.MirrorScanWindowController;
import io.github.mzmine.modules.visualization.spectra.simplespectra.mirrorspectra.MirrorScanWindowFXML;
import io.github.mzmine.modules.visualization.spectra.spectra_stack.SpectraStackVisualizerPane;
import io.github.mzmine.modules.visualization.spectra.spectralmatchresults.SpectraIdentificationResultsWindowFX;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.ToggleSwitch;
import org.graphstream.graph.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkOverviewController {

  private static final Logger logger = Logger.getLogger(NetworkOverviewController.class.getName());
  private final ObservableList<FeatureListRow> focussedRows;
  public ToggleSwitch cbBindToExternalTable;
  private boolean setUpCalled = false;

  private FeatureNetworkController networkController;
  private FeatureTableFX internalTable;
  private MirrorScanWindowController mirrorScanController;
  private SpectraIdentificationResultsWindowFX spectralMatchesController;
  private CompoundDatabaseMatchTab compoundMatchController;

  public BorderPane pnNetwork;
  public Tab tabAnnotations;
  public Tab tabSimilarity;
  public Tab tabAllMs2;
  public Tab tabNodes;
  public Tab tabEdges;
  public GridPane gridAnnotations;
  private EdgeTableController edgeTableController;
  private SpectraStackVisualizerPane allMs2Pane;

  public NetworkOverviewController() {
    this.focussedRows = FXCollections.observableArrayList();
  }

  public void setUp(@NotNull ModularFeatureList featureList, @Nullable FeatureTableFX externalTable,
      @Nullable List<? extends FeatureListRow> focussedRows) throws IOException {
    if (setUpCalled) {
      throw new IllegalStateException(
          "Cannot setup NetworkOverviewController twice. Create a new one.");
    }
    setUpCalled = true;

    // create network
    networkController = FeatureNetworkController.create(featureList, this.focussedRows);
    pnNetwork.setCenter(networkController.getMainPane());

    // create edge table
    createEdgeTable();


    // create internal table
    createInternalTable(featureList);
    linkFeatureTableSelections(internalTable, externalTable);

    // all MS2
    allMs2Pane = new SpectraStackVisualizerPane();

    // create annotations tab
    spectralMatchesController = new SpectraIdentificationResultsWindowFX(internalTable);
    compoundMatchController = new CompoundDatabaseMatchTab(internalTable);
    gridAnnotations.add(spectralMatchesController.getContent(), 0, 0);
    gridAnnotations.add(compoundMatchController.getContent(), 0, 1);

    // create mirror scan tab
    var mirrorScanTab = new MirrorScanWindowFXML();
    mirrorScanController = mirrorScanTab.getController();

    // set content to panes
    // tabEdges.

    tabSimilarity.setContent(mirrorScanController.getMainPane());
    tabAnnotations.setContent(gridAnnotations);
    tabAllMs2.setContent(allMs2Pane);

    // add callbacks
    networkController.getNetworkPane().getSelectedNodes()
        .addListener(this::handleSelectedNodesChanged);

    // set focussed rows last
    if (focussedRows != null) {
      this.focussedRows.setAll(focussedRows);
    }
  }

  private void createEdgeTable() {
    try {
      // Load the window FXML
      FXMLLoader loader = new FXMLLoader(getClass().getResource("EdgeTable.fxml"));
      BorderPane rootPane = loader.load();
      edgeTableController = loader.getController();
      edgeTableController.setGraph(networkController.getNetworkPane().getGraph());

      tabEdges.setContent(rootPane);
    } catch (Exception ex) {
      logger.log(Level.WARNING, "Could not load EdgeTable.fxml " + ex.getMessage(), ex);
    }
  }

  @NotNull
  private void createInternalTable(final @NotNull ModularFeatureList featureList) {
    FeatureTableTab tempTab = new FeatureTableTab(featureList);
    internalTable = tempTab.getFeatureTable();
    tabNodes.setContent(tempTab.getMainPane());

    var tabController = tempTab.getController();
    networkController.getNetworkPane().getVisibleRows()
        .addListener((ListChangeListener<? super FeatureListRow>) c -> {
          ObservableList<? extends FeatureListRow> visible = c.getList();
          tabController.getIdSearchField().setText(
              visible.stream().map(FeatureListRow::getID).map(Object::toString)
                  .collect(Collectors.joining(",")));
        });
  }


  private void linkFeatureTableSelections(final @NotNull FeatureTableFX internal,
      final @Nullable FeatureTableFX external) {
    // just apply selections in network
    internal.getSelectedTableRows()
        .addListener((ListChangeListener<? super TreeItem<ModularFeatureListRow>>) c -> {
          var list = c.getList().stream().map(TreeItem::getValue).toList();
          var networkPane = networkController.getNetworkPane();
          networkPane.getSelectedNodes().setAll(networkPane.getNodes(list));
        });
    // external directly sets new focussed rows - and then selected rows in the internal table
    if (external != null) {
      external.getSelectedTableRows()
          .addListener((ListChangeListener<? super TreeItem<ModularFeatureListRow>>) c -> {
            if (cbBindToExternalTable.isSelected()) {
              var list = c.getList().stream().map(TreeItem::getValue).toList();
              focussedRows.setAll(list);
            }
          });
    }
  }

  protected void handleSelectedNodesChanged(final Change<? extends Node> change) {
    var selectedRows = networkController.getNetworkPane().getRowsFromNodes(change.getList());
      showAnnotations(selectedRows);
    showAllMs2(selectedRows);
    if (selectedRows.size() >= 2) {
      showSimilarityMirror(selectedRows.get(0), selectedRows.get(1));
    } else {
      mirrorScanController.clearScans();
    }
  }

  private void showAllMs2(final List<FeatureListRow> rows) {
    allMs2Pane.setData(rows, false);
  }

  public void showAnnotations(final List<FeatureListRow> rows) {
    var spectralMatches = rows.stream().map(FeatureListRow::getSpectralLibraryMatches)
        .flatMap(Collection::stream).toList();
    spectralMatchesController.setMatches(spectralMatches);
    compoundMatchController.setFeatureRows(rows);
  }

  /**
   * Run the MSMS-MirrorScan module whenever user clicks on edges
   */
  public void showSimilarityMirror(FeatureListRow a, FeatureListRow b) {
    mirrorScanController.setScans(a.getMostIntenseFragmentScan(), b.getMostIntenseFragmentScan());
  }
}
