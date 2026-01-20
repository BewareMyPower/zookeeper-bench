/*
 * Copyright 2026 Yunze Xu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bewaremypower;

import com.google.common.util.concurrent.RateLimiter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.bookkeeper.metastore.MetastoreException;
import org.apache.bookkeeper.zookeeper.BoundExponentialBackoffRetryPolicy;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.pulsar.metadata.api.GetResult;
import org.apache.pulsar.metadata.api.MetadataStoreConfig;
import org.apache.pulsar.metadata.api.MetadataStoreFactory;
import org.apache.pulsar.metadata.api.Stat;
import org.apache.pulsar.metadata.impl.PulsarZooKeeperClient;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Slf4j
@Command(
    name = "zookeeper-bench",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Benchmark tool for ZooKeeper get requests")
public class App implements Callable<Integer> {

  @Parameters(index = "0", description = "ZooKeeper connection URL (e.g., localhost:2181)")
  private String zkUrl;

  @Option(
      names = {"-r", "--rate"},
      description = "Rate of get requests per second (default: ${DEFAULT-VALUE})",
      defaultValue = "100")
  private int rate;

  @Option(
      names = {"-n"},
      description = "Number of get requests to perform (default: ${DEFAULT-VALUE})",
      defaultValue = "1000")
  private int numRequests;

  @Option(
      names = {"--batch-size"},
      description = "Batch size for metadata store (default: ${DEFAULT-VALUE})",
      defaultValue = "1000")
  private int batchSize;

  @Option(
      names = {"--warm-up-count"},
      description = "Number of warm-up get requests (default: ${DEFAULT-VALUE})",
      defaultValue = "500")
  private int warmUpCount;

  @Override
  public Integer call() throws Exception {
    log.info("ZooKeeper URL: {}, batch size: {}", zkUrl, batchSize);

    try (final var metadataStore =
        MetadataStoreFactory.create(zkUrl, MetadataStoreConfig.builder().build())) {
      for (int i = 0; i < 20; i++) {
        final var path =
            "/managed-ledgers/"
                + TopicName.get("my-topic-" + i).getPartition(0).getPersistenceNamingEncoding();
        final var data = ("metadata-for-my-topic-" + i).getBytes();
        try {
          metadataStore.put(path, data, Optional.empty()).get();
        } catch (Exception ignored) {
        }
      }
    } catch (Exception e) {
      log.error("Failed to create metadata store", e);
      return 1;
    }

    @Cleanup
    final var metadataStore =
        MetadataStoreFactory.create(
            zkUrl, MetadataStoreConfig.builder().batchingMaxOperations(batchSize).build());
    final var executor = Executors.newSingleThreadExecutor();
    final var zooKeeper =
        PulsarZooKeeperClient.newBuilder()
            .connectString(zkUrl)
            .connectRetryPolicy(
                new BoundExponentialBackoffRetryPolicy(100, 60_000, Integer.MAX_VALUE))
            .watchers(
                Set.of(
                    new Watcher() {
                      @Override
                      public void process(WatchedEvent event) {
                        executor.execute(() -> log.info("Received event {}", event));
                      }
                    }))
            .build();
    log.info("Warming up...");
    for (int i = 0; i < warmUpCount; i++) {
      final var path = getPath(i);
      final var result1 = metadataStore.get(path).get();
      final var result2 = get(zooKeeper, path, executor).get();
      if (result1.isPresent()) {
        if (result2.isEmpty()) {
          throw new IllegalStateException("Inconsistent results for path " + path);
        }
        if (!result1.get().equals(result2.get())) {
          throw new IllegalStateException("Inconsistent results for path " + path);
        }
      } else {
        if (result2.isPresent()) {
          throw new IllegalStateException("Inconsistent results for path " + path);
        }
      }
    }
    log.info("Warm up is done");

    final var paths = new ArrayList<String>();
    for (int i = 0; i < numRequests; i++) {
      paths.add(getPath(i % 30));
    }

    final var limiter = RateLimiter.create(rate);
    final var futures = new ArrayList<CompletableFuture<Long>>();

    for (final var path : paths) {
      limiter.acquire();
      final var start = System.nanoTime();
      futures.add(
          get(zooKeeper, path, executor)
              .thenApply(__ -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }
    log.info("Latencies of ZK getData: {}", futures.stream().map(CompletableFuture::join).toList());
    futures.clear();

    for (final var path : paths) {
      limiter.acquire();
      final var start = System.nanoTime();
      futures.add(
          metadataStore
              .get(path)
              .thenApply(v -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)));
    }
    log.info(
        "Latencies of metadata store get: {}",
        futures.stream().map(CompletableFuture::join).toList());

    executor.shutdown();
    return 0;
  }

  // The topic `my-topic-<i>-partition-0`'s metadata path in Pulsar
  private static String getPath(int i) {
    return "/managed-ledgers/"
        + TopicName.get("my-topic-" + i).getPartition(0).getPersistenceNamingEncoding();
  }

  private static CompletableFuture<Optional<GetResult>> get(
      PulsarZooKeeperClient zkc, String path, Executor executor) {
    final var future = new CompletableFuture<Optional<GetResult>>();
    zkc.getData(
        path,
        null,
        (rc, p, ctx, data, zkStat) -> {
          executor.execute(
              () -> {
                final var code = Code.get(rc);
                if (code == Code.OK) {
                  final var stat =
                      new Stat(
                          path,
                          zkStat.getVersion(),
                          zkStat.getCtime(),
                          zkStat.getMtime(),
                          zkStat.getEphemeralOwner() != 0L,
                          zkStat.getEphemeralOwner() == zkc.getSessionId());
                  future.complete(Optional.of(new GetResult(data, stat)));
                } else if (code == Code.NONODE) {
                  future.complete(Optional.empty());
                } else {
                  future.completeExceptionally(new MetastoreException("ZK error: " + code));
                }
              });
        },
        null);
    return future;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new App()).execute(args);
    System.exit(exitCode);
  }
}
