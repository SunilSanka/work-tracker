package com.deere.isg.worktracker.spring;

import com.deere.isg.worktracker.DefaultMetricEngine;
import com.deere.isg.worktracker.MetricEngine;
import com.deere.isg.worktracker.RootCauseTurboFilter;
import org.joda.time.Duration;
import org.slf4j.MDC;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.deere.isg.worktracker.MetricEngine.*;
import static java.util.stream.Collectors.groupingBy;

public class DefaultSpringMetrics<W extends SpringWork> {

    public MetricEngine<W> build(Consumer<Bucket> output) {
        return new DefaultMetricEngine.Builder<W>(Duration.standardSeconds(30))
                .collector((b, work) -> {
                    b.getMetric("count", CountMetric.class).increment();
                    MetricSet endpointSet = b.getMetricSet(new Tag("endpoint", work.getEndpoint()));
                    addAllDetails(endpointSet, work);
                    addAllDetails(b, work);
//                    String clientKey = w.getClientKey();
//                    if(clientKey != null) {
//                        addAllDetails(w, endpointSet.getMetricSet("client_key", clientKey));
//                    }

                })
                .outstanding((b, outstanding)->{
                    addOutstanding(b, outstanding.stream().count());
                    outstanding.stream()
                            .collect(groupingBy(SpringWork::getEndpoint, Collectors.counting()))
                            .forEach((k, v) -> addOutstanding(b.getMetricSet(new Tag("endpoint", k)), v));
                }, Duration.standardSeconds(1))

                .output(output)
                .build();

    }

    private void addOutstanding(MetricSet b, long count) {
        b.getMetric("outstanding", LongMetric.class).add(count);
    }

    private void addAllDetails(MetricSet set, W work) {
        addDetails(set, work);

        work.getStatusCode().ifPresent(status->{
            addDetails(set.getMetricSet(new Tag("status", status)), work);
        });

        // unfortunately, the exception name does not get set in the Work's MDC.
        String exceptionName = MDC.get(RootCauseTurboFilter.FIELD_CAUSE_NAME);
        if(exceptionName != null) {
            addDetails(set.getMetricSet(new Tag("error", exceptionName)), work);
        }
    }

    private void addDetails(MetricSet set, W work) {
        set.getMetric("elapsed_millis", LongMetric.class).add(work.getElapsedMillis());

        if (work.isZombie()) {
            set.getMetric("zombie_count", CountMetric.class).increment();
        }

        // these should all get covered by the MDC
//        set.getMetric("thread_count", UniqueMetric.class).add(w.getThreadName());
//        set.getMetric("user_count", UniqueMetric.class).add(w.getRemoteUser());
//        set.getMetric("session_count", UniqueMetric.class).add(w.getSessionId());

        work.getMDC().forEach((key, value) ->
                set.getMetric(key+"_count", UniqueMetric.class).add(value));
    }
}
