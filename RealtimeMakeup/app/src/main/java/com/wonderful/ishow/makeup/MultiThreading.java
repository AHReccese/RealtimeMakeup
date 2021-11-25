package com.wonderful.ishow.makeup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiThreading {

    private ExecutorService executorService;

    public MultiThreading(int poolSize) {
        executorService = Executors.newFixedThreadPool(poolSize);
    }

    public void exec(Runnable runnable) {
        executorService.execute(runnable);
    }

}
