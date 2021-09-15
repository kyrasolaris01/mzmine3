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

package io.github.mzmine.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ZIP related utilities
 */
public class ZipUtils {

  public static void unzipStream(ZipInputStream zipStream, File destinationFolder)
      throws IOException {

    if (!destinationFolder.exists()) {
      destinationFolder.mkdirs();
    }

    ZipEntry entry;
    int readLen;
    byte readBuffer[] = new byte[10000000];
    while ((entry = zipStream.getNextEntry()) != null) {
      File extractedFile = new File(destinationFolder, entry.getName());
      if (entry.isDirectory()) {
        extractedFile.mkdirs();
        continue;
      }
      try (FileOutputStream outputStream = new FileOutputStream(extractedFile)) {
        while ((readLen = zipStream.read(readBuffer)) != -1) {
          outputStream.write(readBuffer, 0, readLen);
        }
        outputStream.close();
        extractedFile.setExecutable(true);
      }
    }
  }

  /**
   *
   * @param stream
   * @param dir
   * @param destPath
   * @throws IOException
   */
  public static void addDirectoryToZip(@NotNull ZipOutputStream stream, @NotNull File dir,
      @Nullable String destPath) throws IOException {
    if (!dir.isDirectory()) {
      return;
    }

    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }

    destPath = (destPath == null) ? "" : destPath;
    if(!destPath.endsWith("/")) {
      destPath = destPath + "/";
    }

    for (final File file : files) {
      if (file.isDirectory()) {
        Path relPath = Paths.get(file.getAbsolutePath())
            .relativize(Paths.get(dir.getAbsolutePath()));
        addDirectoryToZip(stream, file, destPath + relPath.toString());
      }

      if(file.isFile()) {
        stream.putNextEntry(new ZipEntry(destPath + file.getName()));
        final StreamCopy copy = new StreamCopy();
        final FileInputStream inputStream = new FileInputStream(file);
        copy.copy(inputStream, stream);
      }
    }
  }

}
