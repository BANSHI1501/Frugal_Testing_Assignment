const express = require("express");
const crypto = require("crypto");

const app = express();
const PORT = 3001;

// Keep the secret local for this controlled testing environment.
const SECRET = "frugal-testing-local-secret";
const MAX_TIMESTAMP_DRIFT_SECONDS = 300;

const entities = new Map();
const usedNonces = new Set();

// Preserve the exact request body for HMAC verification.
app.use(express.text({
    type: "application/json",
    limit: "100kb"
}));

function calculateSignature(body, entityId, timestamp, nonce) {
    const message = `${body}\n${entityId}\n${timestamp}\n${nonce}`;

    return crypto
        .createHmac("sha256", SECRET)
        .update(message, "utf8")
        .digest("hex");
}

function safeCompare(expected, received) {
    if (!/^[a-f0-9]{64}$/i.test(received || "")) {
        return false;
    }

    return crypto.timingSafeEqual(
        Buffer.from(expected, "hex"),
        Buffer.from(received, "hex")
    );
}

function parseJsonBody(rawBody) {
    if (typeof rawBody !== "string" || rawBody.trim() === "") {
        return null;
    }
    try {
        return JSON.parse(rawBody);
    } catch {
        return null;
    }
}

app.post("/api/entities", (req, res) => {
    const parsed = parseJsonBody(req.body);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
        return res.status(400).json({
            error: "INVALID_ENTITY_PAYLOAD",
            message: "Expected a JSON object body."
        });
    }

    const id = `ent-${entities.size + 1}`;
    const now = Date.now();
    const entity = {
        id,
        ...parsed,
        createdAt: now,
        updatedAt: now
    };

    entities.set(id, entity);
    res.status(201).json(entity);
});

app.get("/api/entities/:id", (req, res) => {
    const entity = entities.get(req.params.id);
    if (!entity) {
        return res.status(404).json({ error: "ENTITY_NOT_FOUND" });
    }
    res.json(entity);
});

app.put("/api/entities/:id", (req, res) => {
    const entityId = req.params.id;
    const timestamp = req.get("X-Timestamp");
    const nonce = req.get("X-Nonce");
    const signature = req.get("X-Signature-Auth");

    if (!timestamp || !nonce || !signature) {
        return res.status(401).json({
            error: "MISSING_AUTH_HEADERS",
            message: "X-Timestamp, X-Nonce, and X-Signature-Auth headers are required."
        });
    }

    const tsSeconds = Number(timestamp);
    const nowSeconds = Math.floor(Date.now() / 1000);
    if (!Number.isFinite(tsSeconds) || Math.abs(nowSeconds - tsSeconds) > MAX_TIMESTAMP_DRIFT_SECONDS) {
        return res.status(401).json({
            error: "EXPIRED_TIMESTAMP",
            message: "Request timestamp is outside the allowed window."
        });
    }

    const rawBody = typeof req.body === "string" ? req.body : "";
    const expected = calculateSignature(rawBody, entityId, timestamp, nonce);

    if (!safeCompare(expected, signature)) {
        return res.status(401).json({
            error: "SIGNATURE_MISMATCH",
            message: "HMAC-SHA256 verification failed."
        });
    }

    if (usedNonces.has(nonce)) {
        return res.status(401).json({
            error: "REPLAY_DETECTED",
            message: "Nonce has already been used."
        });
    }
    usedNonces.add(nonce);

    const entity = entities.get(entityId);
    if (!entity) {
        return res.status(404).json({ error: "ENTITY_NOT_FOUND" });
    }

    const parsed = parseJsonBody(rawBody);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return res.status(400).json({ error: "INVALID_JSON" });
    }

    Object.assign(entity, parsed, { updatedAt: Date.now() });

    res.json({
        status: "UPDATED",
        entity
    });
});

app.listen(PORT, () => {
    console.log(`Q2 HMAC gateway running at http://localhost:${PORT}`);
});
