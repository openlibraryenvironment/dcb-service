#!/bin/bash
curl "$1/metrics/system.cpu.count"| jq
curl "$1/metrics/executor.pool.size"| jq
curl "$1/metrics/process.cpu.usage"| jq
curl "$1/metrics/jvm.threads.live"| jq
curl "$1/metrics/executor.pool.size"| jq
curl "$1/metrics/system.cpu.count"| jq



