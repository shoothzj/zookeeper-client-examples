# zookeeper-client-examples
描述了一些ZooKeeper客户端编码相关的最佳实践，并提供了可商用的样例代码，供大家研发的时候参考，提升大家接入ZooKeeper的效率。在生产环境上，ZooKeeper的地址信息往往都通过配置中心或者是k8s域名发现的方式获得（如`zookeeper-0.zookeeper:2181,zookeeper-1.zookeeper:2181,zookeeper-2.zookeeper:2181`），这块不是这篇文章描述的重点，以`ZooKeeperConstant.SERVERS`代替。平时开发中需要自己书写`ZooKeeper`客户端的时机场景可以说是少之又少，本文描述几个常见的场景。本文中的例子均已上传到[github](https://github.com/Shoothzj/zookeeper-client-examples)

## 使用ZooKeeper实现分布式Id生成

通过zk获取不一样的机器号，机器号取有序节点最后三位
id格式：

```
机器号 + 日期 + 小时 + 分钟 + 秒 + 5位递增号码
```
以5位来计算，一秒可分近10w个id。

因为只需要在最开始的时候获取机器号，没有必要使用`curator`框架，使用`ZooKeeper`类即可，降低复杂度

```java
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author hezhangjian
 */
@Slf4j
public class ZkIdGenerator {

    private final String path = "/zk-id";

    private final AtomicInteger atomicInteger = new AtomicInteger();

    private final AtomicReference<String> machinePrefix = new AtomicReference<>("");

    private static final String[] AUX_ARRAY = {"", "0", "00", "000", "0000", "00000"};

    /**
     * 通过zk获取不一样的机器号，机器号取有序节点最后三位
     * id格式：
     * 机器号 + 日期 + 小时 + 分钟 + 秒 + 5位递增号码
     * 一秒可分近10w个id
     * 需要对齐可以在每一位补零
     *
     * @return
     */
    public Optional<String> genId() {
        if (machinePrefix.get().equals("")) {
            acquireMachinePrefix();
        }
        if (machinePrefix.get().length() == 0) {
            // get id failed
            return Optional.empty();
        }
        final LocalDateTime now = LocalDateTime.now();
        int aux = atomicInteger.getAndAccumulate(1, ((left, right) -> {
            int val = left + right;
            return val > 99999 ? 1 : val;
        }));
        String time = conv2Str(now.getDayOfYear(), 3) + conv2Str(now.getHour(), 2) + conv2Str(now.getMinute(), 2) + conv2Str(now.getSecond(), 2);
        String suffix = conv2Str(aux, 5);
        return Optional.of(machinePrefix.get() + time + suffix);
    }

    private synchronized void acquireMachinePrefix() {
        if (machinePrefix.get().length() > 0) {
            return;
        }
        try {
            ZooKeeper zooKeeper = new ZooKeeper(ZooKeeperConstant.SERVERS, 30_000, null);
            final String s = zooKeeper.create(path, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
            if (s.length() > 3) {
                machinePrefix.compareAndSet("", s.substring(s.length() - 3));
            }
        } catch (Exception e) {
            log.error("connect to zookeeper failed, exception is ", e);
        }
    }

    private static String conv2Str(int value, int length) {
        if (length > 5) {
            throw new IllegalArgumentException("length should be less than 5");
        }
        String str = String.valueOf(value);
        return AUX_ARRAY[length - str.length()] + str;
    }

}
```



## 使用ZooKeeper实现主备选举

使用ZooKeeper做主备选举，商用代码和demo的主要差距就在能否正确处理**SessionTimeout**，如果不能正确处理**SessionTimeout**，主备选举的代码难以自愈。`LeaderElectionService`接收三个输入参数：

- `scene`为场景，用来防止不同场景下主备选举`zkPath`冲突
- `serverId` serverId用来区分主备不同实例，通常使用`ip`地址或`hostname`
- `LeaderLatchListener` 主备回调函数

```java
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author hezhangjian
 */
@Slf4j
public class LeaderElectionService {

    private final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("zookeeper-init").build();

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1, threadFactory);

    private final CuratorFramework framework;

    private final LeaderLatch leaderLatch;

    private final String zkPath;

    public LeaderElectionService(String scene, String serverId, LeaderLatchListener listener) {
        this.framework = CuratorFrameworkFactory.newClient(ZooKeeperConstant.SERVERS, new ExponentialBackoffRetry(1000, 3));
        this.zkPath = String.format("/election/%s", scene);
        this.leaderLatch = new LeaderLatch(framework, zkPath, serverId);
        leaderLatch.addListener(listener);
        executorService.execute(this::init);
    }

    private void init() {
        initStep1();
        initStep2();
        initStep3();
        executorService.shutdown();
    }

    private void initStep1() {
        while (true) {
            try {
                this.framework.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(zkPath);
                break;
            } catch (Exception e) {
                log.error("create parent path exception is ", e);
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void initStep2() {
        while (true) {
            try {
                this.framework.start();
                break;
            } catch (Exception e) {
                log.error("create parent path exception is ", e);
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void initStep3() {
        while (true) {
            try {
                this.leaderLatch.start();
                break;
            } catch (Exception e) {
                log.error("create parent path exception is ", e);
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void close() {
        if (leaderLatch != null) {
            try {
                leaderLatch.close();
            } catch (Exception e) {
                log.error("leader latch close exception ", e);
            }
        }
        if (framework != null) {
            try {
                framework.close();
            } catch (Exception e) {
                log.error("frame close exception ", e);
            }
        }
    }

    static class ConnListener implements ConnectionStateListener {

        private final String path;

        private final String serverId;

        public ConnListener(String path, String serverId) {
            this.path = path;
            this.serverId = serverId;
        }


        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            if (newState != ConnectionState.LOST) {
                return;
            }
            while (true) {
                try {
                    client.getZookeeperClient().blockUntilConnectedOrTimedOut();
                    client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path, serverId.getBytes(StandardCharsets.UTF_8));
                    break;
                } catch (Exception e) {
                    log.error("rebuild exception ", e);
                }
            }
        }
    }

}
```

## 致谢

感谢 [兵权](https://github.com/BingquanChen)的审稿。