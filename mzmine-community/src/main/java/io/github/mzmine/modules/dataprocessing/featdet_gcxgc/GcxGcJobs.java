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

package io.github.mzmine.modules.dataprocessing.featdet_gcxgc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import org.jetbrains.annotations.NotNull;

/**
 * Runs independent jobs of the GCxGC 2D feature finder, in parallel on the task pool of mzmine
 * ({@link TaskPoolGcxGcJobs}) or one after the other ({@link #sequential()}, e.g., in tests).
 */
interface GcxGcJobs {

  /**
   * @param description shown for the jobs in the task list
   * @return the results in the order of the jobs
   * @throws CancellationException if the jobs were canceled or a job failed
   */
  <T> @NotNull List<T> run(@NotNull String description, @NotNull List<Callable<T>> jobs);

  /**
   * @return a runner that runs the jobs on the calling thread
   */
  static @NotNull GcxGcJobs sequential() {
    return new GcxGcJobs() {
      @Override
      public <T> @NotNull List<T> run(@NotNull final String description,
          @NotNull final List<Callable<T>> jobs) {
        final List<T> results = new ArrayList<>(jobs.size());
        for (final Callable<T> job : jobs) {
          try {
            results.add(job.call());
          } catch (final Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
          }
        }
        return results;
      }
    };
  }
}
