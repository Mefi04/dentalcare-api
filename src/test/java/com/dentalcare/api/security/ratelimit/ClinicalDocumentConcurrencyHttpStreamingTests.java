package com.dentalcare.api.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalcare.api.exception.GlobalExceptionHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class ClinicalDocumentConcurrencyHttpStreamingTests {

    @Test
    void downloadPermitIsHeldDuringStreamingThenReleasedAndStreamClosed() throws Exception {
        StreamingController controller = new StreamingController();
        BlockingInputStream stream = new BlockingInputStream();
        controller.stream.set(stream);
        MockMvc mvc = mvc(controller);

        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> first = executor.submit(() -> mvc.perform(download()).andReturn());
            assertThat(stream.started.await(5, TimeUnit.SECONDS)).isTrue();

            mvc.perform(download()).andExpect(status().isTooManyRequests());
            stream.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        }

        assertThat(stream.closed).isTrue();
        controller.stream.set(new ByteArrayInputStream(new byte[]{2}));
        mvc.perform(download()).andExpect(status().isOk());
    }

    @Test
    void downloadPermitAndStreamAreReleasedWhenStreamingFails() throws Exception {
        StreamingController controller = new StreamingController();
        FailingInputStream stream = new FailingInputStream();
        controller.stream.set(stream);
        MockMvc mvc = mvc(controller);

        try {
            mvc.perform(download()).andReturn();
        } catch (Exception expected) {
            assertThat(expected).hasRootCauseInstanceOf(IOException.class);
        }

        assertThat(stream.closed).isTrue();
        controller.stream.set(new ByteArrayInputStream(new byte[]{3}));
        mvc.perform(download()).andExpect(status().isOk());
    }

    private MockMvc mvc(StreamingController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new ClinicalDocumentConcurrencyInterceptor(1, 1))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder download() {
        return get("/api/v1/patients/me/documents/{id}/download", "00000000-0000-0000-0000-000000000001");
    }

    @RestController
    static class StreamingController {
        private final AtomicReference<InputStream> stream = new AtomicReference<>();

        @GetMapping("/api/v1/patients/me/documents/{id}/download")
        ResponseEntity<InputStreamResource> download(@PathVariable String id) {
            return ResponseEntity.ok(new InputStreamResource(stream.get()));
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private boolean sent;
        private volatile boolean closed;

        @Override
        public int read() throws IOException {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out waiting for test release");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", exception);
            }
            if (sent) return -1;
            sent = true;
            return 1;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final AtomicBoolean first = new AtomicBoolean(true);
        private volatile boolean closed;

        @Override
        public int read() throws IOException {
            if (first.getAndSet(false)) return 1;
            throw new IOException("Simulated client transfer failure");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
