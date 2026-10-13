package io.github.ianverderame.urlshortener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A single-threaded stand-in for the click executor: {@link #synchronous()} runs each task on the
 * caller's thread, {@link #deferred()} holds tasks until {@link #runPending()}. Both reject work
 * after shutdown, like a real pool.
 */
final class TestExecutorService extends AbstractExecutorService {

  private final boolean immediate;
  private final Deque<Runnable> pending = new ArrayDeque<>();
  private boolean shutdown;

  private TestExecutorService(boolean immediate) {
    this.immediate = immediate;
  }

  static TestExecutorService synchronous() {
    return new TestExecutorService(true);
  }

  static TestExecutorService deferred() {
    return new TestExecutorService(false);
  }

  void runPending() {
    while (!pending.isEmpty()) {
      pending.removeFirst().run();
    }
  }

  @Override
  public void execute(Runnable task) {
    if (shutdown) {
      throw new RejectedExecutionException("shut down");
    }
    if (immediate) {
      task.run();
    } else {
      pending.addLast(task);
    }
  }

  @Override
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown = true;
    List<Runnable> unrun = new ArrayList<>(pending);
    pending.clear();
    return unrun;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return shutdown;
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    return shutdown;
  }
}
