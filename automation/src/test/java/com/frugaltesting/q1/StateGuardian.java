package com.frugaltesting.q1;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;

public class StateGuardian {

    private enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    private CircuitState state = CircuitState.CLOSED;

    private int failures = 0;
    private final int failureThreshold = 3;

    public void recoverCanvas(Page page) {

        long backoffMs = 100;
        final long maximumBackoffMs = 800;

        while (true) {
            try {
                state = failures == 0
                    ? CircuitState.CLOSED
                    : CircuitState.HALF_OPEN;

                page.waitForFunction("""
                    () => {
                        const canvas = document.querySelector("#chart");
                        const skeleton = document.querySelector("#skeleton");
                        const panel = document.querySelector("#action-panel");

                        return canvas
                            && canvas.dataset.stateSync === "synchronized"
                            && skeleton?.hidden === true
                            && panel && !panel.hidden
                            && canvas.getBoundingClientRect().width > 0;
                    }
                    """,
                    null,
                    new Page.WaitForFunctionOptions()
                        .setTimeout(backoffMs)
                );

                state = CircuitState.CLOSED;
                failures = 0;

                System.out.println("Circuit breaker: CLOSED - UI recovered");
                return;

            } catch (TimeoutError error) {
                failures++;

                System.out.println(
                    "Recovery attempt " + failures
                    + " failed. Timeout window: " + backoffMs + " ms"
                );

                if (failures >= failureThreshold) {
                    state = CircuitState.OPEN;

                    System.out.println("Circuit breaker: OPEN");

                    throw new AssertionError(
                        "Circuit breaker OPEN: UI did not recover.",
                        error
                    );
                }

                backoffMs = Math.min(backoffMs * 2, maximumBackoffMs);
            }
        }
    }
}
