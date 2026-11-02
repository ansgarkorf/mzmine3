/*
 * Copyright (c) 2004-2026 The mzmine Development Team
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

package io.github.mzmine.datamodel;

/**
 * Comprehensive two-dimensional gas chromatography (GCxGC) data. Each modulation of the first
 * column is a {@link Modulation} with the single scans of the second column. The second column
 * retention time (2D RT) is in minutes.
 * <p>
 * The GCxGC data model reuses the ion mobility data model internally (a modulation is stored like a
 * frame, a single scan like a mobility scan, and the 2D RT like the mobility), so that the IMS
 * algorithms can be used. Users never see ion mobility terms for GCxGC data.
 * <p>
 * Do not use {@code instanceof GCxGCRawDataFile} in shared code: files are wrapped during project
 * load. Use {@link io.github.mzmine.util.GcxGcUtils#isGcxGc(RawDataFile)} instead.
 */
public interface GCxGCRawDataFile extends IMSRawDataFile {

}
