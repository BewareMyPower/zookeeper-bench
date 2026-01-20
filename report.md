# Report

This report compares the read performance of Pulsar's `ZkMetadataStore` and the trivial ZooKeeper client `getData` method call. The command is executed in a broker pod of a Pulsar cluster and access the ZooKeeper ensemble within the internal network.

## Case 1: default config (batch size is 1000 and batching delay is 5 ms)

[outputs.txt](./example-results/default.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      1.46 ms
  Median (P50): 1.00 ms
  P90:          2.00 ms
  P99:          4.00 ms
  P100 (Max):   7.00 ms

Line 2: metadata store get
  Min:          3.00 ms
  Average:      4.38 ms
  Median (P50): 4.00 ms
  P90:          5.00 ms
  P99:          7.00 ms
  P100 (Max):   12.00 ms
```

The default rate is 100 ops per second.

## Case 2: batching disabled

Options: `--batch-size 0`

[outputs.txt](./example-results/batch-size-0.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      1.67 ms
  Median (P50): 1.00 ms
  P90:          3.00 ms
  P99:          5.00 ms
  P100 (Max):   13.00 ms

Line 2: metadata store get
  Min:          1.00 ms
  Average:      2.31 ms
  Median (P50): 2.00 ms
  P90:          4.00 ms
  P99:          6.00 ms
  P100 (Max):   15.00 ms
```

The overhead of `ZkMetadataStore` without batching is small.

## Case 3: low get rate

Options: `--rate 5 -n 300`

[outputs.txt](./example-results/rate-5.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      1.95 ms
  Median (P50): 2.00 ms
  P90:          3.00 ms
  P99:          5.00 ms
  P100 (Max):   7.00 ms

Line 2: metadata store get
  Min:          3.00 ms
  Average:      3.58 ms
  Median (P50): 3.00 ms
  P90:          5.00 ms
  P99:          7.00 ms
  P100 (Max):   8.00 ms
```

As expected, in this case, with the default configuration, the `ZkMetadataStore` always has 5ms extra latency.

## Case 4: high get rate

Options: `--rate 1000 -n 1000`

[outputs.txt](./example-results/rate-1000.txt)

```
Line 1: ZK getData
  Min:          0.00 ms
  Average:      5.04 ms
  Median (P50): 2.00 ms
  P90:          15.00 ms
  P99:          28.00 ms
  P100 (Max):   37.00 ms

Line 2: metadata store get
  Min:          1.00 ms
  Average:      3.89 ms
  Median (P50): 4.00 ms
  P90:          6.00 ms
  P99:          15.00 ms
  P100 (Max):   19.00 ms
```

In this case, the tailing latencies (P90 or higher) of `ZkMetadataStore` are significantly better than the direct `getData` calls.

Actually it's an extreme case because generally we should not have such a high read rate in production. For example, in a production Pulsar cluster that as ~100 MB/s produce traffic and ~200 MB/s consume traffic on ~70000 topics, the total metadata rate is about 100 ops per second and most brokers have less than 3 ops per second.
