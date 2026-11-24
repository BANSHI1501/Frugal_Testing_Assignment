package com.frugaltesting.q1;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class Q1RaceConditionTest {

    @Test
    void simulateTripleClickAfterSkeletonDisappears() throws Exception {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            try {
                Page page = browser.newPage();
                page.setDefaultTimeout(120_000);

                List<Integer> transactionStatuses = new CopyOnWriteArrayList<>();
                page.onResponse(response -> {
                    if (response.url().endsWith("/api/transaction")) {
                        transactionStatuses.add(response.status());
                    }
                });

                // Record when the skeleton disappears and timestamps of validate-button clicks.
                page.addInitScript("""
                    (() => {
                        window.__skeletonGoneAt = null;
                        window.__clickTimestamps = [];

                        const observer = new MutationObserver(() => {
                            const skeleton = document.querySelector("#skeleton");
                            if (skeleton && skeleton.hidden && window.__skeletonGoneAt === null) {
                                window.__skeletonGoneAt = performance.now();
                            }
                        });

                        observer.observe(document, {
                            subtree: true,
                            childList: true,
                            attributes: true
                        });

                        document.addEventListener("click", (event) => {
                            if (event.target && event.target.id === "validate-button") {
                                window.__clickTimestamps.push(performance.now());
                            }
                        }, true);
                    })();
                    """);

                page.navigate("http://localhost:3000");

                // Wait dynamically until the dashboard is synchronized and 60 ms have elapsed since skeleton disappearance.
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
                            && window.__skeletonGoneAt !== null
                            && (performance.now() - window.__skeletonGoneAt) >= 60;
                    }
                    """);

                Locator validateButton = page.locator("#validate-button");
                for (int i = 0; i < 3; i++) {
                    validateButton.click();
                }

                // Wait for all 3 transaction clicks and responses, and verify synchronized state persists.
                page.waitForFunction("""
                    () => {
                        const canvas = document.querySelector("#chart");
                        const notification = document.querySelector("#notification");
                        return window.__clickTimestamps.length === 3
                            && canvas !== null
                            && canvas.dataset.stateSync === "synchronized"
                            && notification !== null
                            && notification.textContent.trim() === "Transaction validated successfully.";
                    }
                    """);

                // Ensure all 3 backend /api/transaction responses have arrived.
                page.waitForCondition(() -> transactionStatuses.size() == 3);

                @SuppressWarnings("unchecked")
                List<Number> clickTimestamps =
                    (List<Number>) page.evaluate("() => window.__clickTimestamps");
                double skeletonGoneAt =
                    ((Number) page.evaluate("() => window.__skeletonGoneAt")).doubleValue();

                double firstClickDelayMs = clickTimestamps.get(0).doubleValue() - skeletonGoneAt;
                double burstSpanMs =
                    clickTimestamps.get(2).doubleValue() - clickTimestamps.get(0).doubleValue();
                String finalState = page.locator("#ui-state").textContent().trim();
                String notificationText = page.locator("#notification").textContent().trim();

                assertEquals(3, clickTimestamps.size(), "Expected 3 clicks on #validate-button.");
                assertEquals(List.of(200, 200, 200), transactionStatuses,
                    "All 3 rapid transaction requests should return HTTP 200.");
                assertTrue(firstClickDelayMs >= 60.0,
                    "First click occurred earlier than 60 ms after skeleton disappearance: " + firstClickDelayMs);
                assertEquals("synchronized", finalState);
                assertEquals("Transaction validated successfully.", notificationText);

                Files.createDirectories(Path.of("../evidence/q1"));

                page.screenshot(
                    new Page.ScreenshotOptions()
                        .setPath(Path.of("../evidence/q1/q1-race-condition.png"))
                        .setFullPage(true)
                );

                System.out.println("Q1 RACE CONDITION TEST: PASSED");
                System.out.printf("Measured delay after skeleton disappeared: %.2f ms%n", firstClickDelayMs);
                System.out.printf("Triple-click burst span: %.2f ms%n", burstSpanMs);
                System.out.println("Transaction HTTP statuses: " + transactionStatuses);
                System.out.println("Final UI state: " + finalState);
                System.out.println("Notification: " + notificationText);
                System.out.println("Evidence: evidence/q1/q1-race-condition.png");

            } finally {
                browser.close();
            }
        }
    }
}
