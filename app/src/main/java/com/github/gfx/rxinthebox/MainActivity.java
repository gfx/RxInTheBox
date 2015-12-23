package com.github.gfx.rxinthebox;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Observable<T> {

    static <U> Observable<U> create(OnSubscribe<U> onSubscribe) {
        return new Observable<>(onSubscribe);
    }

    static <U> Observable<U> just(final U value) {
        return new Observable<>(new OnSubscribe<U>() {
            @Override
            public void call(Subscriber<U> subscriber) {
                subscriber.onNext(value);
                subscriber.onComplete();
            }
        });
    }

    OnSubscribe<T> onSubscribe;

    Observable(OnSubscribe<T> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    interface OnSubscribe<T> {

        void call(Subscriber<T> subscriber);
    }

    void subscribe(Subscriber<T> subscriber) {
        onSubscribe.call(subscriber);
    }

    public interface Operator<R, T> {

        Subscriber<T> call(Subscriber<R> value);
    }

    public <R> Observable<R> lift(final Operator<R, T> operator) {
        return new Observable<>(new OnSubscribe<R>() {
            @Override
            public void call(Subscriber<R> subscriber) {
                onSubscribe.call(operator.call(subscriber));
            }
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        return Observable.just(this).lift(new OperatorSubscribeOn<T>(scheduler));
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        return this.lift(new OperatorObserveOn<T>(scheduler));
    }
}

interface Subscriber<T> {

    void onNext(T value);

    void onComplete();

    void onError(Throwable e);
}

interface Scheduler {

    interface Worker {

        void schedule(Runnable task);
    }

    Worker createWorker();
}

class HandlerScheduler implements Scheduler {

    final Handler handler;

    public HandlerScheduler(Handler handler) {
        this.handler = handler;
    }

    @Override
    public Worker createWorker() {
        return new Worker() {
            @Override
            public void schedule(Runnable task) {
                handler.post(task);
            }
        };
    }
}

class ExecutorServiceScheduler implements Scheduler {

    final ExecutorService executorService;

    public ExecutorServiceScheduler(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public Worker createWorker() {
        return new Worker() {
            @Override
            public void schedule(Runnable task) {
                executorService.execute(task);
            }
        };
    }
}

class AndroidSchedulers {

    static Scheduler io() {
        return new ExecutorServiceScheduler(Executors.newCachedThreadPool());
    }

    static Scheduler mainThread() {
        return new HandlerScheduler(new Handler(Looper.getMainLooper()));
    }
}

class OperatorSubscribeOn<T> implements Observable.Operator<T, Observable<T>> {

    final Scheduler scheduler;

    public OperatorSubscribeOn(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Subscriber<Observable<T>> call(final Subscriber<T> subscriber) {
        final Scheduler.Worker worker = scheduler.createWorker();
        return new Subscriber<Observable<T>>() {
            @Override
            public void onNext(final Observable<T> observable) {
                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        observable.subscribe(subscriber);
                    }
                });
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }
        };
    }
}

class OperatorObserveOn<T> implements Observable.Operator<T, T> {

    final Scheduler scheduler;

    public OperatorObserveOn(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Subscriber<T> call(final Subscriber<T> subscriber) {
        final Scheduler.Worker worker = scheduler.createWorker();

        return new Subscriber<T>() {
            @Override
            public void onNext(final T value) {
                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        subscriber.onNext(value);
                    }
                });
            }

            @Override
            public void onComplete() {
                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        subscriber.onComplete();
                    }
                });
            }

            @Override
            public void onError(final Throwable e) {
                worker.schedule(new Runnable() {
                    @Override
                    public void run() {
                        subscriber.onError(e);
                    }
                });
            }
        };
    }
}

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        doit();
    }

    void doit() {
        Observable<String> observable = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<String> subscriber) {
                Log.d(TAG, "OnSubscribe on " + Thread.currentThread());
                subscriber.onNext("foo");
                subscriber.onComplete();
            }
        });

        observable
                .subscribeOn(AndroidSchedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onNext(String value) {
                        log("onNext: " + value + " on " + Thread.currentThread());
                    }

                    @Override
                    public void onComplete() {
                        log("onComplete on " + Thread.currentThread());
                    }

                    @Override
                    public void onError(Throwable e) {
                        log("onError on " + Thread.currentThread());
                    }
                });
    }

    void log(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
