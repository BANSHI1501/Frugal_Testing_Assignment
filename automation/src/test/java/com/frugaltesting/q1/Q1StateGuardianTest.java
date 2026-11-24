package com.frugaltesting.q1;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Q1StateGuardianTest {

    @Test
    void dashboardMustReachStableSynchronizedState() throws Exception {

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            try {
                Page page = browser.newPage();
                page.setDefaultTimeout(120_000);

                // Record state transitions and skeleton disappearance.
                page.addInitScript("""
                    (() => {
                        window.__syncHistory = [];
                        window.__skeletonGoneAt = null;

                        const observer = new MutationObserver(() => {
                            const canvas = document.querySelector("#chart");
                            const skeleton = document.querySelector("#skeleton");

                            if (canvas) {
                                const state = canvas.dataset.stateSync;

                                if (state &&
                                    window.__syncHistory[
                                        window.__syncHistory.length - 1
                                    ] !== state) {
                                    window.__syncHistory.push(state);
                                }
                            }

                            if (skeleton &&
                                skeleton.hidden &&
                                window.__skeletonGoneAt === null) {
                                window.__skeletonGoneAt = performance.now();
                            }
                        });

                        observer.observe(document, {
                            subtree: true,
                            childList: true,
                            attributes: true
                        });
                    })();
                    """);

                // Simulate every fourth packet drop via Playwright while keeping server latency jitter.
                AtomicInteger marketRequests = new AtomicInteger();
                page.route("**/api/market", route -> {
                    if (marketRequests.incrementAndGet() % 4 == 0) {
                        route.abort();
                    } else {
                        route.resume();
                    }
                });

                page.navigate("http://localhost:3000");

                // Dynamic multi-condition state guardian.
                page.waitForFunction("""
                    () => {
                        const canvas = document.querySelector("#chart");
                        const skeleton = document.querySelector("#skeleton");
                        const panel = document.querySelector("#action-panel");

                        return canvas !== null
                            && canvas.dataset.stateSync === "synchronized"
                            && skeleton !== null
                            && skeleton.hidden === true
                            && panel !== null
                            && !panel.hidden
                            && canvas.width > 0
                            && canvas.height > 0;
                    }
                    """);

                // Verify the market feed and packet-loss telemetry.
                page.waitForFunction("""
                    () => {
                        const received =
                            Number(document.querySelector("#received").textContent);
                        const dropped =
                            Number(document.querySelector("#dropped").textContent);

                        return received >= 1 && dropped >= 1;
                    }
                    """);

                String finalState = page.locator("#ui-state").textContent().trim();
                String received = page.locator("#received").textContent().trim();
                String dropped = page.locator("#dropped").textContent().trim();

                @SuppressWarnings("unchecked")
                List<String> history =
                    (List<String>) page.evaluate("() => window.__syncHistory");

                assertEquals("synchronized", finalState);
                assertTrue(history.contains("rendering"),
                    "The intermediate rendering state was not observed.");
                assertTrue(history.contains("synchronized"),
                    "The synchronized state was not observed.");

                Files.createDirectories(Path.of("../evidence/q1"));

                page.screenshot(
                    new Page.ScreenshotOptions()
                        .setPath(Path.of("../evidence/q1/q1-state-guardian.png"))
                        .setFullPage(true)
                );

                System.out.println("Q1 STATE GUARDIAN: PASSED");
                System.out.println("Observed state transitions: " + history);
                System.out.println("Final UI state: " + finalState);
                System.out.println("Received packets: " + received);
                System.out.println("Dropped packets: " + dropped);
                System.out.println("Evidence: evidence/q1/q1-state-guardian.png");

            } finally {
                browser.close();
            }
        }
    }
}
