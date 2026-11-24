# Frugal_Testing_Assignment

Engineering assignment covering Q1 (Playwright dynamic state synchronization, race-condition handling, payload distortion, network mutation, and circuit-breaker recovery), Q2 (HMAC-SHA256 authentication and tampered replay rejection), and Q3 (Shadow DOM traversal and semantic ARIA locator prompt architecture).

## Repository Structure

- `mock-app/` — Q1 Express dashboard (`http://localhost:3000`) with simulated 2–7 s market jitter and transaction validation.
- `mock-app-q2/` — Q2 Express HMAC-SHA256 gateway (`http://localhost:3001`) verifying `X-Timestamp`, `X-Nonce`, and `X-Signature-Auth`.
- `automation/` — Maven + Java 17 test suite (Playwright `1.56.0`, JUnit Jupiter `5.12.2`, REST Assured `5.5.1`).
- `evidence/` — Captured test screenshots (`evidence/q1/`), Q2 artifact (`evidence/q2/`), and Q3 deliverable (`evidence/q3/Q3ShadowDOMAndSemanticPrompt.md`).

## Running Locally

1. Start the Q1 mock dashboard:
   ```bash
   cd mock-app && npm install && npm start
   ```
2. Start the Q2 HMAC gateway (in a separate terminal):
   ```bash
   cd mock-app-q2 && npm install && npm start
   ```
3. Run the Java automation suite:
   ```bash
   cd automation && mvn test
   ```
