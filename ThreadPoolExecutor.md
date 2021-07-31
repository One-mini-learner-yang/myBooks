### 

ThreadPoolExecutor

#### 内部参数

> ctl：是线程池控制状态位，是一个原子整数，其中包括线程池状态以及线程池运行线程数量（workerCount）

```java
private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
```

> 此处说明ctl进行的一种位运算，线程池状态与线程池运行线程数量用或运算连接在一起

```java
private static int ctlOf(int rs, int wc) { return rs | wc; }
```

> 此处说明在ctl中高3位表示线程池状态，剩余29位为线程池运行线程的数量

```java
private static final int COUNT_BITS = Integer.SIZE - 3;
private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

// runState is stored in the high-order bits
private static final int RUNNING    = -1 << COUNT_BITS;
private static final int SHUTDOWN   =  0 << COUNT_BITS;
private static final int STOP       =  1 << COUNT_BITS;
private static final int TIDYING    =  2 << COUNT_BITS;
private static final int TERMINATED =  3 << COUNT_BITS;

// Packing and unpacking ctl
private static int runStateOf(int c)     { return c & ~CAPACITY; }
private static int workerCountOf(int c)  { return c & CAPACITY; }
```

> 线程池状态
>
> RUNNING：运行 （111）
>
> SHUTDOWN：停止向外界获取任务，但是在等待队列中的任务会继续执行，知道全部执行完，关闭线程池  （000）
>
> STOP：强制停止，停止向外界获取任务，同时清除队列中的任务，中断正在进行的任务  （001）
>
> TIDYING：停止所有线程  （010）
>
> TERMINATED：线程池关闭后最后状态

<img src="./pic/线程池状态.png" style="zoom:67%;" />

```java
/**放置任务的阻塞队列
	*当workQueue.poll()==null并不意味着workQueue一定为空，可能此处使用的是DelayQueues（等延时时间到才返回非null）
	*/
private final BlockingQueue<Runnable> workQueue;

/**
	*创建新线程的工厂，用于addWorker方法处
	*/
private volatile ThreadFactory threadFactory;

/**
	*拒绝策略，用于线程池满或已经关闭的情况
	*默认直接抛异常
	*private static final RejectedExecutionHandler defaultHandler =new AbortPolicy();
	*/
private volatile RejectedExecutionHandler handler;

/**
	*除去核心线程之外的线程的存活时间
	*/
private volatile long keepAliveTime;

/**
	*如果false，核心线程即使是空闲状态也是活跃状态
	*如果true，核心线程池存活策略按照keepAliveTime
	*此属性非构造器入参
	*/
private volatile boolean allowCoreThreadTimeOut;

/**
	*线程池最大线程数量
	*/
private volatile int maximumPoolSize;
```

> Worker：线程池中放线程的地方
>
> 继承AQS，实现Runnable
>
> 属性：thread--->线程，firstTask--->要初始化的任务（可能为空），completedTasks--->每个线程的完成任务计数器
>
> 由于继承AQS，Worker中有独占以及释放的方法

```java
private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** Per-thread task counter */
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }



```

> Worker的执行交给了外部的方法runWorker（因为Worker实现了Runnable）
>
> 后面的addWorker中有调用t.start()（后面说）

```java
final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
  			//此处解锁是因为在Worker的构造方法中进行了setState(-1)
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
          //循环获取task，若Worker未带task，则调用getTask()获取task
            while (task != null || (task = getTask()) != null) {
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
              //此处保证，线程池状态为STOP时将线程（若未被设置为中断）设为中断
              //当线程池状态不为STOP时，使用interrupted()，将中断标志位设为false
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                  //执行任务的前置处理
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                      	//执行完任务的后置处理
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
          //调用processWorkerExit销毁Woker
            processWorkerExit(w, completedAbruptly);
        }
    }
```

> 

```

```



> 修改线程池的属性（以上面说到的allowCoreThreadTimeOut为例）
>
> （对于allowCoreThreadTimeOut来说，设置为true需要keepAliveTime大于0）
>
> 修改完属性值后，需要执行interruptIdleWorkers()

```java
public void allowCoreThreadTimeOut(boolean value) {
    if (value && keepAliveTime <= 0)
        throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
    if (value != allowCoreThreadTimeOut) {
        allowCoreThreadTimeOut = value;
        if (value)
            interruptIdleWorkers();
    }
}
```

> interruptIdleWorkers可进行单一中断以及全部中断
>
> interruptIdleWorkers遍历线程池中的所有worker，判断中断标志位，未被中断获取锁，interrupt中断

```java
private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }
    
/**
	*中断可能正在等待任务的线程（如未被锁定所示），以便它们可以检查终止或配置更改。 忽略 SecurityExceptions（在这种情况下，某些线程	*可能保持不间断）
	*/
private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }
```

