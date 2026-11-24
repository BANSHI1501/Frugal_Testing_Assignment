const express = require("express");
const path = require("path");

const app = express();
const PORT = 3000;

app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

let packetCounter = 0;
let price = 95000;

app.get("/api/market", async (req, res) => {
  packetCounter++;

  // Simulated network jitter: 2–7 seconds.
  const latency = 2000 + Math.floor(Math.random() * 5001);
  await new Promise(resolve => setTimeout(resolve, latency));

  price += (Math.random() - 0.5) * 100;

  res.json({
    symbol: "BTC/USDT",
    price: Number(price.toFixed(2)),
    packet: packetCounter,
    timestamp: Date.now()
  });
});

app.post("/api/transaction", (req, res) => {
  const { symbol, quantity } = req.body;

  if (
    typeof quantity !== "number" ||
    !Number.isFinite(quantity) ||
    quantity <= 0
  ) {
    return res.status(400).json({
      error: "INVALID_QUANTITY",
      message: "Quantity must be a positive finite number."
    });
  }

  res.json({
    status: "VALIDATED",
    symbol,
    quantity
  });
});

app.listen(PORT, () => {
  console.log(`Mock dashboard running at http://localhost:${PORT}`);
});
