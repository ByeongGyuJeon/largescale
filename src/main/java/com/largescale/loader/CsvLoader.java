package com.largescale.loader;

import com.largescale.config.AppConfig;

/** Extension point for batch and multi-worker implementations. */
public interface CsvLoader {
    LoadResult load(AppConfig config) throws Exception;
}
