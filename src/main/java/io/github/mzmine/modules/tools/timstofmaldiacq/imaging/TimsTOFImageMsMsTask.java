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

package io.github.mzmine.modules.tools.timstofmaldiacq.imaging;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.IMSImagingRawDataFile;
import io.github.mzmine.datamodel.ImagingFrame;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.data_access.EfficientDataAccess.MobilityScanDataType;
import io.github.mzmine.datamodel.data_access.MobilityScanDataAccess;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.gui.preferences.NumberFormats;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.io.import_rawdata_bruker_tdf.datamodel.sql.MaldiSpotInfo;
import io.github.mzmine.modules.tools.timstofmaldiacq.CeSteppingTables;
import io.github.mzmine.modules.tools.timstofmaldiacq.TimsTOFAcquisitionUtils;
import io.github.mzmine.modules.tools.timstofmaldiacq.TimsTOFMaldiAcquisitionTask;
import io.github.mzmine.modules.tools.timstofmaldiacq.imaging.acquisitionwriters.MaldiMs2AcqusitionWriter;
import io.github.mzmine.modules.tools.timstofmaldiacq.imaging.acquisitionwriters.SingleSpotMs2Writer;
import io.github.mzmine.modules.tools.timstofmaldiacq.precursorselection.MaldiTimsPrecursor;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.IonMobilityUtils;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TimsTOFImageMsMsTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(
      TimsTOFMaldiAcquisitionTask.class.getName());

  public static final NumberFormats formats = MZmineCore.getConfiguration().getGuiFormats();

  public final FeatureList[] flists;
  public final ParameterSet parameters;
  private final Double maxMobilityWidth;
  private final Double minMobilityWidth;
  private final File acqControl;
  private final File savePathDir;
  private final Boolean exportOnly;
  private final Double isolationWidth;
  private final MZTolerance isolationWindow;
  private final int numMsMs;
  private final double minMsMsIntensity;
  private final int minDistance;

  private final double minChimerityScore;
  private final int[] spotsFeatureCounter;

  private final Ms2ImagingMode ms2ImagingMode;
  private final @Nullable ParameterSet ms2ModuleParameters;
  private final @NotNull MaldiMs2AcqusitionWriter ms2Module;
  private final List<Double> collisionEnergies;
  private final CeSteppingTables ceSteppingTables;

  private String desc = "Running MAlDI acquisition";
  private double progress = 0d;
  private File currentCeFile = null;
  private final int totalMsMsPerFeature;

  protected TimsTOFImageMsMsTask(@Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate, ParameterSet parameters, @NotNull MZmineProject project) {
    super(storage, moduleCallDate);
    this.parameters = parameters;

    flists = parameters.getValue(TimsTOFImageMsMsParameters.flists).getMatchingFeatureLists();
    maxMobilityWidth = parameters.getValue(TimsTOFImageMsMsParameters.maxMobilityWidth);
    minMobilityWidth = parameters.getValue(TimsTOFImageMsMsParameters.minMobilityWidth);
    acqControl = parameters.getValue(TimsTOFImageMsMsParameters.acquisitionControl);
    savePathDir = parameters.getValue(TimsTOFImageMsMsParameters.savePathDir);
    exportOnly = parameters.getValue(TimsTOFImageMsMsParameters.exportOnly);
    isolationWidth = parameters.getValue(TimsTOFImageMsMsParameters.isolationWidth);
    numMsMs = parameters.getValue(TimsTOFImageMsMsParameters.numMsMs);
    collisionEnergies = parameters.getValue(TimsTOFImageMsMsParameters.collisionEnergies);
    minMsMsIntensity = parameters.getValue(TimsTOFImageMsMsParameters.minimumIntensity);
    minDistance = parameters.getValue(TimsTOFImageMsMsParameters.minimumDistance);
    minChimerityScore = parameters.getValue(TimsTOFImageMsMsParameters.maximumChimerity);
    isolationWindow = new MZTolerance((isolationWidth / 1.7) / 2,
        0d); // isolation window typically wider than set
    ms2Module = parameters.getValue(TimsTOFImageMsMsParameters.ms2ImagingMode).getModule();
    ms2ModuleParameters = parameters.getValue(TimsTOFImageMsMsParameters.ms2ImagingMode)
        .getParameterSet();
    ms2ImagingMode = ms2Module.equals(MZmineCore.getModuleInstance(SingleSpotMs2Writer.class))
        ? Ms2ImagingMode.SINGLE : Ms2ImagingMode.TRIPLE;

    totalMsMsPerFeature = numMsMs * collisionEnergies.size();
    spotsFeatureCounter = new int[totalMsMsPerFeature + 1];

    ceSteppingTables = new CeSteppingTables(collisionEnergies, isolationWidth);
  }

  @Override
  public String getTaskDescription() {
    return desc;
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    if (!savePathDir.exists()) {
      savePathDir.mkdirs();
    }

    if (isCanceled()) {
      return;
    }

    final FeatureList flist = flists[0];
    final IMSImagingRawDataFile file = (IMSImagingRawDataFile) flist.getRawDataFile(0);

    final MobilityScanDataAccess access = new MobilityScanDataAccess(file,
        MobilityScanDataType.CENTROID, (List<Frame>) file.getFrames(1));

    if (flist.getNumberOfRows() == 0) {
      setStatus(TaskStatus.FINISHED);
      return;
    }

    final Map<ImagingFrame, ImagingSpot> frameSpotMap = new HashMap<>();
    final Map<Feature, List<ImagingSpot>> featureSpotMap = new HashMap<>();
    List<FeatureListRow> rows = new ArrayList<>(flist.getRows());
    // sort low to high area. First find spots for low intensity features so we definitely fragment
    // those. should be easier to find spots for high area features
    rows.sort(Comparator.comparingDouble(FeatureListRow::getAverageArea));
    for (int i = 0; i < rows.size(); i++) {
      progress = 0.1 * i / (double) rows.size();

      final FeatureListRow row = rows.get(i);
      if (isCanceled()) {
        return;
      }

      final Feature f = row.getBestFeature();
      if (f.getHeight() < minMsMsIntensity) {
        continue;
      }

      final MaldiTimsPrecursor precursor = new MaldiTimsPrecursor(f, f.getMZ(),
          TimsTOFAcquisitionUtils.adjustMobilityRange(f.getMobility(), f.getMobilityRange(),
              minMobilityWidth, maxMobilityWidth), collisionEnergies);

      final IonTimeSeries<? extends Scan> data = f.getFeatureData();
      final IonTimeSeries<? extends ImagingFrame> imagingData = (IonTimeSeries<? extends ImagingFrame>) data;

      // check existing msms spots first
      addEntriesToExistingSpots(access, minMsMsIntensity, frameSpotMap, precursor, imagingData,
          numMsMs, featureSpotMap, minDistance, minChimerityScore);

      // we have all needed entries
      if (precursor.getLowestMsMsCountForCollisionEnergies() >= numMsMs) {
        continue;
      }

      if (featureSpotMap.size() == access.getNumberOfScans()) {
        logger.warning(() -> "Too many MS/MS spots, cannot create any more.");
        // can still add more features to existing spots
        continue;
      }

      // find new entries
      createNewMsMsSpots(access, frameSpotMap, minMsMsIntensity, imagingData, precursor, numMsMs,
          featureSpotMap, minDistance, minChimerityScore);

      spotsFeatureCounter[precursor.getTotalMsMs()]++;
    }

    for (int i = 0; i < spotsFeatureCounter.length; i++) {
      final int j = i;
      logger.finest(
          () -> String.format("%d features have %d MS/MS spots. (%.1f)", (spotsFeatureCounter[j]),
              j, (spotsFeatureCounter[j] / (double) rows.size() * 100)));
    }

    final File acqFile = new File(savePathDir, "acquisition.txt");
    acqFile.delete();

    // sort the spots by line, so we limit the movement that we have to do
    final List<ImagingSpot> sortedSpots = frameSpotMap.entrySet().stream().sorted((e1, e2) -> {
      int xCompare = Integer.compare(e1.getKey().getMaldiSpotInfo().xIndexPos(),
          e2.getValue().spotInfo().xIndexPos());
      if (xCompare != 0) {
        return xCompare;
      }
      return Integer.compare(e1.getKey().getMaldiSpotInfo().yIndexPos(),
          e2.getKey().getMaldiSpotInfo().yIndexPos());
    }).map(Entry::getValue).toList();

    ms2Module.writeAcqusitionFile(acqFile, sortedSpots, ceSteppingTables, ms2ModuleParameters,
        this::isCanceled, savePathDir);

    /*for (int i = 0; i < sortedSpots.size(); i++) {
      final ImagingSpot spot = sortedSpots.get(i);

      progress = 0.2 + 0.8 * i / sortedSpots.size();
      if (isCanceled()) {
        return;
      }

      final MaldiSpotInfo spotInfo = spot.spotInfo();

      int counter = 1;
      for (int x = 0; x < 2; x++) {
        for (int y = 0; y < 2; y++) {
          if (x == 0 && y == 0 || spot.getPrecursorList(x, y).isEmpty()) {
            continue;
          }
          try {
            TimsTOFAcquisitionUtils.appendToCommandFile(acqFile, spotInfo.spotName(),
                spot.getPrecursorList(x, y), null, null, x * laserOffsetX, y * laserOffsetY,
                counter++, 0, savePathDir, spotInfo.spotName() + "_" + counter, null, false);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }*/

    if (!isCanceled()) {
      TimsTOFAcquisitionUtils.acquire(acqControl, acqFile, exportOnly);
    }

    setStatus(TaskStatus.FINISHED);
  }

  private void createNewMsMsSpots(MobilityScanDataAccess access,
      Map<ImagingFrame, ImagingSpot> spotMap, double minMsMsIntensity,
      IonTimeSeries<? extends ImagingFrame> imagingData, MaldiTimsPrecursor precursor, int numMsMs,
      Map<Feature, List<ImagingSpot>> featureSpotMap, double minDistance,
      double minChimerityScore) {
    final IntensitySortedSeries<IonTimeSeries<? extends ImagingFrame>> imagingSorted = new IntensitySortedSeries<>(
        imagingData);
    final List<ImagingSpot> spots = featureSpotMap.computeIfAbsent(precursor.feature(),
        f -> new ArrayList<>());

    while (imagingSorted.hasNext() && precursor.getTotalMsMs() < totalMsMsPerFeature) {

      final Integer nextIndex = imagingSorted.next();
      final ImagingFrame frame = imagingData.getSpectrum(nextIndex);

      if (spotMap.containsKey(frame)) {
        // spot already used with a different collision energy and this feature did not fit
        continue;
      }

      final MaldiSpotInfo spotInfo = frame.getMaldiSpotInfo();
      if (spotInfo == null) {
        continue;
      }

      // sorted by intensity, if we are below the threshold, don't add a new scan
      if (imagingData.getIntensity(nextIndex) < minMsMsIntensity) {
        break;
      }

      // check if we can make this spot a new one with a new collision energy
      final Double collisionEnergy = TimsTOFAcquisitionUtils.getBestCollisionEnergyForSpot(
          minDistance, precursor, spots, spotInfo, collisionEnergies, 3);

      if (collisionEnergy == null) {
        continue;
      }

      if (!checkChimerityScore(access, precursor, minChimerityScore, frame)) {
        continue;
      }

      final ImagingSpot spot = spotMap.computeIfAbsent(frame,
          a -> new ImagingSpot(a.getMaldiSpotInfo(), ms2ImagingMode, collisionEnergy));
      if (spot.addPrecursor(precursor)) {
        spots.add(spot);
        logger.finest(
            "Adding precursor " + formats.mz(precursor.feature().getMZ()) + " to new spot "
                + spot.spotInfo().spotName());
      }
    }
  }

  private void addEntriesToExistingSpots(MobilityScanDataAccess access, double minMsMsIntensity,
      Map<ImagingFrame, ImagingSpot> spotMap, MaldiTimsPrecursor precursor,
      IonTimeSeries<? extends ImagingFrame> imagingData, final int numMsMs,
      Map<Feature, List<ImagingSpot>> featureSpotMap, double minDistance,
      final double minChimerityScore) {
    final List<ImagingFrame> usedFrames = getPossibleExistingSpots(spotMap, imagingData,
        minMsMsIntensity);

    final List<ImagingSpot> spots = featureSpotMap.computeIfAbsent(precursor.feature(),
        f -> new ArrayList<>());
    for (ImagingFrame usedFrame : usedFrames) {
      final ImagingSpot imagingSpot = spotMap.get(usedFrame);

      // check if we meet the minimum distance requirement
      final List<Double> collisionEnergy = TimsTOFAcquisitionUtils.getPossibleCollisionEnergiesForSpot(
          minDistance, precursor, spots, usedFrame.getMaldiSpotInfo(), collisionEnergies, numMsMs);
      if (collisionEnergy.isEmpty() || !collisionEnergy.contains(
          imagingSpot.getColissionEnergy())) {
        continue;
      }

      if (!checkChimerityScore(access, precursor, minChimerityScore, usedFrame)) {
        continue;
      }

      // check if the entry fits into the precursor ramp at that spot
      if (imagingSpot.addPrecursor(precursor)) {
        logger.finest(
            "Adding precursor " + formats.mz(precursor.feature().getMZ()) + " to existing spot "
                + imagingSpot.spotInfo().spotName());
        spots.add(imagingSpot);
      }

      if (precursor.getLowestMsMsCountForCollisionEnergies() >= numMsMs) {
        break;
      }
    }
  }

  private boolean checkChimerityScore(MobilityScanDataAccess access, MaldiTimsPrecursor precursor,
      double minChimerityScore, ImagingFrame usedFrame) {
    access.jumpToFrame(usedFrame);

    // check the chimerity of the current spot.
    final double chimerityScore = IonMobilityUtils.getIsolationChimerity(precursor.mz(), access,
        isolationWindow.getToleranceRange(precursor.mz()), precursor.oneOverK0());
    if (chimerityScore < minChimerityScore) {
      return false;
    }
    return true;
  }

  /**
   * @param spotMap          The current spots selected for MS/MS experiments.
   * @param imagingData      The {@link Feature#getFeatureData()} of the image feature.
   * @param minMsMsIntensity The minimum intensity for an MS/MS.
   * @return A list of all spots currently selected for MS/MS experiments, that the feature has the
   * minimum intensity in.
   */
  private List<ImagingFrame> getPossibleExistingSpots(Map<ImagingFrame, ImagingSpot> spotMap,
      IonTimeSeries<? extends ImagingFrame> imagingData, double minMsMsIntensity) {
    final List<ImagingFrame> frames = new ArrayList<>();
    final List<? extends ImagingFrame> spectra = imagingData.getSpectra();

    for (int i = 0; i < spectra.size(); i++) {
      if (imagingData.getIntensity(i) < minMsMsIntensity) {
        continue;
      }
      final ImagingFrame frame = spectra.get(i);
      if (spotMap.containsKey(frame)) {
        frames.add(frame);
      }
    }
    return frames;
  }
}
