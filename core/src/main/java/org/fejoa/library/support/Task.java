/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.support;


import java.util.concurrent.Executor;

public class Task<Update, Result> {
    public interface ICancelFunction {
        void cancel();
    }

    public interface ITaskFunction<Update, Result> extends ICancelFunction {
        void run(Task<Update, Result> task) throws Exception;
    }

    public interface IObserver<Update, Result> {
        void onProgress(Update update);
        void onResult(Result result);
        void onException(Exception exception);
    }

    static public class LooperThreadScheduler implements Executor {
        final private LooperThread thread;

        public LooperThreadScheduler(LooperThread thread) {
            this.thread = thread;
        }

        @Override
        public void execute(Runnable runnable) {
            thread.post(runnable);
        }
    }

    static public class NewThreadScheduler implements Executor {
        @Override
        public void execute(Runnable runnable) {
            new Thread(runnable).start();
        }
    }

    static public class CurrentThreadScheduler implements Executor {
        @Override
        public void execute(Runnable runnable) {
            runnable.run();
        }
    }

    private boolean canceled = false;
    private ITaskFunction<Update, Result> taskFunction;
    private IObserver<Update, Result> observable;
    private Executor startScheduler = new NewThreadScheduler();
    private Executor observerScheduler = new CurrentThreadScheduler();

    public Task(ITaskFunction<Update, Result> taskFunction) {
        this.taskFunction = taskFunction;
    }

    protected Task() {

    }

    protected void setTaskFunction(ITaskFunction<Update, Result> taskFunction) {
        this.taskFunction = taskFunction;
    }

    public Task<Update, Result> setStartScheduler(Executor startScheduler) {
        this.startScheduler = startScheduler;
        return this;
    }

    public Task<Update, Result> setObserverScheduler(Executor observerScheduler) {
        this.observerScheduler = observerScheduler;
        return this;
    }

    public ICancelFunction start(IObserver<Update, Result> observable) {
        this.observable = observable;

        final Task<Update, Result> that = this;
        startScheduler.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    taskFunction.run(that);
                } catch(Exception e) {
                    onException(e);
                }
            }
        });
        return taskFunction;
    }

    public ICancelFunction getCancelFunction() {
        return taskFunction;
    }

    public void cancel() {
        canceled = true;
        taskFunction.cancel();
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void onProgress(final Update update) {
        observerScheduler.execute(new Runnable() {
            @Override
            public void run() {
                observable.onProgress(update);
            }
        });
    }

    public void onResult(final Result result) {
        observerScheduler.execute(new Runnable() {
            @Override
            public void run() {
                observable.onResult(result);
            }
        });
    }

    public void onException(final Exception exception) {
        observerScheduler.execute(new Runnable() {
            @Override
            public void run() {
                observable.onException(exception);
            }
        });
    }
}
