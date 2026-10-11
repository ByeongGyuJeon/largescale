package com.largescale.loader;

public record LoadResult(long committedRows, double elapsedSeconds) {
    public double rowsPerSecond() { return elapsedSeconds > 0 ? committedRows / elapsedSeconds : 0; }
}
