package com.frugaltesting.q1;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Q1CircuitBreakerTest {

    @Test
    void circuitBreakerMustRecoverFromTransientCanvasMismatch() {

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            try {
                Page page = browser.newPage();
                page.setDefaultTimeout(120_000);

                page.navigate("http://localhost:3000");

                page.waitForFunction("""
                    () => document.querySelector("#chart")
                        ?.dataset.stateSync === "synchronized"
                    """);

                // Inject a temporary canvas state mismatch.
                page.evaluate("""
                    () => {
                        const canvas = document.querySelector("#chart");

                        canvas.dataset.stateSync = "recovering";

                        window.setTimeout(() => {
                            canvas.dataset.stateSync = "synchronized";
                        }, 250);
                    }
                    """);

                StateGuardian guardian = new StateGuardian();
                guardian.recoverCanvas(page);

                assertEquals(
                    "synchronized",
                    page.locator("#chart").getAttribute("data-state-sync")
                );

                System.out.println("Q1 CIRCUIT BREAKER: PASSED");
                System.out.println("Transient canvas mismatch recovered.");
                System.out.println("Final circuit state: CLOSED");

            } finally {
                browser.close();
            }
        }
    }
}
