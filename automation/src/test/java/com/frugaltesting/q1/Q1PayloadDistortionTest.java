package com.frugaltesting.q1;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Q1PayloadDistortionTest {

    @Test
    void modifiedMathematicalPayloadMustBeRejected() throws Exception {

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
            );

            try {
                Page page = browser.newPage();
                page.setDefaultTimeout(30_000);

                AtomicReference<String> interceptedPayload =
                    new AtomicReference<>();

                AtomicReference<Integer> responseStatus =
                    new AtomicReference<>();

                page.onResponse(response -> {
                    if (response.url().contains("/api/transaction")) {
                        responseStatus.set(response.status());
                    }
                });

                // Intercept and distort the transaction request.
                page.route("**/api/transaction", route -> {
                    String originalBody = route.request().postData();

                    assertNotNull(originalBody,
                        "Transaction request body was empty.");

                    String modifiedBody = originalBody.replace(
                        "\"quantity\":1",
                        "\"quantity\":\"0xFF\""
                    );

                    assertNotEquals(originalBody, modifiedBody,
                        "Payload distortion did not happen.");

                    interceptedPayload.set(modifiedBody);

                    route.resume(
                        new Route.ResumeOptions()
                            .setPostData(modifiedBody)
                    );
                });

                page.navigate("http://localhost:3000");

                // Wait until the dashboard is ready.
                page.waitForFunction("""
                    () => {
                        const canvas = document.querySelector("#chart");
                        const skeleton = document.querySelector("#skeleton");
                        const panel = document.querySelector("#action-panel");

                        return canvas?.dataset.stateSync === "synchronized"
                            && skeleton?.hidden === true
                            && panel && !panel.hidden;
                    }
                    """);

                page.locator("#validate-button").click();

                // Wait for the rejection response and visible feedback.
                page.waitForFunction("""
                    () => document.querySelector("#notification")
                        ?.textContent.includes("Transaction validation rejected.")
                    """);

                assertNotNull(interceptedPayload.get(),
                    "No transaction payload was intercepted.");

                assertTrue(interceptedPayload.get()
                        .contains("\"quantity\":\"0xFF\""),
                    "The distorted mathematical value was not sent.");

                assertEquals(400, responseStatus.get(),
                    "The server did not reject the modified payload.");

                assertEquals(
                    "Transaction validation rejected.",
                    page.locator("#notification").textContent().trim()
                );

                Files.createDirectories(Path.of("../evidence/q1"));

                page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Path.of("../evidence/q1/q1-payload-distortion.png"))
                    .setFullPage(true));

                System.out.println("Q1 PAYLOAD DISTORTION: PASSED");
                System.out.println("Modified payload: " + interceptedPayload.get());
                System.out.println("HTTP response: " + responseStatus.get());
                System.out.println("UI rejection message verified.");

            } finally {
                browser.close();
            }
        }
    }
}
