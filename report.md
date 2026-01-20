# Report

This report compares the read performance of Pulsar's `ZkMetadataStore` and the trivial ZooKeeper client `getData` method call. The command is executed in a broker pod of a Pulsar cluster and access the ZooKeeper ensemble within the internal network.

## Case 1: default config (batch size is 1000 and batching delay is 5 ms)

[outputs.txt](./example-results/default.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      1.22 ms
  Median (P50): 1.00 ms
  P99:          3.00 ms
  P100 (Max):   4.00 ms

Line 2: metadata store get
  Min:          2.00 ms
  Average:      2.44 ms
  Median (P50): 2.00 ms
  P99:          4.01 ms
  P100 (Max):   6.00 ms
```

The default rate is 100 ops per second.

## Case 2: batch size is 1

Options: `--batch-size 1`

[outputs.txt](./example-results/batch-size-0.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      2.62 ms
  Median (P50): 2.00 ms
  P90:          4.00 ms
  P99:          8.00 ms
  P100 (Max):   12.00 ms

Line 2: metadata store get
  Min:          1.00 ms
  Average:      2.73 ms
  Median (P50): 2.00 ms
  P90:          4.00 ms
  P99:          7.00 ms
  P100 (Max):   12.00 ms
```

The `multi` call performance is similar to the trivial `getData` call overhead.

This case ensures no queueing latency from `ZkMetadataStore` side, which shows the `multi` call overhead.

## Case 3: low get rate

Options: `--rate 5 -n 100`

[outputs.txt](./example-results/rate-5.txt)

```
Line 1: ZK getData
  Min:          1.00 ms
  Average:      1.36 ms
  Median (P50): 1.00 ms
  P99:          4.01 ms
  P100 (Max):   5.00 ms

Line 2: metadata store get
  Min:          5.00 ms
  Average:      5.41 ms
  Median (P50): 5.00 ms
  P99:          7.06 ms
  P100 (Max):   13.00 ms
```

As expected, in this case, with the default configuration, the `ZkMetadataStore` always has 5ms extra latency.

## Case 4: high get rate

Options: `--rate 1000 -n 1000`

[outputs.txt](./example-results/rate-1000.txt)

```
Line 1: ZK getData
  Min:          0.00 ms
  Average:      0.49 ms
  Median (P50): 0.00 ms
  P90:          1.00 ms
  P99:          3.00 ms
  P100 (Max):   22.00 ms

Line 2: metadata store get
  Min:          1.00 ms
  Average:      3.71 ms
  Median (P50): 4.00 ms
  P90:          6.00 ms
  P99:          9.00 ms
  P100 (Max):   25.00 ms
```

Even in this case, the latency of `ZkMetadataStore` that has batching mechanism is still higher than the trivial `getData` call.

Actually it's an extreme case because generally we should not have such a high read rate in production. For example, in a production Pulsar cluster that as ~100 MB/s produce traffic and ~200 MB/s consume traffic on ~70000 topics, the total metadata rate is about 100 ops per second and most brokers have less than 3 ops per second.

## Case 5: very high get rate

Options: `--rate 2000 -n 2000`

[outputs.txt](./example-results/rate-2000.txt)

```
Line 1: ZK getData
  Min:          0.00 ms
  Average:      3.72 ms
  Median (P50): 1.00 ms
  P90:          8.00 ms
  P99:          37.01 ms
  P100 (Max):   44.00 ms

Line 2: metadata store get
  Min:          1.00 ms
  Average:      4.67 ms
  Median (P50): 4.00 ms
  P90:          8.00 ms
  P99:          12.00 ms
  P100 (Max):   15.00 ms
```

Only in this extreme case, the batching mechanism helps to reduce the tailing latency, but the average latency and median latency of trivial `getData` call are still better.
