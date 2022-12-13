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

package io.github.mzmine.modules.dataprocessing.filter_cropfilter;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.BooleanParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.ranges.MZRangeParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;

public class CropFilterParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter dataFiles = new RawDataFilesParameter();

  public static final ScanSelectionParameter scanSelection = new ScanSelectionParameter();

  public static final MZRangeParameter mzRange =
      new MZRangeParameter("m/z", "m/z boundary of the cropped region");

  public static final StringParameter suffix =
      new StringParameter("Suffix", "This string is added to filename as suffix", "filtered");

  public static final BooleanParameter emptyScans =
      new BooleanParameter("Filter out empty scans",
          "USAGE IS STRONGLY DISCOURAGED!\nIf checked, empty scans will be filtered out. Disrupts the initial scan numbering!\n"
              + "Preserving empty scans might be useful for the later analysis.");

  public static final BooleanParameter autoRemove =
      new BooleanParameter("Remove source file after filtering",
          "If checked, original file will be removed and only filtered version remains");

  public CropFilterParameters() {
    super(new Parameter[] {dataFiles, scanSelection, mzRange, suffix, emptyScans, autoRemove},
        "https://mzmine.github.io/mzmine_documentation/module_docs/filter_raw_data/crop-filter.html");
  }

}
