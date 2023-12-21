/*
 *  Copyright 2022 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.service.util;

import io.github.maksymdolgykh.dropwizard.micrometer.MicrometerBundle;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.openmetadata.service.monitoring.EventMonitorConfiguration;

public class MicrometerBundleSingleton {
  private static final MicrometerBundle instance = new MicrometerBundle();
  // We'll use this registry to add monitoring around Ingestion Pipelines
  public static final PrometheusMeterRegistry prometheusMeterRegistry =
      MicrometerBundle.prometheusRegistry;
  public static Timer webAnalyticEvents;

  private MicrometerBundleSingleton() {}

  public static MicrometerBundle getInstance() {
    return instance;
  }

  public static Timer latencyTimer(EventMonitorConfiguration configuration) {
    return Timer.builder("latency_requests")
        .description("Request latency in seconds.")
        .publishPercentiles(configuration.getLatency())
        .publishPercentileHistogram()
        .register(prometheusMeterRegistry);
  }
}
