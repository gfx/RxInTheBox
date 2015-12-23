package com.github.gfx.rxinthebox;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

class Observable<T> {
    static <U> Observable<U> create(OnSubscribe<U> onSubscribe) {
        return new Observable<>(onSubscribe);
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
}

interface Subscriber<T>  {
    void onNext(T value);
    void onComplete();
    void onError(Throwable e);
}

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        doit();
    }

    Observable<String> getSomethingDelayed(final String value) {
        return Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(final Subscriber<String> subscriber) {
                // do something important here

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        subscriber.onNext(value);
                        subscriber.onComplete();
                    }
                }, 1000);
            }
        });
    }

    void doit() {
        Observable<String> observable = getSomethingDelayed("foo");

        observable.subscribe(new Subscriber<String>() {
            @Override
            public void onNext(String value) {
                log("onNext: " + value);
            }

            @Override
            public void onComplete() {
                log("onComplete");
            }

            @Override
            public void onError(Throwable e) {
                log("onError");
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
