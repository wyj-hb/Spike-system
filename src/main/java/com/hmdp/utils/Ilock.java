package com.hmdp.utils;
public interface Ilock {
    //获取锁
    boolean tryLock(long timeoutSec);
    //释放锁
    void unlock();
}
