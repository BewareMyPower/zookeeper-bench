#!/usr/bin/env python3
"""
Analyze latencies from outputs.txt file.
Each line contains a log entry with a list of latencies in milliseconds.
Calculates and compares P99 and P100 (max) latencies for each line.
"""

import re
import statistics
from typing import List, Tuple


def parse_latencies_from_line(line: str) -> Tuple[str, List[float]]:
    """
    Extract latencies from a log line.
    
    Args:
        line: A log line containing latencies in brackets
        
    Returns:
        A tuple of (operation_name, list of latencies)
    """
    # Extract the operation name
    match = re.search(r'Latencies of ([^:]+):', line)
    operation_name = match.group(1) if match else "Unknown"
    
    # Extract the list of numbers between brackets
    match = re.search(r'\[([\d,\s]+)\]', line)
    if not match:
        return operation_name, []
    
    # Parse the numbers
    numbers_str = match.group(1)
    latencies = [float(x.strip()) for x in numbers_str.split(',') if x.strip()]
    
    return operation_name, latencies


def calculate_percentile(data: List[float], percentile: float) -> float:
    """
    Calculate the percentile of a dataset.
    
    Args:
        data: List of values
        percentile: Percentile to calculate (0-100)
        
    Returns:
        The value at the given percentile
    """
    if not data:
        return 0.0
    
    sorted_data = sorted(data)
    n = len(sorted_data)
    
    if percentile == 100:
        return sorted_data[-1]
    
    # Using linear interpolation between closest ranks
    rank = (percentile / 100.0) * (n - 1)
    lower_idx = int(rank)
    upper_idx = min(lower_idx + 1, n - 1)
    fraction = rank - lower_idx
    
    return sorted_data[lower_idx] + fraction * (sorted_data[upper_idx] - sorted_data[lower_idx])


def analyze_latencies(filename: str):
    """
    Analyze latencies from the given file.
    
    Args:
        filename: Path to the file containing latency data
    """
    print("=" * 80)
    print("Latency Analysis Report")
    print("=" * 80)
    print()
    
    with open(filename, 'r') as f:
        line_number = 0
        for line in f:
            line = line.strip()
            if not line or '[' not in line:
                continue
            
            line_number += 1
            operation_name, latencies = parse_latencies_from_line(line)
            
            if not latencies:
                print(f"Line {line_number}: No latencies found")
                print()
                continue
            
            # Calculate statistics
            count = len(latencies)
            min_latency = min(latencies)
            max_latency = max(latencies)
            avg_latency = statistics.mean(latencies)
            median_latency = statistics.median(latencies)
            p99 = calculate_percentile(latencies, 99)
            p100 = calculate_percentile(latencies, 100)  # This is the same as max
            
            # Print results
            print(f"Line {line_number}: {operation_name}")
            print(f"  Min:          {min_latency:.2f} ms")
            print(f"  Average:      {avg_latency:.2f} ms")
            print(f"  Median (P50): {median_latency:.2f} ms")
            print(f"  P99:          {p99:.2f} ms")
            print(f"  P100 (Max):   {p100:.2f} ms")
            print()
    
    print("=" * 80)


if __name__ == "__main__":
    import sys
    
    filename = "outputs.txt"
    
    # Allow custom filename as command line argument
    if len(sys.argv) > 1:
        filename = sys.argv[1]
    
    try:
        analyze_latencies(filename)
    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)
