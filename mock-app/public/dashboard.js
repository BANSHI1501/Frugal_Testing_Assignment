const canvas = document.querySelector("#chart");
const ctx = canvas.getContext("2d");
const skeleton = document.querySelector("#skeleton");
const actionPanel = document.querySelector("#action-panel");
const priceElement = document.querySelector("#price");
const stateElement = document.querySelector("#ui-state");
const packetElement = document.querySelector("#packet-info");
const notification = document.querySelector("#notification");

let prices = [];
let received = 0;
let dropped = 0;
let initialized = false;
let polling = false;

function updateState(state) {
  canvas.dataset.stateSync = state;
  stateElement.textContent = state;
}

function drawChart() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  if (prices.length < 2) return;

  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;

  ctx.beginPath();
  ctx.strokeStyle = "#4ade80";
  ctx.lineWidth = 3;

  prices.forEach((value, index) => {
    const x = index * canvas.width / (prices.length - 1);
    const y = canvas.height - ((value - min) / range) * (canvas.height - 30) - 15;

    if (index === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });

  ctx.stroke();
}

async function fetchMarketData() {
  if (polling) return;
  polling = true;

  try {
    const response = await fetch("/api/market");

    if (!response.ok) {
      dropped++;
      document.querySelector("#dropped").textContent = dropped;
      return;
    }

    const data = await response.json();
    prices.push(data.price);
    if (prices.length > 30) prices.shift();

    received++;
    priceElement.textContent = "$" + data.price.toFixed(2);
    packetElement.textContent = `Packet #${data.packet}`;
    document.querySelector("#received").textContent = received;

    updateState("rendering");
    drawChart();

    if (!initialized) {
      initialized = true;
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          skeleton.hidden = true;
          actionPanel.hidden = false;
          updateState("synchronized");
        });
      });
    } else {
      updateState("synchronized");
    }
  } catch (error) {
    dropped++;
    document.querySelector("#dropped").textContent = dropped;

    notification.textContent = "Market data unavailable. Recovering safely.";
  } finally {
    polling = false;
  }
}

document.querySelector("#validate-button")
  .addEventListener("click", async () => {
    const response = await fetch("/api/transaction", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ symbol: "BTC/USDT", quantity: 1 })
    });

    notification.textContent = response.ok
      ? "Transaction validated successfully."
      : "Transaction validation rejected.";
  });

document.querySelector("#distort-button")
  .addEventListener("click", async () => {
    updateState("distorted");

    try {
      const response = await fetch("/api/transaction", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: '{"symbol":"BTC/USDT","quantity":0xFF}'
      });

      if (!response.ok) {
        updateState("fallback");
        notification.textContent = "SECURITY WARNING: Malformed payload rejected.";
      } else {
        notification.textContent = "Unexpected response: malformed input accepted.";
      }
    } catch (error) {
      updateState("fallback");
      notification.textContent = "SAFE FALLBACK: Malformed payload parsing failed.";
    }
  });

updateState("loading");
fetchMarketData();
setInterval(fetchMarketData, 1000);
