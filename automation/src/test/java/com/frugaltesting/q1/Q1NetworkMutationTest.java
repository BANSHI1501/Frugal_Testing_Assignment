package com.frugaltesting.q1;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Q1NetworkMutationTest {

    @Test
    void dropEveryFourthMarketRequest() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            try {
                Page page = browser.newPage();
                page.setDefaultTimeout(120_000);

                AtomicInteger requests = new AtomicInteger();
                AtomicInteger dropped = new AtomicInteger();

                page.route("**/api/market", route -> {
                    int requestNumber = requests.incrementAndGet();

                    if (requestNumber % 4 == 0) {
                        dropped.incrementAndGet();
                        route.abort();
                    } else {
                        // The mock server supplies 2–7 second latency jitter.
                        route.resume();
                    }
                });

                page.navigate("http://localhost:3000");

                page.waitForFunction("""
                    () => Number(
                        document.querySelector("#received").textContent
                    ) >= 3
                    """);

                page.waitForFunction("""
                    () => Number(
                        document.querySelector("#dropped").textContent
                    ) >= 1
                    """);

                assertTrue(requests.get() >= 4,
                    "Fewer than four market requests were intercepted.");

                assertTrue(dropped.get() >= 1,
                    "No request was dropped by Playwright.");

                assertEquals(0, requests.get() % 4,
                    "Expected to finish on a fourth-request boundary.");

                Files.createDirectories(Path.of("../evidence/q1"));

                page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Path.of("../evidence/q1/q1-network-mutation.png"))
                    .setFullPage(true));

                System.out.println("Q1 NETWORK MUTATION: PASSED");
                System.out.println("Intercepted requests: " + requests.get());
                System.out.println("Dropped by Playwright: " + dropped.get());
                System.out.println("Server latency jitter: 2000–7000 ms");

            } finally {
                browser.close();
            }
        }
    }
}
