# zookeeper-bench

A benchmarking tool to measure the performance for different approaches of using Apache ZooKeeper client.

## Build

```bash
mvn clean install -DskipTests
```

Run the benchmark and analyze it (assuming the ZK url is `127.0.0.1:2181`):

```bash
java -jar ./target/app-1.0-SNAPSHOT.jar 127.0.0.1:2181
grep Latencies logs/application.log > outputs.txt
python3 analyze_latencies.py outputs.txt
```
