/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.modules.io.projectsave;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.io.projectload.ProjectLoaderParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.ExceptionUtils;
import io.github.mzmine.util.StreamCopy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javafx.scene.control.ButtonType;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

public class ProjectSavingTask extends AbstractTask {

  public static final String VERSION_FILENAME = "MZMINE_VERSION";
  public static final String CONFIG_FILENAME = "configuration.xml";
  public static final String PARAMETERS_FILENAME = "User parameters.xml";

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private File saveFile;
  private MZmineProjectImpl savedProject;

  private RawDataFileSaveHandler rawDataFileSaveHandler;
  private PeakListSaveHandler peakListSaveHandler;
  private UserParameterSaveHandler userParameterSaveHandler;

  private final int totalSaveItems;
  private int currentStage, finishedSaveItems = 0;
  private String currentSavedObjectName;

  // This hashtable maps raw data files to their ID within the saved project
  private Hashtable<RawDataFile, String> dataFilesIDMap;
  private boolean saveStandaloneProject = true;

  public ProjectSavingTask(MZmineProject project, ParameterSet parameters,
      @NotNull Date moduleCallDate) {
    super(null, moduleCallDate);
    this.savedProject = (MZmineProjectImpl) project;
    this.saveFile = parameters.getParameter(ProjectLoaderParameters.projectFile).getValue();
    dataFilesIDMap = new Hashtable<RawDataFile, String>();
    this.totalSaveItems = project.getDataFiles().length + project.getFeatureLists().size();
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getTaskDescription()
   */
  @Override
  public String getTaskDescription() {
    if (currentSavedObjectName == null) {
      return "Saving project";
    }
    return "Saving project (" + currentSavedObjectName + ")";
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#getFinishedPercentage()
   */
  @Override
  public double getFinishedPercentage() {

    if (totalSaveItems == 0) {
      return 0.0;
    }

    double currentItemProgress = 0.0;

    switch (currentStage) {
      case 2:
        if (rawDataFileSaveHandler != null) {
          currentItemProgress = rawDataFileSaveHandler.getProgress();
        }
        break;
      case 3:
        if (peakListSaveHandler != null) {
          currentItemProgress = peakListSaveHandler.getProgress();
        }
        break;
      case 4:
      case 5:
        return 1.0;
      default:
        return 0;
    }

    double progress = (finishedSaveItems + currentItemProgress) / totalSaveItems;

    return progress;
  }

  /**
   * @see io.github.mzmine.taskcontrol.Task#cancel()
   */
  @Override
  public void cancel() {

    logger.info("Canceling saving of project to " + saveFile);

    setStatus(TaskStatus.CANCELED);

    if (rawDataFileSaveHandler != null) {
      rawDataFileSaveHandler.cancel();
    }

    if (peakListSaveHandler != null) {
      peakListSaveHandler.cancel();
    }

    if (userParameterSaveHandler != null) {
      userParameterSaveHandler.cancel();
    }

  }

  /**
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    try {
      logger.info("Saving project to " + saveFile);
      setStatus(TaskStatus.PROCESSING);

      if (!MZmineCore.isHeadLessMode()) {
        ButtonType btn = MZmineCore.getDesktop().displayConfirmation(
            "Would you like to save a standalone project?\n\nIf yes, raw data files will be "
                + "copied into the project file. Otherwise the project will require that all raw "
                + "files stay in the same directories.\n<b>WARNING</b> If this is an existing "
                + "project, it is recommended to save it in the same way.", ButtonType.YES,
            ButtonType.NO, ButtonType.CANCEL);
        if (btn == ButtonType.CANCEL) {
          setStatus(TaskStatus.FINISHED);
          return;
        } else if (btn == ButtonType.YES) {
          saveStandaloneProject = true;
        } else {
          saveStandaloneProject = false;
        }
      } else {
        saveStandaloneProject = true;
      }

      // Prepare a temporary ZIP file. We create this file in the same
      // directory as the final saveFile to avoid moving between
      // filesystems in the last stage (renameTo)
      File tempFile = File.createTempFile(saveFile.getName(), ".tmp", saveFile.getParentFile());
      tempFile.deleteOnExit();

      // Create a ZIP stream writing to the temporary file
      FileOutputStream tempStream = new FileOutputStream(tempFile);
      ZipOutputStream zipStream = new ZipOutputStream(tempStream);

      // Stage 1 - save version and configuration
      currentStage++;
      saveVersion(zipStream);
      saveConfiguration(zipStream);
      if (isCanceled()) {
        zipStream.close();
        tempFile.delete();
        return;
      }

      // Stage 2 - save RawDataFile objects
      currentStage++;
      saveRawDataFiles(zipStream);
      if (isCanceled()) {
        zipStream.close();
        tempFile.delete();
        return;
      }

      // Stage 3 - save PeakList objects
      currentStage++;
      savePeakLists(zipStream);
      if (isCanceled()) {
        zipStream.close();
        tempFile.delete();
        return;
      }

      // Stage 4 - save user parameters
      currentStage++;
      saveUserParameters(zipStream);
      if (isCanceled()) {
        zipStream.close();
        tempFile.delete();
        return;
      }

      // Stage 5 - finish and close the temporary ZIP file
      currentStage++;
      currentSavedObjectName = null;
      zipStream.close();

      // Final check for cancel
      if (isCanceled()) {
        tempFile.delete();
        return;
      }

      // Move the temporary ZIP file to the final location
      if (saveFile.exists() && !saveFile.delete()) {
        throw new IOException("Could not delete old file " + saveFile);
      }

      boolean renameOK = tempFile.renameTo(saveFile);
      if (!renameOK) {
        throw new IOException(
            "Could not move the temporary file " + tempFile + " to the final location " + saveFile);
      }

      // Update the location of the project
      savedProject.setProjectFile(saveFile);

      // Update the window title to reflect the new name of the project
      // if (MZmineCore.getDesktop() instanceof MainWindow) {
      // MainWindow mainWindow = (MainWindow) MZmineCore.getDesktop();
      // mainWindow.updateTitle();
      // }

      logger.info("Finished saving the project to " + saveFile);
      setStatus(TaskStatus.FINISHED);

      // add to last loaded projects
      MZmineCore.getConfiguration().getLastProjectsParameter().addFile(saveFile);

    } catch (Throwable e) {

      e.printStackTrace();

      setStatus(TaskStatus.ERROR);

      if (currentSavedObjectName == null) {
        setErrorMessage("Failed saving the project: " + ExceptionUtils.exceptionToString(e));
      } else {
        setErrorMessage(
            "Failed saving the project. Error while saving " + currentSavedObjectName + ": "
                + ExceptionUtils.exceptionToString(e));
      }

    }
  }

  /**
   * Save the version info
   *
   * @throws java.io.IOException
   */
  private void saveVersion(ZipOutputStream zipStream) throws IOException {

    zipStream.putNextEntry(new ZipEntry(VERSION_FILENAME));

    String MZmineVersion = String.valueOf(MZmineCore.getMZmineVersion());

    zipStream.write(MZmineVersion.getBytes());

  }

  /**
   * Save the configuration file.
   *
   * @throws java.io.IOException
   */
  private void saveConfiguration(ZipOutputStream zipStream) throws IOException {

    logger.info("Saving configuration file");

    currentSavedObjectName = "configuration";

    zipStream.putNextEntry(new ZipEntry(CONFIG_FILENAME));

    try {
      File tempConfigFile = File.createTempFile("mzmineconfig", ".tmp");
      MZmineCore.getConfiguration().saveConfiguration(tempConfigFile);
      FileInputStream fileStream = new FileInputStream(tempConfigFile);

      StreamCopy copyMachine = new StreamCopy();
      copyMachine.copy(fileStream, zipStream);

      fileStream.close();
      tempConfigFile.delete();
    } catch (Exception e) {
      e.printStackTrace();
      logger.warning("Could not save configuration" + ExceptionUtils.exceptionToString(e));
    }
  }

  /**
   * Save the raw data files
   */
  private void saveRawDataFiles(ZipOutputStream zipStream)
      throws IOException, ParserConfigurationException {

    AtomicBoolean finished = new AtomicBoolean(false);
    rawDataFileSaveHandler = new RawDataFileSaveHandler(savedProject, zipStream,
        saveStandaloneProject, getModuleCallDate());
    rawDataFileSaveHandler.addTaskStatusListener((task, newStatus, oldStatus) -> {
      switch (newStatus) {
        case WAITING, PROCESSING -> {
        }
        case FINISHED -> {
          finished.set(true);
        }
        case CANCELED -> {
          finished.set(true);
          setStatus(TaskStatus.CANCELED);
        }
        case ERROR -> {
          finished.set(true);
          setErrorMessage("Error while saving raw data files.");
          setStatus(TaskStatus.ERROR);
        }
      }
    });
    MZmineCore.getTaskController().addTask(rawDataFileSaveHandler);

    while (!finished.get() && !isCanceled()) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Save the feature lists
   *
   * @throws SAXException
   * @throws TransformerConfigurationException
   */
  private void savePeakLists(ZipOutputStream zipStream)
      throws IOException, TransformerConfigurationException, SAXException {

    for (FeatureList featureList : savedProject.getFeatureLists()) {
      FeatureListSaveTask saveTask = new FeatureListSaveTask((ModularFeatureList) featureList,
          zipStream);

      AtomicBoolean finished = new AtomicBoolean(false);
      saveTask.addTaskStatusListener((task, newStatus, oldStatus) -> {
        switch (newStatus) {
          case WAITING, PROCESSING -> {
          }
          case FINISHED, ERROR, CANCELED -> {
            finished.set(true);
          }
        }
      });
      MZmineCore.getTaskController().addTask(saveTask);

      while (!finished.get()) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }

      if (isCanceled()) {
        break;
      }
    }
  }

  /**
   * Save the feature lists
   *
   * @throws SAXException
   * @throws TransformerConfigurationException
   */
  private void saveUserParameters(ZipOutputStream zipStream)
      throws IOException, TransformerConfigurationException, SAXException {

    if (isCanceled()) {
      return;
    }

    logger.info("Saving user parameters");

    zipStream.putNextEntry(new ZipEntry(PARAMETERS_FILENAME));

    userParameterSaveHandler = new UserParameterSaveHandler(zipStream, savedProject,
        dataFilesIDMap);

    currentSavedObjectName = "User parameters";
    userParameterSaveHandler.saveParameters();

  }

}
