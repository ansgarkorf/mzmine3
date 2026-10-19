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

import io.github.mzmine.taskcontrol.TaskService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the jobs on the thread pool of the task controller, which respects the maximum number of
 * threads set by the user, like the other multithreaded modules.
 * <p>
 * decision: the jobs are submitted to the executor of the task controller directly, not as sub
 * tasks of a ThreadPoolTask: those are checked one by one for a logged-in user, which the calling
 * task already passed. The calling thread works on the jobs too, so the jobs finish even if all
 * threads of the pool are busy, e.g., with other finder tasks waiting for their jobs.
 */
final class TaskPoolGcxGcJobs implements GcxGcJobs {

  // waiting time while other threads finish their last jobs
  private static final long WAIT_NANOS = 1_000_000;

  private final @NotNull BooleanSupplier canceled;

  /**
   * @param canceled true if the calling task was canceled
   */
  TaskPoolGcxGcJobs(@NotNull final BooleanSupplier canceled) {
    this.canceled = canceled;
  }

  /**
   * @return the number of threads of the task controller
   */
  static int threads() {
    return Math.max(1, TaskService.getController().getNumberOfThreads());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> @NotNull List<T> run(@NotNull final String description,
      @NotNull final List<Callable<T>> jobs) {
    final int n = jobs.size();
    if (n == 0) {
      return List.of();
    }
    final Object[] results = new Object[n];
    final AtomicInteger next = new AtomicInteger(0);
    final AtomicInteger running = new AtomicInteger(0);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final Runnable worker = () -> {
      while (failure.get() == null && !canceled.getAsBoolean()) {
        running.incrementAndGet();
        try {
          final int i = next.getAndIncrement();
          if (i >= n) {
            return;
          }
          results[i] = jobs.get(i).call();
        } catch (final Throwable t) {
          failure.compareAndSet(null, t);
        } finally {
          running.decrementAndGet();
        }
      }
    };

    final List<Future<?>> helpers = new ArrayList<>();
    final ExecutorService executor = TaskService.getController().getExecutor();
    for (int h = 0; h < Math.min(threads(), n) - 1; h++) {
      try {
        helpers.add(executor.submit(worker));
      } catch (final RejectedExecutionException e) {
        // the calling thread runs the jobs alone
        break;
      }
    }
    worker.run();
    // jobs of the helpers that are still running; helpers that did not start find no job
    while (running.get() > 0) {
      LockSupport.parkNanos(WAIT_NANOS);
    }
    helpers.forEach(helper -> helper.cancel(false));

    final Throwable error = failure.get();
    if (error != null) {
      throw new CancellationException(description + ": " + error.getMessage());
    }
    if (canceled.getAsBoolean() || next.get() < n) {
      throw new CancellationException("Canceled");
    }
    return (List<T>) Arrays.asList(results);
  }
}
